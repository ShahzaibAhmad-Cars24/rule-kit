package com.cars24.rulekit.core.evaluation;

import com.cars24.rulekit.core.model.ConditionDefinition;
import com.cars24.rulekit.core.model.ConditionGroup;
import com.cars24.rulekit.core.model.ConditionGroupNode;
import com.cars24.rulekit.core.model.ConditionKind;
import com.cars24.rulekit.core.model.ConditionLeaf;
import com.cars24.rulekit.core.model.ConditionNode;
import com.cars24.rulekit.core.model.ExecutionMode;
import com.cars24.rulekit.core.model.RuleDefinition;
import com.cars24.rulekit.core.model.RuleThen;
import com.cars24.rulekit.core.operator.OperatorValueParser;
import com.cars24.rulekit.core.operator.RuleKitOperator;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

public final class CompiledRuleSet {

    private final String ruleSetId;
    private final String schemaVersion;
    private final ExecutionMode executionMode;
    private final JsonNode defaultResponse;
    private final List<RuleDefinition> rules;
    private final List<CompiledRule> compiledRules;
    private final CompiledRuleSetMetadata metadata;

    /** Defaults to FIRST_MATCH. */
    public CompiledRuleSet(String ruleSetId,
                           String schemaVersion,
                           JsonNode defaultResponse,
                           List<RuleDefinition> rules) {
        this(ruleSetId, schemaVersion, ExecutionMode.FIRST_MATCH, defaultResponse, rules);
    }

    public CompiledRuleSet(String ruleSetId,
                           String schemaVersion,
                           ExecutionMode executionMode,
                           JsonNode defaultResponse,
                           List<RuleDefinition> rules) {
        this.ruleSetId = ruleSetId;
        this.schemaVersion = schemaVersion;
        this.executionMode = executionMode != null ? executionMode : ExecutionMode.FIRST_MATCH;
        this.defaultResponse = deepCopy(defaultResponse);
        this.rules = copyRules(rules);
        this.compiledRules = this.rules.stream()
                .map(CompiledRuleSet::compileRule)
                .toList();
        this.metadata = collectMetadata(this.rules);
    }

    public String ruleSetId()         { return ruleSetId; }
    public String schemaVersion()     { return schemaVersion; }
    public ExecutionMode executionMode() { return executionMode; }
    public JsonNode defaultResponse() { return deepCopy(defaultResponse); }
    public List<RuleDefinition> rules() { return copyRules(rules); }
    public CompiledRuleSetMetadata metadata() { return metadata; }
    List<CompiledRule> compiledRules() { return compiledRules; }

    // -------------------------------------------------------------------------
    // Rule copy (defensive)
    // -------------------------------------------------------------------------

    private static List<RuleDefinition> copyRules(List<RuleDefinition> rules) {
        if (rules == null || rules.isEmpty()) return List.of();
        return rules.stream().map(CompiledRuleSet::copyRule).toList();
    }

    private static RuleDefinition copyRule(RuleDefinition rule) {
        // Deep-copy the tree so mutations to source JsonNode values don't affect compiled evaluation
        ConditionGroup when = rule.when() != null
                ? new ConditionGroup(
                        deepCopyTree(rule.when().tree()),
                        deepCopyConditions(rule.when().all())
                )
                : null;
        RuleThen then = rule.then() != null
                ? new RuleThen(deepCopy(rule.then().response()))
                : null;
        return new RuleDefinition(rule.id(), rule.priority(), rule.enabled(), when, rule.rollout(), then);
    }

    /**
     * Deep-copies a {@link ConditionNode} tree, including all {@link JsonNode} values
     * inside {@link ConditionLeaf} nodes, so that post-compile mutations to the source
     * have no effect on the compiled rule set.
     */
    private static ConditionNode deepCopyTree(ConditionNode node) {
        if (node == null) return null;
        if (node instanceof ConditionLeaf leaf) {
            return new ConditionLeaf(
                    leaf.kind(),
                    leaf.fieldRef(),
                    leaf.operator(),
                    deepCopy(leaf.value()),
                    deepCopy(leaf.valueTo()),
                    leaf.segmentNames(),
                    leaf.match(),
                    leaf.lookupRef(),
                    leaf.ruleSetId(),
                    leaf.expect()
            );
        } else if (node instanceof ConditionGroupNode group) {
            List<ConditionNode> copiedChildren = group.children() == null ? List.of()
                    : group.children().stream().map(CompiledRuleSet::deepCopyTree).toList();
            return new ConditionGroupNode(group.op(), copiedChildren);
        }
        throw new IllegalArgumentException("Unknown ConditionNode type: " + node.getClass());
    }

    private static List<ConditionDefinition> deepCopyConditions(List<ConditionDefinition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return List.of();
        }
        return conditions.stream()
                .map(condition -> new ConditionDefinition(
                        condition.kind(),
                        condition.fieldRef(),
                        condition.operator(),
                        deepCopy(condition.value()),
                        deepCopy(condition.valueTo()),
                        condition.segmentNames(),
                        condition.match(),
                        condition.lookupRef(),
                        condition.ruleSetId(),
                        condition.expect()
                ))
                .toList();
    }

    // -------------------------------------------------------------------------
    // Rule compilation
    // -------------------------------------------------------------------------

    private static CompiledRule compileRule(RuleDefinition rule) {
        return new CompiledRule(rule);
    }

    // -------------------------------------------------------------------------
    // On-the-fly condition compilation (used by tree leaf evaluation)
    // -------------------------------------------------------------------------

    static CompiledCondition compileCondition(ConditionDefinition condition) {
        if (condition.resolvedKind() != ConditionKind.FIELD) {
            return new CompiledCondition(condition, null, List.of(), 0.0, 0.0, null);
        }
        RuleKitOperator operator = RuleKitOperator.from(condition.operator())
                .orElseThrow(() -> new IllegalArgumentException("Unsupported operator: " + condition.operator()));
        Pattern regexPattern = operator.regex() && condition.value() != null && !condition.value().isNull()
                ? Pattern.compile(condition.value().asText())
                : null;
        return new CompiledCondition(
                condition,
                operator,
                operator.listBased() ? OperatorValueParser.expectedValues(condition.value()) : List.of(),
                operator.numeric() ? OperatorValueParser.asNumber(condition.value()).orElse(0.0) : 0.0,
                operator == RuleKitOperator.BETWEEN ? OperatorValueParser.asNumber(condition.valueTo()).orElse(0.0) : 0.0,
                regexPattern
        );
    }

    private static JsonNode deepCopy(JsonNode node) {
        return node == null ? null : node.deepCopy();
    }

    private static CompiledRuleSetMetadata collectMetadata(List<RuleDefinition> rules) {
        LinkedHashSet<String> segmentNames = new LinkedHashSet<>();
        LinkedHashSet<String> dependencyRuleSetIds = new LinkedHashSet<>();
        if (rules != null) {
            for (RuleDefinition rule : rules) {
                if (rule == null || rule.when() == null) {
                    continue;
                }
                collectMetadata(rule.when().rootNode(), segmentNames, dependencyRuleSetIds);
            }
        }
        return new CompiledRuleSetMetadata(List.copyOf(segmentNames), List.copyOf(dependencyRuleSetIds));
    }

    private static void collectMetadata(ConditionNode node,
                                        LinkedHashSet<String> segmentNames,
                                        LinkedHashSet<String> dependencyRuleSetIds) {
        if (node == null) {
            return;
        }
        if (node instanceof ConditionLeaf leaf) {
            ConditionDefinition definition = leaf.toConditionDefinition();
            if (definition.resolvedKind() == ConditionKind.SEGMENT) {
                segmentNames.addAll(definition.segmentNames());
            } else if (definition.resolvedKind() == ConditionKind.DEPENDENCY && definition.ruleSetId() != null) {
                dependencyRuleSetIds.add(definition.ruleSetId());
            }
            return;
        }
        if (node instanceof ConditionGroupNode group && group.children() != null) {
            for (ConditionNode child : group.children()) {
                collectMetadata(child, segmentNames, dependencyRuleSetIds);
            }
        }
    }
}

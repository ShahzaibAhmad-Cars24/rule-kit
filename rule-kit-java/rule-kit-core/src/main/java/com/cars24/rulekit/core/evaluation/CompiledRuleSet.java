package com.cars24.rulekit.core.evaluation;

import com.cars24.rulekit.core.model.ConditionDefinition;
import com.cars24.rulekit.core.model.ConditionGroup;
import com.cars24.rulekit.core.model.ConditionKind;
import com.cars24.rulekit.core.model.RuleDefinition;
import com.cars24.rulekit.core.model.RuleThen;
import com.cars24.rulekit.core.operator.OperatorValueParser;
import com.cars24.rulekit.core.operator.RuleKitOperator;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.regex.Pattern;

public final class CompiledRuleSet {

    private final String ruleSetId;
    private final String schemaVersion;
    private final JsonNode defaultResponse;
    private final List<RuleDefinition> rules;
    private final List<CompiledRule> compiledRules;

    public CompiledRuleSet(String ruleSetId,
                           String schemaVersion,
                           JsonNode defaultResponse,
                           List<RuleDefinition> rules) {
        this.ruleSetId = ruleSetId;
        this.schemaVersion = schemaVersion;
        this.defaultResponse = deepCopy(defaultResponse);
        this.rules = copyRules(rules);
        this.compiledRules = this.rules.stream()
                .map(CompiledRuleSet::compileRule)
                .toList();
    }

    public String ruleSetId() {
        return ruleSetId;
    }

    public String schemaVersion() {
        return schemaVersion;
    }

    public JsonNode defaultResponse() {
        return deepCopy(defaultResponse);
    }

    public List<RuleDefinition> rules() {
        return copyRules(rules);
    }

    List<CompiledRule> compiledRules() {
        return compiledRules;
    }

    private static List<RuleDefinition> copyRules(List<RuleDefinition> rules) {
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }
        return rules.stream()
                .map(CompiledRuleSet::copyRule)
                .toList();
    }

    private static RuleDefinition copyRule(RuleDefinition rule) {
        List<ConditionDefinition> conditions = rule.when() != null && rule.when().all() != null
                ? rule.when().all().stream().map(CompiledRuleSet::copyCondition).toList()
                : List.of();
        RuleThen then = rule.then() != null
                ? new RuleThen(deepCopy(rule.then().response()))
                : null;
        return new RuleDefinition(
                rule.id(),
                rule.priority(),
                rule.enabled(),
                new ConditionGroup(conditions),
                rule.rollout(),
                then
        );
    }

    private static ConditionDefinition copyCondition(ConditionDefinition condition) {
        return new ConditionDefinition(
                condition.resolvedKind(),
                condition.fieldRef(),
                condition.operator(),
                deepCopy(condition.value()),
                deepCopy(condition.valueTo()),
                condition.segmentNames(),
                condition.match(),
                condition.lookupRef(),
                condition.ruleSetId(),
                condition.expect()
        );
    }

    private static CompiledRule compileRule(RuleDefinition rule) {
        List<CompiledCondition> conditions = rule.when() != null && rule.when().all() != null
                ? rule.when().all().stream().map(CompiledRuleSet::compileCondition).toList()
                : List.of();
        return new CompiledRule(rule, conditions, rule.rollout());
    }

    private static CompiledCondition compileCondition(ConditionDefinition condition) {
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
}

package com.cars24.rulekit.core.validation;

import com.cars24.rulekit.core.exception.RuleKitExceptionCode;
import com.cars24.rulekit.core.model.ConditionDefinition;
import com.cars24.rulekit.core.model.ConditionGroupNode;
import com.cars24.rulekit.core.model.ConditionLeaf;
import com.cars24.rulekit.core.model.ConditionNode;
import com.cars24.rulekit.core.model.ExecutionMode;
import com.cars24.rulekit.core.model.RolloutDefinition;
import com.cars24.rulekit.core.model.RuleDefinition;
import com.cars24.rulekit.core.model.RuleKitVersions;
import com.cars24.rulekit.core.model.RuleSet;
import com.cars24.rulekit.core.operator.OperatorValueParser;
import com.cars24.rulekit.core.operator.RuleKitOperator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class RuleSetValidator {

    private RuleSetValidator() {
    }

    public static ValidationResult validate(RuleSet ruleSet) {
        List<ValidationMessage> errors = new ArrayList<>();
        List<ValidationMessage> warnings = new ArrayList<>();

        if (ruleSet == null) {
            errors.add(error(RuleKitExceptionCode.RULESET_VALIDATION_FAILED, "$", "RuleSet cannot be null"));
            return new ValidationResult(errors, warnings);
        }
        if (isBlank(ruleSet.id())) {
            errors.add(error(RuleKitExceptionCode.MISSING_RULESET_ID, "$.id", "RuleSet id is required"));
        }
        if (!RuleKitVersions.RULESET_SCHEMA_VERSION.equals(ruleSet.schemaVersion())) {
            errors.add(error(RuleKitExceptionCode.UNSUPPORTED_SCHEMA_VERSION, "$.schemaVersion",
                    "RuleSet schemaVersion must be " + RuleKitVersions.RULESET_SCHEMA_VERSION));
        }
        if (ruleSet.executionMode() != ExecutionMode.FIRST_MATCH
                && ruleSet.executionMode() != ExecutionMode.ALL_MATCHES) {
            errors.add(error(RuleKitExceptionCode.UNSUPPORTED_EXECUTION_MODE, "$.executionMode",
                    "executionMode must be FIRST_MATCH or ALL_MATCHES"));
        }
        if (ruleSet.rules() == null) {
            errors.add(error(RuleKitExceptionCode.RULESET_VALIDATION_FAILED, "$.rules", "Rules array is required"));
            return new ValidationResult(errors, warnings);
        }

        Set<String> ruleIds = new HashSet<>();
        for (int i = 0; i < ruleSet.rules().size(); i++) {
            RuleDefinition rule = ruleSet.rules().get(i);
            String rulePath = "$.rules[" + i + "]";
            if (rule == null) {
                errors.add(error(RuleKitExceptionCode.RULESET_VALIDATION_FAILED, rulePath, "Rule cannot be null"));
                continue;
            }
            if (isBlank(rule.id())) {
                errors.add(error(RuleKitExceptionCode.MISSING_RULE_ID, rulePath + ".id", "Rule id is required"));
            } else if (!ruleIds.add(rule.id())) {
                errors.add(error(RuleKitExceptionCode.DUPLICATE_RULE_ID, rulePath + ".id", "Duplicate rule id: " + rule.id()));
            }
            if (rule.priority() == null) {
                errors.add(error(RuleKitExceptionCode.MISSING_RULE_PRIORITY, rulePath + ".priority", "Rule priority is required"));
            }
            if (rule.enabled() == null) {
                errors.add(error(RuleKitExceptionCode.MISSING_RULE_ENABLED, rulePath + ".enabled", "Rule enabled flag is required"));
            }
            ConditionNode rootNode = rule.when() != null ? rule.when().rootNode() : null;
            if (rootNode == null) {
                errors.add(error(RuleKitExceptionCode.MISSING_WHEN, rulePath + ".when",
                        "Rule when.tree or when.all is required"));
                continue;
            }
            if (rule.then() == null || rule.then().response() == null) {
                errors.add(error(RuleKitExceptionCode.MISSING_THEN, rulePath + ".then.response", "Rule then.response is required"));
            }
            validateRollout(rule.rollout(), rulePath + ".rollout", errors);
            validateConditionNode(rootNode, rulePath + ".when", errors);
        }

        return new ValidationResult(List.copyOf(errors), List.copyOf(warnings));
    }

    // -------------------------------------------------------------------------
    // Tree validation
    // -------------------------------------------------------------------------

    private static void validateConditionNode(ConditionNode node, String path, List<ValidationMessage> errors) {
        if (node == null) {
            errors.add(error(RuleKitExceptionCode.RULESET_VALIDATION_FAILED, path, "Condition node cannot be null"));
            return;
        }
        if (node instanceof ConditionLeaf leaf) {
            validateCondition(leaf.toConditionDefinition(), path, errors);
        } else if (node instanceof ConditionGroupNode group) {
            if (group.op() == null) {
                errors.add(error(RuleKitExceptionCode.RULESET_VALIDATION_FAILED, path + ".op", "Group op (AND/OR) is required"));
            }
            if (group.children() == null) {
                errors.add(error(RuleKitExceptionCode.RULESET_VALIDATION_FAILED, path + ".children", "Group children cannot be null"));
                return;
            }
            for (int i = 0; i < group.children().size(); i++) {
                validateConditionNode(group.children().get(i), path + ".children[" + i + "]", errors);
            }
        } else {
            errors.add(error(RuleKitExceptionCode.RULESET_VALIDATION_FAILED, path, "Unknown condition node type"));
        }
    }

    // -------------------------------------------------------------------------
    // Condition validation
    // -------------------------------------------------------------------------

    private static void validateCondition(ConditionDefinition condition, String path, List<ValidationMessage> errors) {
        switch (condition.resolvedKind()) {
            case FIELD -> validateFieldCondition(condition, path, errors);
            case SEGMENT -> validateSegmentCondition(condition, path, errors);
            case DEPENDENCY -> validateDependencyCondition(condition, path, errors);
        }
    }

    private static void validateFieldCondition(ConditionDefinition c, String path, List<ValidationMessage> errors) {
        if (isBlank(c.fieldRef())) {
            errors.add(error(RuleKitExceptionCode.MISSING_FIELD_REF, path + ".fieldRef", "Condition fieldRef is required"));
        }
        if (hasSegmentFields(c) || hasDependencyFields(c)) {
            errors.add(error(RuleKitExceptionCode.RULESET_VALIDATION_FAILED, path,
                    "FIELD condition cannot include segment or dependency fields"));
        }
        Optional<RuleKitOperator> operator = RuleKitOperator.from(c.operator());
        if (operator.isEmpty()) {
            errors.add(error(RuleKitExceptionCode.UNSUPPORTED_OPERATOR, path + ".operator",
                    "Unsupported operator: " + c.operator()));
            return;
        }
        validateConditionOperands(c, operator.get(), path, errors);
    }

    private static void validateSegmentCondition(ConditionDefinition c, String path, List<ValidationMessage> errors) {
        if (c.segmentNames() == null || c.segmentNames().isEmpty()) {
            errors.add(error(RuleKitExceptionCode.INVALID_SEGMENT_CONDITION, path + ".segmentNames",
                    "Segment condition requires at least one segment name"));
        } else if (c.segmentNames().stream().anyMatch(RuleSetValidator::isBlank)) {
            errors.add(error(RuleKitExceptionCode.INVALID_SEGMENT_CONDITION, path + ".segmentNames",
                    "Segment names cannot be blank"));
        }
        if (c.match() == null) {
            errors.add(error(RuleKitExceptionCode.INVALID_SEGMENT_CONDITION, path + ".match",
                    "Segment condition requires match"));
        }
        if (isBlank(c.lookupRef())) {
            errors.add(error(RuleKitExceptionCode.INVALID_SEGMENT_CONDITION, path + ".lookupRef",
                    "Segment condition requires lookupRef"));
        }
        if (hasFieldOperatorFields(c) || hasDependencyFields(c)) {
            errors.add(error(RuleKitExceptionCode.INVALID_SEGMENT_CONDITION, path,
                    "SEGMENT condition cannot include field or dependency fields"));
        }
    }

    private static void validateDependencyCondition(ConditionDefinition c, String path, List<ValidationMessage> errors) {
        if (isBlank(c.ruleSetId())) {
            errors.add(error(RuleKitExceptionCode.INVALID_DEPENDENCY_CONDITION, path + ".ruleSetId",
                    "Dependency condition requires ruleSetId"));
        }
        if (c.expect() == null) {
            errors.add(error(RuleKitExceptionCode.INVALID_DEPENDENCY_CONDITION, path + ".expect",
                    "Dependency condition requires expect"));
        }
        if (hasFieldOperatorFields(c) || hasSegmentFields(c)) {
            errors.add(error(RuleKitExceptionCode.INVALID_DEPENDENCY_CONDITION, path,
                    "DEPENDENCY condition cannot include field or segment fields"));
        }
    }

    private static void validateRollout(RolloutDefinition rollout, String path, List<ValidationMessage> errors) {
        if (rollout == null) return;
        if (rollout.percentage() == null || rollout.percentage() < 0 || rollout.percentage() > 100) {
            errors.add(error(RuleKitExceptionCode.INVALID_ROLLOUT, path + ".percentage",
                    "Rollout percentage must be between 0 and 100"));
        }
        if (isBlank(rollout.unitRef())) {
            errors.add(error(RuleKitExceptionCode.INVALID_ROLLOUT, path + ".unitRef", "Rollout unitRef is required"));
        }
        if (rollout.algorithm() == null) {
            errors.add(error(RuleKitExceptionCode.INVALID_ROLLOUT, path + ".algorithm", "Rollout algorithm is required"));
        }
        if (rollout.bucketCount() == null || rollout.bucketCount() <= 0) {
            errors.add(error(RuleKitExceptionCode.INVALID_ROLLOUT, path + ".bucketCount",
                    "Rollout bucketCount must be greater than zero"));
        }
    }

    private static void validateConditionOperands(ConditionDefinition c, RuleKitOperator operator,
                                                   String path, List<ValidationMessage> errors) {
        if (operator.requiresValue() && c.value() == null) {
            errors.add(error(RuleKitExceptionCode.MISSING_CONDITION_VALUE, path + ".value",
                    "Operator " + c.operator() + " requires value"));
            return;
        }
        if (operator.requiresValueTo() && c.valueTo() == null) {
            errors.add(error(RuleKitExceptionCode.MISSING_CONDITION_VALUE_TO, path + ".valueTo",
                    "Operator " + c.operator() + " requires valueTo"));
        }
        if (operator.regex() && c.value() != null && !c.value().isNull()) {
            try {
                Pattern.compile(c.value().asText());
            } catch (PatternSyntaxException e) {
                errors.add(error(RuleKitExceptionCode.INVALID_REGEX_PATTERN, path + ".value",
                        "Invalid regex pattern for operator " + c.operator() + ": " + e.getDescription()));
            }
        }
        if (operator.numeric()) {
            if (OperatorValueParser.asNumber(c.value()).isEmpty()) {
                errors.add(error(RuleKitExceptionCode.INVALID_NUMERIC_VALUE, path + ".value",
                        "Strict validation requires numeric value for operator " + c.operator()));
            }
            if (operator.requiresValueTo()) {
                if (OperatorValueParser.asNumber(c.valueTo()).isEmpty()) {
                    errors.add(error(RuleKitExceptionCode.INVALID_NUMERIC_VALUE, path + ".valueTo",
                            "Strict validation requires numeric value for operator " + c.operator()));
                }
                Optional<Double> start = OperatorValueParser.asNumber(c.value());
                Optional<Double> end = OperatorValueParser.asNumber(c.valueTo());
                if (start.isPresent() && end.isPresent() && start.get() > end.get()) {
                    errors.add(error(RuleKitExceptionCode.INVALID_RANGE, path + ".valueTo",
                            "BETWEEN valueTo must be greater than or equal to value"));
                }
            }
        }
    }

    private static ValidationMessage error(RuleKitExceptionCode code, String path, String message) {
        return new ValidationMessage(ValidationSeverity.ERROR, code, path, message);
    }

    private static boolean isBlank(String v) { return v == null || v.isBlank(); }

    private static boolean hasFieldOperatorFields(ConditionDefinition c) {
        return !isBlank(c.fieldRef()) || !isBlank(c.operator()) || c.value() != null || c.valueTo() != null;
    }

    private static boolean hasSegmentFields(ConditionDefinition c) {
        return (c.segmentNames() != null && !c.segmentNames().isEmpty()) || c.match() != null || !isBlank(c.lookupRef());
    }

    private static boolean hasDependencyFields(ConditionDefinition c) {
        return !isBlank(c.ruleSetId()) || c.expect() != null;
    }
}

package com.cars24.rulekit.core.validation;

import com.cars24.rulekit.core.exception.RuleKitExceptionCode;
import com.cars24.rulekit.core.model.ConditionDefinition;
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
            errors.add(error(
                    RuleKitExceptionCode.UNSUPPORTED_SCHEMA_VERSION,
                    "$.schemaVersion",
                    "RuleSet schemaVersion must be " + RuleKitVersions.RULESET_SCHEMA_VERSION
            ));
        }

        if (ruleSet.executionMode() != ExecutionMode.FIRST_MATCH) {
            errors.add(error(RuleKitExceptionCode.UNSUPPORTED_EXECUTION_MODE, "$.executionMode", "Only FIRST_MATCH is supported"));
        }

        if (ruleSet.rules() == null) {
            errors.add(error(RuleKitExceptionCode.RULESET_VALIDATION_FAILED, "$.rules", "Rules array is required"));
            return new ValidationResult(errors, warnings);
        }

        Set<String> ruleIds = new HashSet<>();
        for (int ruleIndex = 0; ruleIndex < ruleSet.rules().size(); ruleIndex++) {
            RuleDefinition rule = ruleSet.rules().get(ruleIndex);
            String rulePath = "$.rules[" + ruleIndex + "]";
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
            if (rule.when() == null || rule.when().all() == null) {
                errors.add(error(RuleKitExceptionCode.MISSING_WHEN, rulePath + ".when.all", "Rule when.all is required"));
                continue;
            }
            if (rule.then() == null || rule.then().response() == null) {
                errors.add(error(RuleKitExceptionCode.MISSING_THEN, rulePath + ".then.response", "Rule then.response is required"));
            }
            validateRollout(rule.rollout(), rulePath + ".rollout", errors);

            for (int conditionIndex = 0; conditionIndex < rule.when().all().size(); conditionIndex++) {
                ConditionDefinition condition = rule.when().all().get(conditionIndex);
                String conditionPath = rulePath + ".when.all[" + conditionIndex + "]";
                if (condition == null) {
                    errors.add(error(RuleKitExceptionCode.RULESET_VALIDATION_FAILED, conditionPath, "Condition cannot be null"));
                    continue;
                }
                validateCondition(condition, conditionPath, errors);
            }
        }

        return new ValidationResult(List.copyOf(errors), List.copyOf(warnings));
    }

    private static void validateCondition(ConditionDefinition condition,
                                          String conditionPath,
                                          List<ValidationMessage> errors) {
        switch (condition.resolvedKind()) {
            case FIELD -> validateFieldCondition(condition, conditionPath, errors);
            case SEGMENT -> validateSegmentCondition(condition, conditionPath, errors);
            case DEPENDENCY -> validateDependencyCondition(condition, conditionPath, errors);
        }
    }

    private static void validateFieldCondition(ConditionDefinition condition,
                                               String conditionPath,
                                               List<ValidationMessage> errors) {
        if (isBlank(condition.fieldRef())) {
            errors.add(error(RuleKitExceptionCode.MISSING_FIELD_REF, conditionPath + ".fieldRef", "Condition fieldRef is required"));
        }
        if (hasSegmentFields(condition) || hasDependencyFields(condition)) {
            errors.add(error(
                    RuleKitExceptionCode.RULESET_VALIDATION_FAILED,
                    conditionPath,
                    "FIELD condition cannot include segment or dependency fields"
            ));
        }
        Optional<RuleKitOperator> operator = RuleKitOperator.from(condition.operator());
        if (operator.isEmpty()) {
            errors.add(error(
                    RuleKitExceptionCode.UNSUPPORTED_OPERATOR,
                    conditionPath + ".operator",
                    "Unsupported operator: " + condition.operator()
            ));
            return;
        }
        validateConditionOperands(condition, operator.get(), conditionPath, errors);
    }

    private static void validateSegmentCondition(ConditionDefinition condition,
                                                 String conditionPath,
                                                 List<ValidationMessage> errors) {
        if (condition.segmentNames() == null || condition.segmentNames().isEmpty()) {
            errors.add(error(
                    RuleKitExceptionCode.INVALID_SEGMENT_CONDITION,
                    conditionPath + ".segmentNames",
                    "Segment condition requires at least one segment name"
            ));
        } else if (condition.segmentNames().stream().anyMatch(RuleSetValidator::isBlank)) {
            errors.add(error(
                    RuleKitExceptionCode.INVALID_SEGMENT_CONDITION,
                    conditionPath + ".segmentNames",
                    "Segment names cannot be blank"
            ));
        }
        if (condition.match() == null) {
            errors.add(error(
                    RuleKitExceptionCode.INVALID_SEGMENT_CONDITION,
                    conditionPath + ".match",
                    "Segment condition requires match"
            ));
        }
        if (isBlank(condition.lookupRef())) {
            errors.add(error(
                    RuleKitExceptionCode.INVALID_SEGMENT_CONDITION,
                    conditionPath + ".lookupRef",
                    "Segment condition requires lookupRef"
            ));
        }
        if (hasFieldOperatorFields(condition) || hasDependencyFields(condition)) {
            errors.add(error(
                    RuleKitExceptionCode.INVALID_SEGMENT_CONDITION,
                    conditionPath,
                    "SEGMENT condition cannot include field or dependency fields"
            ));
        }
    }

    private static void validateDependencyCondition(ConditionDefinition condition,
                                                    String conditionPath,
                                                    List<ValidationMessage> errors) {
        if (isBlank(condition.ruleSetId())) {
            errors.add(error(
                    RuleKitExceptionCode.INVALID_DEPENDENCY_CONDITION,
                    conditionPath + ".ruleSetId",
                    "Dependency condition requires ruleSetId"
            ));
        }
        if (condition.expect() == null) {
            errors.add(error(
                    RuleKitExceptionCode.INVALID_DEPENDENCY_CONDITION,
                    conditionPath + ".expect",
                    "Dependency condition requires expect"
            ));
        }
        if (hasFieldOperatorFields(condition) || hasSegmentFields(condition)) {
            errors.add(error(
                    RuleKitExceptionCode.INVALID_DEPENDENCY_CONDITION,
                    conditionPath,
                    "DEPENDENCY condition cannot include field or segment fields"
            ));
        }
    }

    private static void validateRollout(RolloutDefinition rollout,
                                        String rolloutPath,
                                        List<ValidationMessage> errors) {
        if (rollout == null) {
            return;
        }
        if (rollout.percentage() == null || rollout.percentage() < 0 || rollout.percentage() > 100) {
            errors.add(error(
                    RuleKitExceptionCode.INVALID_ROLLOUT,
                    rolloutPath + ".percentage",
                    "Rollout percentage must be between 0 and 100"
            ));
        }
        if (isBlank(rollout.unitRef())) {
            errors.add(error(
                    RuleKitExceptionCode.INVALID_ROLLOUT,
                    rolloutPath + ".unitRef",
                    "Rollout unitRef is required"
            ));
        }
        if (rollout.algorithm() == null) {
            errors.add(error(
                    RuleKitExceptionCode.INVALID_ROLLOUT,
                    rolloutPath + ".algorithm",
                    "Rollout algorithm is required"
            ));
        }
        if (rollout.bucketCount() == null || rollout.bucketCount() <= 0) {
            errors.add(error(
                    RuleKitExceptionCode.INVALID_ROLLOUT,
                    rolloutPath + ".bucketCount",
                    "Rollout bucketCount must be greater than zero"
            ));
        }
    }

    private static void validateConditionOperands(ConditionDefinition condition,
                                                  RuleKitOperator operator,
                                                  String conditionPath,
                                                  List<ValidationMessage> errors) {
        if (operator.requiresValue() && condition.value() == null) {
            errors.add(error(
                    RuleKitExceptionCode.MISSING_CONDITION_VALUE,
                    conditionPath + ".value",
                    "Operator " + condition.operator() + " requires value"
            ));
            return;
        }
        if (operator.requiresValueTo() && condition.valueTo() == null) {
            errors.add(error(
                    RuleKitExceptionCode.MISSING_CONDITION_VALUE_TO,
                    conditionPath + ".valueTo",
                    "Operator " + condition.operator() + " requires valueTo"
            ));
        }
        if (operator.regex() && condition.value() != null && !condition.value().isNull()) {
            validateRegex(condition, conditionPath, errors);
        }
        if (operator.numeric()) {
            validateNumericValue(condition.value(), conditionPath + ".value", condition.operator(), errors);
            if (operator.requiresValueTo()) {
                validateNumericValue(condition.valueTo(), conditionPath + ".valueTo", condition.operator(), errors);
                validateRange(condition, conditionPath, errors);
            }
        }
    }

    private static void validateRegex(ConditionDefinition condition,
                                      String conditionPath,
                                      List<ValidationMessage> errors) {
        try {
            Pattern.compile(condition.value().asText());
        } catch (PatternSyntaxException e) {
            errors.add(error(
                    RuleKitExceptionCode.INVALID_REGEX_PATTERN,
                    conditionPath + ".value",
                    "Invalid regex pattern for operator " + condition.operator() + ": " + e.getDescription()
            ));
        }
    }

    private static void validateNumericValue(com.fasterxml.jackson.databind.JsonNode value,
                                             String path,
                                             String operator,
                                             List<ValidationMessage> errors) {
        if (OperatorValueParser.asNumber(value).isEmpty()) {
            errors.add(error(
                    RuleKitExceptionCode.INVALID_NUMERIC_VALUE,
                    path,
                    "Strict validation requires numeric value for operator " + operator
            ));
        }
    }

    private static void validateRange(ConditionDefinition condition,
                                      String conditionPath,
                                      List<ValidationMessage> errors) {
        Optional<Double> start = OperatorValueParser.asNumber(condition.value());
        Optional<Double> end = OperatorValueParser.asNumber(condition.valueTo());
        if (start.isPresent() && end.isPresent() && start.get() > end.get()) {
            errors.add(error(
                    RuleKitExceptionCode.INVALID_RANGE,
                    conditionPath + ".valueTo",
                    "BETWEEN valueTo must be greater than or equal to value"
            ));
        }
    }

    private static ValidationMessage error(RuleKitExceptionCode code, String path, String message) {
        return new ValidationMessage(ValidationSeverity.ERROR, code, path, message);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean hasFieldOperatorFields(ConditionDefinition condition) {
        return !isBlank(condition.fieldRef())
                || !isBlank(condition.operator())
                || condition.value() != null
                || condition.valueTo() != null;
    }

    private static boolean hasSegmentFields(ConditionDefinition condition) {
        return condition.segmentNames() != null && !condition.segmentNames().isEmpty()
                || condition.match() != null
                || !isBlank(condition.lookupRef());
    }

    private static boolean hasDependencyFields(ConditionDefinition condition) {
        return !isBlank(condition.ruleSetId()) || condition.expect() != null;
    }
}

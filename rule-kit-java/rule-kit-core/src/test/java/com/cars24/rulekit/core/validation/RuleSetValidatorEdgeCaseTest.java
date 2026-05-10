package com.cars24.rulekit.core.validation;

import com.cars24.rulekit.core.exception.RuleKitExceptionCode;
import com.cars24.rulekit.core.model.ConditionDefinition;
import com.cars24.rulekit.core.model.ConditionGroup;
import com.cars24.rulekit.core.model.DependencyExpectation;
import com.cars24.rulekit.core.model.ExecutionMode;
import com.cars24.rulekit.core.model.RolloutAlgorithm;
import com.cars24.rulekit.core.model.RolloutDefinition;
import com.cars24.rulekit.core.model.RuleDefinition;
import com.cars24.rulekit.core.model.RuleKitVersions;
import com.cars24.rulekit.core.model.RuleSet;
import com.cars24.rulekit.core.model.RuleThen;
import com.cars24.rulekit.core.model.SegmentMatchType;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleSetValidatorEdgeCaseTest {

    @Test
    void validatesNullRuleSetsMissingRulesAndMissingRuleFields() {
        ValidationResult nullResult = RuleSetValidator.validate(null);
        ValidationResult missingRulesResult = RuleSetValidator.validate(new RuleSet(
                "broken",
                RuleKitVersions.RULESET_SCHEMA_VERSION,
                ExecutionMode.FIRST_MATCH,
                null,
                null
        ));
        ValidationResult missingFieldsResult = RuleSetValidator.validate(new RuleSet(
                "broken-fields",
                RuleKitVersions.RULESET_SCHEMA_VERSION,
                null,
                null,
                Arrays.asList(
                        null,
                        new RuleDefinition(null, null, null, new ConditionGroup(null, List.of()), null)
                )
        ));

        assertThat(nullResult.errors()).extracting(ValidationMessage::code)
                .contains(RuleKitExceptionCode.RULESET_VALIDATION_FAILED);
        assertThat(missingRulesResult.errors()).extracting(ValidationMessage::path)
                .contains("$.rules");
        assertThat(missingFieldsResult.errors()).extracting(ValidationMessage::code)
                .contains(
                        RuleKitExceptionCode.UNSUPPORTED_EXECUTION_MODE,
                        RuleKitExceptionCode.RULESET_VALIDATION_FAILED,
                        RuleKitExceptionCode.MISSING_RULE_ID,
                        RuleKitExceptionCode.MISSING_RULE_PRIORITY,
                        RuleKitExceptionCode.MISSING_RULE_ENABLED,
                        RuleKitExceptionCode.MISSING_WHEN
                );
    }

    @Test
    void validatesKindSpecificFieldSegmentDependencyAndRolloutErrors() {
        ConditionDefinition invalidField = new ConditionDefinition(
                null,
                null,
                "EQ",
                BooleanNode.TRUE,
                null,
                List.of("beta"),
                SegmentMatchType.ANY,
                "userId",
                "child",
                DependencyExpectation.MATCHED
        );
        ConditionDefinition invalidSegment = new ConditionDefinition(
                com.cars24.rulekit.core.model.ConditionKind.SEGMENT,
                null,
                "EQ",
                BooleanNode.TRUE,
                null,
                List.of(" ", "beta"),
                null,
                " ",
                "child",
                null
        );
        ConditionDefinition invalidDependency = new ConditionDefinition(
                com.cars24.rulekit.core.model.ConditionKind.DEPENDENCY,
                "plan",
                "EQ",
                BooleanNode.TRUE,
                null,
                List.of("beta"),
                SegmentMatchType.ANY,
                "userId",
                " ",
                null
        );

        RuleSet ruleSet = new RuleSet(
                "edge-cases",
                RuleKitVersions.RULESET_SCHEMA_VERSION,
                ExecutionMode.ALL_MATCHES,
                BooleanNode.FALSE,
                List.of(
                        new RuleDefinition("field", 10, true, new ConditionGroup(null, List.of(invalidField)),
                                new RolloutDefinition(-1.0, " ", null, 0, List.of()),
                                new RuleThen(BooleanNode.TRUE)),
                        new RuleDefinition("segment", 9, true, new ConditionGroup(null, List.of(invalidSegment)),
                                new RuleThen(BooleanNode.TRUE)),
                        new RuleDefinition("dependency", 8, true, new ConditionGroup(null, List.of(invalidDependency)),
                                new RuleThen(BooleanNode.TRUE))
                )
        );

        ValidationResult result = RuleSetValidator.validate(ruleSet);

        assertThat(result.errors()).extracting(ValidationMessage::code)
                .contains(
                        RuleKitExceptionCode.MISSING_FIELD_REF,
                        RuleKitExceptionCode.RULESET_VALIDATION_FAILED,
                        RuleKitExceptionCode.INVALID_SEGMENT_CONDITION,
                        RuleKitExceptionCode.INVALID_DEPENDENCY_CONDITION,
                        RuleKitExceptionCode.INVALID_ROLLOUT
                );
    }

    @Test
    void validatesNumericRangesOnLegacyAllConditions() {
        RuleSet ruleSet = new RuleSet(
                "numeric-legacy",
                RuleKitVersions.RULESET_SCHEMA_VERSION,
                ExecutionMode.FIRST_MATCH,
                BooleanNode.FALSE,
                List.of(new RuleDefinition(
                        "between",
                        1,
                        true,
                        new ConditionGroup(null, List.of(new ConditionDefinition(
                                "age",
                                "BETWEEN",
                                IntNode.valueOf(30),
                                IntNode.valueOf(18)
                        ))),
                        new RolloutDefinition(50.0, "userId", RolloutAlgorithm.MURMUR3_32_SALTED_V1, 100, List.of()),
                        new RuleThen(BooleanNode.TRUE)
                ))
        );

        ValidationResult result = RuleSetValidator.validate(ruleSet);

        assertThat(result.errors()).extracting(ValidationMessage::code)
                .contains(RuleKitExceptionCode.INVALID_RANGE);
    }
}

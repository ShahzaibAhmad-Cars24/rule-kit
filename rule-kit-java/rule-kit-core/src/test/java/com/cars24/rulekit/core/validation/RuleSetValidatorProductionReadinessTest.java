package com.cars24.rulekit.core.validation;

import com.cars24.rulekit.core.exception.RuleKitExceptionCode;
import com.cars24.rulekit.core.model.RuleSet;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuleSetValidatorProductionReadinessTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void validatesRequiredOperandsAndRegexSyntax() throws Exception {
        RuleSet ruleSet = objectMapper.readValue("""
                {
                  "id": "invalid-operands",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": false,
                  "rules": [
                    {
                      "id": "missing-gt-value",
                      "priority": 30,
                      "enabled": true,
                      "when": { "all": [
                        { "fieldRef": "age", "operator": "GT" }
                      ]},
                      "then": { "response": true }
                    },
                    {
                      "id": "missing-between-end",
                      "priority": 20,
                      "enabled": true,
                      "when": { "all": [
                        { "fieldRef": "age", "operator": "BETWEEN", "value": 18 }
                      ]},
                      "then": { "response": true }
                    },
                    {
                      "id": "invalid-regex",
                      "priority": 10,
                      "enabled": true,
                      "when": { "all": [
                        { "fieldRef": "email", "operator": "MATCHES", "value": "[" }
                      ]},
                      "then": { "response": true }
                    }
                  ]
                }
                """, RuleSet.class);

        ValidationResult result = RuleSetValidator.validate(ruleSet);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors())
                .extracting(ValidationMessage::code)
                .contains(
                        RuleKitExceptionCode.MISSING_CONDITION_VALUE,
                        RuleKitExceptionCode.MISSING_CONDITION_VALUE_TO,
                        RuleKitExceptionCode.INVALID_REGEX_PATTERN
                );
    }

    @Test
    void defaultValidationRejectsInvalidNumericConstantsAndReversedRanges() throws Exception {
        RuleSet ruleSet = objectMapper.readValue("""
                {
                  "id": "strict-invalid-operands",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": false,
                  "rules": [
                    {
                      "id": "invalid-number",
                      "priority": 20,
                      "enabled": true,
                      "when": { "all": [
                        { "fieldRef": "age", "operator": "GT", "value": "abc" }
                      ]},
                      "then": { "response": true }
                    },
                    {
                      "id": "reversed-range",
                      "priority": 10,
                      "enabled": true,
                      "when": { "all": [
                        { "fieldRef": "age", "operator": "BETWEEN", "value": 50, "valueTo": 18 }
                      ]},
                      "then": { "response": true }
                    }
                  ]
                }
                """, RuleSet.class);

        ValidationResult result = RuleSetValidator.validate(ruleSet);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors())
                .extracting(ValidationMessage::code)
                .contains(
                        RuleKitExceptionCode.INVALID_NUMERIC_VALUE,
                        RuleKitExceptionCode.INVALID_RANGE
                );
    }

    @Test
    void rejectsMissingSchemaVersionLegacyOperatorAliasesAndKindSpecificExtraFields() throws Exception {
        RuleSet ruleSet = objectMapper.readValue("""
                {
                  "id": "strict-v1-shape",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": false,
                  "rules": [
                    {
                      "id": "legacy-alias",
                      "priority": 30,
                      "enabled": true,
                      "when": { "all": [
                        { "kind": "FIELD", "fieldRef": "age", "operator": "GREATER_THAN", "value": 18 }
                      ]},
                      "then": { "response": true }
                    },
                    {
                      "id": "segment-with-operator",
                      "priority": 20,
                      "enabled": true,
                      "when": { "all": [
                        { "kind": "SEGMENT", "segmentNames": ["beta"], "match": "ANY", "lookupRef": "userId", "operator": "EQ" }
                      ]},
                      "then": { "response": true }
                    },
                    {
                      "id": "dependency-with-field",
                      "priority": 10,
                      "enabled": true,
                      "when": { "all": [
                        { "kind": "DEPENDENCY", "ruleSetId": "child", "expect": "MATCHED", "fieldRef": "gate" }
                      ]},
                      "then": { "response": true }
                    }
                  ]
                }
                """, RuleSet.class);

        ValidationResult result = RuleSetValidator.validate(ruleSet);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors())
                .extracting(ValidationMessage::code)
                .contains(
                        RuleKitExceptionCode.UNSUPPORTED_SCHEMA_VERSION,
                        RuleKitExceptionCode.UNSUPPORTED_OPERATOR,
                        RuleKitExceptionCode.INVALID_SEGMENT_CONDITION,
                        RuleKitExceptionCode.INVALID_DEPENDENCY_CONDITION
                );
    }
}

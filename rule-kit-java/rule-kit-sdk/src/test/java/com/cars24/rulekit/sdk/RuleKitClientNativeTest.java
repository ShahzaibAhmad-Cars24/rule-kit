package com.cars24.rulekit.sdk;

import com.cars24.rulekit.core.evaluation.EvaluationOptions;
import com.cars24.rulekit.core.exception.RuleKitExceptionCode;
import com.cars24.rulekit.core.resolver.SegmentMembershipResult;
import com.cars24.rulekit.core.trace.TraceMode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleKitClientNativeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void evaluatesRuleSetWithEvaluationOptions() throws Exception {
        RuleKitClient client = new RuleKitClient(objectMapper);
        var ruleSet = objectMapper.readTree("""
                {
                  "id": "sdk-native",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": false,
                  "rules": [
                    {
                      "id": "segment-rule",
                      "priority": 1,
                      "enabled": true,
                      "when": { "all": [{ "kind": "SEGMENT", "segmentNames": ["beta-users"], "match": "ANY", "lookupRef": "userId" }] },
                      "then": { "response": true }
                    }
                  ]
                }
                """);
        EvaluationOptions options = EvaluationOptions.builder()
                .segmentResolver(context -> SegmentMembershipResult.of(Map.of("beta-users", true)))
                .build();

        var result = client.evaluate(ruleSet, objectMapper.readTree("{\"userId\":\"u1\"}"), TraceMode.COMPACT, options);

        assertThat(result.defaultUsed()).isFalse();
    }

    @Test
    void rejectsUnknownRuleSetFieldsInsteadOfSilentlyIgnoringTypos() throws Exception {
        RuleKitClient client = new RuleKitClient(objectMapper);
        var ruleSetWithTypo = objectMapper.readTree("""
                {
                  "id": "sdk-native",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": false,
                  "rules": [
                    {
                      "id": "field-rule",
                      "priority": 1,
                      "enabled": true,
                      "when": {
                        "all": [
                          {
                            "kind": "FIELD",
                            "fieldRef": "plan",
                            "operator": "EQ",
                            "value": "gold",
                            "unexpectedTypo": true
                          }
                        ]
                      },
                      "then": { "response": true }
                    }
                  ]
                }
                """);

        assertThatThrownBy(() -> client.validate(ruleSetWithTypo))
                .isInstanceOf(RuleKitClientException.class)
                .extracting(error -> ((RuleKitClientException) error).code())
                .isEqualTo(RuleKitExceptionCode.INVALID_RULESET_PAYLOAD);
    }

    @Test
    void keepsRuleSetParsingStrictEvenWhenHostObjectMapperIsPermissive() throws Exception {
        ObjectMapper permissiveMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        RuleKitClient client = new RuleKitClient(permissiveMapper);
        var ruleSetWithTypo = permissiveMapper.readTree("""
                {
                  "id": "sdk-native",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": false,
                  "rules": [
                    {
                      "id": "field-rule",
                      "priority": 1,
                      "enabled": true,
                      "when": {
                        "all": [
                          {
                            "kind": "FIELD",
                            "fieldRef": "plan",
                            "operator": "EQ",
                            "value": "gold",
                            "unexpectedTypo": true
                          }
                        ]
                      },
                      "then": { "response": true }
                    }
                  ]
                }
                """);

        assertThatThrownBy(() -> client.validate(ruleSetWithTypo))
                .isInstanceOf(RuleKitClientException.class)
                .extracting(error -> ((RuleKitClientException) error).code())
                .isEqualTo(RuleKitExceptionCode.INVALID_RULESET_PAYLOAD);
    }
}

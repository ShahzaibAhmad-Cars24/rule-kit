package com.cars24.rulekit.sdk;

import com.cars24.rulekit.core.evaluation.EvaluationOptions;
import com.cars24.rulekit.core.exception.RuleKitExceptionCode;
import com.cars24.rulekit.core.resolver.SegmentMembershipResult;
import com.cars24.rulekit.core.trace.TraceMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleKitClientAdditionalTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void evaluateToJsonSupportsEvaluationOptionsAndSourceBackedLookups() throws Exception {
        String segmentRuleSetJson = """
                {
                  "id": "segment-rules",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": false,
                  "rules": [
                    {
                      "id": "segment-match",
                      "priority": 10,
                      "enabled": true,
                      "when": { "all": [
                        { "kind": "SEGMENT", "segmentNames": ["vip"], "match": "ANY", "lookupRef": "userId" }
                      ]},
                      "then": { "response": true }
                    }
                  ]
                }
                """;
        String sourceRuleSetJson = """
                {
                  "id": "source-rules",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": "default",
                  "rules": [
                    {
                      "id": "city-match",
                      "priority": 10,
                      "enabled": true,
                      "when": { "all": [
                        { "fieldRef": "city", "operator": "EQ", "value": "Gurgaon" }
                      ]},
                      "then": { "response": "matched" }
                    }
                  ]
                }
                """;
        var segmentRuleSetNode = objectMapper.readTree(segmentRuleSetJson);
        var sourceRuleSet = objectMapper.readValue(sourceRuleSetJson, com.cars24.rulekit.core.model.RuleSet.class);
        var input = objectMapper.readTree("{\"userId\":\"u-1\"}");
        EvaluationOptions options = EvaluationOptions.builder()
                .segmentResolver(context -> SegmentMembershipResult.of(Map.of("vip", true)))
                .build();

        RuleKitClient sourceBackedClient = new RuleKitClient(objectMapper, ruleSetId ->
                "source-rules".equals(ruleSetId) ? Optional.of(sourceRuleSet) : Optional.empty());

        assertThat(sourceBackedClient.evaluateToJson(segmentRuleSetNode, input, TraceMode.COMPACT, options).get("response").asBoolean()).isTrue();
        assertThat(sourceBackedClient.validateById("source-rules").valid()).isTrue();
        assertThat(sourceBackedClient.evaluateById("source-rules", objectMapper.readTree("{\"city\":\"Gurgaon\"}"), TraceMode.COMPACT).response().asText())
                .isEqualTo("matched");
    }

    @Test
    void sourceBackedOperationsFailClearlyWhenMissingOrNotConfigured() {
        RuleKitClient defaultClient = new RuleKitClient(objectMapper);
        RuleKitClient sourceBackedClient = new RuleKitClient(objectMapper, ruleSetId -> Optional.empty());

        assertThatThrownBy(() -> defaultClient.compileById("missing"))
                .isInstanceOfSatisfying(RuleKitClientException.class, error ->
                        assertThat(error.code()).isEqualTo(RuleKitExceptionCode.RULESET_SOURCE_NOT_CONFIGURED));

        assertThatThrownBy(() -> sourceBackedClient.evaluateById("missing", objectMapper.createObjectNode(), TraceMode.COMPACT))
                .isInstanceOfSatisfying(RuleKitClientException.class, error ->
                        assertThat(error.code()).isEqualTo(RuleKitExceptionCode.RULESET_NOT_FOUND));
    }
}

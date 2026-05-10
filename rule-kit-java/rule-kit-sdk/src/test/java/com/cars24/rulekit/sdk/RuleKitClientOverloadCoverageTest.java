package com.cars24.rulekit.sdk;

import com.cars24.rulekit.core.evaluation.EvaluationOptions;
import com.cars24.rulekit.core.evaluation.RuleKitEvaluator;
import com.cars24.rulekit.core.exception.RuleKitExceptionCode;
import com.cars24.rulekit.core.model.RuleSet;
import com.cars24.rulekit.core.resolver.FactResolver;
import com.cars24.rulekit.core.resolver.ResolvedFact;
import com.cars24.rulekit.core.trace.TraceMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleKitClientOverloadCoverageTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String ruleSetJson = """
            {
              "id": "client-overloads",
              "schemaVersion": "rule-kit.ruleset.v1",
              "executionMode": "FIRST_MATCH",
              "defaultResponse": "default",
              "rules": [
                {
                  "id": "gate-rule",
                  "priority": 10,
                  "enabled": true,
                  "when": { "all": [
                    { "fieldRef": "_facts.allowed", "operator": "EQ", "value": true }
                  ]},
                  "then": { "response": "allowed" }
                }
              ]
            }
            """;
    private final String inputJson = "{\"ignored\":true}";
    private final FactResolver factResolver = (fieldRef, input) -> "_facts.allowed".equals(fieldRef)
            ? ResolvedFact.found(objectMapper.getNodeFactory().booleanNode(true))
            : ResolvedFact.missing();

    @Test
    void constructorsAndPublicOverloadsDelegateToTheSameBehavior() throws Exception {
        RuleKitClient defaultClient = new RuleKitClient();
        RuleKitClient traceConfiguredClient = new RuleKitClient(objectMapper, (TraceMode) null);
        RuleKitClient evaluatorConfiguredClient = new RuleKitClient(objectMapper, new RuleKitEvaluator());
        RuleKitClient fullyConfiguredClient = new RuleKitClient(objectMapper, new RuleKitEvaluator(), TraceMode.VERBOSE);

        RuleSet typedRuleSet = objectMapper.readValue(ruleSetJson, RuleSet.class);
        var ruleSetNode = objectMapper.readTree(ruleSetJson);
        var inputNode = objectMapper.readTree(inputJson);
        var compiled = defaultClient.compile(ruleSetJson);
        EvaluationOptions options = EvaluationOptions.builder().factResolver(factResolver).build();

        assertThat(defaultClient.evaluate(typedRuleSet, inputNode, TraceMode.COMPACT, factResolver).response().asText()).isEqualTo("allowed");
        assertThat(defaultClient.evaluate(compiled, inputNode, TraceMode.COMPACT, factResolver).response().asText()).isEqualTo("allowed");
        assertThat(defaultClient.evaluate(compiled, inputNode, TraceMode.COMPACT, options).response().asText()).isEqualTo("allowed");
        assertThat(defaultClient.evaluate(ruleSetNode, inputNode, TraceMode.COMPACT, factResolver).response().asText()).isEqualTo("allowed");
        assertThat(defaultClient.evaluate(ruleSetJson, inputJson, TraceMode.COMPACT, factResolver).response().asText()).isEqualTo("allowed");
        assertThat(defaultClient.evaluate(ruleSetJson, inputJson, TraceMode.COMPACT, options).response().asText()).isEqualTo("allowed");

        assertThat(defaultClient.evaluateToJson(ruleSetNode, inputNode, TraceMode.COMPACT).get("response").asText()).isEqualTo("default");
        assertThat(defaultClient.evaluateToJson(ruleSetNode, inputNode, TraceMode.COMPACT, factResolver).get("response").asText()).isEqualTo("allowed");
        assertThat(defaultClient.evaluateToJson(ruleSetJson, inputJson, TraceMode.COMPACT).get("response").asText()).isEqualTo("default");
        assertThat(defaultClient.evaluateToJson(ruleSetJson, inputJson, TraceMode.COMPACT, factResolver).get("response").asText()).isEqualTo("allowed");
        assertThat(defaultClient.evaluateToJson(ruleSetJson, inputJson, TraceMode.COMPACT, options).get("response").asText()).isEqualTo("allowed");

        assertThat(defaultClient.validate(ruleSetNode).valid()).isTrue();
        assertThat(defaultClient.validate(ruleSetJson).valid()).isTrue();
        assertThat(defaultClient.compile(ruleSetNode).ruleSetId()).isEqualTo("client-overloads");
        assertThat(evaluatorConfiguredClient.evaluate(ruleSetJson, inputJson, TraceMode.COMPACT, factResolver).response().asText()).isEqualTo("allowed");
        assertThat(traceConfiguredClient.defaultTraceMode()).isEqualTo(TraceMode.COMPACT);
        assertThat(fullyConfiguredClient.evaluate(typedRuleSet, inputNode, null).trace().mode()).isEqualTo(TraceMode.VERBOSE);
    }

    @Test
    void sourceBackedEvaluateByIdWithFactResolverUsesTheConfiguredRuleSetSource() throws Exception {
        RuleSet ruleSet = objectMapper.readValue(ruleSetJson, RuleSet.class);
        RuleKitClient sourceClient = new RuleKitClient(objectMapper, ruleSetId ->
                "client-overloads".equals(ruleSetId) ? Optional.of(ruleSet) : Optional.empty());

        assertThat(sourceClient.evaluateById("client-overloads", objectMapper.readTree(inputJson), TraceMode.COMPACT, factResolver).response().asText())
                .isEqualTo("allowed");
        assertThat(sourceClient.compileById("client-overloads").ruleSetId()).isEqualTo("client-overloads");

        assertThatThrownBy(() -> sourceClient.validate("{"))
                .isInstanceOfSatisfying(RuleKitClientException.class, error ->
                        assertThat(error.code()).isEqualTo(RuleKitExceptionCode.INVALID_JSON_PAYLOAD));
    }
}

package com.cars24.rulekit.sdk;

import com.cars24.rulekit.core.evaluation.EvaluationResult;
import com.cars24.rulekit.core.evaluation.CompiledRuleSet;
import com.cars24.rulekit.core.resolver.FactResolver;
import com.cars24.rulekit.core.resolver.ResolvedFact;
import com.cars24.rulekit.core.exception.RuleKitExceptionCode;
import com.cars24.rulekit.core.model.RuleSet;
import com.cars24.rulekit.core.trace.TraceMode;
import com.cars24.rulekit.core.validation.ValidationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleKitClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RuleKitClient client = new RuleKitClient(objectMapper);

    @Test
    void evaluatesJsonNodesThroughStableSdkFacade() throws Exception {
        JsonNode ruleSet = objectMapper.readTree("""
                {
                  "id": "fee-rules",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": { "fee": 0 },
                  "rules": [
                    {
                      "id": "high-value-order",
                      "priority": 50,
                      "enabled": true,
                      "when": { "all": [
                        { "fieldRef": "order.amount", "operator": "GT", "value": 10000 }
                      ]},
                      "then": { "response": { "fee": 99 } }
                    }
                  ]
                }
                """);
        JsonNode input = objectMapper.readTree("{ \"order\": { \"amount\": 12000 } }");

        EvaluationResult result = client.evaluate(ruleSet, input, TraceMode.VERBOSE);

        assertThat(result.matchedRuleId()).isEqualTo("high-value-order");
        assertThat(result.response().get("fee").asInt()).isEqualTo(99);
        assertThat(result.trace().rules().get(0).conditions()).hasSize(1);
    }

    @Test
    void evaluatesJsonStringsAndCanReturnJsonResult() {
        String ruleSet = """
                {
                  "id": "string-rules",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": "default",
                  "rules": [
                    {
                      "id": "exact-name",
                      "priority": 10,
                      "enabled": true,
                      "when": { "all": [
                        { "fieldRef": "name", "operator": "EQ", "value": "Amit" }
                      ]},
                      "then": { "response": "matched" }
                    }
                  ]
                }
                """;
        String input = "{ \"name\": \"Amit\" }";

        JsonNode result = client.evaluateToJson(ruleSet, input, TraceMode.COMPACT);

        assertThat(result.get("matchedRuleId").asText()).isEqualTo("exact-name");
        assertThat(result.get("response").asText()).isEqualTo("matched");
    }

    @Test
    void exposesValidationAndCompiledRuleSetApis() throws Exception {
        JsonNode ruleSetJson = objectMapper.readTree("""
                {
                  "id": "compiled-via-client",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "rules": [
                    {
                      "id": "match",
                      "priority": 10,
                      "enabled": true,
                      "when": { "all": [
                        { "fieldRef": "plan", "operator": "EQ", "value": "premium" }
                      ]},
                      "then": { "response": "ok" }
                    }
                  ]
                }
                """);
        RuleSet ruleSet = objectMapper.treeToValue(ruleSetJson, RuleSet.class);

        ValidationResult validation = client.validate(ruleSet);
        CompiledRuleSet compiled = client.compile(ruleSet);
        EvaluationResult result = client.evaluate(compiled, objectMapper.readTree("{ \"plan\": \"premium\" }"), TraceMode.COMPACT);

        assertThat(validation.valid()).isTrue();
        assertThat(compiled.ruleSetId()).isEqualTo("compiled-via-client");
        assertThat(result.matchedRuleId()).isEqualTo("match");
    }

    @Test
    void evaluatesJsonNodeRuleSetsWithHostFactResolverThroughSdkFacade() throws Exception {
        JsonNode ruleSetJson = objectMapper.readTree("""
                {
                  "id": "resolver-via-client",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": "default",
                  "rules": [
                    {
                      "id": "gate-open",
                      "priority": 10,
                      "enabled": true,
                      "when": { "all": [
                        { "fieldRef": "_gates.checkout.matched", "operator": "EQ", "value": true }
                      ]},
                      "then": { "response": "open" }
                    }
                  ]
                }
                """);
        FactResolver resolver = (fieldRef, input) -> "_gates.checkout.matched".equals(fieldRef)
                ? ResolvedFact.found(objectMapper.getNodeFactory().booleanNode(true))
                : ResolvedFact.missing();

        EvaluationResult result = client.evaluate(ruleSetJson, objectMapper.readTree("{}"), TraceMode.COMPACT, resolver);

        assertThat(result.matchedRuleId()).isEqualTo("gate-open");
        assertThat(result.response().asText()).isEqualTo("open");
    }

    @Test
    void evaluatesStringRuleSetsWithHostFactResolverThroughSdkFacade() {
        String ruleSetJson = """
                {
                  "id": "string-resolver-via-client",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": "default",
                  "rules": [
                    {
                      "id": "gate-open",
                      "priority": 10,
                      "enabled": true,
                      "when": { "all": [
                        { "fieldRef": "_gates.checkout.matched", "operator": "EQ", "value": true }
                      ]},
                      "then": { "response": "open" }
                    }
                  ]
                }
                """;
        FactResolver resolver = (fieldRef, input) -> "_gates.checkout.matched".equals(fieldRef)
                ? ResolvedFact.found(objectMapper.getNodeFactory().booleanNode(true))
                : ResolvedFact.missing();

        EvaluationResult result = client.evaluate(ruleSetJson, "{}", TraceMode.COMPACT, resolver);

        assertThat(result.matchedRuleId()).isEqualTo("gate-open");
        assertThat(result.response().asText()).isEqualTo("open");
    }

    @Test
    void surfacesStructuredClientErrorsForInvalidJsonAndRuleSetPayloads() {
        assertThatThrownBy(() -> client.validate("{"))
                .isInstanceOfSatisfying(RuleKitClientException.class, error ->
                        assertThat(error.code()).isEqualTo(RuleKitExceptionCode.INVALID_JSON_PAYLOAD));

        JsonNode invalidRuleSet = objectMapper.createObjectNode()
                .put("id", "bad-mode")
                .put("schemaVersion", "rule-kit.ruleset.v1")
                .put("executionMode", "NOT_A_MODE")
                .set("rules", objectMapper.createArrayNode());

        assertThatThrownBy(() -> client.validate(invalidRuleSet))
                .isInstanceOfSatisfying(RuleKitClientException.class, error ->
                        assertThat(error.code()).isEqualTo(RuleKitExceptionCode.INVALID_RULESET_PAYLOAD));
    }

    @Test
    void usesConfiguredDefaultTraceModeWhenCallerPassesNullTraceMode() throws Exception {
        RuleKitClient verboseClient = new RuleKitClient(objectMapper, TraceMode.VERBOSE);
        RuleSet ruleSet = objectMapper.readValue("""
                {
                  "id": "default-trace-rules",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "rules": [
                    {
                      "id": "match",
                      "priority": 10,
                      "enabled": true,
                      "when": { "all": [
                        { "fieldRef": "plan", "operator": "EQ", "value": "gold" }
                      ]},
                      "then": { "response": true }
                    }
                  ]
                }
                """, RuleSet.class);

        EvaluationResult result = verboseClient.evaluate(ruleSet, objectMapper.readTree("{ \"plan\": \"gold\" }"), null);

        assertThat(verboseClient.defaultTraceMode()).isEqualTo(TraceMode.VERBOSE);
        assertThat(result.trace().mode()).isEqualTo(TraceMode.VERBOSE);
        assertThat(result.trace().rules().get(0).conditions()).hasSize(1);
    }

    @Test
    void evaluatesAndCompilesRuleSetsFromHostProvidedSource() throws Exception {
        RuleSet ruleSet = objectMapper.readValue("""
                {
                  "id": "source-backed-rules",
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
                """, RuleSet.class);
        RuleSetSource source = ruleSetId -> "source-backed-rules".equals(ruleSetId)
                ? java.util.Optional.of(ruleSet)
                : java.util.Optional.empty();
        RuleKitClient sourceClient = new RuleKitClient(objectMapper, source);

        EvaluationResult result = sourceClient.evaluateById(
                "source-backed-rules",
                objectMapper.readTree("{ \"city\": \"Gurgaon\" }"),
                TraceMode.COMPACT
        );
        CompiledRuleSet compiled = sourceClient.compileById("source-backed-rules");

        assertThat(result.response().asText()).isEqualTo("matched");
        assertThat(compiled.ruleSetId()).isEqualTo("source-backed-rules");
        assertThatThrownBy(() -> sourceClient.compileById("missing"))
                .isInstanceOfSatisfying(RuleKitClientException.class, error ->
                        assertThat(error.code()).isEqualTo(RuleKitExceptionCode.RULESET_NOT_FOUND));
    }
}

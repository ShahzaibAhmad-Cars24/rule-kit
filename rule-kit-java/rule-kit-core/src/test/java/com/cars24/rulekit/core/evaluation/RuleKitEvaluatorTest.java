package com.cars24.rulekit.core.evaluation;

import com.cars24.rulekit.core.model.RuleSet;
import com.cars24.rulekit.core.trace.TraceMode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RuleKitEvaluatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RuleKitEvaluator evaluator = new RuleKitEvaluator();

    @Test
    void evaluatesFirstEnabledMatchingRuleByDescendingPriority() throws Exception {
        RuleSet ruleSet = objectMapper.readValue("""
                {
                  "id": "pricing-rules",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": { "discountPercent": 0 },
                  "rules": [
                    {
                      "id": "standard-discount",
                      "priority": 10,
                      "enabled": true,
                      "when": { "tree": { "type": "group", "op": "AND", "children": [
                        { "type": "leaf", "fieldRef": "cart.total", "operator": "GTE", "value": 1000 }
                      ]}},
                      "then": { "response": { "discountPercent": 5 } }
                    },
                    {
                      "id": "vip-discount",
                      "priority": 100,
                      "enabled": true,
                      "when": { "tree": { "type": "group", "op": "AND", "children": [
                        { "type": "leaf", "fieldRef": "customer.tier", "operator": "EQ", "value": "vip" },
                        { "type": "leaf", "fieldRef": "cart.total", "operator": "GTE", "value": 5000 }
                      ]}},
                      "then": { "response": { "discountPercent": 12 } }
                    }
                  ]
                }
                """, RuleSet.class);
        JsonNode input = objectMapper.readTree("""
                {
                  "customer": { "tier": "vip" },
                  "cart": { "total": 6200 }
                }
                """);

        EvaluationResult result = evaluator.evaluate(ruleSet, input, TraceMode.VERBOSE);

        assertThat(result.defaultUsed()).isFalse();
        assertThat(result.matchedRuleId()).isEqualTo("vip-discount");
        assertThat(result.response().get("discountPercent").asInt()).isEqualTo(12);
        assertThat(result.trace().rules()).hasSize(1);
        assertThat(result.trace().rules().get(0).conditions()).hasSize(2);
    }

    @Test
    void returnsDefaultWhenNoRuleMatches() throws Exception {
        RuleSet ruleSet = objectMapper.readValue("""
                {
                  "id": "pricing-rules",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": "no-discount",
                  "rules": [
                    {
                      "id": "vip-discount",
                      "priority": 100,
                      "enabled": true,
                      "when": { "tree": { "type": "group", "op": "AND", "children": [
                        { "type": "leaf", "fieldRef": "customer.tier", "operator": "EQ", "value": "vip" }
                      ]}},
                      "then": { "response": "vip-discount" }
                    }
                  ]
                }
                """, RuleSet.class);
        JsonNode input = objectMapper.readTree("{ \"customer\": { \"tier\": \"regular\" } }");

        EvaluationResult result = evaluator.evaluate(ruleSet, input, TraceMode.COMPACT);

        assertThat(result.defaultUsed()).isTrue();
        assertThat(result.matchedRuleId()).isNull();
        assertThat(result.response().asText()).isEqualTo("no-discount");
        assertThat(result.trace().rules()).hasSize(1);
        assertThat(result.trace().rules().get(0).conditions()).isEmpty();
    }

    @Test
    void supportsCoreStringNumberCollectionRegexAndExistenceOperators() throws Exception {
        RuleSet ruleSet = objectMapper.readValue("""
                {
                  "id": "operator-rules",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "rules": [
                    {
                      "id": "operator-match",
                      "priority": 10,
                      "enabled": true,
                      "when": { "tree": { "type": "group", "op": "AND", "children": [
                        { "type": "leaf", "fieldRef": "name", "operator": "STARTS_WITH", "value": "Jo" },
                        { "type": "leaf", "fieldRef": "name", "operator": "ENDS_WITH", "value": "hn" },
                        { "type": "leaf", "fieldRef": "age", "operator": "BETWEEN", "value": 18, "valueTo": 30 },
                        { "type": "leaf", "fieldRef": "city", "operator": "IN", "value": ["Delhi", "Gurgaon"] },
                        { "type": "leaf", "fieldRef": "tags", "operator": "CONTAINS", "value": "gold" },
                        { "type": "leaf", "fieldRef": "email", "operator": "MATCHES", "value": "^[^@]+@cars24\\\\.com$" },
                        { "type": "leaf", "fieldRef": "active", "operator": "IS_TRUE" },
                        { "type": "leaf", "fieldRef": "metadata", "operator": "EXISTS" },
                        { "type": "leaf", "fieldRef": "deletedAt", "operator": "NOT_EXISTS" }
                      ]}},
                      "then": { "response": { "matched": true } }
                    }
                  ]
                }
                """, RuleSet.class);
        JsonNode input = objectMapper.readTree("""
                {
                  "name": "John",
                  "age": "24",
                  "city": "Delhi",
                  "tags": ["gold", "internal"],
                  "email": "john@cars24.com",
                  "active": true,
                  "metadata": {}
                }
                """);

        EvaluationResult result = evaluator.evaluate(ruleSet, input, TraceMode.VERBOSE);

        assertThat(result.matchedRuleId()).isEqualTo("operator-match");
        assertThat(result.response().get("matched").asBoolean()).isTrue();
        assertThat(result.trace().rules().get(0).conditions())
                .allSatisfy(condition -> assertThat(condition.matched()).isTrue());
    }

    @Test
    void treatsEmptyAndGroupAsMatchForHostMappedEnableForAllRules() throws Exception {
        RuleSet ruleSet = objectMapper.readValue("""
                {
                  "id": "enable-all-rules",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "rules": [
                    {
                      "id": "global-value",
                      "priority": 10,
                      "enabled": true,
                      "when": { "tree": { "type": "group", "op": "AND", "children": []} },
                      "then": { "response": { "enabled": true } }
                    }
                  ]
                }
                """, RuleSet.class);

        EvaluationResult result = evaluator.evaluate(ruleSet, objectMapper.readTree("{}"), TraceMode.VERBOSE);

        assertThat(result.defaultUsed()).isFalse();
        assertThat(result.matchedRuleId()).isEqualTo("global-value");
        assertThat(result.response().get("enabled").asBoolean()).isTrue();
    }

    @Test
    void executesSharedContractTestVectors() throws Exception {
        assertVector("first-match-basic.json");
        assertVector("no-match-default.json");
    }

    private void assertVector(String fileName) throws Exception {
        Path vectorPath = Path.of(System.getProperty("user.dir"))
                .resolve("../../rule-kit-contract/test-vectors")
                .resolve(fileName)
                .normalize();
        JsonNode vector = objectMapper.readTree(Files.readString(vectorPath));

        RuleSet ruleSet = objectMapper.treeToValue(vector.get("ruleSet"), RuleSet.class);
        EvaluationResult result = evaluator.evaluate(
                ruleSet,
                vector.get("input"),
                TraceMode.valueOf(vector.get("traceMode").asText())
        );

        assertThat(result.matchedRuleId()).isEqualTo(
                vector.get("expected").get("matchedRuleId").isNull()
                        ? null
                        : vector.get("expected").get("matchedRuleId").asText()
        );
        assertThat(result.defaultUsed()).isEqualTo(vector.get("expected").get("defaultUsed").asBoolean());
        assertThat(result.response()).isEqualTo(vector.get("expected").get("response"));
    }
}

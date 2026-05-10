package com.cars24.rulekit.core.evaluation;

import com.cars24.rulekit.core.model.RuleSet;
import com.cars24.rulekit.core.trace.TraceMode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for AND/OR condition tree evaluation (when.tree field).
 */
class AndOrConditionTreeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RuleKitEvaluator evaluator = new RuleKitEvaluator();

    // -----------------------------------------------------------------------
    // Simple AND tree
    // -----------------------------------------------------------------------

    @Test
    void andTree_allConditionsMet_matches() throws Exception {
        RuleSet ruleSet = objectMapper.readValue("""
                {
                  "id": "test.and-tree",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": "default",
                  "rules": [{
                    "id": "rule-1",
                    "priority": 10,
                    "enabled": true,
                    "when": { "tree": {
                      "type": "group",
                      "op": "AND",
                      "children": [
                        { "type": "leaf", "kind": "FIELD", "fieldRef": "country", "operator": "EQ", "value": "IN" },
                        { "type": "leaf", "kind": "FIELD", "fieldRef": "age", "operator": "GTE", "value": 18 }
                      ]
                    }},
                    "then": { "response": "matched" }
                  }]
                }
                """, RuleSet.class);

        JsonNode input = objectMapper.readTree("""
                { "country": "IN", "age": 25 }
                """);

        EvaluationResult result = evaluator.evaluate(ruleSet, input, TraceMode.COMPACT);

        assertThat(result.defaultUsed()).isFalse();
        assertThat(result.response().asText()).isEqualTo("matched");
    }

    @Test
    void andTree_oneConditionFails_usesDefault() throws Exception {
        RuleSet ruleSet = objectMapper.readValue("""
                {
                  "id": "test.and-tree-fail",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": "default",
                  "rules": [{
                    "id": "rule-1",
                    "priority": 10,
                    "enabled": true,
                    "when": { "tree": {
                      "type": "group",
                      "op": "AND",
                      "children": [
                        { "type": "leaf", "kind": "FIELD", "fieldRef": "country", "operator": "EQ", "value": "IN" },
                        { "type": "leaf", "kind": "FIELD", "fieldRef": "age", "operator": "GTE", "value": 18 }
                      ]
                    }},
                    "then": { "response": "matched" }
                  }]
                }
                """, RuleSet.class);

        JsonNode input = objectMapper.readTree("""
                { "country": "IN", "age": 15 }
                """);

        EvaluationResult result = evaluator.evaluate(ruleSet, input, TraceMode.COMPACT);

        assertThat(result.defaultUsed()).isTrue();
        assertThat(result.response().asText()).isEqualTo("default");
    }

    // -----------------------------------------------------------------------
    // Simple OR tree
    // -----------------------------------------------------------------------

    @Test
    void orTree_oneConditionMet_matches() throws Exception {
        RuleSet ruleSet = objectMapper.readValue("""
                {
                  "id": "test.or-tree",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": "default",
                  "rules": [{
                    "id": "rule-1",
                    "priority": 10,
                    "enabled": true,
                    "when": { "tree": {
                      "type": "group",
                      "op": "OR",
                      "children": [
                        { "type": "leaf", "kind": "FIELD", "fieldRef": "country", "operator": "EQ", "value": "IN" },
                        { "type": "leaf", "kind": "FIELD", "fieldRef": "country", "operator": "EQ", "value": "AE" }
                      ]
                    }},
                    "then": { "response": "matched" }
                  }]
                }
                """, RuleSet.class);

        JsonNode input = objectMapper.readTree("""
                { "country": "AE" }
                """);

        EvaluationResult result = evaluator.evaluate(ruleSet, input, TraceMode.COMPACT);

        assertThat(result.defaultUsed()).isFalse();
        assertThat(result.response().asText()).isEqualTo("matched");
    }

    @Test
    void orTree_noConditionMet_usesDefault() throws Exception {
        RuleSet ruleSet = objectMapper.readValue("""
                {
                  "id": "test.or-tree-fail",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": "default",
                  "rules": [{
                    "id": "rule-1",
                    "priority": 10,
                    "enabled": true,
                    "when": { "tree": {
                      "type": "group",
                      "op": "OR",
                      "children": [
                        { "type": "leaf", "kind": "FIELD", "fieldRef": "country", "operator": "EQ", "value": "IN" },
                        { "type": "leaf", "kind": "FIELD", "fieldRef": "country", "operator": "EQ", "value": "AE" }
                      ]
                    }},
                    "then": { "response": "matched" }
                  }]
                }
                """, RuleSet.class);

        JsonNode input = objectMapper.readTree("""
                { "country": "US" }
                """);

        EvaluationResult result = evaluator.evaluate(ruleSet, input, TraceMode.COMPACT);

        assertThat(result.defaultUsed()).isTrue();
    }

    // -----------------------------------------------------------------------
    // Nested AND inside OR
    // -----------------------------------------------------------------------

    @Test
    void nestedTree_andInsideOr_firstGroupMatches() throws Exception {
        // (country=IN AND age>=18) OR (country=AE)
        RuleSet ruleSet = objectMapper.readValue("""
                {
                  "id": "test.nested-tree",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": "default",
                  "rules": [{
                    "id": "rule-1",
                    "priority": 10,
                    "enabled": true,
                    "when": { "tree": {
                      "type": "group",
                      "op": "OR",
                      "children": [
                        {
                          "type": "group",
                          "op": "AND",
                          "children": [
                            { "type": "leaf", "kind": "FIELD", "fieldRef": "country", "operator": "EQ", "value": "IN" },
                            { "type": "leaf", "kind": "FIELD", "fieldRef": "age", "operator": "GTE", "value": 18 }
                          ]
                        },
                        { "type": "leaf", "kind": "FIELD", "fieldRef": "country", "operator": "EQ", "value": "AE" }
                      ]
                    }},
                    "then": { "response": "matched" }
                  }]
                }
                """, RuleSet.class);

        JsonNode input = objectMapper.readTree("""
                { "country": "IN", "age": 20 }
                """);

        EvaluationResult result = evaluator.evaluate(ruleSet, input, TraceMode.COMPACT);
        assertThat(result.defaultUsed()).isFalse();
        assertThat(result.response().asText()).isEqualTo("matched");
    }

    @Test
    void nestedTree_andInsideOr_secondBranchMatches() throws Exception {
        RuleSet ruleSet = objectMapper.readValue("""
                {
                  "id": "test.nested-tree-2",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": "default",
                  "rules": [{
                    "id": "rule-1",
                    "priority": 10,
                    "enabled": true,
                    "when": { "tree": {
                      "type": "group",
                      "op": "OR",
                      "children": [
                        {
                          "type": "group",
                          "op": "AND",
                          "children": [
                            { "type": "leaf", "kind": "FIELD", "fieldRef": "country", "operator": "EQ", "value": "IN" },
                            { "type": "leaf", "kind": "FIELD", "fieldRef": "age", "operator": "GTE", "value": 18 }
                          ]
                        },
                        { "type": "leaf", "kind": "FIELD", "fieldRef": "country", "operator": "EQ", "value": "AE" }
                      ]
                    }},
                    "then": { "response": "matched" }
                  }]
                }
                """, RuleSet.class);

        JsonNode input = objectMapper.readTree("""
                { "country": "AE", "age": 15 }
                """);

        EvaluationResult result = evaluator.evaluate(ruleSet, input, TraceMode.COMPACT);
        assertThat(result.defaultUsed()).isFalse();
        assertThat(result.response().asText()).isEqualTo("matched");
    }

    @Test
    void nestedTree_noBranchMatches_usesDefault() throws Exception {
        RuleSet ruleSet = objectMapper.readValue("""
                {
                  "id": "test.nested-tree-fail",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": "default",
                  "rules": [{
                    "id": "rule-1",
                    "priority": 10,
                    "enabled": true,
                    "when": { "tree": {
                      "type": "group",
                      "op": "OR",
                      "children": [
                        {
                          "type": "group",
                          "op": "AND",
                          "children": [
                            { "type": "leaf", "kind": "FIELD", "fieldRef": "country", "operator": "EQ", "value": "IN" },
                            { "type": "leaf", "kind": "FIELD", "fieldRef": "age", "operator": "GTE", "value": 18 }
                          ]
                        },
                        { "type": "leaf", "kind": "FIELD", "fieldRef": "country", "operator": "EQ", "value": "AE" }
                      ]
                    }},
                    "then": { "response": "matched" }
                  }]
                }
                """, RuleSet.class);

        JsonNode input = objectMapper.readTree("""
                { "country": "US", "age": 25 }
                """);

        EvaluationResult result = evaluator.evaluate(ruleSet, input, TraceMode.COMPACT);
        assertThat(result.defaultUsed()).isTrue();
    }
}

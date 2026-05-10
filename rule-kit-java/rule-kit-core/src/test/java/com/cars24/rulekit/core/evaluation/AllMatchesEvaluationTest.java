package com.cars24.rulekit.core.evaluation;

import com.cars24.rulekit.core.model.RuleSet;
import com.cars24.rulekit.core.trace.TraceMode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ExecutionMode.ALL_MATCHES — every rule that fires is collected.
 */
class AllMatchesEvaluationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RuleKitEvaluator evaluator = new RuleKitEvaluator();

    // -----------------------------------------------------------------------
    // shared rule-set JSON (reused across most tests)
    // priority 100 = "premium"  matches cart.total >= 5000
    // priority 50  = "standard" matches cart.total >= 1000
    // priority 10  = "new-user" matches customer.isNew == true
    // -----------------------------------------------------------------------
    private static final String MULTI_RULE_SET = """
            {
              "id": "promotions",
              "schemaVersion": "rule-kit.ruleset.v1",
              "executionMode": "ALL_MATCHES",
              "defaultResponse": { "discountPercent": 0 },
              "rules": [
                {
                  "id": "premium-discount",
                  "priority": 100,
                  "enabled": true,
                  "when": { "tree": { "type": "group", "op": "AND", "children": [
                    { "type": "leaf", "fieldRef": "cart.total", "operator": "GTE", "value": 5000 }
                  ]}},
                  "then": { "response": { "discountPercent": 12, "badge": "PREMIUM" } }
                },
                {
                  "id": "standard-discount",
                  "priority": 50,
                  "enabled": true,
                  "when": { "tree": { "type": "group", "op": "AND", "children": [
                    { "type": "leaf", "fieldRef": "cart.total", "operator": "GTE", "value": 1000 }
                  ]}},
                  "then": { "response": { "discountPercent": 5, "badge": "STANDARD" } }
                },
                {
                  "id": "new-user-bonus",
                  "priority": 10,
                  "enabled": true,
                  "when": { "tree": { "type": "group", "op": "AND", "children": [
                    { "type": "leaf", "fieldRef": "customer.isNew", "operator": "EQ", "value": true }
                  ]}},
                  "then": { "response": { "bonusCredits": 200 } }
                }
              ]
            }
            """;

    // -----------------------------------------------------------------------
    // TC-1: two rules fire; ordered highest-priority first
    // -----------------------------------------------------------------------
    @Test
    void twoRulesMatchReturnsBothInPriorityOrder() throws Exception {
        RuleSet ruleSet = objectMapper.readValue(MULTI_RULE_SET, RuleSet.class);
        JsonNode input = objectMapper.readTree("""
                { "cart": { "total": 6000 }, "customer": { "isNew": false } }
                """);

        EvaluationResult result = evaluator.evaluate(ruleSet, input, TraceMode.COMPACT);

        assertThat(result.defaultUsed()).isFalse();
        assertThat(result.matches()).hasSize(2);
        assertThat(result.matches().get(0).ruleId()).isEqualTo("premium-discount");
        assertThat(result.matches().get(1).ruleId()).isEqualTo("standard-discount");
        // primary result == highest-priority match
        assertThat(result.matchedRuleId()).isEqualTo("premium-discount");
        assertThat(result.response().get("badge").asText()).isEqualTo("PREMIUM");
    }

    // -----------------------------------------------------------------------
    // TC-2: all three rules fire
    // -----------------------------------------------------------------------
    @Test
    void allThreeRulesFireWhenAllConditionsMet() throws Exception {
        RuleSet ruleSet = objectMapper.readValue(MULTI_RULE_SET, RuleSet.class);
        JsonNode input = objectMapper.readTree("""
                { "cart": { "total": 7500 }, "customer": { "isNew": true } }
                """);

        EvaluationResult result = evaluator.evaluate(ruleSet, input, TraceMode.COMPACT);

        assertThat(result.matches()).hasSize(3);
        List<String> ids = result.matches().stream().map(RuleMatch::ruleId).toList();
        assertThat(ids).containsExactly("premium-discount", "standard-discount", "new-user-bonus");
    }

    // -----------------------------------------------------------------------
    // TC-3: no rules fire → default response returned, empty matches list
    // -----------------------------------------------------------------------
    @Test
    void noRulesMatchReturnsDefaultAndEmptyMatchesList() throws Exception {
        RuleSet ruleSet = objectMapper.readValue(MULTI_RULE_SET, RuleSet.class);
        JsonNode input = objectMapper.readTree("""
                { "cart": { "total": 50 }, "customer": { "isNew": false } }
                """);

        EvaluationResult result = evaluator.evaluate(ruleSet, input, TraceMode.COMPACT);

        assertThat(result.defaultUsed()).isTrue();
        assertThat(result.matchedRuleId()).isNull();
        assertThat(result.matches()).isNotNull().isEmpty();
        assertThat(result.response().get("discountPercent").asInt()).isZero();
    }

    // -----------------------------------------------------------------------
    // TC-4: exactly one rule fires → matches list has size 1
    // -----------------------------------------------------------------------
    @Test
    void singleMatchReturnsSingleElementMatchesList() throws Exception {
        RuleSet ruleSet = objectMapper.readValue(MULTI_RULE_SET, RuleSet.class);
        JsonNode input = objectMapper.readTree("""
                { "cart": { "total": 1500 }, "customer": { "isNew": false } }
                """);

        EvaluationResult result = evaluator.evaluate(ruleSet, input, TraceMode.COMPACT);

        assertThat(result.defaultUsed()).isFalse();
        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().get(0).ruleId()).isEqualTo("standard-discount");
        assertThat(result.matchedRuleId()).isEqualTo("standard-discount");
    }

    // -----------------------------------------------------------------------
    // TC-5: disabled rules are not included in matches
    // -----------------------------------------------------------------------
    @Test
    void disabledRuleIsNeverIncludedInMatches() throws Exception {
        RuleSet ruleSet = objectMapper.readValue("""
                {
                  "id": "test-disabled",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "ALL_MATCHES",
                  "defaultResponse": { "val": 0 },
                  "rules": [
                    {
                      "id": "active-rule",
                      "priority": 10,
                      "enabled": true,
                      "when": { "tree": { "type": "group", "op": "AND", "children": [
                        { "type": "leaf", "fieldRef": "x", "operator": "EQ", "value": 1 }
                      ]}},
                      "then": { "response": { "val": 1 } }
                    },
                    {
                      "id": "disabled-rule",
                      "priority": 5,
                      "enabled": false,
                      "when": { "tree": { "type": "group", "op": "AND", "children": [
                        { "type": "leaf", "fieldRef": "x", "operator": "EQ", "value": 1 }
                      ]}},
                      "then": { "response": { "val": 99 } }
                    }
                  ]
                }
                """, RuleSet.class);
        JsonNode input = objectMapper.readTree("{ \"x\": 1 }");

        EvaluationResult result = evaluator.evaluate(ruleSet, input, TraceMode.COMPACT);

        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().get(0).ruleId()).isEqualTo("active-rule");
    }

    // -----------------------------------------------------------------------
    // TC-6: RuleMatch carries the correct priority value
    // -----------------------------------------------------------------------
    @Test
    void ruleMatchRecordCarriesPriority() throws Exception {
        RuleSet ruleSet = objectMapper.readValue(MULTI_RULE_SET, RuleSet.class);
        JsonNode input = objectMapper.readTree("""
                { "cart": { "total": 6000 }, "customer": { "isNew": false } }
                """);

        EvaluationResult result = evaluator.evaluate(ruleSet, input, TraceMode.COMPACT);

        RuleMatch first = result.matches().get(0);
        RuleMatch second = result.matches().get(1);
        assertThat(first.priority()).isEqualTo(100);
        assertThat(second.priority()).isEqualTo(50);
    }

    // -----------------------------------------------------------------------
    // TC-7: RuleMatch carries the correct response payload
    // -----------------------------------------------------------------------
    @Test
    void ruleMatchRecordCarriesResponsePayload() throws Exception {
        RuleSet ruleSet = objectMapper.readValue(MULTI_RULE_SET, RuleSet.class);
        JsonNode input = objectMapper.readTree("""
                { "cart": { "total": 7500 }, "customer": { "isNew": true } }
                """);

        EvaluationResult result = evaluator.evaluate(ruleSet, input, TraceMode.COMPACT);

        RuleMatch newUserMatch = result.matches().stream()
                .filter(m -> "new-user-bonus".equals(m.ruleId()))
                .findFirst()
                .orElseThrow();
        assertThat(newUserMatch.response().get("bonusCredits").asInt()).isEqualTo(200);
    }

    // -----------------------------------------------------------------------
    // TC-8: FIRST_MATCH mode still produces null matches list (backward compat)
    // -----------------------------------------------------------------------
    @Test
    void firstMatchModeStillProducesNullMatchesList() throws Exception {
        RuleSet ruleSet = objectMapper.readValue("""
                {
                  "id": "first-match-test",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": { "val": 0 },
                  "rules": [
                    {
                      "id": "r1",
                      "priority": 10,
                      "enabled": true,
                      "when": { "tree": { "type": "group", "op": "AND", "children": [
                        { "type": "leaf", "fieldRef": "x", "operator": "EQ", "value": 1 }
                      ]}},
                      "then": { "response": { "val": 1 } }
                    }
                  ]
                }
                """, RuleSet.class);
        JsonNode input = objectMapper.readTree("{ \"x\": 1 }");

        EvaluationResult result = evaluator.evaluate(ruleSet, input, TraceMode.COMPACT);

        assertThat(result.matchedRuleId()).isEqualTo("r1");
        assertThat(result.matches()).isNull();  // not present in FIRST_MATCH
    }

    // -----------------------------------------------------------------------
    // TC-9: ALL_MATCHES compiled rule-set can be pre-compiled and reused
    // -----------------------------------------------------------------------
    @Test
    void compiledRuleSetPreservesExecutionMode() throws Exception {
        RuleSet ruleSet = objectMapper.readValue(MULTI_RULE_SET, RuleSet.class);
        CompiledRuleSet compiled = evaluator.compile(ruleSet);

        assertThat(compiled.executionMode().name()).isEqualTo("ALL_MATCHES");

        JsonNode inputA = objectMapper.readTree("""
                { "cart": { "total": 6000 }, "customer": { "isNew": false } }
                """);
        JsonNode inputB = objectMapper.readTree("""
                { "cart": { "total": 50 }, "customer": { "isNew": false } }
                """);

        EvaluationResult resultA = evaluator.evaluate(compiled, inputA, TraceMode.COMPACT);
        EvaluationResult resultB = evaluator.evaluate(compiled, inputB, TraceMode.COMPACT);

        assertThat(resultA.matches()).hasSize(2);
        assertThat(resultB.matches()).isEmpty();
    }
}

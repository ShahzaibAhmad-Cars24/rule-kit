package com.cars24.rulekit.core.evaluation;

import com.cars24.rulekit.core.model.RuleSet;
import com.cars24.rulekit.core.trace.TraceMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompiledRuleSetLegacyAllTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RuleKitEvaluator evaluator = new RuleKitEvaluator();

    @Test
    void compiledRuleSetsPreserveLegacyAllConditionsAndDeepCopyResponses() throws Exception {
        RuleSet ruleSet = objectMapper.readValue("""
                {
                  "id": "legacy-all-rules",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": "default",
                  "rules": [
                    {
                      "id": "match",
                      "priority": 10,
                      "enabled": true,
                      "when": { "all": [
                        { "fieldRef": "plan", "operator": "EQ", "value": "gold" }
                      ]},
                      "then": { "response": { "status": "matched" } }
                    }
                  ]
                }
                """, RuleSet.class);

        CompiledRuleSet compiled = evaluator.compile(ruleSet);
        ((ObjectNode) ruleSet.rules().get(0).then().response()).put("status", "mutated");

        var result = evaluator.evaluate(compiled, objectMapper.readTree("{\"plan\":\"gold\"}"), TraceMode.VERBOSE);

        assertThat(compiled.rules().get(0).when().all()).hasSize(1);
        assertThat(result.matchedRuleId()).isEqualTo("match");
        assertThat(result.trace().rules().get(0).conditions()).hasSize(1);
        assertThat(result.response().get("status").asText()).isEqualTo("matched");
    }
}

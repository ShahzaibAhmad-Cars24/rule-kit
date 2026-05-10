package com.cars24.rulekit.sdk;

import com.cars24.rulekit.core.evaluation.EvaluationOptions;
import com.cars24.rulekit.core.model.RuleSet;
import com.cars24.rulekit.core.trace.TraceMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RuleKitClient client = new RuleKitClient(objectMapper);
    private final SimulationService simulationService = new SimulationService(client);

    @Test
    void simulatesTypedAndJsonRuleSetsInVerboseMode() throws Exception {
        String ruleSetJson = """
                {
                  "id": "simulation-rules",
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
                      "then": { "response": "matched" }
                    }
                  ]
                }
                """;
        RuleSet ruleSet = objectMapper.readValue(ruleSetJson, RuleSet.class);
        var input = objectMapper.readTree("{\"plan\":\"gold\"}");

        var typedResult = simulationService.simulate(ruleSet, input);
        var jsonResult = simulationService.simulate(objectMapper.readTree(ruleSetJson), input);

        assertThat(typedResult.response().asText()).isEqualTo("matched");
        assertThat(jsonResult.trace().mode()).isEqualTo(TraceMode.VERBOSE);
    }
}

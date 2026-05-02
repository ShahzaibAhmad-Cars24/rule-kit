package com.cars24.rulekit.sdk;

import com.cars24.rulekit.core.evaluation.EvaluationResult;
import com.cars24.rulekit.core.model.RuleSet;
import com.cars24.rulekit.core.trace.TraceMode;
import com.fasterxml.jackson.databind.JsonNode;

public class SimulationService {

    private final RuleKitClient ruleKitClient;

    public SimulationService(RuleKitClient ruleKitClient) {
        this.ruleKitClient = ruleKitClient;
    }

    public EvaluationResult simulate(RuleSet ruleSet, JsonNode input) {
        return ruleKitClient.evaluate(ruleSet, input, TraceMode.VERBOSE);
    }

    public EvaluationResult simulate(JsonNode ruleSet, JsonNode input) {
        return ruleKitClient.evaluate(ruleSet, input, TraceMode.VERBOSE);
    }
}

package com.cars24.rulekit.sample;

import com.cars24.rulekit.core.evaluation.CompiledRuleSet;
import com.cars24.rulekit.core.evaluation.EvaluationResult;
import com.cars24.rulekit.core.model.RuleSet;
import com.cars24.rulekit.core.trace.TraceMode;
import com.cars24.rulekit.sdk.RuleKitClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rule-kit/sample")
public class RuleKitSampleController {

    private final RuleKitClient ruleKitClient;
    private final RuleSet samplePricingRuleSet;
    private final CompiledRuleSet compiledSamplePricingRuleSet;
    private final ObjectMapper objectMapper;

    public RuleKitSampleController(RuleKitClient ruleKitClient,
                                   RuleSet samplePricingRuleSet,
                                   CompiledRuleSet compiledSamplePricingRuleSet,
                                   ObjectMapper objectMapper) {
        this.ruleKitClient = ruleKitClient;
        this.samplePricingRuleSet = samplePricingRuleSet;
        this.compiledSamplePricingRuleSet = compiledSamplePricingRuleSet;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/ruleset")
    public RuleSet ruleSet() {
        return samplePricingRuleSet;
    }

    @GetMapping("/evaluate")
    public EvaluationResult evaluate(@RequestParam(defaultValue = "gold") String plan,
                                     @RequestParam(defaultValue = "Gurgaon") String city,
                                     @RequestParam(defaultValue = "7500") int cartTotal,
                                     @RequestParam(defaultValue = "COMPACT") TraceMode traceMode) {
        ObjectNode input = objectMapper.createObjectNode()
                .put("plan", plan)
                .put("city", city)
                .put("cartTotal", cartTotal);
        return ruleKitClient.evaluate(compiledSamplePricingRuleSet, input, traceMode);
    }

    @PostMapping("/simulate")
    public EvaluationResult simulate(@RequestBody SimulationRequest request) {
        RuleSet ruleSet = request.ruleSet() != null ? request.ruleSet() : samplePricingRuleSet;
        JsonNode input = request.input() != null ? request.input() : objectMapper.createObjectNode();
        return ruleKitClient.evaluate(ruleSet, input, TraceMode.VERBOSE);
    }

    public record SimulationRequest(
            RuleSet ruleSet,
            JsonNode input
    ) {
    }
}

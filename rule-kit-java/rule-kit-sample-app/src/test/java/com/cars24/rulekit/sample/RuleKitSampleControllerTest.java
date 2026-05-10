package com.cars24.rulekit.sample;

import com.cars24.rulekit.core.evaluation.CompiledRuleSet;
import com.cars24.rulekit.core.model.RuleSet;
import com.cars24.rulekit.core.trace.TraceMode;
import com.cars24.rulekit.sdk.RuleKitClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuleKitSampleControllerTest {

    private final RuleKitYamlRuleSetLoader loader = new RuleKitYamlRuleSetLoader();
    private final RuleKitClient ruleKitClient = new RuleKitClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RuleSet sampleRuleSet = loader.load(new org.springframework.core.io.ClassPathResource("rule-kit/sample-pricing-rules.yaml"));
    private final CompiledRuleSet compiledRuleSet = ruleKitClient.compile(sampleRuleSet);
    private final RuleKitSampleController controller = new RuleKitSampleController(
            ruleKitClient,
            sampleRuleSet,
            compiledRuleSet,
            objectMapper
    );

    @Test
    void returnsTheSampleRuleSetAndEvaluatesWithQueryParameters() {
        assertThat(controller.ruleSet().id()).isEqualTo(sampleRuleSet.id());

        var result = controller.evaluate("gold", "Gurgaon", 7500, TraceMode.COMPACT);

        assertThat(result.matchedRuleId()).isEqualTo("gold-gurgaon-high-cart");
        assertThat(result.response().get("discountPercent").asInt()).isEqualTo(15);
    }

    @Test
    void simulateUsesDefaultRuleSetAndSupportsCustomPayloads() {
        ObjectNode defaultInput = objectMapper.createObjectNode()
                .put("plan", "basic")
                .put("city", "Gurgaon")
                .put("cartTotal", 8000);

        var defaultResult = controller.simulate(new RuleKitSampleController.SimulationRequest(null, defaultInput));
        assertThat(defaultResult.defaultUsed()).isTrue();
        assertThat(defaultResult.response().get("discountPercent").asInt()).isEqualTo(0);

        ObjectNode customInput = objectMapper.createObjectNode()
                .put("plan", "gold")
                .put("city", "Mumbai")
                .put("cartTotal", 1000);
        var customResult = controller.simulate(new RuleKitSampleController.SimulationRequest(sampleRuleSet, customInput));

        assertThat(customResult.trace().mode()).isEqualTo(TraceMode.VERBOSE);
        assertThat(customResult.response().get("discountPercent").asInt()).isEqualTo(5);
    }
}

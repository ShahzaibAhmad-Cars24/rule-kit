package com.cars24.rulekit.sample;

import com.cars24.rulekit.core.evaluation.CompiledRuleSet;
import com.cars24.rulekit.core.evaluation.EvaluationResult;
import com.cars24.rulekit.core.model.RuleSet;
import com.cars24.rulekit.core.trace.TraceMode;
import com.cars24.rulekit.sdk.RuleKitClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class RuleKitYamlEvaluationTest {

    private final RuleKitYamlRuleSetLoader yamlLoader = new RuleKitYamlRuleSetLoader();
    private final RuleKitClient ruleKitClient = new RuleKitClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void loadsRuleSetFromYamlFileAndEvaluatesConditions() {
        RuleSet ruleSet = yamlLoader.load(new ClassPathResource("rule-kit/sample-pricing-rules.yaml"));
        CompiledRuleSet compiledRuleSet = ruleKitClient.compile(ruleSet);

        EvaluationResult result = ruleKitClient.evaluate(
                compiledRuleSet,
                objectMapper.createObjectNode()
                        .put("plan", "gold")
                        .put("city", "Gurgaon")
                        .put("cartTotal", 7500),
                TraceMode.VERBOSE
        );

        assertThat(result.matchedRuleId()).isEqualTo("gold-gurgaon-high-cart");
        assertThat(result.defaultUsed()).isFalse();
        assertThat(result.response().get("discountPercent").asInt()).isEqualTo(15);
        assertThat(result.trace().rules().get(0).conditions()).hasSize(3);
    }
}

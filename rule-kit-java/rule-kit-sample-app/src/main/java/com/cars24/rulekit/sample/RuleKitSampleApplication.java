package com.cars24.rulekit.sample;

import com.cars24.rulekit.core.evaluation.CompiledRuleSet;
import com.cars24.rulekit.core.model.RuleSet;
import com.cars24.rulekit.sdk.RuleKitClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;

@SpringBootApplication
public class RuleKitSampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(RuleKitSampleApplication.class, args);
    }

    @Bean
    RuleSet samplePricingRuleSet(RuleKitYamlRuleSetLoader loader) {
        return loader.load(new ClassPathResource("rule-kit/sample-pricing-rules.yaml"));
    }

    @Bean
    CompiledRuleSet compiledSamplePricingRuleSet(RuleKitClient ruleKitClient, RuleSet samplePricingRuleSet) {
        return ruleKitClient.compile(samplePricingRuleSet);
    }
}

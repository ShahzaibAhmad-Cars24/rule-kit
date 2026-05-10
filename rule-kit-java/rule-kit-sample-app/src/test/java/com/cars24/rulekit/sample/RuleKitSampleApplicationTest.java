package com.cars24.rulekit.sample;

import com.cars24.rulekit.core.evaluation.CompiledRuleSet;
import com.cars24.rulekit.core.exception.RuleKitExceptionCode;
import com.cars24.rulekit.core.model.RuleSet;
import com.cars24.rulekit.sdk.RuleKitClient;
import com.cars24.rulekit.sdk.RuleKitClientException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleKitSampleApplicationTest {

    @Test
    void beanMethodsLoadAndCompileTheSampleRuleSet() {
        RuleKitSampleApplication application = new RuleKitSampleApplication();
        RuleKitYamlRuleSetLoader loader = new RuleKitYamlRuleSetLoader();
        RuleSet ruleSet = application.samplePricingRuleSet(loader);
        CompiledRuleSet compiled = application.compiledSamplePricingRuleSet(new RuleKitClient(), ruleSet);

        assertThat(ruleSet.id()).isEqualTo("sample-pricing-rules");
        assertThat(compiled.ruleSetId()).isEqualTo("sample-pricing-rules");
    }

    @Test
    void yamlLoaderRejectsInvalidYamlResources() {
        RuleKitYamlRuleSetLoader loader = new RuleKitYamlRuleSetLoader();
        ByteArrayResource invalidYaml = new ByteArrayResource("id: bad\nrules: [".getBytes()) {
            @Override
            public String getDescription() {
                return "broken-yaml";
            }
        };

        assertThatThrownBy(() -> loader.load(invalidYaml))
                .isInstanceOfSatisfying(RuleKitClientException.class, error ->
                        assertThat(error.code()).isEqualTo(RuleKitExceptionCode.INVALID_RULESET_PAYLOAD));

        assertThat(loader.load(new ClassPathResource("rule-kit/sample-pricing-rules.yaml")).id())
                .isEqualTo("sample-pricing-rules");
    }
}

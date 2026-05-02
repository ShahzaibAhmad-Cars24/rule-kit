package com.cars24.rulekit.starter;

import com.cars24.rulekit.core.trace.TraceMode;
import com.cars24.rulekit.sdk.RuleKitClient;
import com.cars24.rulekit.sdk.SimulationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class RuleKitAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RuleKitAutoConfiguration.class));

    @Test
    void createsRuleKitClientAndSimulationServiceBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(RuleKitClient.class);
            assertThat(context).hasSingleBean(SimulationService.class);
            assertThat(context).hasSingleBean(RuleKitProperties.class);
        });
    }

    @Test
    void backsOffWhenHostProvidesRuleKitClient() {
        RuleKitClient hostClient = new RuleKitClient();

        contextRunner
                .withBean(RuleKitClient.class, () -> hostClient)
                .run(context -> assertThat(context.getBean(RuleKitClient.class)).isSameAs(hostClient));
    }

    @Test
    void wiresConfiguredDefaultTraceModeIntoRuleKitClient() {
        contextRunner
                .withPropertyValues("rule-kit.default-trace-mode=verbose")
                .run(context -> {
                    RuleKitClient client = context.getBean(RuleKitClient.class);

                    assertThat(client.defaultTraceMode()).isEqualTo(TraceMode.VERBOSE);
                });
    }
}

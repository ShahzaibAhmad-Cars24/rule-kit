package com.cars24.rulekit.starter;

import com.cars24.rulekit.sdk.RuleKitClient;
import com.cars24.rulekit.sdk.SimulationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(RuleKitProperties.class)
public class RuleKitAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    RuleKitClient ruleKitClient(ObjectProvider<ObjectMapper> objectMapperProvider,
                                RuleKitProperties properties) {
        return new RuleKitClient(
                objectMapperProvider.getIfAvailable(ObjectMapper::new),
                properties.getDefaultTraceMode()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    SimulationService simulationService(RuleKitClient ruleKitClient) {
        return new SimulationService(ruleKitClient);
    }
}

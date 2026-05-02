package com.cars24.rulekit.sample;

import com.cars24.rulekit.core.exception.RuleKitExceptionCode;
import com.cars24.rulekit.core.model.RuleSet;
import com.cars24.rulekit.sdk.RuleKitClientException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RuleKitYamlRuleSetLoader {

    private final ObjectMapper yamlObjectMapper = new ObjectMapper(new YAMLFactory());

    public RuleSet load(Resource resource) {
        try {
            return yamlObjectMapper.readValue(resource.getInputStream(), RuleSet.class);
        } catch (IOException e) {
            throw new RuleKitClientException(
                    RuleKitExceptionCode.INVALID_RULESET_PAYLOAD,
                    "Unable to load RuleKit RuleSet from YAML resource: " + resource.getDescription(),
                    e
            );
        }
    }
}

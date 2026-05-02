package com.cars24.rulekit.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record RuleSet(
        String id,
        String schemaVersion,
        @JsonProperty("executionMode") ExecutionMode executionMode,
        @JsonProperty("defaultResponse") JsonNode defaultResponse,
        List<RuleDefinition> rules
) {
    public RuleSet(String id, ExecutionMode executionMode, JsonNode defaultResponse, List<RuleDefinition> rules) {
        this(id, null, executionMode, defaultResponse, rules);
    }
}

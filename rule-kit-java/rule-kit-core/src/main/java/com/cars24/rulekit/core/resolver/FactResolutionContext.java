package com.cars24.rulekit.core.resolver;

import com.cars24.rulekit.core.model.ConditionDefinition;
import com.fasterxml.jackson.databind.JsonNode;

public record FactResolutionContext(
        String ruleSetId,
        String ruleId,
        int conditionIndex,
        String fieldRef,
        JsonNode input,
        ConditionDefinition condition
) {
}

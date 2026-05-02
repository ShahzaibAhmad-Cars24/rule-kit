package com.cars24.rulekit.core.resolver;

import com.cars24.rulekit.core.model.ConditionDefinition;
import com.fasterxml.jackson.databind.JsonNode;

public record DependencyResolutionContext(
        String parentRuleSetId,
        String parentRuleId,
        int conditionIndex,
        String dependencyRuleSetId,
        JsonNode input,
        ConditionDefinition condition
) {
}

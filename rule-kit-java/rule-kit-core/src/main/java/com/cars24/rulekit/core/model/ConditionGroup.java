package com.cars24.rulekit.core.model;

import java.util.List;

public record ConditionGroup(
        List<ConditionDefinition> all
) {
}

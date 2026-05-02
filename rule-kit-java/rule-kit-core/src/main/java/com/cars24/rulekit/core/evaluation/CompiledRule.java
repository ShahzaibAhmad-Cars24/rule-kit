package com.cars24.rulekit.core.evaluation;

import com.cars24.rulekit.core.model.RolloutDefinition;
import com.cars24.rulekit.core.model.RuleDefinition;

import java.util.List;

record CompiledRule(
        RuleDefinition source,
        List<CompiledCondition> conditions,
        RolloutDefinition rollout
) {

    CompiledRule(RuleDefinition source, List<CompiledCondition> conditions) {
        this(source, conditions, source != null ? source.rollout() : null);
    }
}

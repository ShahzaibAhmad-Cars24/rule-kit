package com.cars24.rulekit.core.evaluation;

import com.cars24.rulekit.core.model.RolloutDefinition;
import com.cars24.rulekit.core.model.RuleDefinition;

record CompiledRule(
        RuleDefinition source,
        RolloutDefinition rollout
) {
    CompiledRule(RuleDefinition source) {
        this(source, source != null ? source.rollout() : null);
    }
}

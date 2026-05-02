package com.cars24.rulekit.core.trace;

import java.util.List;

public record RuleTrace(
        String ruleId,
        boolean matched,
        List<ConditionTrace> conditions,
        RolloutTrace rollout
) {

    public RuleTrace(String ruleId, boolean matched, List<ConditionTrace> conditions) {
        this(ruleId, matched, conditions, null);
    }
}

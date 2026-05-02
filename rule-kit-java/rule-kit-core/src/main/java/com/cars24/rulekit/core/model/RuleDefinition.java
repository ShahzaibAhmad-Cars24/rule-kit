package com.cars24.rulekit.core.model;

public record RuleDefinition(
        String id,
        Integer priority,
        Boolean enabled,
        ConditionGroup when,
        RolloutDefinition rollout,
        RuleThen then
) {

    public RuleDefinition(String id,
                          Integer priority,
                          Boolean enabled,
                          ConditionGroup when,
                          RuleThen then) {
        this(id, priority, enabled, when, null, then);
    }
}

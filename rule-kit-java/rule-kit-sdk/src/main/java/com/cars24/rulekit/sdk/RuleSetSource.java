package com.cars24.rulekit.sdk;

import com.cars24.rulekit.core.model.RuleSet;

import java.util.Optional;

@FunctionalInterface
public interface RuleSetSource {
    Optional<RuleSet> findById(String ruleSetId);
}

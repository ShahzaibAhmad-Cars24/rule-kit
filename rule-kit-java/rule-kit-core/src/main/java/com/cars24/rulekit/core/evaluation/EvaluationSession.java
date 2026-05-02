package com.cars24.rulekit.core.evaluation;

import com.cars24.rulekit.core.resolver.DependencyResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class EvaluationSession {

    private final Set<String> activeRuleSets = new HashSet<>();
    private final Map<String, DependencyResult> dependencyResults = new HashMap<>();

    boolean enter(String ruleSetId) {
        return activeRuleSets.add(ruleSetId);
    }

    void exit(String ruleSetId) {
        activeRuleSets.remove(ruleSetId);
    }

    DependencyResult cachedDependency(String ruleSetId) {
        return dependencyResults.get(ruleSetId);
    }

    void cacheDependency(String ruleSetId, DependencyResult result) {
        dependencyResults.put(ruleSetId, result);
    }
}

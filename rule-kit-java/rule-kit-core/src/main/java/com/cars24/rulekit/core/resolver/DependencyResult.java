package com.cars24.rulekit.core.resolver;

import com.cars24.rulekit.core.evaluation.EvaluationResult;
import com.cars24.rulekit.core.model.DependencyExpectation;

public record DependencyResult(
        String ruleSetId,
        String matchedRuleId,
        boolean defaultUsed
) {

    public static DependencyResult from(EvaluationResult result, String ruleSetId) {
        return new DependencyResult(ruleSetId, result.matchedRuleId(), result.defaultUsed());
    }

    public boolean satisfies(DependencyExpectation expectation) {
        DependencyExpectation resolvedExpectation = expectation != null ? expectation : DependencyExpectation.MATCHED;
        return switch (resolvedExpectation) {
            case MATCHED -> !defaultUsed;
            case DEFAULT_USED -> defaultUsed;
        };
    }
}

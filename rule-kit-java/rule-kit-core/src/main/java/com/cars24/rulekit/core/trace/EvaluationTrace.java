package com.cars24.rulekit.core.trace;

import java.util.List;

public record EvaluationTrace(
        TraceMode mode,
        String schemaVersion,
        String evaluatorVersion,
        String ruleSetId,
        int evaluatedRuleCount,
        List<RuleTrace> rules
) {
}

package com.cars24.rulekit.core.evaluation;

import com.cars24.rulekit.core.trace.EvaluationTrace;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EvaluationResult(
        String matchedRuleId,
        boolean defaultUsed,
        JsonNode response,
        EvaluationTrace trace
) {
}

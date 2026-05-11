package com.cars24.rulekit.core.evaluation;

import com.cars24.rulekit.core.trace.EvaluationTrace;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * The result of evaluating a RuleSet against an input.
 *
 * <p>For FIRST_MATCH mode, {@code matchedRuleId}, {@code defaultUsed}, and {@code response}
 * are populated; {@code matches} is null.
 *
 * <p>For ALL_MATCHES mode, {@code matches} contains every rule that fired.
 * {@code matchedRuleId} is the id of the highest-priority match (or null when nothing matched),
 * {@code response} is that same match's response (or the defaultResponse when nothing matched),
 * and {@code defaultUsed} is true only when the list is empty and the default was returned.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EvaluationResult(
        String matchedRuleId,
        boolean defaultUsed,
        JsonNode response,
        EvaluationTrace trace,
        List<RuleMatch> matches,
        EvaluationStats stats
) {

    /** Backward-compatible constructor used by FIRST_MATCH path (no matches list). */
    public EvaluationResult(String matchedRuleId, boolean defaultUsed, JsonNode response, EvaluationTrace trace) {
        this(matchedRuleId, defaultUsed, response, trace, null, null);
    }

    public EvaluationResult(String matchedRuleId,
                            boolean defaultUsed,
                            JsonNode response,
                            EvaluationTrace trace,
                            List<RuleMatch> matches) {
        this(matchedRuleId, defaultUsed, response, trace, matches, null);
    }
}

package com.cars24.rulekit.core.evaluation;

import com.cars24.rulekit.core.model.RolloutDefinition;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Represents a single matched rule in an ALL_MATCHES evaluation.
 *
 * @param ruleId   the id of the matched rule
 * @param priority the priority of the matched rule (higher = evaluated first)
 * @param response the response payload from the matched rule's then block
 * @param rollout  the rollout definition for the matched rule, or null if none
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RuleMatch(
        String ruleId,
        Integer priority,
        JsonNode response,
        RolloutDefinition rollout
) {
}

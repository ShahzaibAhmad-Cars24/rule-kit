package com.cars24.rulekit.core.trace;

import com.cars24.rulekit.core.model.ConditionKind;
import com.fasterxml.jackson.databind.JsonNode;

public record ConditionTrace(
        ConditionKind conditionKind,
        String fieldRef,
        String operator,
        JsonNode expected,
        JsonNode expectedTo,
        JsonNode actual,
        boolean matched,
        boolean resolved,
        boolean skipped,
        String reason,
        JsonNode details
) {

    public ConditionTrace(String fieldRef,
                          String operator,
                          JsonNode expected,
                          JsonNode expectedTo,
                          JsonNode actual,
                          boolean matched,
                          boolean resolved,
                          boolean skipped,
                          String reason) {
        this(ConditionKind.FIELD, fieldRef, operator, expected, expectedTo, actual, matched, resolved, skipped, reason, null);
    }
}

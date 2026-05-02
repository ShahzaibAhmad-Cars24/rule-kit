package com.cars24.rulekit.core.resolver;

import com.cars24.rulekit.core.model.ConditionDefinition;
import com.cars24.rulekit.core.model.SegmentMatchType;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record SegmentResolutionContext(
        String ruleSetId,
        String ruleId,
        int conditionIndex,
        String lookupRef,
        JsonNode lookupValue,
        List<String> segmentNames,
        SegmentMatchType match,
        JsonNode input,
        ConditionDefinition condition
) {
}

package com.cars24.rulekit.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public record ConditionDefinition(
        ConditionKind kind,
        @JsonProperty("fieldRef") String fieldRef,
        String operator,
        JsonNode value,
        @JsonProperty("valueTo") JsonNode valueTo,
        @JsonProperty("segmentNames") java.util.List<String> segmentNames,
        SegmentMatchType match,
        @JsonProperty("lookupRef") String lookupRef,
        @JsonProperty("ruleSetId") String ruleSetId,
        DependencyExpectation expect
) {

    public ConditionDefinition(String fieldRef,
                               String operator,
                               JsonNode value,
                               JsonNode valueTo) {
        this(ConditionKind.FIELD, fieldRef, operator, value, valueTo, java.util.List.of(), null, null, null, null);
    }

    public ConditionDefinition {
        kind = kind != null ? kind : ConditionKind.FIELD;
        segmentNames = segmentNames == null ? java.util.List.of() : java.util.List.copyOf(segmentNames);
    }

    public ConditionKind resolvedKind() {
        return kind != null ? kind : ConditionKind.FIELD;
    }
}

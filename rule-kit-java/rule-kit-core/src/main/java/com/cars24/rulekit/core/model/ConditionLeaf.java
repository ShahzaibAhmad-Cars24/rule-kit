package com.cars24.rulekit.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * A leaf node in a condition tree — wraps a single {@link ConditionDefinition}.
 * All fields mirror {@link ConditionDefinition} and are passed through unchanged.
 */
public record ConditionLeaf(
        ConditionKind kind,
        @JsonProperty("fieldRef") String fieldRef,
        String operator,
        JsonNode value,
        @JsonProperty("valueTo") JsonNode valueTo,
        @JsonProperty("segmentNames") List<String> segmentNames,
        SegmentMatchType match,
        @JsonProperty("lookupRef") String lookupRef,
        @JsonProperty("ruleSetId") String ruleSetId,
        DependencyExpectation expect
) implements ConditionNode {

    public ConditionLeaf {
        kind = kind != null ? kind : ConditionKind.FIELD;
        segmentNames = segmentNames == null ? List.of() : List.copyOf(segmentNames);
    }

    /** Convert to a {@link ConditionDefinition} for evaluation. */
    public ConditionDefinition toConditionDefinition() {
        return new ConditionDefinition(
                kind,
                fieldRef,
                operator,
                value,
                valueTo,
                segmentNames,
                match,
                lookupRef,
                ruleSetId,
                expect
        );
    }

    public static ConditionLeaf fromConditionDefinition(ConditionDefinition definition) {
        return new ConditionLeaf(
                definition.kind(),
                definition.fieldRef(),
                definition.operator(),
                definition.value(),
                definition.valueTo(),
                definition.segmentNames(),
                definition.match(),
                definition.lookupRef(),
                definition.ruleSetId(),
                definition.expect()
        );
    }
}

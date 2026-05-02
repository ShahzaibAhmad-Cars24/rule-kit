package com.cars24.rulekit.core.resolver;

import com.cars24.rulekit.core.model.ConditionDefinition;
import com.cars24.rulekit.core.model.SegmentMatchType;

import java.util.Map;

public record SegmentMembershipResult(
        Map<String, Boolean> memberships
) {

    public SegmentMembershipResult {
        memberships = memberships == null ? Map.of() : Map.copyOf(memberships);
    }

    public static SegmentMembershipResult of(Map<String, Boolean> memberships) {
        return new SegmentMembershipResult(memberships);
    }

    public boolean matches(ConditionDefinition condition) {
        SegmentMatchType matchType = condition.match() != null ? condition.match() : SegmentMatchType.ANY;
        if (condition.segmentNames() == null || condition.segmentNames().isEmpty()) {
            return false;
        }
        return switch (matchType) {
            case ANY -> condition.segmentNames().stream().anyMatch(name -> Boolean.TRUE.equals(memberships.get(name)));
            case ALL -> condition.segmentNames().stream().allMatch(name -> Boolean.TRUE.equals(memberships.get(name)));
        };
    }
}

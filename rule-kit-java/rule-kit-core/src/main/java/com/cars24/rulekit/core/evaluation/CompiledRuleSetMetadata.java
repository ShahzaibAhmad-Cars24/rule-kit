package com.cars24.rulekit.core.evaluation;

import java.util.List;

public record CompiledRuleSetMetadata(
        List<String> referencedSegmentNames,
        List<String> dependencyRuleSetIds
) {

    public CompiledRuleSetMetadata {
        referencedSegmentNames = referencedSegmentNames == null ? List.of() : List.copyOf(referencedSegmentNames);
        dependencyRuleSetIds = dependencyRuleSetIds == null ? List.of() : List.copyOf(dependencyRuleSetIds);
    }
}

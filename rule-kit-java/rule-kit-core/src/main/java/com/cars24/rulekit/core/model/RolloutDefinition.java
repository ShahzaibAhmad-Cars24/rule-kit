package com.cars24.rulekit.core.model;

import java.util.List;

public record RolloutDefinition(
        Double percentage,
        String unitRef,
        RolloutAlgorithm algorithm,
        Integer bucketCount,
        List<String> saltRefs
) {

    public RolloutDefinition {
        saltRefs = saltRefs == null ? List.of() : List.copyOf(saltRefs);
    }
}

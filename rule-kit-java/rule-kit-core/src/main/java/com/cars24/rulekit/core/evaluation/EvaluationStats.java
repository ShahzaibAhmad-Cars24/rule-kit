package com.cars24.rulekit.core.evaluation;

public record EvaluationStats(
        int segmentReferencesConsulted,
        int prefetchedSegmentHits,
        int lazySegmentResolutions,
        int cachedSegmentHits,
        int dependencyReferencesConsulted,
        int prefetchedDependencyResultHits,
        int prefetchedDependencyRuleSetHits,
        int cachedDependencyHits
) {
}

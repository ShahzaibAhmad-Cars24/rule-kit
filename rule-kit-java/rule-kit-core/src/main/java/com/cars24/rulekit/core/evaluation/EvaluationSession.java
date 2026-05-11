package com.cars24.rulekit.core.evaluation;

import com.cars24.rulekit.core.resolver.DependencyResult;
import com.cars24.rulekit.core.resolver.SegmentMembershipResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class EvaluationSession {

    private final Set<String> activeRuleSets = new HashSet<>();
    private final Map<String, DependencyResult> dependencyResults = new HashMap<>();
    private final Map<SegmentCacheKey, SegmentMembershipResult> segmentResults = new HashMap<>();
    private int segmentReferencesConsulted;
    private int prefetchedSegmentHits;
    private int lazySegmentResolutions;
    private int cachedSegmentHits;
    private int dependencyReferencesConsulted;
    private int prefetchedDependencyResultHits;
    private int prefetchedDependencyRuleSetHits;
    private int cachedDependencyHits;

    boolean enter(String ruleSetId) {
        return activeRuleSets.add(ruleSetId);
    }

    void exit(String ruleSetId) {
        activeRuleSets.remove(ruleSetId);
    }

    DependencyResult cachedDependency(String ruleSetId) {
        return dependencyResults.get(ruleSetId);
    }

    void cacheDependency(String ruleSetId, DependencyResult result) {
        dependencyResults.put(ruleSetId, result);
    }

    SegmentMembershipResult cachedSegment(SegmentCacheKey key) {
        return segmentResults.get(key);
    }

    void cacheSegment(SegmentCacheKey key, SegmentMembershipResult result) {
        segmentResults.put(key, result);
    }

    SegmentCacheKey segmentKey(String lookupRef, String lookupValue, List<String> segmentNames) {
        return new SegmentCacheKey(lookupRef, lookupValue, segmentNames == null ? List.of() : List.copyOf(segmentNames));
    }

    void recordSegmentReferenceConsulted() {
        segmentReferencesConsulted++;
    }

    void recordPrefetchedSegmentHit() {
        prefetchedSegmentHits++;
    }

    void recordLazySegmentResolution() {
        lazySegmentResolutions++;
    }

    void recordCachedSegmentHit() {
        cachedSegmentHits++;
    }

    void recordDependencyReferenceConsulted() {
        dependencyReferencesConsulted++;
    }

    void recordPrefetchedDependencyResultHit() {
        prefetchedDependencyResultHits++;
    }

    void recordPrefetchedDependencyRuleSetHit() {
        prefetchedDependencyRuleSetHits++;
    }

    void recordCachedDependencyHit() {
        cachedDependencyHits++;
    }

    EvaluationStats stats() {
        return new EvaluationStats(
                segmentReferencesConsulted,
                prefetchedSegmentHits,
                lazySegmentResolutions,
                cachedSegmentHits,
                dependencyReferencesConsulted,
                prefetchedDependencyResultHits,
                prefetchedDependencyRuleSetHits,
                cachedDependencyHits
        );
    }

    record SegmentCacheKey(String lookupRef, String lookupValue, List<String> segmentNames) {
    }
}

package com.cars24.rulekit.core.evaluation;

import com.cars24.rulekit.core.model.RuleSet;
import com.cars24.rulekit.core.resolver.DependencyResult;
import com.cars24.rulekit.core.resolver.FactResolver;
import com.cars24.rulekit.core.resolver.SegmentMembershipResult;
import com.cars24.rulekit.core.trace.TraceMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RuleKitEvaluationOptimizationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RuleKitEvaluator evaluator = new RuleKitEvaluator();

    @Test
    void compiledRuleSetMetadataExposesReferencedSegmentsAndDependencies() throws Exception {
        RuleSet ruleSet = objectMapper.readValue("""
                {
                  "id": "metadata-rules",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": false,
                  "rules": [
                    {
                      "id": "rule-1",
                      "priority": 20,
                      "enabled": true,
                      "when": { "tree": { "type": "group", "op": "AND", "children": [
                        { "type": "leaf", "kind": "SEGMENT", "segmentNames": ["vip", "beta"], "match": "ANY", "lookupRef": "userId" },
                        { "type": "leaf", "kind": "DEPENDENCY", "ruleSetId": "child-a", "expect": "MATCHED" }
                      ]}},
                      "then": { "response": true }
                    },
                    {
                      "id": "rule-2",
                      "priority": 10,
                      "enabled": true,
                      "when": { "all": [
                        { "kind": "SEGMENT", "segmentNames": ["beta", "new-user"], "match": "ANY", "lookupRef": "userId" },
                        { "kind": "DEPENDENCY", "ruleSetId": "child-b", "expect": "MATCHED" },
                        { "kind": "DEPENDENCY", "ruleSetId": "child-a", "expect": "MATCHED" }
                      ]},
                      "then": { "response": true }
                    }
                  ]
                }
                """, RuleSet.class);

        CompiledRuleSet compiled = evaluator.compile(ruleSet);

        assertThat(compiled.metadata().referencedSegmentNames())
                .containsExactly("vip", "beta", "new-user");
        assertThat(compiled.metadata().dependencyRuleSetIds())
                .containsExactly("child-a", "child-b");
    }

    @Test
    void prefetchedSegmentMembershipsBypassLookupResolutionAndSegmentResolver() throws Exception {
        RuleSet ruleSet = objectMapper.readValue("""
                {
                  "id": "segment-prefetch-rules",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": false,
                  "rules": [
                    {
                      "id": "segment-rule",
                      "priority": 10,
                      "enabled": true,
                      "when": { "all": [
                        { "kind": "SEGMENT", "segmentNames": ["vip"], "match": "ANY", "lookupRef": "userId" }
                      ]},
                      "then": { "response": true }
                    }
                  ]
                }
                """, RuleSet.class);

        EvaluationOptions options = EvaluationOptions.builder()
                .factResolver(FactResolver.defaultResolver())
                .segmentResolver(context -> { throw new AssertionError("segment resolver should not be called"); })
                .prefetchedSegmentMembershipResolver(context -> Optional.of(SegmentMembershipResult.of(Map.of("vip", true))))
                .build();

        EvaluationResult result = evaluator.evaluate(ruleSet, objectMapper.readTree("{\"userId\":\"u1\"}"), TraceMode.COMPACT, options);

        assertThat(result.defaultUsed()).isFalse();
        assertThat(result.stats().segmentReferencesConsulted()).isEqualTo(1);
        assertThat(result.stats().prefetchedSegmentHits()).isEqualTo(1);
        assertThat(result.stats().lazySegmentResolutions()).isZero();
    }

    @Test
    void dependencyResultInjectionTakesPrecedenceOverPrefetchedAndLazyRulesets() throws Exception {
        RuleSet parent = objectMapper.readValue("""
                {
                  "id": "parent-rules",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": false,
                  "rules": [
                    {
                      "id": "parent-rule",
                      "priority": 10,
                      "enabled": true,
                      "when": { "all": [
                        { "kind": "DEPENDENCY", "ruleSetId": "child", "expect": "MATCHED" }
                      ]},
                      "then": { "response": true }
                    }
                  ]
                }
                """, RuleSet.class);

        AtomicInteger prefetchedRuleSetCalls = new AtomicInteger();
        EvaluationOptions options = EvaluationOptions.builder()
                .dependencyResultResolver(context -> Optional.of(new DependencyResult("child", "precomputed", false)))
                .dependencyRuleSetResolver(context -> {
                    prefetchedRuleSetCalls.incrementAndGet();
                    return Optional.empty();
                })
                .build();

        EvaluationResult result = evaluator.evaluate(parent, objectMapper.readTree("{}"), TraceMode.COMPACT, options);

        assertThat(result.defaultUsed()).isFalse();
        assertThat(prefetchedRuleSetCalls).hasValue(0);
        assertThat(result.stats().dependencyReferencesConsulted()).isEqualTo(1);
        assertThat(result.stats().prefetchedDependencyResultHits()).isEqualTo(1);
        assertThat(result.stats().prefetchedDependencyRuleSetHits()).isZero();
    }

    @Test
    void repeatedSegmentAndDependencyReferencesAreDeduplicatedWithinOneEvaluation() throws Exception {
        RuleSet parent = objectMapper.readValue("""
                {
                  "id": "parent-rules",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": false,
                  "rules": [
                    {
                      "id": "parent-rule",
                      "priority": 10,
                      "enabled": true,
                      "when": { "tree": { "type": "group", "op": "AND", "children": [
                        { "type": "leaf", "kind": "SEGMENT", "segmentNames": ["vip"], "match": "ANY", "lookupRef": "userId" },
                        { "type": "leaf", "kind": "SEGMENT", "segmentNames": ["vip"], "match": "ANY", "lookupRef": "userId" },
                        { "type": "leaf", "kind": "DEPENDENCY", "ruleSetId": "child", "expect": "MATCHED" },
                        { "type": "leaf", "kind": "DEPENDENCY", "ruleSetId": "child", "expect": "MATCHED" }
                      ]}},
                      "then": { "response": true }
                    }
                  ]
                }
                """, RuleSet.class);
        RuleSet child = objectMapper.readValue("""
                {
                  "id": "child",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": false,
                  "rules": [
                    {
                      "id": "child-rule",
                      "priority": 10,
                      "enabled": true,
                      "when": { "all": [
                        { "fieldRef": "platform", "operator": "EQ", "value": "android" }
                      ]},
                      "then": { "response": true }
                    }
                  ]
                }
                """, RuleSet.class);
        CompiledRuleSet compiledChild = evaluator.compile(child);

        AtomicInteger segmentResolverCalls = new AtomicInteger();
        AtomicInteger dependencyRuleSetCalls = new AtomicInteger();
        EvaluationOptions options = EvaluationOptions.builder()
                .segmentResolver(context -> {
                    segmentResolverCalls.incrementAndGet();
                    return SegmentMembershipResult.of(Map.of("vip", true));
                })
                .dependencyRuleSetResolver(context -> {
                    dependencyRuleSetCalls.incrementAndGet();
                    return Optional.of(compiledChild);
                })
                .build();

        EvaluationResult result = evaluator.evaluate(
                parent,
                objectMapper.readTree("{\"userId\":\"u1\",\"platform\":\"android\"}"),
                TraceMode.COMPACT,
                options
        );

        assertThat(result.defaultUsed()).isFalse();
        assertThat(segmentResolverCalls).hasValue(1);
        assertThat(dependencyRuleSetCalls).hasValue(1);
        assertThat(result.stats().segmentReferencesConsulted()).isEqualTo(2);
        assertThat(result.stats().lazySegmentResolutions()).isEqualTo(1);
        assertThat(result.stats().cachedSegmentHits()).isEqualTo(1);
        assertThat(result.stats().dependencyReferencesConsulted()).isEqualTo(2);
        assertThat(result.stats().prefetchedDependencyRuleSetHits()).isEqualTo(1);
        assertThat(result.stats().cachedDependencyHits()).isEqualTo(1);
    }

    @Test
    void prefetchedSegmentMembershipsAreScopedToResolvedLookupIdentity() throws Exception {
        RuleSet ruleSet = objectMapper.readValue("""
                {
                  "id": "segment-prefetch-scoped-rules",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": false,
                  "rules": [
                    {
                      "id": "segment-rule",
                      "priority": 10,
                      "enabled": true,
                      "when": { "tree": { "type": "group", "op": "AND", "children": [
                        { "type": "leaf", "kind": "SEGMENT", "segmentNames": ["vip"], "match": "ANY", "lookupRef": "userId" },
                        { "type": "leaf", "kind": "SEGMENT", "segmentNames": ["seller-priority"], "match": "ANY", "lookupRef": "sellerId" }
                      ]}},
                      "then": { "response": true }
                    }
                  ]
                }
                """, RuleSet.class);

        AtomicInteger prefetchCalls = new AtomicInteger();
        EvaluationOptions options = EvaluationOptions.builder()
                .prefetchedSegmentMembershipResolver(context -> {
                    prefetchCalls.incrementAndGet();
                    if ("userId".equals(context.lookupRef()) && "u1".equals(context.lookupValue().asText())) {
                        return Optional.of(SegmentMembershipResult.of(Map.of("vip", true)));
                    }
                    if ("sellerId".equals(context.lookupRef()) && "s1".equals(context.lookupValue().asText())) {
                        return Optional.of(SegmentMembershipResult.of(Map.of("seller-priority", true)));
                    }
                    return Optional.empty();
                })
                .segmentResolver(context -> {
                    throw new AssertionError("segment resolver should not be called");
                })
                .build();

        EvaluationResult result = evaluator.evaluate(
                ruleSet,
                objectMapper.readTree("{\"userId\":\"u1\",\"sellerId\":\"s1\"}"),
                TraceMode.COMPACT,
                options
        );

        assertThat(result.defaultUsed()).isFalse();
        assertThat(prefetchCalls).hasValue(2);
        assertThat(result.stats().segmentReferencesConsulted()).isEqualTo(2);
        assertThat(result.stats().prefetchedSegmentHits()).isEqualTo(2);
        assertThat(result.stats().cachedSegmentHits()).isZero();
    }
}

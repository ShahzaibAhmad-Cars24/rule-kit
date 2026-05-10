package com.cars24.rulekit.core.evaluation;

import com.cars24.rulekit.core.exception.RuleKitEvaluationException;
import com.cars24.rulekit.core.exception.RuleKitExceptionCode;
import com.cars24.rulekit.core.model.ConditionDefinition;
import com.cars24.rulekit.core.model.ConditionKind;
import com.cars24.rulekit.core.model.DependencyExpectation;
import com.cars24.rulekit.core.model.RuleDefinition;
import com.cars24.rulekit.core.model.RuleKitVersions;
import com.cars24.rulekit.core.model.RuleSet;
import com.cars24.rulekit.core.resolver.FactResolver;
import com.cars24.rulekit.core.resolver.ResolvedFact;
import com.cars24.rulekit.core.resolver.SegmentMembershipResult;
import com.cars24.rulekit.core.trace.ConditionTrace;
import com.cars24.rulekit.core.trace.RuleTrace;
import com.cars24.rulekit.core.trace.TraceMode;
import com.cars24.rulekit.core.validation.RuleSetValidator;
import com.cars24.rulekit.core.validation.ValidationMessage;
import com.cars24.rulekit.core.validation.ValidationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleKitNativeRuleSetTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RuleKitEvaluator evaluator = new RuleKitEvaluator();

    @Test
    void deserializesNativeRuleSetWithFieldSegmentDependencyAndRollout() throws Exception {
        RuleSet ruleSet = objectMapper.readValue("""
                {
                  "id": "tenant-a.checkout-banner",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": { "enabled": false },
                  "rules": [
                    {
                      "id": "android-beta",
                      "priority": 100,
                      "enabled": true,
                      "when": { "tree": { "type": "group", "op": "AND", "children": [
                        { "type": "leaf", "kind": "FIELD", "fieldRef": "platform", "operator": "EQ", "value": "android" },
                        { "type": "leaf", "kind": "SEGMENT", "segmentNames": ["beta-users"], "match": "ANY", "lookupRef": "userId" },
                        { "type": "leaf", "kind": "DEPENDENCY", "ruleSetId": "tenant-a.checkout-enabled", "expect": "MATCHED" }
                      ]}},
                      "rollout": {
                        "percentage": 25,
                        "unitRef": "userId",
                        "algorithm": "MURMUR3_32_SALTED_V1",
                        "bucketCount": 100,
                        "saltRefs": ["tenantId", "configName", "ruleId", "splitSeed"]
                      },
                      "then": { "response": { "enabled": true } }
                    }
                  ]
                }
                """, RuleSet.class);

        RuleDefinition rule = ruleSet.rules().get(0);

        assertThat(ruleSet.schemaVersion()).isEqualTo(RuleKitVersions.RULESET_SCHEMA_VERSION);
        assertThat(rule.rollout().percentage()).isEqualTo(25);
        // Verify tree structure: root group with 3 children (FIELD, SEGMENT, DEPENDENCY)
        assertThat(rule.when().tree()).isInstanceOf(com.cars24.rulekit.core.model.ConditionGroupNode.class);
        var rootGroup = (com.cars24.rulekit.core.model.ConditionGroupNode) rule.when().tree();
        assertThat(rootGroup.children()).hasSize(3);
        assertThat(rootGroup.children().get(0)).isInstanceOf(com.cars24.rulekit.core.model.ConditionLeaf.class);
        assertThat(rootGroup.children().get(1)).isInstanceOf(com.cars24.rulekit.core.model.ConditionLeaf.class);
        assertThat(rootGroup.children().get(2)).isInstanceOf(com.cars24.rulekit.core.model.ConditionLeaf.class);
        var segmentLeaf = (com.cars24.rulekit.core.model.ConditionLeaf) rootGroup.children().get(1);
        var depLeaf = (com.cars24.rulekit.core.model.ConditionLeaf) rootGroup.children().get(2);
        assertThat(segmentLeaf.segmentNames()).containsExactly("beta-users");
        assertThat(depLeaf.expect()).isEqualTo(DependencyExpectation.MATCHED);
    }

    @Test
    void validationRejectsInvalidNativeOperands() throws Exception {
        RuleSet ruleSet = objectMapper.readValue("""
                {
                  "id": "invalid-native",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": false,
                  "rules": [
                    {
                      "id": "bad-rule",
                      "priority": 1,
                      "enabled": true,
                      "when": { "tree": { "type": "group", "op": "AND", "children": [
                        { "type": "leaf", "kind": "FIELD", "fieldRef": "age", "operator": "GT", "value": "abc" },
                        { "type": "leaf", "kind": "SEGMENT", "segmentNames": [], "match": "ANY", "lookupRef": "userId" },
                        { "type": "leaf", "kind": "DEPENDENCY", "ruleSetId": "", "expect": "MATCHED" }
                      ]}},
                      "rollout": { "percentage": 101, "unitRef": "userId", "algorithm": "MURMUR3_32_SALTED_V1", "bucketCount": 100 },
                      "then": { "response": true }
                    }
                  ]
                }
                """, RuleSet.class);

        ValidationResult result = RuleSetValidator.validate(ruleSet);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).extracting(ValidationMessage::code)
                .contains(
                        RuleKitExceptionCode.INVALID_NUMERIC_VALUE,
                        RuleKitExceptionCode.INVALID_SEGMENT_CONDITION,
                        RuleKitExceptionCode.INVALID_DEPENDENCY_CONDITION,
                        RuleKitExceptionCode.INVALID_ROLLOUT
                );
    }

    @Test
    void evaluatesSegmentDependencyAndRolloutWithVerboseTrace() throws Exception {
        RuleSet parent = objectMapper.readValue("""
                {
                  "id": "tenant-a.checkout-banner",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": false,
                  "rules": [
                    {
                      "id": "android-beta",
                      "priority": 100,
                      "enabled": true,
                      "when": { "tree": { "type": "group", "op": "AND", "children": [
                        { "type": "leaf", "kind": "FIELD", "fieldRef": "platform", "operator": "EQ", "value": "android" },
                        { "type": "leaf", "kind": "SEGMENT", "segmentNames": ["beta-users", "vip-users"], "match": "ANY", "lookupRef": "userId" },
                        { "type": "leaf", "kind": "DEPENDENCY", "ruleSetId": "tenant-a.checkout-enabled", "expect": "MATCHED" }
                      ]}},
                      "rollout": { "percentage": 100, "unitRef": "userId", "algorithm": "MURMUR3_32_SALTED_V1", "bucketCount": 100 },
                      "then": { "response": { "enabled": true } }
                    }
                  ]
                }
                """, RuleSet.class);
        RuleSet dependency = objectMapper.readValue("""
                {
                  "id": "tenant-a.checkout-enabled",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": false,
                  "rules": [
                    {
                      "id": "enabled-for-city",
                      "priority": 1,
                      "enabled": true,
                      "when": { "tree": { "type": "group", "op": "AND", "children": [{ "type": "leaf", "kind": "FIELD", "fieldRef": "city", "operator": "EQ", "value": "Gurgaon" }]} },
                      "then": { "response": true }
                    }
                  ]
                }
                """, RuleSet.class);
        CompiledRuleSet compiledDependency = evaluator.compile(dependency);
        EvaluationOptions options = EvaluationOptions.builder()
                .segmentResolver(context -> SegmentMembershipResult.of(Map.of(
                        "beta-users", true,
                        "vip-users", false
                )))
                .dependencyResolver(context -> Optional.of(compiledDependency))
                .build();

        EvaluationResult result = evaluator.evaluate(
                parent,
                objectMapper.readTree("""
                        {
                          "tenantId": "tenant-a",
                          "configName": "checkout-banner",
                          "splitSeed": "seed-1",
                          "userId": "user-123",
                          "platform": "android",
                          "city": "Gurgaon"
                        }
                        """),
                TraceMode.VERBOSE,
                options
        );

        RuleTrace ruleTrace = result.trace().rules().get(0);

        assertThat(result.defaultUsed()).isFalse();
        assertThat(result.matchedRuleId()).isEqualTo("android-beta");
        assertThat(ruleTrace.rollout().hashInput()).isEqualTo("tenant-a:checkout-banner:android-beta:seed-1:user-123");
        assertThat(ruleTrace.conditions()).extracting(ConditionTrace::conditionKind)
                .containsExactly(ConditionKind.FIELD, ConditionKind.SEGMENT, ConditionKind.DEPENDENCY);
        assertThat(ruleTrace.conditions().get(1).details().get("match").asText()).isEqualTo("ANY");
        assertThat(ruleTrace.conditions().get(2).details().get("dependencyRuleSetId").asText())
                .isEqualTo("tenant-a.checkout-enabled");
    }

    @Test
    void dependencyCycleThrowsTypedException() throws Exception {
        RuleSet ruleSet = objectMapper.readValue("""
                {
                  "id": "cycle",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": false,
                  "rules": [
                    {
                      "id": "cycle-rule",
                      "priority": 1,
                      "enabled": true,
                      "when": { "tree": { "type": "group", "op": "AND", "children": [{ "type": "leaf", "kind": "DEPENDENCY", "ruleSetId": "cycle", "expect": "MATCHED" }]} },
                      "then": { "response": true }
                    }
                  ]
                }
                """, RuleSet.class);
        CompiledRuleSet compiled = evaluator.compile(ruleSet);
        EvaluationOptions options = EvaluationOptions.builder()
                .dependencyResolver(context -> Optional.of(compiled))
                .build();

        assertThatThrownBy(() -> evaluator.evaluate(compiled, objectMapper.readTree("{}"), TraceMode.COMPACT, options))
                .isInstanceOf(RuleKitEvaluationException.class)
                .hasMessageContaining("Dependency cycle detected");
    }

    @Test
    void rolloutExclusionFallsThroughToDefaultWithTrace() throws Exception {
        RuleSet ruleSet = objectMapper.readValue("""
                {
                  "id": "rollout-zero",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": false,
                  "rules": [
                    {
                      "id": "never-included",
                      "priority": 1,
                      "enabled": true,
                      "when": { "tree": { "type": "group", "op": "AND", "children": [{ "type": "leaf", "kind": "FIELD", "fieldRef": "city", "operator": "EQ", "value": "Gurgaon" }]} },
                      "rollout": { "percentage": 0, "unitRef": "userId", "algorithm": "MURMUR3_32_SALTED_V1", "bucketCount": 100 },
                      "then": { "response": true }
                    }
                  ]
                }
                """, RuleSet.class);

        EvaluationResult result = evaluator.evaluate(
                ruleSet,
                objectMapper.readTree("{\"tenantId\":\"t1\",\"configName\":\"c1\",\"splitSeed\":\"s1\",\"userId\":\"u1\",\"city\":\"Gurgaon\"}"),
                TraceMode.VERBOSE
        );

        assertThat(result.defaultUsed()).isTrue();
        assertThat(result.trace().rules().get(0).rollout().evaluated()).isTrue();
        assertThat(result.trace().rules().get(0).rollout().included()).isFalse();
    }

    @Test
    void rolloutMissingRequiredSaltThrowsTypedException() throws Exception {
        RuleSet ruleSet = objectMapper.readValue("""
                {
                  "id": "rollout-missing-salt",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": false,
                  "rules": [
                    {
                      "id": "needs-seed",
                      "priority": 1,
                      "enabled": true,
                      "when": { "tree": { "type": "group", "op": "AND", "children": [{ "type": "leaf", "kind": "FIELD", "fieldRef": "city", "operator": "EQ", "value": "Gurgaon" }]} },
                      "rollout": { "percentage": 100, "unitRef": "userId", "algorithm": "MURMUR3_32_SALTED_V1", "bucketCount": 100 },
                      "then": { "response": true }
                    }
                  ]
                }
                """, RuleSet.class);

        assertThatThrownBy(() -> evaluator.evaluate(
                ruleSet,
                objectMapper.readTree("{\"tenantId\":\"t1\",\"configName\":\"c1\",\"userId\":\"u1\",\"city\":\"Gurgaon\"}"),
                TraceMode.COMPACT
        ))
                .isInstanceOf(RuleKitEvaluationException.class)
                .extracting(error -> ((RuleKitEvaluationException) error).code())
                .isEqualTo(RuleKitExceptionCode.ROLLOUT_SALT_NOT_RESOLVED);
    }

    @Test
    void rolloutAndSegmentLookupCanUseLazyFactResolver() throws Exception {
        RuleSet ruleSet = objectMapper.readValue("""
                {
                  "id": "lazy-rollout-and-segment",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": false,
                  "rules": [
                    {
                      "id": "lazy-rule",
                      "priority": 1,
                      "enabled": true,
                      "when": { "tree": { "type": "group", "op": "AND", "children": [
                        { "type": "leaf", "kind": "FIELD", "fieldRef": "city", "operator": "EQ", "value": "Gurgaon" },
                        { "type": "leaf", "kind": "SEGMENT", "segmentNames": ["beta-users"], "match": "ANY", "lookupRef": "userId" }
                      ]}},
                      "rollout": { "percentage": 100, "unitRef": "userId", "algorithm": "MURMUR3_32_SALTED_V1", "bucketCount": 100 },
                      "then": { "response": true }
                    }
                  ]
                }
                """, RuleSet.class);
        EvaluationOptions options = EvaluationOptions.builder()
                .factResolver(FactResolver.contextual(context -> switch (context.fieldRef()) {
                    case "tenantId" -> ResolvedFact.found(objectMapper.valueToTree("tenant-a"));
                    case "configName" -> ResolvedFact.found(objectMapper.valueToTree("checkout"));
                    case "splitSeed" -> ResolvedFact.found(objectMapper.valueToTree("seed-1"));
                    case "userId" -> ResolvedFact.found(objectMapper.valueToTree("user-123"));
                    default -> FactResolver.defaultResolver().resolve(context);
                }))
                .segmentResolver(context -> {
                    assertThat(context.lookupValue().asText()).isEqualTo("user-123");
                    return SegmentMembershipResult.of(Map.of("beta-users", true));
                })
                .build();

        EvaluationResult result = evaluator.evaluate(
                ruleSet,
                objectMapper.readTree("{\"city\":\"Gurgaon\"}"),
                TraceMode.VERBOSE,
                options
        );

        assertThat(result.defaultUsed()).isFalse();
        assertThat(result.trace().rules().get(0).rollout().hashInput())
                .isEqualTo("tenant-a:checkout:lazy-rule:seed-1:user-123");
    }

    @Test
    void missingSegmentResolverThrowsTypedExceptionOnlyWhenSegmentConditionIsReached() throws Exception {
        RuleSet ruleSet = objectMapper.readValue("""
                {
                  "id": "segment-requires-host",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": false,
                  "rules": [
                    {
                      "id": "segment-rule",
                      "priority": 1,
                      "enabled": true,
                      "when": { "tree": { "type": "group", "op": "AND", "children": [
                        { "type": "leaf", "kind": "FIELD", "fieldRef": "platform", "operator": "EQ", "value": "android" },
                        { "type": "leaf", "kind": "SEGMENT", "segmentNames": ["beta-users"], "match": "ALL", "lookupRef": "userId" }
                      ]}},
                      "then": { "response": true }
                    }
                  ]
                }
                """, RuleSet.class);

        EvaluationResult skipped = evaluator.evaluate(
                ruleSet,
                objectMapper.readTree("{\"platform\":\"ios\",\"userId\":\"u1\"}"),
                TraceMode.COMPACT
        );
        assertThat(skipped.defaultUsed()).isTrue();

        assertThatThrownBy(() -> evaluator.evaluate(
                ruleSet,
                objectMapper.readTree("{\"platform\":\"android\",\"userId\":\"u1\"}"),
                TraceMode.COMPACT
        ))
                .isInstanceOf(RuleKitEvaluationException.class)
                .extracting(error -> ((RuleKitEvaluationException) error).code())
                .isEqualTo(RuleKitExceptionCode.SEGMENT_RESOLVER_NOT_CONFIGURED);
    }

    @Test
    void numericActualMismatchDoesNotCoerceToZero() throws Exception {
        RuleSet ruleSet = objectMapper.readValue("""
                {
                  "id": "numeric-native",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": false,
                  "rules": [
                    {
                      "id": "zero-or-more",
                      "priority": 1,
                      "enabled": true,
                      "when": { "tree": { "type": "group", "op": "AND", "children": [{ "type": "leaf", "kind": "FIELD", "fieldRef": "age", "operator": "GTE", "value": 0 }]} },
                      "then": { "response": true }
                    }
                  ]
                }
                """, RuleSet.class);

        EvaluationResult result = evaluator.evaluate(
                evaluator.compile(ruleSet),
                objectMapper.readTree("{\"age\":\"abc\"}"),
                TraceMode.COMPACT,
                EvaluationOptions.defaults()
        );

        assertThat(result.defaultUsed()).isTrue();
    }
}

package com.cars24.rulekit.core.evaluation;

import com.cars24.rulekit.core.exception.RuleKitEvaluationException;
import com.cars24.rulekit.core.exception.RuleKitExceptionCode;
import com.cars24.rulekit.core.model.RuleSet;
import com.cars24.rulekit.core.trace.TraceMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleKitEvaluatorErrorPathTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RuleKitEvaluator evaluator = new RuleKitEvaluator();

    @Test
    void emptyTreeGroupsMatchAndSegmentConditionsRequireResolvers() throws Exception {
        RuleSet emptyGroupRuleSet = objectMapper.readValue("""
                {
                  "id": "empty-group",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": false,
                  "rules": [
                    {
                      "id": "always",
                      "priority": 10,
                      "enabled": true,
                      "when": { "tree": { "type": "group", "op": "AND", "children": [] }},
                      "then": { "response": true }
                    }
                  ]
                }
                """, RuleSet.class);
        RuleSet segmentRuleSet = objectMapper.readValue("""
                {
                  "id": "segment-rules",
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

        assertThat(evaluator.evaluate(emptyGroupRuleSet, objectMapper.readTree("{}"), TraceMode.COMPACT).response().asBoolean()).isTrue();
        assertThatThrownBy(() -> evaluator.evaluate(segmentRuleSet, objectMapper.readTree("{\"userId\":\"1\"}"), TraceMode.COMPACT, EvaluationOptions.defaults()))
                .isInstanceOfSatisfying(RuleKitEvaluationException.class, error ->
                        assertThat(error.code()).isEqualTo(RuleKitExceptionCode.SEGMENT_RESOLVER_NOT_CONFIGURED));
    }

    @Test
    void segmentAndFactResolverFailuresSurfaceStructuredErrorsAndVerboseTraces() throws Exception {
        RuleSet segmentRuleSet = objectMapper.readValue("""
                {
                  "id": "segment-rules",
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

        EvaluationOptions missingLookupOptions = EvaluationOptions.builder()
                .segmentResolver(context -> { throw new AssertionError("should not be called"); })
                .build();
        var missingLookupResult = evaluator.evaluate(segmentRuleSet, objectMapper.readTree("{}"), TraceMode.VERBOSE, missingLookupOptions);

        assertThat(missingLookupResult.defaultUsed()).isTrue();
        assertThat(missingLookupResult.trace().rules().get(0).conditions().get(0).reason())
                .isEqualTo("Segment lookupRef could not be resolved");

        EvaluationOptions failingFactResolver = EvaluationOptions.builder()
                .factResolver((fieldRef, input) -> { throw new IllegalStateException("fact exploded"); })
                .segmentResolver(context -> null)
                .build();

        assertThatThrownBy(() -> evaluator.evaluate(segmentRuleSet, objectMapper.readTree("{\"userId\":\"1\"}"), TraceMode.COMPACT, failingFactResolver))
                .isInstanceOfSatisfying(RuleKitEvaluationException.class, error ->
                        assertThat(error.code()).isEqualTo(RuleKitExceptionCode.RESOLVER_FAILED));

        EvaluationOptions failingSegmentResolver = EvaluationOptions.builder()
                .segmentResolver(context -> { throw new IllegalStateException("segment exploded"); })
                .build();

        assertThatThrownBy(() -> evaluator.evaluate(segmentRuleSet, objectMapper.readTree("{\"userId\":\"1\"}"), TraceMode.COMPACT, failingSegmentResolver))
                .isInstanceOfSatisfying(RuleKitEvaluationException.class, error ->
                        assertThat(error.code()).isEqualTo(RuleKitExceptionCode.RESOLVER_FAILED));
    }

    @Test
    void dependencyConditionsRequireResolversAndReportResolverFailures() throws Exception {
        RuleSet dependencyRuleSet = objectMapper.readValue("""
                {
                  "id": "dependency-rules",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": false,
                  "rules": [
                    {
                      "id": "dependency-rule",
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

        assertThatThrownBy(() -> evaluator.evaluate(dependencyRuleSet, objectMapper.readTree("{}"), TraceMode.COMPACT, EvaluationOptions.defaults()))
                .isInstanceOfSatisfying(RuleKitEvaluationException.class, error ->
                        assertThat(error.code()).isEqualTo(RuleKitExceptionCode.DEPENDENCY_RESOLVER_NOT_CONFIGURED));

        EvaluationOptions missingDependency = EvaluationOptions.builder()
                .dependencyRuleSetResolver(context -> java.util.Optional.empty())
                .build();
        assertThatThrownBy(() -> evaluator.evaluate(dependencyRuleSet, objectMapper.readTree("{}"), TraceMode.COMPACT, missingDependency))
                .isInstanceOfSatisfying(RuleKitEvaluationException.class, error ->
                        assertThat(error.code()).isEqualTo(RuleKitExceptionCode.RULESET_NOT_FOUND));

        EvaluationOptions failingDependency = EvaluationOptions.builder()
                .dependencyRuleSetResolver(context -> { throw new IllegalStateException("dependency exploded"); })
                .build();
        assertThatThrownBy(() -> evaluator.evaluate(dependencyRuleSet, objectMapper.readTree("{}"), TraceMode.COMPACT, failingDependency))
                .isInstanceOfSatisfying(RuleKitEvaluationException.class, error ->
                        assertThat(error.code()).isEqualTo(RuleKitExceptionCode.RESOLVER_FAILED));
    }
}

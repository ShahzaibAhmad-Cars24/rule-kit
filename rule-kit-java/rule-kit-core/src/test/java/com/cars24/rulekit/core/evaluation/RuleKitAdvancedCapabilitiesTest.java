package com.cars24.rulekit.core.evaluation;

import com.cars24.rulekit.core.exception.RuleKitExceptionCode;
import com.cars24.rulekit.core.exception.RuleKitValidationException;
import com.cars24.rulekit.core.model.ConditionGroup;
import com.cars24.rulekit.core.model.ConditionGroupNode;
import com.cars24.rulekit.core.model.ConditionLeaf;
import com.cars24.rulekit.core.model.ConditionKind;
import com.cars24.rulekit.core.model.ExecutionMode;
import com.cars24.rulekit.core.model.LogicalOp;
import com.cars24.rulekit.core.model.RuleDefinition;
import com.cars24.rulekit.core.model.RuleKitVersions;
import com.cars24.rulekit.core.model.RuleSet;
import com.cars24.rulekit.core.model.RuleThen;
import com.cars24.rulekit.core.resolver.FactResolutionContext;
import com.cars24.rulekit.core.resolver.FactResolver;
import com.cars24.rulekit.core.resolver.FieldValueResolver;
import com.cars24.rulekit.core.resolver.ResolvedFact;
import com.cars24.rulekit.core.trace.TraceMode;
import com.cars24.rulekit.core.validation.RuleSetValidator;
import com.cars24.rulekit.core.validation.ValidationMessage;
import com.cars24.rulekit.core.validation.ValidationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleKitAdvancedCapabilitiesTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RuleKitEvaluator evaluator = new RuleKitEvaluator();

    @Test
    void resolvesLiteralAndEscapedDottedFieldReferences() throws Exception {
        JsonNode input = objectMapper.readTree("""
                {
                  "customer.tier": "literal-vip",
                  "customer": {
                    "tier": "nested-vip",
                    "address.city": "escaped-city"
                  }
                }
                """);

        assertThat(FieldValueResolver.resolve(input, "customer.tier"))
                .hasValueSatisfying(value -> assertThat(value.asText()).isEqualTo("literal-vip"));
        assertThat(FieldValueResolver.resolve(input, "customer\\.tier"))
                .hasValueSatisfying(value -> assertThat(value.asText()).isEqualTo("literal-vip"));
        assertThat(FieldValueResolver.resolve(input, "customer.address\\.city"))
                .hasValueSatisfying(value -> assertThat(value.asText()).isEqualTo("escaped-city"));
    }

    @Test
    void supportsLazyHostFactResolverAndDoesNotResolveUnnecessaryFields() throws Exception {
        // Rule 1 (priority=100): plan EQ "basic" AND _gates.expensive.matched EQ true
        // Rule 2 (priority=10):  plan EQ "premium"
        // Input: plan=premium → rule 1 short-circuits on plan EQ "basic" → expensive gate never resolved
        RuleSet ruleSet = objectMapper.readValue("""
                {
                  "id": "lazy-rules",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": "default",
                  "rules": [
                    {
                      "id": "first-rule",
                      "priority": 100,
                      "enabled": true,
                      "when": { "tree": {
                        "type": "group", "op": "AND", "children": [
                          { "type": "leaf", "kind": "FIELD", "fieldRef": "plan", "operator": "EQ", "value": "basic" },
                          { "type": "leaf", "kind": "FIELD", "fieldRef": "_gates.expensive.matched", "operator": "EQ", "value": true }
                        ]
                      }},
                      "then": { "response": "first" }
                    },
                    {
                      "id": "second-rule",
                      "priority": 10,
                      "enabled": true,
                      "when": { "tree": {
                        "type": "group", "op": "AND", "children": [
                          { "type": "leaf", "kind": "FIELD", "fieldRef": "plan", "operator": "EQ", "value": "premium" }
                        ]
                      }},
                      "then": { "response": "second" }
                    }
                  ]
                }
                """, RuleSet.class);

        JsonNode input = objectMapper.readTree("{ \"plan\": \"premium\" }");
        List<String> resolvedFields = new ArrayList<>();

        FactResolver resolver = (fieldRef, sourceInput) -> {
            resolvedFields.add(fieldRef);
            return FieldValueResolver.resolve(sourceInput, fieldRef)
                    .map(ResolvedFact::found)
                    .orElseGet(ResolvedFact::missing);
        };

        EvaluationResult result = evaluator.evaluate(ruleSet, input, TraceMode.COMPACT, resolver);

        assertThat(result.matchedRuleId()).isEqualTo("second-rule");
        // plan resolved for rule-1 (fails), then plan resolved for rule-2 (matches)
        // expensive gate should NOT be resolved at all (AND short-circuited)
        assertThat(resolvedFields).doesNotContain("_gates.expensive.matched");
        assertThat(resolvedFields.stream().filter("plan"::equals).count()).isEqualTo(2);
    }

    @Test
    void factResolverCanUseRuleAndConditionContext() throws Exception {
        RuleSet ruleSet = objectMapper.readValue("""
                {
                  "id": "context-rules",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": "default",
                  "rules": [
                    {
                      "id": "uses-context-resolver",
                      "priority": 10,
                      "enabled": true,
                      "when": { "tree": {
                        "type": "group", "op": "AND", "children": [
                          { "type": "leaf", "kind": "FIELD", "fieldRef": "_gates.checkout-enabled.matched", "operator": "EQ", "value": true },
                          { "type": "leaf", "kind": "FIELD", "fieldRef": "plan", "operator": "EQ", "value": "premium" }
                        ]
                      }},
                      "then": { "response": "enabled" }
                    }
                  ]
                }
                """, RuleSet.class);

        JsonNode input = objectMapper.readTree("{\"plan\":\"premium\"}");
        List<String> contexts = new ArrayList<>();
        FactResolver resolver = new FactResolver() {
            @Override
            public ResolvedFact resolve(String fieldRef, JsonNode input) {
                return ResolvedFact.missing();
            }

            @Override
            public ResolvedFact resolve(FactResolutionContext context) {
                contexts.add(context.ruleSetId() + "/" + context.ruleId() + "/" + context.conditionIndex() + "/" + context.fieldRef());
                if ("_gates.checkout-enabled.matched".equals(context.fieldRef())) {
                    return ResolvedFact.found(objectMapper.getNodeFactory().booleanNode(true));
                }
                return FieldValueResolver.resolve(input, context.fieldRef())
                        .map(ResolvedFact::found)
                        .orElseGet(ResolvedFact::missing);
            }
        };

        EvaluationResult result = evaluator.evaluate(ruleSet, input, TraceMode.COMPACT, resolver);

        assertThat(result.matchedRuleId()).isEqualTo("uses-context-resolver");
        assertThat(contexts).containsExactly(
                "context-rules/uses-context-resolver/0/_gates.checkout-enabled.matched",
                "context-rules/uses-context-resolver/1/plan"
        );
    }

    @Test
    void validatesRuleSetsAndReportsStructuredErrors() throws Exception {
        // "tree" is absent → validator should report MISSING_WHEN
        // schema version is wrong
        // duplicate rule ids
        RuleSet ruleSet = objectMapper.readValue("""
                {
                  "id": "invalid-rules",
                  "schemaVersion": "unknown",
                  "executionMode": "FIRST_MATCH",
                  "rules": [
                    {
                      "id": "same",
                      "priority": 10,
                      "enabled": true,
                      "when": { "tree": {
                        "type": "group", "op": "AND", "children": [
                          { "type": "leaf", "kind": "FIELD", "fieldRef": "plan", "operator": "NOPE", "value": "premium" }
                        ]
                      }},
                      "then": { "response": "a" }
                    },
                    {
                      "id": "same",
                      "priority": 5,
                      "enabled": true,
                      "when": { "tree": { "type": "group", "op": "AND", "children": [] } },
                      "then": { "response": "b" }
                    }
                  ]
                }
                """, RuleSet.class);

        ValidationResult result = RuleSetValidator.validate(ruleSet);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors())
                .extracting(ValidationMessage::code)
                .contains(
                        RuleKitExceptionCode.UNSUPPORTED_SCHEMA_VERSION,
                        RuleKitExceptionCode.UNSUPPORTED_OPERATOR,
                        RuleKitExceptionCode.DUPLICATE_RULE_ID
                );
    }

    @Test
    void validatesNullRulesAndMissingTreeAsStructuredErrors() throws Exception {
        // Rule at index 0 is null; rule at index 1 has neither tree nor legacy all conditions.
        RuleSet ruleSet = new RuleSet(
                "null-entry-rules",
                RuleKitVersions.RULESET_SCHEMA_VERSION,
                ExecutionMode.FIRST_MATCH,
                objectMapper.nullNode(),
                Arrays.asList(
                        null,
                        new RuleDefinition(
                                "missing-tree",
                                1,
                                true,
                                new ConditionGroup(null),   // tree=null → validator reports error
                                new RuleThen(objectMapper.getNodeFactory().booleanNode(true))
                        )
                )
        );

        ValidationResult result = RuleSetValidator.validate(ruleSet);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors())
                .extracting(ValidationMessage::path)
                .contains("$.rules[0]", "$.rules[1].when");
    }

    @Test
    void throwsStructuredValidationExceptionWhenCompilingInvalidRuleSet() throws Exception {
        RuleSet invalid = objectMapper.readValue("""
                {
                  "id": "",
                  "executionMode": "FIRST_MATCH",
                  "rules": []
                }
                """, RuleSet.class);

        assertThatThrownBy(() -> evaluator.compile(invalid))
                .isInstanceOf(RuleKitValidationException.class)
                .satisfies(error -> assertThat(((RuleKitValidationException) error).code())
                        .isEqualTo(RuleKitExceptionCode.RULESET_VALIDATION_FAILED));
    }

    @Test
    void compiledRuleSetCanBeReusedAndTraceCarriesVersionMetadata() throws Exception {
        RuleSet ruleSet = objectMapper.readValue("""
                {
                  "id": "compiled-rules",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "rules": [
                    {
                      "id": "match",
                      "priority": 10,
                      "enabled": true,
                      "when": { "tree": {
                        "type": "group", "op": "AND", "children": [
                          { "type": "leaf", "kind": "FIELD", "fieldRef": "count", "operator": "GTE", "value": 3 }
                        ]
                      }},
                      "then": { "response": { "ok": true } }
                    }
                  ]
                }
                """, RuleSet.class);

        CompiledRuleSet compiled = evaluator.compile(ruleSet);
        EvaluationResult result = evaluator.evaluate(compiled, objectMapper.readTree("{ \"count\": 5 }"), TraceMode.COMPACT);

        assertThat(compiled.ruleSetId()).isEqualTo("compiled-rules");
        assertThat(compiled.rules()).hasSize(1);
        assertThat(result.matchedRuleId()).isEqualTo("match");
        assertThat(result.trace().schemaVersion()).isEqualTo(RuleKitVersions.RULESET_SCHEMA_VERSION);
        assertThat(result.trace().evaluatorVersion()).isEqualTo(RuleKitVersions.EVALUATOR_VERSION);
        assertThat(result.trace().evaluatedRuleCount()).isEqualTo(1);
    }

    @Test
    void resolverIsLazyForRulesAfterFirstMatch() throws Exception {
        RuleSet ruleSet = objectMapper.readValue("""
                {
                  "id": "first-match-lazy-rules",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "rules": [
                    {
                      "id": "first-rule",
                      "priority": 100,
                      "enabled": true,
                      "when": { "tree": {
                        "type": "group", "op": "AND", "children": [
                          { "type": "leaf", "kind": "FIELD", "fieldRef": "plan", "operator": "EQ", "value": "premium" }
                        ]
                      }},
                      "then": { "response": "first" }
                    },
                    {
                      "id": "second-rule",
                      "priority": 10,
                      "enabled": true,
                      "when": { "tree": {
                        "type": "group", "op": "AND", "children": [
                          { "type": "leaf", "kind": "FIELD", "fieldRef": "_gates.expensive.matched", "operator": "EQ", "value": true }
                        ]
                      }},
                      "then": { "response": "second" }
                    }
                  ]
                }
                """, RuleSet.class);

        AtomicInteger expensiveGateResolveCount = new AtomicInteger();
        FactResolver resolver = (fieldRef, input) -> {
            if (fieldRef.startsWith("_gates.")) expensiveGateResolveCount.incrementAndGet();
            return FieldValueResolver.resolve(input, fieldRef)
                    .map(ResolvedFact::found)
                    .orElseGet(ResolvedFact::missing);
        };

        EvaluationResult result = evaluator.evaluate(
                ruleSet, objectMapper.readTree("{ \"plan\": \"premium\" }"), TraceMode.COMPACT, resolver);

        assertThat(result.matchedRuleId()).isEqualTo("first-rule");
        assertThat(expensiveGateResolveCount).hasValue(0); // second rule never evaluated
    }
}

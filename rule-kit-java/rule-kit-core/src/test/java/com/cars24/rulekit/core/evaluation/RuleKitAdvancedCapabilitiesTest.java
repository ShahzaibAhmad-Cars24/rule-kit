package com.cars24.rulekit.core.evaluation;

import com.cars24.rulekit.core.exception.RuleKitExceptionCode;
import com.cars24.rulekit.core.exception.RuleKitValidationException;
import com.cars24.rulekit.core.model.ConditionDefinition;
import com.cars24.rulekit.core.model.ConditionGroup;
import com.cars24.rulekit.core.model.ExecutionMode;
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
    void supportsLazyHostFactResolverAndDoesNotResolveSkippedConditions() throws Exception {
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
                      "when": { "all": [
                        { "fieldRef": "plan", "operator": "EQ", "value": "basic" },
                        { "fieldRef": "_gates.expensive.matched", "operator": "EQ", "value": true }
                      ]},
                      "then": { "response": "first" }
                    },
                    {
                      "id": "second-rule",
                      "priority": 10,
                      "enabled": true,
                      "when": { "all": [
                        { "fieldRef": "plan", "operator": "EQ", "value": "premium" }
                      ]},
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

        EvaluationResult result = evaluator.evaluate(ruleSet, input, TraceMode.VERBOSE, resolver);

        assertThat(result.matchedRuleId()).isEqualTo("second-rule");
        assertThat(resolvedFields).containsExactly("plan", "plan");
        assertThat(result.trace().rules().get(0).conditions()).hasSize(2);
        assertThat(result.trace().rules().get(0).conditions().get(1).skipped()).isTrue();
        assertThat(result.trace().rules().get(0).conditions().get(1).resolved()).isFalse();
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
                      "when": { "all": [
                        { "fieldRef": "_gates.checkout-enabled.matched", "operator": "EQ", "value": true }
                      ]},
                      "then": { "response": "enabled" }
                    }
                  ]
                }
                """, RuleSet.class);
        List<String> contexts = new ArrayList<>();
        FactResolver resolver = new FactResolver() {
            @Override
            public ResolvedFact resolve(String fieldRef, JsonNode input) {
                return ResolvedFact.missing();
            }

            @Override
            public ResolvedFact resolve(FactResolutionContext context) {
                contexts.add(context.ruleSetId() + "/" + context.ruleId() + "/" + context.conditionIndex());
                if ("_gates.checkout-enabled.matched".equals(context.fieldRef())) {
                    return ResolvedFact.found(objectMapper.getNodeFactory().booleanNode(true));
                }
                return ResolvedFact.missing();
            }
        };

        EvaluationResult result = evaluator.evaluate(ruleSet, objectMapper.readTree("{}"), TraceMode.VERBOSE, resolver);

        assertThat(result.matchedRuleId()).isEqualTo("uses-context-resolver");
        assertThat(contexts).containsExactly("context-rules/uses-context-resolver/0");
        assertThat(result.trace().rules().get(0).conditions().get(0).resolved()).isTrue();
    }

    @Test
    void validatesRuleSetsAndReportsStructuredErrors() throws Exception {
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
                      "when": { "all": [
                        { "fieldRef": "plan", "operator": "NOPE", "value": "premium" }
                      ]},
                      "then": { "response": "a" }
                    },
                    {
                      "id": "same",
                      "priority": 5,
                      "enabled": true,
                      "when": { "all": [] },
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
    void validatesNullRulesAndConditionsAsStructuredErrors() throws Exception {
        RuleSet ruleSet = new RuleSet(
                "null-entry-rules",
                RuleKitVersions.RULESET_SCHEMA_VERSION,
                ExecutionMode.FIRST_MATCH,
                objectMapper.nullNode(),
                java.util.Arrays.asList(
                        null,
                        new RuleDefinition(
                                "has-null-condition",
                                1,
                                true,
                                new ConditionGroup(java.util.Arrays.asList((ConditionDefinition) null)),
                                new RuleThen(objectMapper.getNodeFactory().booleanNode(true))
                        )
                )
        );

        ValidationResult result = RuleSetValidator.validate(ruleSet);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors())
                .extracting(ValidationMessage::path)
                .contains("$.rules[0]", "$.rules[1].when.all[0]");
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
                      "when": { "all": [
                        { "fieldRef": "count", "operator": "GTE", "value": 3 }
                      ]},
                      "then": { "response": { "ok": true } }
                    }
                  ]
                }
                """, RuleSet.class);

        CompiledRuleSet compiled = evaluator.compile(ruleSet);
        EvaluationResult result = evaluator.evaluate(compiled, objectMapper.readTree("{ \"count\": 5 }"), TraceMode.VERBOSE);

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
                      "when": { "all": [
                        { "fieldRef": "plan", "operator": "EQ", "value": "premium" }
                      ]},
                      "then": { "response": "first" }
                    },
                    {
                      "id": "second-rule",
                      "priority": 10,
                      "enabled": true,
                      "when": { "all": [
                        { "fieldRef": "_gates.expensive.matched", "operator": "EQ", "value": true }
                      ]},
                      "then": { "response": "second" }
                    }
                  ]
                }
                """, RuleSet.class);
        AtomicInteger expensiveGateResolveCount = new AtomicInteger();

        FactResolver resolver = (fieldRef, input) -> {
            if (fieldRef.startsWith("_gates.")) {
                expensiveGateResolveCount.incrementAndGet();
            }
            return FieldValueResolver.resolve(input, fieldRef)
                    .map(ResolvedFact::found)
                    .orElseGet(ResolvedFact::missing);
        };

        EvaluationResult result = evaluator.evaluate(ruleSet, objectMapper.readTree("{ \"plan\": \"premium\" }"), TraceMode.VERBOSE, resolver);

        assertThat(result.matchedRuleId()).isEqualTo("first-rule");
        assertThat(expensiveGateResolveCount).hasValue(0);
    }
}

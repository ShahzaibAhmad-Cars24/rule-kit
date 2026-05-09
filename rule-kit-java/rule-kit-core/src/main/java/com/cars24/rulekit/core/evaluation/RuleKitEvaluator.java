package com.cars24.rulekit.core.evaluation;

import com.cars24.rulekit.core.exception.RuleKitEvaluationException;
import com.cars24.rulekit.core.exception.RuleKitExceptionCode;
import com.cars24.rulekit.core.exception.RuleKitValidationException;
import com.cars24.rulekit.core.model.ConditionDefinition;
import com.cars24.rulekit.core.model.ConditionKind;
import com.cars24.rulekit.core.model.ExecutionMode;
import com.cars24.rulekit.core.model.RuleDefinition;
import com.cars24.rulekit.core.model.RuleKitVersions;
import com.cars24.rulekit.core.model.RuleSet;
import com.cars24.rulekit.core.resolver.DependencyResolutionContext;
import com.cars24.rulekit.core.resolver.DependencyResult;
import com.cars24.rulekit.core.resolver.FactResolutionContext;
import com.cars24.rulekit.core.resolver.FactResolver;
import com.cars24.rulekit.core.resolver.ResolvedFact;
import com.cars24.rulekit.core.resolver.SegmentMembershipResult;
import com.cars24.rulekit.core.resolver.SegmentResolutionContext;
import com.cars24.rulekit.core.rollout.RolloutEvaluator;
import com.cars24.rulekit.core.trace.ConditionTrace;
import com.cars24.rulekit.core.trace.EvaluationTrace;
import com.cars24.rulekit.core.trace.RolloutTrace;
import com.cars24.rulekit.core.trace.RuleTrace;
import com.cars24.rulekit.core.trace.TraceMode;
import com.cars24.rulekit.core.validation.RuleSetValidator;
import com.cars24.rulekit.core.validation.ValidationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class RuleKitEvaluator {

    public EvaluationResult evaluate(RuleSet ruleSet, JsonNode input, TraceMode traceMode) {
        return evaluate(ruleSet, input, traceMode, FactResolver.defaultResolver());
    }

    public EvaluationResult evaluate(RuleSet ruleSet, JsonNode input, TraceMode traceMode, FactResolver factResolver) {
        return evaluate(
                compile(ruleSet),
                input,
                traceMode,
                EvaluationOptions.builder().factResolver(factResolver).build()
        );
    }

    public EvaluationResult evaluate(RuleSet ruleSet, JsonNode input, TraceMode traceMode, EvaluationOptions options) {
        return evaluate(compile(ruleSet), input, traceMode, options);
    }

    public EvaluationResult evaluate(CompiledRuleSet ruleSet, JsonNode input, TraceMode traceMode) {
        return evaluate(ruleSet, input, traceMode, FactResolver.defaultResolver());
    }

    public EvaluationResult evaluate(CompiledRuleSet ruleSet, JsonNode input, TraceMode traceMode, FactResolver factResolver) {
        return evaluate(
                ruleSet,
                input,
                traceMode,
                EvaluationOptions.builder().factResolver(factResolver).build()
        );
    }

    public EvaluationResult evaluate(CompiledRuleSet ruleSet, JsonNode input, TraceMode traceMode, EvaluationOptions options) {
        return evaluate(ruleSet, input, traceMode, options, new EvaluationSession());
    }

    private EvaluationResult evaluate(CompiledRuleSet ruleSet,
                                      JsonNode input,
                                      TraceMode traceMode,
                                      EvaluationOptions options,
                                      EvaluationSession session) {
        Objects.requireNonNull(ruleSet, "ruleSet cannot be null");
        Objects.requireNonNull(input, "input cannot be null");
        EvaluationOptions resolvedOptions = options != null ? options : EvaluationOptions.defaults();
        TraceMode resolvedTraceMode = traceMode != null ? traceMode : TraceMode.COMPACT;

        if (!session.enter(ruleSet.ruleSetId())) {
            throw new RuleKitEvaluationException(
                    RuleKitExceptionCode.DEPENDENCY_CYCLE_DETECTED,
                    "Dependency cycle detected for RuleSet: " + ruleSet.ruleSetId(),
                    null
            );
        }

        try {
            if (ruleSet.executionMode() == ExecutionMode.ALL_MATCHES) {
                return evaluateAllMatches(ruleSet, input, resolvedTraceMode, resolvedOptions, session);
            } else {
                return evaluateFirstMatch(ruleSet, input, resolvedTraceMode, resolvedOptions, session);
            }
        } finally {
            session.exit(ruleSet.ruleSetId());
        }
    }

    /** FIRST_MATCH: stop at the first rule that fires. */
    private EvaluationResult evaluateFirstMatch(CompiledRuleSet ruleSet,
                                                JsonNode input,
                                                TraceMode traceMode,
                                                EvaluationOptions options,
                                                EvaluationSession session) {
        List<RuleTrace> ruleTraces = new ArrayList<>();
        int evaluatedRuleCount = 0;

        for (CompiledRule rule : ruleSet.compiledRules()) {
            evaluatedRuleCount++;
            RuleEvaluation ruleEvaluation = evaluateRule(
                    ruleSet, rule, input, traceMode, options, session
            );
            ruleTraces.add(ruleEvaluation.trace());

            if (ruleEvaluation.matched()) {
                return new EvaluationResult(
                        rule.source().id(),
                        false,
                        rule.source().then() != null && rule.source().then().response() != null
                                ? copy(rule.source().then().response())
                                : NullNode.getInstance(),
                        trace(traceMode, ruleSet, evaluatedRuleCount, ruleTraces)
                );
            }
        }

        return new EvaluationResult(
                null,
                true,
                ruleSet.defaultResponse() != null ? copy(ruleSet.defaultResponse()) : NullNode.getInstance(),
                trace(traceMode, ruleSet, evaluatedRuleCount, ruleTraces)
        );
    }

    /** ALL_MATCHES: evaluate every enabled rule and collect all that fire. */
    private EvaluationResult evaluateAllMatches(CompiledRuleSet ruleSet,
                                                JsonNode input,
                                                TraceMode traceMode,
                                                EvaluationOptions options,
                                                EvaluationSession session) {
        List<RuleTrace> ruleTraces = new ArrayList<>();
        List<RuleMatch> matches = new ArrayList<>();
        int evaluatedRuleCount = 0;

        for (CompiledRule rule : ruleSet.compiledRules()) {
            evaluatedRuleCount++;
            RuleEvaluation ruleEvaluation = evaluateRule(
                    ruleSet, rule, input, traceMode, options, session
            );
            ruleTraces.add(ruleEvaluation.trace());

            if (ruleEvaluation.matched()) {
                JsonNode response = rule.source().then() != null && rule.source().then().response() != null
                        ? copy(rule.source().then().response())
                        : NullNode.getInstance();
                matches.add(new RuleMatch(
                        rule.source().id(),
                        rule.source().priority(),
                        response,
                        rule.source().rollout()
                ));
            }
        }

        EvaluationTrace evalTrace = trace(traceMode, ruleSet, evaluatedRuleCount, ruleTraces);

        if (matches.isEmpty()) {
            // Nothing matched — return default
            return new EvaluationResult(
                    null,
                    true,
                    ruleSet.defaultResponse() != null ? copy(ruleSet.defaultResponse()) : NullNode.getInstance(),
                    evalTrace,
                    List.of()
            );
        }

        // Primary result = first match (highest priority, since rules are sorted descending)
        RuleMatch primary = matches.get(0);
        return new EvaluationResult(
                primary.ruleId(),
                false,
                primary.response(),
                evalTrace,
                List.copyOf(matches)
        );
    }

    public CompiledRuleSet compile(RuleSet ruleSet) {
        ValidationResult validation = RuleSetValidator.validate(ruleSet);
        if (!validation.valid()) {
            throw new RuleKitValidationException(validation);
        }

        return new CompiledRuleSet(
                ruleSet.id(),
                ruleSet.schemaVersion() != null && !ruleSet.schemaVersion().isBlank()
                        ? ruleSet.schemaVersion()
                        : RuleKitVersions.RULESET_SCHEMA_VERSION,
                ruleSet.executionMode() != null ? ruleSet.executionMode() : ExecutionMode.FIRST_MATCH,
                ruleSet.defaultResponse(),
                orderedEnabledRules(ruleSet)
        );
    }

    private List<RuleDefinition> orderedEnabledRules(RuleSet ruleSet) {
        if (ruleSet.rules() == null) {
            return List.of();
        }
        return ruleSet.rules().stream()
                .filter(rule -> Boolean.TRUE.equals(rule.enabled()))
                .sorted(Comparator
                        .comparing((RuleDefinition rule) -> rule.priority() != null ? rule.priority() : 0)
                        .reversed())
                .toList();
    }

    private RuleEvaluation evaluateRule(CompiledRuleSet ruleSet,
                                        CompiledRule rule,
                                        JsonNode input,
                                        TraceMode traceMode,
                                        EvaluationOptions options,
                                        EvaluationSession session) {
        List<CompiledCondition> conditions = rule.conditions();

        List<ConditionTrace> conditionTraces = new ArrayList<>();
        boolean matched = true;
        boolean shortCircuited = false;

        for (int conditionIndex = 0; conditionIndex < conditions.size(); conditionIndex++) {
            CompiledCondition compiledCondition = conditions.get(conditionIndex);
            ConditionDefinition condition = compiledCondition.source();
            if (shortCircuited) {
                if (traceMode == TraceMode.VERBOSE) {
                    conditionTraces.add(new ConditionTrace(
                            condition.resolvedKind(),
                            condition.fieldRef(),
                            condition.operator(),
                            condition.value(),
                            condition.valueTo(),
                            null,
                            false,
                            false,
                            true,
                            "Skipped because a previous AND condition failed",
                            null
                    ));
                }
                continue;
            }

            ConditionEvaluation conditionEvaluation = evaluateCondition(
                    ruleSet,
                    rule.source(),
                    conditionIndex,
                    compiledCondition,
                    input,
                    options,
                    traceMode,
                    session
            );

            if (traceMode == TraceMode.VERBOSE) {
                conditionTraces.add(conditionEvaluation.trace());
            }

            if (!conditionEvaluation.matched()) {
                matched = false;
                shortCircuited = true;
                if (traceMode != TraceMode.VERBOSE) {
                    break;
                }
            }
        }

        RolloutTrace rolloutTrace = null;
        if (matched && rule.rollout() != null) {
            rolloutTrace = RolloutEvaluator.evaluate(
                    rule.rollout(),
                    input,
                    ruleSet.ruleSetId(),
                    rule.source().id(),
                    options.factResolver()
            );
            matched = rolloutTrace.included();
        }

        return new RuleEvaluation(
                matched,
                new RuleTrace(
                        rule.source().id(),
                        matched,
                        traceMode == TraceMode.VERBOSE ? conditionTraces : List.of(),
                        rolloutTrace
                )
        );
    }

    private record RuleEvaluation(boolean matched, RuleTrace trace) {
    }

    private record ConditionEvaluation(boolean matched, ConditionTrace trace) {
    }

    private ConditionEvaluation evaluateCondition(CompiledRuleSet ruleSet,
                                                  RuleDefinition rule,
                                                  int conditionIndex,
                                                  CompiledCondition compiledCondition,
                                                  JsonNode input,
                                                  EvaluationOptions options,
                                                  TraceMode traceMode,
                                                  EvaluationSession session) {
        return switch (compiledCondition.source().resolvedKind()) {
            case FIELD -> evaluateFieldCondition(ruleSet, rule, conditionIndex, compiledCondition, input, options, traceMode);
            case SEGMENT -> evaluateSegmentCondition(ruleSet, rule, conditionIndex, compiledCondition.source(), input, options, traceMode);
            case DEPENDENCY -> evaluateDependencyCondition(ruleSet, rule, conditionIndex, compiledCondition.source(), input, options, traceMode, session);
        };
    }

    private ConditionEvaluation evaluateFieldCondition(CompiledRuleSet ruleSet,
                                                       RuleDefinition rule,
                                                       int conditionIndex,
                                                       CompiledCondition compiledCondition,
                                                       JsonNode input,
                                                       EvaluationOptions options,
                                                       TraceMode traceMode) {
        ConditionDefinition condition = compiledCondition.source();
        ResolvedFact resolved = resolveFact(ruleSet, rule, conditionIndex, condition, options.factResolver(), input);
        Optional<JsonNode> actual = resolved.resolved() ? Optional.ofNullable(resolved.value()) : Optional.empty();
        boolean conditionMatched = OperatorEvaluator.evaluate(compiledCondition, actual);

        return new ConditionEvaluation(
                conditionMatched,
                traceMode == TraceMode.VERBOSE
                        ? new ConditionTrace(
                                ConditionKind.FIELD,
                                condition.fieldRef(),
                                condition.operator(),
                                condition.value(),
                                condition.valueTo(),
                                actual.orElse(null),
                                conditionMatched,
                                resolved.resolved(),
                                false,
                                null,
                                null
                        )
                        : null
        );
    }

    private ConditionEvaluation evaluateSegmentCondition(CompiledRuleSet ruleSet,
                                                         RuleDefinition rule,
                                                         int conditionIndex,
                                                         ConditionDefinition condition,
                                                         JsonNode input,
                                                         EvaluationOptions options,
                                                         TraceMode traceMode) {
        if (options.segmentResolver() == null) {
            throw new RuleKitEvaluationException(
                    RuleKitExceptionCode.SEGMENT_RESOLVER_NOT_CONFIGURED,
                    "SegmentResolver is required for segment condition in ruleId=" + rule.id(),
                    null
            );
        }

        ResolvedFact resolvedLookup = resolveReference(
                ruleSet,
                rule,
                conditionIndex,
                condition.lookupRef(),
                condition,
                options.factResolver(),
                input
        );
        Optional<JsonNode> lookupValue = resolvedLookup.resolved()
                ? Optional.ofNullable(resolvedLookup.value()).filter(node -> !node.isNull())
                : Optional.empty();
        if (lookupValue.isEmpty()) {
            return new ConditionEvaluation(
                    false,
                    traceMode == TraceMode.VERBOSE
                            ? new ConditionTrace(
                                    ConditionKind.SEGMENT,
                                    condition.lookupRef(),
                                    null,
                                    null,
                                    null,
                                    null,
                                    false,
                                    false,
                                    false,
                                    "Segment lookupRef could not be resolved",
                                    segmentDetails(condition, null)
                            )
                            : null
            );
        }

        SegmentMembershipResult result;
        try {
            result = options.segmentResolver().resolve(new SegmentResolutionContext(
                    ruleSet.ruleSetId(),
                    rule.id(),
                    conditionIndex,
                    condition.lookupRef(),
                    lookupValue.get(),
                    condition.segmentNames(),
                    condition.match(),
                    input,
                    condition
            ));
        } catch (RuntimeException e) {
            throw new RuleKitEvaluationException(
                    RuleKitExceptionCode.RESOLVER_FAILED,
                    "Segment resolver failed for ruleId=" + rule.id() + ", conditionIndex=" + conditionIndex,
                    e
            );
        }
        SegmentMembershipResult resolvedResult = result != null ? result : SegmentMembershipResult.of(java.util.Map.of());
        boolean matched = resolvedResult.matches(condition);

        return new ConditionEvaluation(
                matched,
                traceMode == TraceMode.VERBOSE
                        ? new ConditionTrace(
                                ConditionKind.SEGMENT,
                                condition.lookupRef(),
                                null,
                                null,
                                null,
                                lookupValue.get(),
                                matched,
                                true,
                                false,
                                null,
                                segmentDetails(condition, resolvedResult)
                        )
                        : null
        );
    }

    private ConditionEvaluation evaluateDependencyCondition(CompiledRuleSet ruleSet,
                                                            RuleDefinition rule,
                                                            int conditionIndex,
                                                            ConditionDefinition condition,
                                                            JsonNode input,
                                                            EvaluationOptions options,
                                                            TraceMode traceMode,
                                                            EvaluationSession session) {
        if (options.dependencyResolver() == null) {
            throw new RuleKitEvaluationException(
                    RuleKitExceptionCode.DEPENDENCY_RESOLVER_NOT_CONFIGURED,
                    "RuleSetDependencyResolver is required for dependency condition in ruleId=" + rule.id(),
                    null
            );
        }

        DependencyResult dependencyResult = session.cachedDependency(condition.ruleSetId());
        if (dependencyResult == null) {
            CompiledRuleSet dependency = resolveDependency(ruleSet, rule, conditionIndex, condition, input, options);
            EvaluationResult dependencyEvaluation = evaluate(dependency, input, TraceMode.COMPACT, options, session);
            dependencyResult = DependencyResult.from(dependencyEvaluation, dependency.ruleSetId());
            session.cacheDependency(condition.ruleSetId(), dependencyResult);
        }
        boolean matched = dependencyResult.satisfies(condition.expect());

        return new ConditionEvaluation(
                matched,
                traceMode == TraceMode.VERBOSE
                        ? new ConditionTrace(
                                ConditionKind.DEPENDENCY,
                                condition.ruleSetId(),
                                null,
                                null,
                                null,
                                null,
                                matched,
                                true,
                                false,
                                null,
                                dependencyDetails(condition, dependencyResult)
                        )
                        : null
        );
    }

    private CompiledRuleSet resolveDependency(CompiledRuleSet ruleSet,
                                              RuleDefinition rule,
                                              int conditionIndex,
                                              ConditionDefinition condition,
                                              JsonNode input,
                                              EvaluationOptions options) {
        try {
            return options.dependencyResolver().resolve(new DependencyResolutionContext(
                            ruleSet.ruleSetId(),
                            rule.id(),
                            conditionIndex,
                            condition.ruleSetId(),
                            input,
                            condition
                    ))
                    .orElseThrow(() -> new RuleKitEvaluationException(
                            RuleKitExceptionCode.RULESET_NOT_FOUND,
                            "Dependency RuleSet not found: " + condition.ruleSetId(),
                            null
                    ));
        } catch (RuleKitEvaluationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new RuleKitEvaluationException(
                    RuleKitExceptionCode.RESOLVER_FAILED,
                    "Dependency resolver failed for ruleId=" + rule.id() + ", conditionIndex=" + conditionIndex,
                    e
            );
        }
    }

    private ResolvedFact resolveFact(CompiledRuleSet ruleSet,
                                     RuleDefinition rule,
                                     int conditionIndex,
                                     ConditionDefinition condition,
                                     FactResolver factResolver,
                                     JsonNode input) {
        return resolveReference(ruleSet, rule, conditionIndex, condition.fieldRef(), condition, factResolver, input);
    }

    private ResolvedFact resolveReference(CompiledRuleSet ruleSet,
                                          RuleDefinition rule,
                                          int conditionIndex,
                                          String fieldRef,
                                          ConditionDefinition condition,
                                          FactResolver factResolver,
                                          JsonNode input) {
        try {
            ResolvedFact resolvedFact = factResolver.resolve(new FactResolutionContext(
                    ruleSet.ruleSetId(),
                    rule.id(),
                    conditionIndex,
                    fieldRef,
                    input,
                    condition
            ));
            return resolvedFact != null ? resolvedFact : ResolvedFact.missing();
        } catch (RuntimeException e) {
            throw new RuleKitEvaluationException(
                    RuleKitExceptionCode.RESOLVER_FAILED,
                    "Fact resolver failed for ruleId=" + rule.id()
                            + ", conditionIndex=" + conditionIndex
                            + ", fieldRef=" + fieldRef,
                    e
            );
        }
    }

    private EvaluationTrace trace(TraceMode traceMode,
                                  CompiledRuleSet ruleSet,
                                  int evaluatedRuleCount,
                                  List<RuleTrace> ruleTraces) {
        return new EvaluationTrace(
                traceMode,
                ruleSet.schemaVersion(),
                RuleKitVersions.EVALUATOR_VERSION,
                ruleSet.ruleSetId(),
                evaluatedRuleCount,
                ruleTraces
        );
    }

    private JsonNode copy(JsonNode node) {
        return node == null ? null : node.deepCopy();
    }

    private ObjectNode segmentDetails(ConditionDefinition condition, SegmentMembershipResult result) {
        ObjectNode details = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        details.put("match", condition.match() != null ? condition.match().name() : null);
        details.putPOJO("segmentNames", condition.segmentNames());
        if (result != null) {
            details.putPOJO("memberships", result.memberships());
        }
        return details;
    }

    private ObjectNode dependencyDetails(ConditionDefinition condition, DependencyResult result) {
        ObjectNode details = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        details.put("dependencyRuleSetId", condition.ruleSetId());
        details.put("expect", condition.expect() != null ? condition.expect().name() : null);
        details.put("matchedRuleId", result.matchedRuleId());
        details.put("defaultUsed", result.defaultUsed());
        return details;
    }
}

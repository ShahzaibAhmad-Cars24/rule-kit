package com.cars24.rulekit.sdk;

import com.cars24.rulekit.core.evaluation.CompiledRuleSet;
import com.cars24.rulekit.core.evaluation.EvaluationOptions;
import com.cars24.rulekit.core.evaluation.EvaluationResult;
import com.cars24.rulekit.core.evaluation.RuleKitEvaluator;
import com.cars24.rulekit.core.exception.RuleKitExceptionCode;
import com.cars24.rulekit.core.model.RuleSet;
import com.cars24.rulekit.core.resolver.FactResolver;
import com.cars24.rulekit.core.trace.TraceMode;
import com.cars24.rulekit.core.validation.RuleSetValidator;
import com.cars24.rulekit.core.validation.ValidationResult;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;
import java.util.Optional;

public class RuleKitClient {

    private final ObjectMapper objectMapper;
    private final RuleKitEvaluator evaluator;
    private final TraceMode defaultTraceMode;
    private final RuleSetSource ruleSetSource;

    public RuleKitClient() {
        this(new ObjectMapper());
    }

    public RuleKitClient(ObjectMapper objectMapper) {
        this(objectMapper, new RuleKitEvaluator(), TraceMode.COMPACT, null);
    }

    public RuleKitClient(ObjectMapper objectMapper, TraceMode defaultTraceMode) {
        this(objectMapper, new RuleKitEvaluator(), defaultTraceMode, null);
    }

    public RuleKitClient(ObjectMapper objectMapper, RuleSetSource ruleSetSource) {
        this(objectMapper, new RuleKitEvaluator(), TraceMode.COMPACT, ruleSetSource);
    }

    public RuleKitClient(ObjectMapper objectMapper, RuleKitEvaluator evaluator) {
        this(objectMapper, evaluator, TraceMode.COMPACT, null);
    }

    public RuleKitClient(ObjectMapper objectMapper, RuleKitEvaluator evaluator, TraceMode defaultTraceMode) {
        this(objectMapper, evaluator, defaultTraceMode, null);
    }

    public RuleKitClient(ObjectMapper objectMapper,
                         RuleKitEvaluator evaluator,
                         TraceMode defaultTraceMode,
                         RuleSetSource ruleSetSource) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null")
                .copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator cannot be null");
        this.defaultTraceMode = defaultTraceMode != null ? defaultTraceMode : TraceMode.COMPACT;
        this.ruleSetSource = ruleSetSource;
    }

    public TraceMode defaultTraceMode() {
        return defaultTraceMode;
    }

    public EvaluationResult evaluate(RuleSet ruleSet, JsonNode input, TraceMode traceMode) {
        return evaluator.evaluate(ruleSet, input, resolveTraceMode(traceMode));
    }

    public EvaluationResult evaluate(RuleSet ruleSet, JsonNode input, TraceMode traceMode, FactResolver factResolver) {
        return evaluator.evaluate(ruleSet, input, resolveTraceMode(traceMode), factResolver);
    }

    public EvaluationResult evaluate(RuleSet ruleSet, JsonNode input, TraceMode traceMode, EvaluationOptions options) {
        return evaluator.evaluate(ruleSet, input, resolveTraceMode(traceMode), options);
    }

    public EvaluationResult evaluate(CompiledRuleSet ruleSet, JsonNode input, TraceMode traceMode) {
        return evaluator.evaluate(ruleSet, input, resolveTraceMode(traceMode));
    }

    public EvaluationResult evaluate(CompiledRuleSet ruleSet, JsonNode input, TraceMode traceMode, FactResolver factResolver) {
        return evaluator.evaluate(ruleSet, input, resolveTraceMode(traceMode), factResolver);
    }

    public EvaluationResult evaluate(CompiledRuleSet ruleSet, JsonNode input, TraceMode traceMode, EvaluationOptions options) {
        return evaluator.evaluate(ruleSet, input, resolveTraceMode(traceMode), options);
    }

    public EvaluationResult evaluate(JsonNode ruleSet, JsonNode input, TraceMode traceMode) {
        return evaluate(toRuleSet(ruleSet), input, traceMode);
    }

    public EvaluationResult evaluate(JsonNode ruleSet, JsonNode input, TraceMode traceMode, FactResolver factResolver) {
        return evaluate(toRuleSet(ruleSet), input, traceMode, factResolver);
    }

    public EvaluationResult evaluate(JsonNode ruleSet, JsonNode input, TraceMode traceMode, EvaluationOptions options) {
        return evaluate(toRuleSet(ruleSet), input, traceMode, options);
    }

    public EvaluationResult evaluate(String ruleSetJson, String inputJson, TraceMode traceMode) {
        return evaluate(readRuleSet(ruleSetJson), readInput(inputJson), traceMode);
    }

    public EvaluationResult evaluate(String ruleSetJson, String inputJson, TraceMode traceMode, FactResolver factResolver) {
        return evaluate(readRuleSet(ruleSetJson), readInput(inputJson), traceMode, factResolver);
    }

    public EvaluationResult evaluate(String ruleSetJson, String inputJson, TraceMode traceMode, EvaluationOptions options) {
        return evaluate(readRuleSet(ruleSetJson), readInput(inputJson), traceMode, options);
    }

    public JsonNode evaluateToJson(JsonNode ruleSet, JsonNode input, TraceMode traceMode) {
        return objectMapper.valueToTree(evaluate(ruleSet, input, traceMode));
    }

    public JsonNode evaluateToJson(JsonNode ruleSet, JsonNode input, TraceMode traceMode, FactResolver factResolver) {
        return objectMapper.valueToTree(evaluate(ruleSet, input, traceMode, factResolver));
    }

    public JsonNode evaluateToJson(JsonNode ruleSet, JsonNode input, TraceMode traceMode, EvaluationOptions options) {
        return objectMapper.valueToTree(evaluate(ruleSet, input, traceMode, options));
    }

    public JsonNode evaluateToJson(String ruleSetJson, String inputJson, TraceMode traceMode) {
        return objectMapper.valueToTree(evaluate(ruleSetJson, inputJson, traceMode));
    }

    public JsonNode evaluateToJson(String ruleSetJson, String inputJson, TraceMode traceMode, FactResolver factResolver) {
        return objectMapper.valueToTree(evaluate(ruleSetJson, inputJson, traceMode, factResolver));
    }

    public JsonNode evaluateToJson(String ruleSetJson, String inputJson, TraceMode traceMode, EvaluationOptions options) {
        return objectMapper.valueToTree(evaluate(ruleSetJson, inputJson, traceMode, options));
    }

    public ValidationResult validate(RuleSet ruleSet) {
        return RuleSetValidator.validate(ruleSet);
    }

    public ValidationResult validate(JsonNode ruleSet) {
        return validate(toRuleSet(ruleSet));
    }

    public ValidationResult validate(String ruleSetJson) {
        return validate(readRuleSet(ruleSetJson));
    }

    public CompiledRuleSet compile(RuleSet ruleSet) {
        return evaluator.compile(ruleSet);
    }

    public CompiledRuleSet compile(JsonNode ruleSet) {
        return compile(toRuleSet(ruleSet));
    }

    public CompiledRuleSet compile(String ruleSetJson) {
        return compile(readRuleSet(ruleSetJson));
    }

    public EvaluationResult evaluateById(String ruleSetId, JsonNode input, TraceMode traceMode) {
        return evaluate(findRuleSet(ruleSetId), input, traceMode);
    }

    public EvaluationResult evaluateById(String ruleSetId, JsonNode input, TraceMode traceMode, FactResolver factResolver) {
        return evaluate(findRuleSet(ruleSetId), input, traceMode, factResolver);
    }

    public ValidationResult validateById(String ruleSetId) {
        return validate(findRuleSet(ruleSetId));
    }

    public CompiledRuleSet compileById(String ruleSetId) {
        return compile(findRuleSet(ruleSetId));
    }

    private RuleSet findRuleSet(String ruleSetId) {
        if (ruleSetSource == null) {
            throw new RuleKitClientException(
                    RuleKitExceptionCode.RULESET_SOURCE_NOT_CONFIGURED,
                    "RuleSetSource is not configured"
            );
        }
        Optional<RuleSet> ruleSet = ruleSetSource.findById(ruleSetId);
        return ruleSet.orElseThrow(() -> new RuleKitClientException(
                RuleKitExceptionCode.RULESET_NOT_FOUND,
                "RuleSet not found: " + ruleSetId
        ));
    }

    private RuleSet readRuleSet(String ruleSetJson) {
        return toRuleSet(readJson(ruleSetJson));
    }

    private JsonNode readInput(String inputJson) {
        return readJson(inputJson);
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new RuleKitClientException(
                    RuleKitExceptionCode.INVALID_JSON_PAYLOAD,
                    "Invalid JSON payload",
                    e
            );
        }
    }

    private RuleSet toRuleSet(JsonNode ruleSet) {
        try {
            return objectMapper.convertValue(ruleSet, RuleSet.class);
        } catch (IllegalArgumentException e) {
            throw new RuleKitClientException(
                    RuleKitExceptionCode.INVALID_RULESET_PAYLOAD,
                    "Invalid RuleSet payload",
                    e
            );
        }
    }

    private TraceMode resolveTraceMode(TraceMode traceMode) {
        return traceMode != null ? traceMode : defaultTraceMode;
    }
}

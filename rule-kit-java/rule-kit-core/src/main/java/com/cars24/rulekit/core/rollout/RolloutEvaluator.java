package com.cars24.rulekit.core.rollout;

import com.cars24.rulekit.core.exception.RuleKitEvaluationException;
import com.cars24.rulekit.core.exception.RuleKitExceptionCode;
import com.cars24.rulekit.core.model.RolloutDefinition;
import com.cars24.rulekit.core.resolver.FactResolutionContext;
import com.cars24.rulekit.core.resolver.FactResolver;
import com.cars24.rulekit.core.resolver.ResolvedFact;
import com.cars24.rulekit.core.trace.RolloutTrace;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.hash.Hashing;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RolloutEvaluator {

    private RolloutEvaluator() {
    }

    public static RolloutTrace evaluate(RolloutDefinition rollout,
                                        JsonNode input,
                                        String ruleSetId,
                                        String ruleId,
                                        FactResolver factResolver) {
        if (rollout == null) {
            return RolloutTrace.notConfigured();
        }

        Optional<JsonNode> unitValue = resolveFact(ruleSetId, ruleId, rollout.unitRef(), input, factResolver);
        if (unitValue.isEmpty()) {
            throw new RuleKitEvaluationException(
                    RuleKitExceptionCode.ROLLOUT_UNIT_NOT_RESOLVED,
                    "Rollout unitRef could not be resolved: " + rollout.unitRef(),
                    null
            );
        }

        String hashInput = hashInput(rollout, input, ruleSetId, ruleId, factResolver, unitValue.get().asText());
        int hash = Hashing.murmur3_32_fixed()
                .hashString(hashInput, StandardCharsets.UTF_8)
                .asInt();
        int bucketCount = rollout.bucketCount();
        int bucket = Integer.remainderUnsigned(hash, bucketCount);
        boolean included = bucket < ((rollout.percentage() / 100.0) * bucketCount);

        return new RolloutTrace(
                true,
                included,
                rollout.algorithm().name(),
                hashInput,
                bucket,
                rollout.percentage(),
                included ? "Bucket is inside rollout percentage" : "Bucket is outside rollout percentage"
        );
    }

    private static String hashInput(RolloutDefinition rollout,
                                    JsonNode input,
                                    String ruleSetId,
                                    String ruleId,
                                    FactResolver factResolver,
                                    String entityId) {
        List<String> parts = new ArrayList<>();
        List<String> saltRefs = rollout.saltRefs() == null || rollout.saltRefs().isEmpty()
                ? List.of("tenantId", "configName", "ruleId", "splitSeed")
                : rollout.saltRefs();
        for (String saltRef : saltRefs) {
            parts.add(resolveSalt(input, ruleSetId, ruleId, factResolver, saltRef));
        }
        parts.add(entityId);
        return String.join(":", parts);
    }

    private static String resolveSalt(JsonNode input,
                                      String ruleSetId,
                                      String ruleId,
                                      FactResolver factResolver,
                                      String saltRef) {
        if ("ruleId".equals(saltRef)) {
            return ruleId != null ? ruleId : "";
        }
        if ("configName".equals(saltRef)) {
            Optional<JsonNode> configName = resolveFact(ruleSetId, ruleId, "configName", input, factResolver);
            if (configName.isPresent()) {
                return configName.get().asText();
            }
            return resolveFact(ruleSetId, ruleId, "configId", input, factResolver)
                    .map(JsonNode::asText)
                    .orElseThrow(() -> missingSalt(saltRef));
        }
        return resolveFact(ruleSetId, ruleId, saltRef, input, factResolver)
                .map(JsonNode::asText)
                .orElseThrow(() -> missingSalt(saltRef));
    }

    private static Optional<JsonNode> resolveFact(String ruleSetId,
                                                  String ruleId,
                                                  String fieldRef,
                                                  JsonNode input,
                                                  FactResolver factResolver) {
        FactResolver resolver = factResolver != null ? factResolver : FactResolver.defaultResolver();
        try {
            ResolvedFact resolvedFact = resolver.resolve(new FactResolutionContext(
                    ruleSetId,
                    ruleId,
                    -1,
                    fieldRef,
                    input,
                    null
            ));
            if (resolvedFact == null || !resolvedFact.resolved() || resolvedFact.value() == null || resolvedFact.value().isNull()) {
                return Optional.empty();
            }
            return Optional.of(resolvedFact.value());
        } catch (RuntimeException e) {
            throw new RuleKitEvaluationException(
                    RuleKitExceptionCode.RESOLVER_FAILED,
                    "Fact resolver failed while resolving rollout fieldRef=" + fieldRef + ", ruleId=" + ruleId,
                    e
            );
        }
    }

    private static RuleKitEvaluationException missingSalt(String saltRef) {
        return new RuleKitEvaluationException(
                RuleKitExceptionCode.ROLLOUT_SALT_NOT_RESOLVED,
                "Rollout saltRef could not be resolved: " + saltRef,
                null
        );
    }
}

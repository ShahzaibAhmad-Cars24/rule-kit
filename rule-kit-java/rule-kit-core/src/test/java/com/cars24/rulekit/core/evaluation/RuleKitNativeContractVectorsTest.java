package com.cars24.rulekit.core.evaluation;

import com.cars24.rulekit.core.exception.RuleKitExceptionCode;
import com.cars24.rulekit.core.model.ConditionKind;
import com.cars24.rulekit.core.model.RuleSet;
import com.cars24.rulekit.core.resolver.SegmentMembershipResult;
import com.cars24.rulekit.core.trace.ConditionTrace;
import com.cars24.rulekit.core.trace.TraceMode;
import com.cars24.rulekit.core.validation.RuleSetValidator;
import com.cars24.rulekit.core.validation.ValidationMessage;
import com.cars24.rulekit.core.validation.ValidationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RuleKitNativeContractVectorsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RuleKitEvaluator evaluator = new RuleKitEvaluator();

    @Test
    void executesNativeContractVectors() throws Exception {
        assertVector("rollout-included.json");
        assertVector("segment-any.json");
        assertVector("dependency-gate.json");
        assertVector("validation-invalid-number.json");
    }

    private void assertVector(String fileName) throws Exception {
        Path vectorPath = Path.of(System.getProperty("user.dir"))
                .resolve("../../rule-kit-contract/test-vectors/native")
                .resolve(fileName)
                .normalize();
        JsonNode vector = objectMapper.readTree(Files.readString(vectorPath));

        if ("validation".equals(vector.path("type").asText())) {
            assertValidationVector(vector);
            return;
        }
        assertEvaluationVector(vector);
    }

    private void assertValidationVector(JsonNode vector) throws Exception {
        RuleSet ruleSet = objectMapper.treeToValue(vector.get("ruleSet"), RuleSet.class);
        ValidationResult result = RuleSetValidator.validate(ruleSet);

        assertThat(result.valid()).isEqualTo(vector.get("expected").get("valid").asBoolean());
        assertThat(result.errors())
                .extracting(ValidationMessage::code)
                .containsAll(expectedErrorCodes(vector.get("expected").path("errorCodes")));
    }

    private void assertEvaluationVector(JsonNode vector) throws Exception {
        RuleSet ruleSet = objectMapper.treeToValue(vector.get("ruleSet"), RuleSet.class);
        EvaluationOptions options = evaluationOptions(vector);
        EvaluationResult result = evaluator.evaluate(
                ruleSet,
                vector.get("input"),
                TraceMode.valueOf(vector.get("traceMode").asText()),
                options
        );

        JsonNode expected = vector.get("expected");
        assertThat(result.matchedRuleId()).isEqualTo(
                expected.get("matchedRuleId").isNull() ? null : expected.get("matchedRuleId").asText()
        );
        assertThat(result.defaultUsed()).isEqualTo(expected.get("defaultUsed").asBoolean());
        assertThat(result.response()).isEqualTo(expected.get("response"));

        if (expected.has("rolloutHashInput")) {
            assertThat(result.trace().rules().get(0).rollout().hashInput())
                    .isEqualTo(expected.get("rolloutHashInput").asText());
        }
        if (expected.has("conditionKinds")) {
            assertThat(result.trace().rules().get(0).conditions())
                    .extracting(ConditionTrace::conditionKind)
                    .containsExactlyElementsOf(expectedConditionKinds(expected.get("conditionKinds")));
        }
    }

    private EvaluationOptions evaluationOptions(JsonNode vector) {
        EvaluationOptions.Builder builder = EvaluationOptions.builder();

        if (vector.has("segmentMemberships")) {
            Map<String, Boolean> memberships = new HashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = vector.get("segmentMemberships").fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                memberships.put(field.getKey(), field.getValue().asBoolean());
            }
            builder.segmentResolver(context -> SegmentMembershipResult.of(memberships));
        }

        if (vector.has("dependencies")) {
            Map<String, CompiledRuleSet> dependencies = new HashMap<>();
            for (JsonNode dependency : vector.get("dependencies")) {
                RuleSet dependencyRuleSet = objectMapper.convertValue(dependency, RuleSet.class);
                dependencies.put(dependencyRuleSet.id(), evaluator.compile(dependencyRuleSet));
            }
            builder.dependencyRuleSetResolver(context -> Optional.ofNullable(dependencies.get(context.dependencyRuleSetId())));
        }

        return builder.build();
    }

    private java.util.List<RuleKitExceptionCode> expectedErrorCodes(JsonNode errorCodes) {
        java.util.List<RuleKitExceptionCode> codes = new java.util.ArrayList<>();
        errorCodes.forEach(code -> codes.add(RuleKitExceptionCode.valueOf(code.asText())));
        return codes;
    }

    private java.util.List<ConditionKind> expectedConditionKinds(JsonNode conditionKinds) {
        java.util.List<ConditionKind> kinds = new java.util.ArrayList<>();
        conditionKinds.forEach(kind -> kinds.add(ConditionKind.valueOf(kind.asText())));
        return kinds;
    }
}

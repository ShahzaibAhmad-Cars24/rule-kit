package com.cars24.rulekit.core.operator;

import com.cars24.rulekit.core.evaluation.EvaluationResult;
import com.cars24.rulekit.core.evaluation.RuleKitEvaluator;
import com.cars24.rulekit.core.exception.RuleKitExceptionCode;
import com.cars24.rulekit.core.model.RuleSet;
import com.cars24.rulekit.core.trace.TraceMode;
import com.cars24.rulekit.core.validation.RuleSetValidator;
import com.cars24.rulekit.core.validation.ValidationMessage;
import com.cars24.rulekit.core.validation.ValidationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicConfigOperatorParityContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RuleKitEvaluator evaluator = new RuleKitEvaluator();

    @Test
    void contractDocumentsEveryCanonicalRuleKitOperatorMapping() throws Exception {
        JsonNode contract = readContract();
        Set<String> operations = new HashSet<>();
        contract.get("operators").forEach(operator -> {
            if (!operator.path("hostResolved").asBoolean(false)) {
                operations.add(operator.get("ruleKitOperator").asText());
            }
        });

        assertThat(operations).containsExactlyInAnyOrder(
                "IN",
                "IN_CASE_INSENSITIVE",
                "NOT_IN",
                "NOT_IN_CASE_INSENSITIVE",
                "STARTS_WITH",
                "STARTS_WITH_CASE_INSENSITIVE",
                "ENDS_WITH",
                "ENDS_WITH_CASE_INSENSITIVE",
                "CONTAINS",
                "CONTAINS_CASE_INSENSITIVE",
                "NOT_CONTAINS",
                "NOT_CONTAINS_CASE_INSENSITIVE",
                "MATCHES",
                "NOT_MATCHES",
                "GT",
                "LT",
                "GTE",
                "LTE",
                "EQ",
                "NEQ",
                "NOT_EXISTS",
                "EXISTS"
        );
    }

    @Test
    void directRuleKitOperatorExamplesInContractEvaluateAsExpected() throws Exception {
        JsonNode contract = readContract();

        for (JsonNode operator : contract.get("operators")) {
            if (operator.path("hostResolved").asBoolean(false)) {
                continue;
            }

            String operation = operator.get("dynamicConfigOperation").asText();
            assertContractCase(operation, operator.get("example"));
            for (JsonNode edgeCase : operator.path("edgeCases")) {
                assertContractCase(operation + "/" + edgeCase.path("name").asText(), edgeCase);
            }
        }
    }

    private void assertContractCase(String label, JsonNode example) throws Exception {
        RuleSet ruleSet = objectMapper.treeToValue(example.get("ruleSet"), RuleSet.class);
        JsonNode expected = example.path("expected");

        if (expected.has("validationError")) {
            ValidationResult validation = RuleSetValidator.validate(ruleSet);
            assertThat(validation.errors())
                    .as(label)
                    .extracting(ValidationMessage::code)
                    .contains(RuleKitExceptionCode.valueOf(expected.get("validationError").asText()));
            return;
        }

        EvaluationResult result = evaluator.evaluate(ruleSet, example.get("input"), TraceMode.COMPACT);

        assertThat(result.defaultUsed())
                .as(label)
                .isEqualTo(expected.path("defaultUsed").asBoolean(false));
        assertThat(result.matchedRuleId())
                .as(label)
                .isEqualTo(expected.has("matchedRuleId")
                        ? (expected.get("matchedRuleId").isNull() ? null : expected.get("matchedRuleId").asText())
                        : "operator-match");
    }

    private JsonNode readContract() throws Exception {
        Path contractPath = Path.of(System.getProperty("user.dir"))
                .resolve("../../rule-kit-contract/dynamic-config/operator-parity-v1.json")
                .normalize();
        return objectMapper.readTree(Files.readString(contractPath));
    }
}

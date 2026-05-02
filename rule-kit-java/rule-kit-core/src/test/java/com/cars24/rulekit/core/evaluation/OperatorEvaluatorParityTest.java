package com.cars24.rulekit.core.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OperatorEvaluator native v1 semantics")
class OperatorEvaluatorParityTest {

    @Test
    @DisplayName("numeric comparisons treat missing and invalid actual values as non-matches")
    void numericComparisonsTreatMissingAndInvalidActualValuesAsNonMatches() {
        assertThat(evaluate("GT", TextNode.valueOf("abc"), IntNode.valueOf(-1))).isFalse();
        assertThat(evaluate("GTE", Optional.empty(), IntNode.valueOf(0))).isFalse();
        assertThat(evaluate("LT", TextNode.valueOf("abc"), IntNode.valueOf(1))).isFalse();
        assertThat(evaluate("LTE", Optional.empty(), IntNode.valueOf(0))).isFalse();
    }

    @Test
    @DisplayName("equality is type-aware for booleans and numbers")
    void equalityIsTypeAwareForBooleansAndNumbers() {
        assertThat(evaluate("EQ", TextNode.valueOf("true"), BooleanNode.TRUE)).isFalse();
        assertThat(evaluate("EQ", TextNode.valueOf("TRUE"), BooleanNode.TRUE)).isFalse();
        assertThat(evaluate("NEQ", TextNode.valueOf("TRUE"), BooleanNode.TRUE)).isTrue();
        assertThat(evaluate("EQ", BooleanNode.TRUE, TextNode.valueOf("TRUE"))).isFalse();
        assertThat(evaluate("EQ", TextNode.valueOf("1"), IntNode.valueOf(1))).isFalse();
        assertThat(evaluate("EQ", IntNode.valueOf(1), IntNode.valueOf(1))).isTrue();
    }

    @Test
    @DisplayName("negative operators do not match missing or null actual values except NOT_EXISTS")
    void negativeOperatorsDoNotMatchMissingOrNullActualValuesExceptNotExists() {
        assertThat(evaluate("NEQ", Optional.empty(), TextNode.valueOf("gold"))).isFalse();
        assertThat(evaluate("NOT_IN", Optional.empty(), TextNode.valueOf("gold"))).isFalse();
        assertThat(evaluate("NOT_CONTAINS", Optional.empty(), TextNode.valueOf("gold"))).isFalse();
        assertThat(evaluate("NOT_MATCHES", Optional.empty(), TextNode.valueOf("^gold$"))).isFalse();
        assertThat(evaluate("NOT_EXISTS", Optional.empty(), null)).isTrue();

        assertThat(evaluate("NEQ", NullNode.getInstance(), TextNode.valueOf("gold"))).isFalse();
        assertThat(evaluate("NOT_IN", NullNode.getInstance(), TextNode.valueOf("gold"))).isFalse();
        assertThat(evaluate("NOT_CONTAINS", NullNode.getInstance(), TextNode.valueOf("gold"))).isFalse();
        assertThat(evaluate("NOT_MATCHES", NullNode.getInstance(), TextNode.valueOf("^gold$"))).isFalse();
        assertThat(evaluate("NOT_EXISTS", NullNode.getInstance(), null)).isTrue();
    }

    private boolean evaluate(String operator, JsonNode actual, JsonNode expected) {
        return evaluate(operator, Optional.of(actual), expected);
    }

    private boolean evaluate(String operator, Optional<JsonNode> actual, JsonNode expected) {
        return OperatorEvaluator.evaluate(operator, actual, expected, null);
    }
}

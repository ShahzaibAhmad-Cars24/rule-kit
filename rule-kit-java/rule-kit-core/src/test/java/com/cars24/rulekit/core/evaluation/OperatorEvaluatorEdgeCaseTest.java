package com.cars24.rulekit.core.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OperatorEvaluatorEdgeCaseTest {

    @Test
    void supportsStringCollectionAndCaseInsensitiveOperators() {
        JsonNode array = JsonNodeFactory.instance.arrayNode().add("Gurgaon").add("Delhi");

        assertThat(OperatorEvaluator.evaluate("CONTAINS_CASE_INSENSITIVE", Optional.of(array), TextNode.valueOf("delhi"), null)).isTrue();
        assertThat(OperatorEvaluator.evaluate("STARTS_WITH_CASE_INSENSITIVE", Optional.of(TextNode.valueOf("Cars24")), TextNode.valueOf("car"), null)).isTrue();
        assertThat(OperatorEvaluator.evaluate("ENDS_WITH_CASE_INSENSITIVE", Optional.of(TextNode.valueOf("Cars24")), TextNode.valueOf("24"), null)).isTrue();
    }

    @Test
    void handlesRegexBooleanAndUnsupportedOperatorEdges() {
        assertThat(OperatorEvaluator.evaluate("MATCHES", Optional.of(TextNode.valueOf("gold-plan")), TextNode.valueOf("[invalid"), null)).isFalse();
        assertThat(OperatorEvaluator.evaluate("IS_TRUE", Optional.of(TextNode.valueOf("TRUE")), null, null)).isTrue();
        assertThat(OperatorEvaluator.evaluate("IS_FALSE", Optional.of(BooleanNode.FALSE), null, null)).isTrue();
        assertThat(OperatorEvaluator.evaluate("UNKNOWN", Optional.of(TextNode.valueOf("x")), TextNode.valueOf("x"), null)).isFalse();
    }
}

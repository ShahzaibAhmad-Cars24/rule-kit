package com.cars24.rulekit.core.operator;

import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OperatorValueParserTest {

    @Test
    void parsesNumbersAndGracefullyRejectsUnsupportedValues() {
        assertThat(OperatorValueParser.asNumber(null)).isEmpty();
        assertThat(OperatorValueParser.asNumber(JsonNodeFactory.instance.nullNode())).isEmpty();
        assertThat(OperatorValueParser.asNumber(IntNode.valueOf(7))).contains(7.0);
        assertThat(OperatorValueParser.asNumber(TextNode.valueOf("7.5"))).contains(7.5);
        assertThat(OperatorValueParser.asNumber(TextNode.valueOf("NaN-ish"))).isEmpty();
        assertThat(OperatorValueParser.asNumber(JsonNodeFactory.instance.objectNode())).isEmpty();
    }

    @Test
    void expandsExpectedValuesFromArraysCommaSeparatedTextAndSingles() {
        assertThat(OperatorValueParser.expectedValues(null)).isEmpty();
        assertThat(OperatorValueParser.expectedValues(JsonNodeFactory.instance.arrayNode().add("a").add("b")))
                .containsExactly("a", "b");
        assertThat(OperatorValueParser.expectedValues(TextNode.valueOf(" gold, silver , bronze ")))
                .containsExactly("gold", "silver", "bronze");
        assertThat(OperatorValueParser.expectedValues(TextNode.valueOf("premium")))
                .containsExactly("premium");
    }
}

package com.cars24.rulekit.core.operator;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class OperatorValueParser {

    private OperatorValueParser() {
    }

    public static Optional<Double> asNumber(JsonNode value) {
        if (value == null || value.isNull()) {
            return Optional.empty();
        }
        if (value.isNumber()) {
            return Optional.of(value.asDouble());
        }
        if (!value.isTextual()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Double.parseDouble(value.asText()));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    public static List<String> expectedValues(JsonNode expected) {
        List<String> values = new ArrayList<>();
        if (expected == null || expected.isNull()) {
            return values;
        }
        if (expected.isArray()) {
            expected.forEach(value -> values.add(value.asText()));
            return values;
        }
        if (expected.isTextual() && expected.asText().contains(",")) {
            for (String value : expected.asText().split(",")) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    values.add(trimmed);
                }
            }
            return values;
        }
        values.add(expected.asText());
        return values;
    }
}

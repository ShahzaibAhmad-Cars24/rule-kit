package com.cars24.rulekit.core.evaluation;

import com.cars24.rulekit.core.operator.OperatorValueParser;
import com.cars24.rulekit.core.operator.RuleKitOperator;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

final class OperatorEvaluator {

    private OperatorEvaluator() {
    }

    static boolean evaluate(String operator, Optional<JsonNode> actual, JsonNode expected, JsonNode expectedTo) {
        return RuleKitOperator.from(operator)
                .map(ruleKitOperator -> evaluate(ruleKitOperator, actual, expected, expectedTo, null))
                .orElse(false);
    }

    static boolean evaluate(CompiledCondition condition, Optional<JsonNode> actual) {
        return evaluate(
                condition.operator(),
                actual,
                condition.source().value(),
                condition.source().valueTo(),
                condition
        );
    }

    private static boolean evaluate(RuleKitOperator operator,
                                    Optional<JsonNode> actual,
                                    JsonNode expected,
                                    JsonNode expectedTo,
                                    CompiledCondition compiledCondition) {
        return switch (operator) {
            case EQ -> equalsValue(actual, expected);
            case NEQ -> hasActualValue(actual) && !equalsValue(actual, expected);
            case GT -> compare(actual, expected, compiledCondition).map(value -> value > 0).orElse(false);
            case GTE -> compare(actual, expected, compiledCondition).map(value -> value >= 0).orElse(false);
            case LT -> compare(actual, expected, compiledCondition).map(value -> value < 0).orElse(false);
            case LTE -> compare(actual, expected, compiledCondition).map(value -> value <= 0).orElse(false);
            case BETWEEN -> compare(actual, expected, compiledCondition).map(value -> value >= 0).orElse(false)
                    && compareToUpperBound(actual, expectedTo, compiledCondition).map(value -> value <= 0).orElse(false);
            case IN -> in(actual, expected, compiledCondition, false);
            case IN_CASE_INSENSITIVE -> in(actual, expected, compiledCondition, true);
            case NOT_IN -> hasActualValue(actual) && !in(actual, expected, compiledCondition, false);
            case NOT_IN_CASE_INSENSITIVE -> hasActualValue(actual) && !in(actual, expected, compiledCondition, true);
            case CONTAINS -> contains(actual, expected, false);
            case CONTAINS_CASE_INSENSITIVE -> contains(actual, expected, true);
            case NOT_CONTAINS -> hasActualValue(actual) && !contains(actual, expected, false);
            case NOT_CONTAINS_CASE_INSENSITIVE -> hasActualValue(actual) && !contains(actual, expected, true);
            case STARTS_WITH -> startsWith(actual, expected, false);
            case STARTS_WITH_CASE_INSENSITIVE -> startsWith(actual, expected, true);
            case ENDS_WITH -> endsWith(actual, expected, false);
            case ENDS_WITH_CASE_INSENSITIVE -> endsWith(actual, expected, true);
            case MATCHES -> regex(actual, expected, compiledCondition, false);
            case NOT_MATCHES -> hasActualValue(actual) && regex(actual, expected, compiledCondition, true);
            case EXISTS -> actual.filter(node -> !node.isNull()).isPresent();
            case NOT_EXISTS -> actual.isEmpty() || actual.get().isNull();
            case IS_TRUE -> asBoolean(actual).orElse(false);
            case IS_FALSE -> actual.isPresent() && !asBoolean(actual).orElse(true);
        };
    }

    private static boolean equalsValue(Optional<JsonNode> actual, JsonNode expected) {
        if (actual.isEmpty()) {
            return false;
        }
        JsonNode actualNode = actual.get();
        if (actualNode.isNull() || expected == null || expected.isNull()) {
            return actualNode.isNull() && (expected == null || expected.isNull());
        }
        if (actualNode.isNumber() || expected.isNumber()) {
            return actualNode.isNumber() && expected.isNumber()
                    && Double.compare(actualNode.asDouble(), expected.asDouble()) == 0;
        }
        if (actualNode.isBoolean() || expected.isBoolean()) {
            return actualNode.isBoolean() && expected.isBoolean()
                    && actualNode.asBoolean() == expected.asBoolean();
        }
        if (actualNode.isTextual() || expected.isTextual()) {
            return actualNode.isTextual() && expected.isTextual()
                    && actualNode.asText().equals(expected.asText());
        }
        return actualNode.equals(expected);
    }

    private static boolean hasActualValue(Optional<JsonNode> actual) {
        return actual.isPresent() && !actual.get().isNull();
    }

    private static Optional<Integer> compare(Optional<JsonNode> actual, JsonNode expected, CompiledCondition compiledCondition) {
        Optional<Double> actualNumber = actual.flatMap(OperatorValueParser::asNumber);
        Optional<Double> expectedNumber = compiledCondition != null
                ? Optional.of(compiledCondition.expectedNumber())
                : OperatorValueParser.asNumber(expected);
        if (actualNumber.isEmpty() || expectedNumber.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Double.compare(actualNumber.get(), expectedNumber.get()));
    }

    private static Optional<Integer> compareToUpperBound(Optional<JsonNode> actual,
                                                         JsonNode expectedTo,
                                                         CompiledCondition compiledCondition) {
        Optional<Double> actualNumber = actual.flatMap(OperatorValueParser::asNumber);
        Optional<Double> expectedToNumber = compiledCondition != null
                ? Optional.of(compiledCondition.expectedToNumber())
                : OperatorValueParser.asNumber(expectedTo);
        if (actualNumber.isEmpty() || expectedToNumber.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Double.compare(actualNumber.get(), expectedToNumber.get()));
    }

    private static boolean in(Optional<JsonNode> actual, JsonNode expected, CompiledCondition compiledCondition, boolean ignoreCase) {
        if (actual.isEmpty() || actual.get().isNull()) {
            return false;
        }
        String actualText = actual.get().asText();
        List<String> expectedValues = compiledCondition != null
                ? compiledCondition.expectedValues()
                : OperatorValueParser.expectedValues(expected);
        return expectedValues.stream().anyMatch(candidate -> sameText(actualText, candidate, ignoreCase));
    }

    private static boolean contains(Optional<JsonNode> actual, JsonNode expected, boolean ignoreCase) {
        if (actual.isEmpty() || actual.get().isNull() || expected == null || expected.isNull()) {
            return false;
        }
        JsonNode actualNode = actual.get();
        if (actualNode.isArray()) {
            for (JsonNode item : actualNode) {
                if (sameText(item.asText(), expected.asText(), ignoreCase)) {
                    return true;
                }
            }
            return false;
        }
        String actualText = normalizeCase(actualNode.asText(), ignoreCase);
        String expectedText = normalizeCase(expected.asText(), ignoreCase);
        return actualText.contains(expectedText);
    }

    private static boolean startsWith(Optional<JsonNode> actual, JsonNode expected, boolean ignoreCase) {
        if (actual.isEmpty() || expected == null || expected.isNull()) {
            return false;
        }
        return normalizeCase(actual.get().asText(), ignoreCase).startsWith(normalizeCase(expected.asText(), ignoreCase));
    }

    private static boolean endsWith(Optional<JsonNode> actual, JsonNode expected, boolean ignoreCase) {
        if (actual.isEmpty() || expected == null || expected.isNull()) {
            return false;
        }
        return normalizeCase(actual.get().asText(), ignoreCase).endsWith(normalizeCase(expected.asText(), ignoreCase));
    }

    private static boolean regex(Optional<JsonNode> actual, JsonNode expected, CompiledCondition compiledCondition, boolean negate) {
        if (actual.isEmpty() || actual.get().isNull() || expected == null || expected.isNull()) {
            return negate;
        }
        try {
            Pattern pattern = compiledCondition != null && compiledCondition.regexPattern() != null
                    ? compiledCondition.regexPattern()
                    : Pattern.compile(expected.asText());
            boolean matches = pattern.matcher(actual.get().asText()).find();
            return negate ? !matches : matches;
        } catch (PatternSyntaxException ignored) {
            return false;
        }
    }

    private static Optional<Boolean> asBoolean(Optional<JsonNode> value) {
        if (value.isEmpty() || value.get().isNull()) {
            return Optional.empty();
        }
        JsonNode node = value.get();
        if (node.isBoolean()) {
            return Optional.of(node.asBoolean());
        }
        if (node.isTextual() && ("true".equalsIgnoreCase(node.asText()) || "false".equalsIgnoreCase(node.asText()))) {
            return Optional.of(Boolean.parseBoolean(node.asText()));
        }
        return Optional.empty();
    }

    private static boolean sameText(String left, String right, boolean ignoreCase) {
        return ignoreCase ? left.equalsIgnoreCase(right) : left.equals(right);
    }

    private static String normalizeCase(String value, boolean ignoreCase) {
        return ignoreCase ? value.toLowerCase(Locale.ROOT) : value;
    }
}

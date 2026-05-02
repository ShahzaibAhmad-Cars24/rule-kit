package com.cars24.rulekit.core.resolver;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

public final class FieldValueResolver {

    private FieldValueResolver() {
    }

    public static Optional<JsonNode> resolve(JsonNode input, String fieldRef) {
        if (input == null || input.isMissingNode() || fieldRef == null || fieldRef.isBlank()) {
            return Optional.empty();
        }

        JsonNode exact = input.get(unescape(fieldRef));
        if (exact != null) {
            return Optional.of(exact);
        }

        JsonNode current = input;
        for (String part : splitPath(fieldRef)) {
            if (part.isBlank() || current == null || current.isMissingNode()) {
                return Optional.empty();
            }
            current = current.get(part);
            if (current == null) {
                return Optional.empty();
            }
        }

        return Optional.of(current);
    }

    private static java.util.List<String> splitPath(String fieldRef) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaped = false;

        for (int i = 0; i < fieldRef.length(); i++) {
            char ch = fieldRef.charAt(i);
            if (escaped) {
                current.append(ch);
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                escaped = true;
                continue;
            }
            if (ch == '.') {
                parts.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }

        if (escaped) {
            current.append('\\');
        }
        parts.add(current.toString());
        return parts;
    }

    private static String unescape(String fieldRef) {
        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < fieldRef.length(); i++) {
            char ch = fieldRef.charAt(i);
            if (escaped) {
                value.append(ch);
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                escaped = true;
                continue;
            }
            value.append(ch);
        }
        if (escaped) {
            value.append('\\');
        }
        return value.toString();
    }
}

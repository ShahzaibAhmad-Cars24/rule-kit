package com.cars24.rulekit.core.operator;

import java.util.Optional;

public enum RuleKitOperator {
    EQ(true, false, false, false, false),
    NEQ(true, false, false, false, false),
    GT(true, false, true, false, false),
    GTE(true, false, true, false, false),
    LT(true, false, true, false, false),
    LTE(true, false, true, false, false),
    BETWEEN(true, true, true, false, false),
    IN(true, false, false, false, true),
    IN_CASE_INSENSITIVE(true, false, false, false, true),
    NOT_IN(true, false, false, false, true),
    NOT_IN_CASE_INSENSITIVE(true, false, false, false, true),
    CONTAINS(true, false, false, false, false),
    CONTAINS_CASE_INSENSITIVE(true, false, false, false, false),
    NOT_CONTAINS(true, false, false, false, false),
    NOT_CONTAINS_CASE_INSENSITIVE(true, false, false, false, false),
    STARTS_WITH(true, false, false, false, false),
    STARTS_WITH_CASE_INSENSITIVE(true, false, false, false, false),
    ENDS_WITH(true, false, false, false, false),
    ENDS_WITH_CASE_INSENSITIVE(true, false, false, false, false),
    MATCHES(true, false, false, true, false),
    NOT_MATCHES(true, false, false, true, false),
    EXISTS(false, false, false, false, false),
    NOT_EXISTS(false, false, false, false, false),
    IS_TRUE(false, false, false, false, false),
    IS_FALSE(false, false, false, false, false);

    private final boolean requiresValue;
    private final boolean requiresValueTo;
    private final boolean numeric;
    private final boolean regex;
    private final boolean listBased;

    RuleKitOperator(boolean requiresValue,
                    boolean requiresValueTo,
                    boolean numeric,
                    boolean regex,
                    boolean listBased) {
        this.requiresValue = requiresValue;
        this.requiresValueTo = requiresValueTo;
        this.numeric = numeric;
        this.regex = regex;
        this.listBased = listBased;
    }

    public boolean requiresValue() {
        return requiresValue;
    }

    public boolean requiresValueTo() {
        return requiresValueTo;
    }

    public boolean numeric() {
        return numeric;
    }

    public boolean regex() {
        return regex;
    }

    public boolean listBased() {
        return listBased;
    }

    public static Optional<RuleKitOperator> from(String operator) {
        return switch (normalize(operator)) {
            case "EQ" -> Optional.of(EQ);
            case "NEQ" -> Optional.of(NEQ);
            case "GT" -> Optional.of(GT);
            case "GTE" -> Optional.of(GTE);
            case "LT" -> Optional.of(LT);
            case "LTE" -> Optional.of(LTE);
            case "BETWEEN" -> Optional.of(BETWEEN);
            case "IN" -> Optional.of(IN);
            case "IN_CASE_INSENSITIVE" -> Optional.of(IN_CASE_INSENSITIVE);
            case "NOT_IN" -> Optional.of(NOT_IN);
            case "NOT_IN_CASE_INSENSITIVE" -> Optional.of(NOT_IN_CASE_INSENSITIVE);
            case "CONTAINS" -> Optional.of(CONTAINS);
            case "CONTAINS_CASE_INSENSITIVE" -> Optional.of(CONTAINS_CASE_INSENSITIVE);
            case "NOT_CONTAINS" -> Optional.of(NOT_CONTAINS);
            case "NOT_CONTAINS_CASE_INSENSITIVE" -> Optional.of(NOT_CONTAINS_CASE_INSENSITIVE);
            case "STARTS_WITH" -> Optional.of(STARTS_WITH);
            case "STARTS_WITH_CASE_INSENSITIVE" -> Optional.of(STARTS_WITH_CASE_INSENSITIVE);
            case "ENDS_WITH" -> Optional.of(ENDS_WITH);
            case "ENDS_WITH_CASE_INSENSITIVE" -> Optional.of(ENDS_WITH_CASE_INSENSITIVE);
            case "MATCHES" -> Optional.of(MATCHES);
            case "NOT_MATCHES" -> Optional.of(NOT_MATCHES);
            case "EXISTS" -> Optional.of(EXISTS);
            case "NOT_EXISTS" -> Optional.of(NOT_EXISTS);
            case "IS_TRUE" -> Optional.of(IS_TRUE);
            case "IS_FALSE" -> Optional.of(IS_FALSE);
            default -> Optional.empty();
        };
    }

    private static String normalize(String operator) {
        return operator == null ? "" : operator.trim().toUpperCase();
    }
}

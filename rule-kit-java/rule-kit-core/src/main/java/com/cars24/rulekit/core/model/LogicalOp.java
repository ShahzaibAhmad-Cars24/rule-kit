package com.cars24.rulekit.core.model;

/**
 * Logical operator for combining child conditions in a {@link ConditionNode} group.
 */
public enum LogicalOp {
    /** All children must match (short-circuit on first failure). */
    AND,
    /** At least one child must match (short-circuit on first success). */
    OR
}

package com.cars24.rulekit.core.model;

import java.util.List;

/**
 * A group node in a condition tree — combines child nodes with AND or OR logic.
 */
public record ConditionGroupNode(
        LogicalOp op,
        List<ConditionNode> children
) implements ConditionNode {

    public ConditionGroupNode {
        op = op != null ? op : LogicalOp.AND;
        children = children == null ? List.of() : List.copyOf(children);
    }

    /** Convenience factory for AND groups. */
    public static ConditionGroupNode and(List<ConditionNode> children) {
        return new ConditionGroupNode(LogicalOp.AND, children);
    }

    /** Convenience factory for OR groups. */
    public static ConditionGroupNode or(List<ConditionNode> children) {
        return new ConditionGroupNode(LogicalOp.OR, children);
    }
}

package com.cars24.rulekit.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Holds the condition structure for a rule.
 * Supports both the newer recursive {@code tree} shape and the legacy flat {@code all} list.
 */
public record ConditionGroup(
        ConditionNode tree,
        @JsonProperty("all") List<ConditionDefinition> all
) {

    public ConditionGroup {
        all = all == null ? List.of() : List.copyOf(all);
    }

    public ConditionGroup(ConditionNode tree) {
        this(tree, List.of());
    }

    public ConditionNode rootNode() {
        if (tree != null) {
            return tree;
        }
        if (!all.isEmpty()) {
            return ConditionGroupNode.and(all.stream()
                    .map(ConditionLeaf::fromConditionDefinition)
                    .map(ConditionNode.class::cast)
                    .toList());
        }
        return null;
    }
}

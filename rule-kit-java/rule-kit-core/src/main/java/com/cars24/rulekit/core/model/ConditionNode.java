package com.cars24.rulekit.core.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Sealed node in a condition tree.
 * <p>
 * Use {@link ConditionLeaf} for a single condition, or {@link ConditionGroupNode}
 * to group child nodes with AND / OR logic.
 * <p>
 * JSON discriminator field: {@code "type"}
 * <ul>
 *   <li>{@code "leaf"} → {@link ConditionLeaf}</li>
 *   <li>{@code "group"} → {@link ConditionGroupNode}</li>
 * </ul>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ConditionLeaf.class, name = "leaf"),
        @JsonSubTypes.Type(value = ConditionGroupNode.class, name = "group")
})
public sealed interface ConditionNode permits ConditionLeaf, ConditionGroupNode {
}

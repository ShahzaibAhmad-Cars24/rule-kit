package com.cars24.rulekit.core.model;

import com.cars24.rulekit.core.trace.ConditionTrace;
import com.cars24.rulekit.core.trace.RuleTrace;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConditionGroupCompatibilityTest {

    @Test
    void rootNodePrefersExplicitTreeAndNormalizesLegacyAllConditions() {
        ConditionDefinition definition = new ConditionDefinition("plan", "EQ", TextNode.valueOf("gold"), null);
        ConditionGroup legacyOnly = new ConditionGroup(null, List.of(definition));

        ConditionNode normalized = legacyOnly.rootNode();

        assertThat(normalized).isInstanceOf(ConditionGroupNode.class);
        ConditionLeaf leaf = (ConditionLeaf) ((ConditionGroupNode) normalized).children().get(0);
        assertThat(leaf.toConditionDefinition().fieldRef()).isEqualTo("plan");

        ConditionGroup explicitTree = new ConditionGroup(new ConditionLeaf(
                ConditionKind.FIELD, "age", "GTE", IntNode.valueOf(18), null, List.of(), null, null, null, null
        ), List.of(definition));

        assertThat(explicitTree.rootNode()).isInstanceOf(ConditionLeaf.class);
    }

    @Test
    void conditionLeafRoundTripsAndRuleTraceConvenienceConstructorWorks() {
        ConditionDefinition definition = new ConditionDefinition(
                null,
                "age",
                "BETWEEN",
                IntNode.valueOf(18),
                IntNode.valueOf(30),
                List.of(),
                null,
                null,
                null,
                null
        );

        ConditionLeaf leaf = ConditionLeaf.fromConditionDefinition(definition);
        RuleTrace trace = new RuleTrace("rule-1", true, List.of(new ConditionTrace("age", "BETWEEN",
                IntNode.valueOf(18), IntNode.valueOf(30), IntNode.valueOf(20), true, true, false, null)));

        assertThat(leaf.kind()).isEqualTo(ConditionKind.FIELD);
        assertThat(leaf.toConditionDefinition().resolvedKind()).isEqualTo(ConditionKind.FIELD);
        assertThat(trace.rollout()).isNull();
    }
}

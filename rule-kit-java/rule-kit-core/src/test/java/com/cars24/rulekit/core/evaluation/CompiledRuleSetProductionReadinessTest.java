package com.cars24.rulekit.core.evaluation;

import com.cars24.rulekit.core.model.ConditionDefinition;
import com.cars24.rulekit.core.model.ConditionGroup;
import com.cars24.rulekit.core.model.ConditionGroupNode;
import com.cars24.rulekit.core.model.ConditionLeaf;
import com.cars24.rulekit.core.model.ExecutionMode;
import com.cars24.rulekit.core.model.RuleDefinition;
import com.cars24.rulekit.core.model.RuleKitVersions;
import com.cars24.rulekit.core.model.RuleSet;
import com.cars24.rulekit.core.model.RuleThen;
import com.cars24.rulekit.core.trace.TraceMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompiledRuleSetProductionReadinessTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RuleKitEvaluator evaluator = new RuleKitEvaluator();

    @Test
    void compiledRuleSetIsDeepImmutableFromSourceRuleSetMutations() {
        ArrayNode allowedPlans = objectMapper.createArrayNode().add("gold");
        ObjectNode defaultResponse = objectMapper.createObjectNode().put("discount", 0);
        ObjectNode matchedResponse = objectMapper.createObjectNode().put("discount", 15);

        ConditionLeaf leaf = new ConditionLeaf(
                com.cars24.rulekit.core.model.ConditionKind.FIELD,
                "plan", "IN", allowedPlans, null, null, null, null, null, null
        );
        ConditionGroupNode treeRoot = ConditionGroupNode.and(List.of(leaf));

        RuleDefinition rule = new RuleDefinition(
                "gold-discount",
                10,
                true,
                new ConditionGroup(treeRoot),
                new RuleThen(matchedResponse)
        );
        List<RuleDefinition> rules = new ArrayList<>();
        rules.add(rule);

        RuleSet ruleSet = new RuleSet(
                "immutable-rules",
                RuleKitVersions.RULESET_SCHEMA_VERSION,
                ExecutionMode.FIRST_MATCH,
                defaultResponse,
                rules
        );

        CompiledRuleSet compiled = evaluator.compile(ruleSet);

        rules.clear();
        allowedPlans.removeAll().add("silver");
        defaultResponse.put("discount", -1);
        matchedResponse.put("discount", 99);

        EvaluationResult matched = evaluator.evaluate(
                compiled,
                objectMapper.createObjectNode().put("plan", "gold"),
                TraceMode.COMPACT
        );
        EvaluationResult fallback = evaluator.evaluate(
                compiled,
                objectMapper.createObjectNode().put("plan", "bronze"),
                TraceMode.COMPACT
        );

        assertThat(compiled.rules()).hasSize(1);
        assertThatThrownBy(() -> compiled.rules().add(rule)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(matched.matchedRuleId()).isEqualTo("gold-discount");
        assertThat(matched.response().get("discount").asInt()).isEqualTo(15);
        assertThat(fallback.response().get("discount").asInt()).isEqualTo(0);
    }
}

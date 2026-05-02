package com.cars24.rulekit.core.resolver;

import com.cars24.rulekit.core.evaluation.EvaluationResult;
import com.cars24.rulekit.core.evaluation.RuleKitEvaluator;
import com.cars24.rulekit.core.model.RuleSet;
import com.cars24.rulekit.core.trace.TraceMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FactResolverErgonomicsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RuleKitEvaluator evaluator = new RuleKitEvaluator();

    @Test
    void contextualFactoryAllowsLambdaAccessToRuleAndConditionContext() throws Exception {
        RuleSet ruleSet = objectMapper.readValue("""
                {
                  "id": "context-factory-rules",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": "closed",
                  "rules": [
                    {
                      "id": "checkout-gate",
                      "priority": 10,
                      "enabled": true,
                      "when": { "all": [
                        { "fieldRef": "_gates.checkout.matched", "operator": "EQ", "value": true }
                      ]},
                      "then": { "response": "open" }
                    }
                  ]
                }
                """, RuleSet.class);
        List<String> contexts = new ArrayList<>();

        FactResolver resolver = FactResolver.contextual(context -> {
            contexts.add(context.ruleSetId() + "/" + context.ruleId() + "/" + context.conditionIndex());
            return ResolvedFact.found(objectMapper.getNodeFactory().booleanNode(true));
        });

        EvaluationResult result = evaluator.evaluate(ruleSet, objectMapper.readTree("{}"), TraceMode.COMPACT, resolver);

        assertThat(result.response().asText()).isEqualTo("open");
        assertThat(contexts).containsExactly("context-factory-rules/checkout-gate/0");
    }
}

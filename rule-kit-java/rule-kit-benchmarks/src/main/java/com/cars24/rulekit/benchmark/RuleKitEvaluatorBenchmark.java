package com.cars24.rulekit.benchmark;

import com.cars24.rulekit.core.evaluation.CompiledRuleSet;
import com.cars24.rulekit.core.evaluation.EvaluationResult;
import com.cars24.rulekit.core.evaluation.RuleKitEvaluator;
import com.cars24.rulekit.core.model.RuleSet;
import com.cars24.rulekit.core.trace.TraceMode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class RuleKitEvaluatorBenchmark {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RuleKitEvaluator evaluator = new RuleKitEvaluator();

    private RuleSet ruleSet;
    private CompiledRuleSet compiledRuleSet;
    private JsonNode matchingInput;
    private JsonNode noMatchInput;

    @Setup
    public void setUp() throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        ruleSet = objectMapper.readValue("""
                {
                  "id": "benchmark-rules",
                  "schemaVersion": "rule-kit.ruleset.v1",
                  "executionMode": "FIRST_MATCH",
                  "defaultResponse": { "eligible": false },
                  "rules": [
                    {
                      "id": "vip-large-cart",
                      "priority": 100,
                      "enabled": true,
                      "when": { "all": [
                        { "fieldRef": "customer.tier", "operator": "EQ", "value": "vip" },
                        { "fieldRef": "cart.total", "operator": "GTE", "value": 5000 },
                        { "fieldRef": "email", "operator": "MATCHES", "value": "^[^@]+@cars24\\\\.com$" }
                      ]},
                      "then": { "response": { "eligible": true, "discount": 15 } }
                    },
                    {
                      "id": "gold-large-cart",
                      "priority": 50,
                      "enabled": true,
                      "when": { "all": [
                        { "fieldRef": "customer.tier", "operator": "EQ", "value": "gold" },
                        { "fieldRef": "cart.total", "operator": "GTE", "value": 3000 },
                        { "fieldRef": "city", "operator": "IN_CASE_INSENSITIVE", "value": ["Gurgaon", "Delhi NCR"] }
                      ]},
                      "then": { "response": { "eligible": true, "discount": 10 } }
                    }
                  ]
                }
                """, RuleSet.class);
        compiledRuleSet = evaluator.compile(ruleSet);
        matchingInput = objectMapper.readTree("""
                {
                  "customer": { "tier": "vip" },
                  "cart": { "total": 8000 },
                  "email": "buyer@cars24.com",
                  "city": "Gurgaon"
                }
                """);
        noMatchInput = objectMapper.readTree("""
                {
                  "customer": { "tier": "silver" },
                  "cart": { "total": 100 },
                  "email": "buyer@example.com",
                  "city": "Mumbai"
                }
                """);
    }

    @Benchmark
    public EvaluationResult coldRuleSetEvaluateCompact() {
        return evaluator.evaluate(ruleSet, matchingInput, TraceMode.COMPACT);
    }

    @Benchmark
    public EvaluationResult compiledEvaluateCompactMatch() {
        return evaluator.evaluate(compiledRuleSet, matchingInput, TraceMode.COMPACT);
    }

    @Benchmark
    public EvaluationResult compiledEvaluateVerboseMatch() {
        return evaluator.evaluate(compiledRuleSet, matchingInput, TraceMode.VERBOSE);
    }

    @Benchmark
    public EvaluationResult compiledEvaluateCompactNoMatch() {
        return evaluator.evaluate(compiledRuleSet, noMatchInput, TraceMode.COMPACT);
    }
}

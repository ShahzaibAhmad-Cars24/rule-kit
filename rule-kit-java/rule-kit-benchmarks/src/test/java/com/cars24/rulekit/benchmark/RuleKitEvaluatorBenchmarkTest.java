package com.cars24.rulekit.benchmark;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuleKitEvaluatorBenchmarkTest {

    @Test
    void benchmarkScenariosProduceExpectedMatchOutcomes() throws Exception {
        RuleKitEvaluatorBenchmark benchmark = new RuleKitEvaluatorBenchmark();

        benchmark.setUp();

        assertThat(benchmark.coldRuleSetEvaluateCompact().matchedRuleId()).isEqualTo("vip-large-cart");
        assertThat(benchmark.compiledEvaluateCompactMatch().matchedRuleId()).isEqualTo("vip-large-cart");
        assertThat(benchmark.compiledEvaluateVerboseMatch().trace().rules()).isNotEmpty();
        assertThat(benchmark.compiledEvaluateCompactNoMatch().defaultUsed()).isTrue();
    }
}

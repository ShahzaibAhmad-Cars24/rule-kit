# RuleKit JMH Benchmark Artifact

Date: 2026-05-02
Purpose: initial RuleKit-local benchmark evidence for the Dynamic Config v1 execution-plane handoff.

## Workspace

- Workspace path: `/Users/user/Desktop/cars24/projects/rule-kit`
- Workspace revision: unavailable because this folder is not a git checkout.
- Maven version: Apache Maven 3.9.10
- OS: macOS 26.2, Darwin 25.2.0, arm64
- CPU: Apple M3 Pro
- CPU cores reported by `hw.ncpu`: 12
- Memory reported by `hw.memsize`: 19327352832 bytes

## Commands

Build command:

```bash
mvn -pl rule-kit-java/rule-kit-benchmarks -am package -DskipTests
```

JDK 21 benchmark command:

```bash
java -cp rule-kit-java/rule-kit-benchmarks/target/rule-kit-benchmarks.jar org.openjdk.jmh.Main -rf json -rff docs/performance/artifacts/2026-05-02-rulekit-jmh/jmh-results.json
```

JDK 17 benchmark command:

```bash
JAVA17_HOME=$(/usr/libexec/java_home -v 17)
"$JAVA17_HOME/bin/java" -cp rule-kit-java/rule-kit-benchmarks/target/rule-kit-benchmarks.jar org.openjdk.jmh.Main -rf json -rff docs/performance/artifacts/2026-05-02-rulekit-jmh/jmh-results-java17.json
```

## Scenario Matrix

| Benchmark | Scenario |
| --- | --- |
| `coldRuleSetEvaluateCompact` | Evaluate raw `RuleSet` in compact trace mode. |
| `compiledEvaluateCompactMatch` | Evaluate cached `CompiledRuleSet` where the first rule matches. |
| `compiledEvaluateCompactNoMatch` | Evaluate cached `CompiledRuleSet` where no rules match and default response is used. |
| `compiledEvaluateVerboseMatch` | Evaluate cached `CompiledRuleSet` in verbose trace mode where the first rule matches. |

The benchmark RuleSet contains two enabled rules with field conditions covering equality, numeric comparison, regex, and case-insensitive list matching.

## Results Summary

JDK 21: Corretto 21.0.3

| Benchmark | Score |
| --- | --- |
| `coldRuleSetEvaluateCompact` | 787570.573 ops/s |
| `compiledEvaluateCompactMatch` | 3385823.406 ops/s |
| `compiledEvaluateCompactNoMatch` | 3685411.231 ops/s |
| `compiledEvaluateVerboseMatch` | 3160919.639 ops/s |

JDK 17: Corretto 17.0.8

| Benchmark | Score |
| --- | --- |
| `coldRuleSetEvaluateCompact` | 788983.417 ops/s |
| `compiledEvaluateCompactMatch` | 3579575.379 ops/s |
| `compiledEvaluateCompactNoMatch` | 3590589.622 ops/s |
| `compiledEvaluateVerboseMatch` | 3075115.764 ops/s |

## Raw Artifacts

- `docs/performance/artifacts/2026-05-02-rulekit-jmh/jmh-output.txt`
- `docs/performance/artifacts/2026-05-02-rulekit-jmh/jmh-results.json`
- `docs/performance/artifacts/2026-05-02-rulekit-jmh/jmh-output-java17.txt`
- `docs/performance/artifacts/2026-05-02-rulekit-jmh/jmh-results-java17.json`

## Interpretation Limits

- This is a local SDK microbenchmark, not an end-to-end Dynamic Config load test.
- It does not include network calls, persistence, exposure logging, segment storage, dependency cache misses, or host API overhead.
- The JDK 17 verbose benchmark has a wide confidence interval due to one lower iteration; use the raw output for review rather than treating the summary score as a hard SLA.
- Dynamic Config still needs an end-to-end shadow/load artifact before active production rollout.

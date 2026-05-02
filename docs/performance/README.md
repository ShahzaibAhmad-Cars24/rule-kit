# RuleKit Performance

RuleKit includes a JMH benchmark module so SDK-level performance can be measured separately from host application behavior.

Run the current benchmark suite:

```bash
mvn -pl rule-kit-java/rule-kit-benchmarks -am package -DskipTests
java -cp rule-kit-java/rule-kit-benchmarks/target/rule-kit-benchmarks.jar org.openjdk.jmh.Main
```

If a migration decision cites load-test numbers, attach the source artifact beside this document: commit SHA, command, environment, raw output, scenario matrix, and any accuracy preflight output. Do not treat unlinked performance numbers as release-gating evidence.

Current local artifact:

- `docs/performance/artifacts/2026-05-02-rulekit-jmh/report.md`

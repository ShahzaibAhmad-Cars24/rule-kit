# RuleKit Documentation Index

Use this file as the entry point for the current RuleKit SDK state.

## Current Authoritative Docs

- `../README.md`: SDK overview, package structure, Java/Spring usage, and evaluation model.
- `examples/2026-05-01-rule-kit-sdk-examples.md`: concrete Java SDK examples, YAML loading, resolver usage, native segment/dependency/rollout examples, and benchmark commands.
- `migration/2026-05-01-dynamic-config-v1-rulekit-native-plan.md`: implementation-ready Dynamic Config v1 integration plan.
- `migration/2026-05-01-dynamic-config-team-rulekit-native-message.md`: short shareable message for Dynamic Config backend/SDK/admin UI teams.
- `migration/2026-05-01-rule-kit-sdk-review.md`: SDK readiness review and rollout gates.
- `performance/artifacts/2026-05-02-rulekit-jmh/report.md`: current RuleKit-local JMH benchmark artifact.

## Current Source Layout

- `rule-kit-contract/`: JSON schema and contract vectors.
- `rule-kit-java/rule-kit-core/`: internal source module for canonical model, validation, evaluator, resolvers, traces, rollout, operators, and typed exceptions.
- `rule-kit-java/rule-kit-sdk/`: client-facing bundled Java SDK artifact; shades internal `rule-kit-core` classes into one RuleKit JAR.
- `rule-kit-java/rule-kit-spring-boot-starter/`: optional Spring Boot 3-oriented bundled starter artifact.
- `rule-kit-java/rule-kit-sample-app/`: runnable Spring Boot sample, including YAML RuleSet loading.
- `rule-kit-java/rule-kit-benchmarks/`: JMH benchmark module.

## Current Java Package Structure

These packages are available from the published `rule-kit-sdk` artifact even though many classes live in the internal `rule-kit-core` source module.

- `com.cars24.rulekit.core.model`: canonical RuleSet schema records and enums.
- `com.cars24.rulekit.core.evaluation`: evaluator, compiled runtime, evaluation result/options.
- `com.cars24.rulekit.core.resolver`: host fact, segment, and dependency resolver contracts.
- `com.cars24.rulekit.core.validation`: RuleSet validation API and messages.
- `com.cars24.rulekit.core.trace`: compact/verbose trace DTOs.
- `com.cars24.rulekit.core.operator`: canonical operator metadata and operand parsing helpers.
- `com.cars24.rulekit.core.rollout`: deterministic rollout evaluation.
- `com.cars24.rulekit.core.exception`: typed RuleKit exceptions and error codes.

## Historical Docs

- `research/`: research history and option analysis. These files are not the current implementation contract.
- `migration/2026-05-01-dynamic-config-backend-rule-kit-migration.md`: superseded compatibility-layer migration.
- `migration/2026-05-01-dynamic-config-rule-kit-team-handoff.md`: superseded compatibility-adapter handoff.
- `migration/2026-05-01-lead-service-java-rule-kit-migration.md`: separate Lead Service migration note, not the Dynamic Config v1 plan.
- `superpowers/plans/`: completed or superseded implementation plans. Use the current migration docs instead.

# RuleKit SDK Readiness Review

Date: 2026-05-01
Last updated: 2026-05-02
Audience: RuleKit maintainers and Dynamic Config backend/SDK team

## Verdict

RuleKit is now in the right shape to be the clean v1 execution plane for Dynamic Config, not a temporary bridge around Dynamic Config's own evaluator.

The important boundary is clear:

- RuleKit owns the canonical RuleSet schema, operator semantics, strict validation, compiled evaluation, deterministic rollout bucketing, dependency/segment primitives, typed exceptions, traces, Java SDK APIs, and contract vectors.
- Dynamic Config owns tenants, auth, CRUD, drafts, approvals, persistence, enabled/deleted checks, kill switch, force overrides, segment storage, exposure logs, SDK sync/cache, admin UI, and product release policy.

Because Dynamic Config is also pre-operational, there is no requirement to preserve old Dynamic Config evaluator quirks. Dynamic Config should align with RuleKit v1 semantics from first production launch.

## Review Findings Status

| Finding | Status | Resolution |
| --- | --- | --- |
| P1 operand validation as active-rollout blocker | Closed in RuleKit; still a rollout gate for Dynamic Config writes. | `RuleSetValidator` now validates required operands, numeric operands, `BETWEEN` bounds, regex syntax, rollout shape, segment shape, dependency shape, required `schemaVersion`, and unsupported operators. Invalid expected numeric operands are validation errors. |
| P2 performance numbers need source artifacts | Closed for RuleKit-local benchmarks; Dynamic Config still needs host load artifacts. | This doc no longer uses untraceable Dynamic Config load numbers as evidence. Initial RuleKit-local JMH output is archived at `docs/performance/artifacts/2026-05-02-rulekit-jmh/report.md` with command, environment, scenario matrix, raw output, and workspace revision note. |
| P2 clarify `FactResolver` context ergonomics | Closed in docs and examples. | A simple `FactResolver` lambda receives only `(fieldRef, input)`. A resolver that needs `ruleSetId`, `ruleId`, `conditionIndex`, or the full condition must use `FactResolver.contextual(context -> ...)` or override `resolve(FactResolutionContext)` manually. |

## Production-Ready Capabilities Now In RuleKit

- Java 17-compatible client-facing `rule-kit-sdk` artifact.
- Internal `rule-kit-core` source module shaded into the published SDK JAR, so host applications use one RuleKit dependency.
- Responsibility-based Java packages for model, evaluation, resolver hooks, validation, traces, operators, rollout, and exceptions.
- Canonical `rule-kit.ruleset.v1` model with strict unknown-field rejection through the SDK.
- Required `schemaVersion` validation.
- `FIRST_MATCH` rule execution with `AND` conditions inside a rule.
- Canonical operators only: `EQ`, `NEQ`, `GT`, `GTE`, `LT`, `LTE`, `BETWEEN`, `IN`, `IN_CASE_INSENSITIVE`, `NOT_IN`, `NOT_IN_CASE_INSENSITIVE`, `CONTAINS`, `NOT_CONTAINS`, starts/ends-with variants, regex variants, `EXISTS`, `NOT_EXISTS`, `IS_TRUE`, and `IS_FALSE`.
- Strict operator validation: missing values, missing `valueTo`, invalid numeric expected values, reversed ranges, and invalid regex patterns are validation errors.
- Native operator semantics: invalid or missing numeric actual values are non-matches, equality is type-aware, and negative operators do not implicitly match missing/null actuals.
- `CompiledRuleSet` for compile-once/evaluate-many runtime usage.
- Immutable/deep-copied compiled runtime state with normalized operators and precompiled regex/list/numeric operands.
- `FIELD`, `SEGMENT`, and `DEPENDENCY` condition kinds.
- Host-provided lazy `FactResolver`, with context-aware resolution through `FactResolver.contextual(...)`.
- Host-provided `SegmentResolver` with `ANY` and `ALL` matching.
- Host-provided `RuleSetDependencyResolver` with lazy dependency evaluation, per-evaluation caching, and cycle detection.
- Rule-level deterministic rollout bucketing with `MURMUR3_32_SALTED_V1`.
- Rollout fact resolution through the configured `FactResolver`, including `tenantId`, `configName` or `configId`, `ruleId`, `splitSeed`, and `unitRef`.
- Typed exceptions for validation failures, resolver failures, missing rollout facts, missing segment/dependency resolvers, and dependency cycles.
- Compact and verbose traces, including matched rule ID, default-used status, evaluator/schema version, condition-level details, segment details, dependency details, and rollout bucket details.
- Contract vectors for native rollout, segment, dependency, validation, and operator behavior.
- Spring Boot starter and sample app, including YAML RuleSet loading.

## Dynamic Config Integration Position

Dynamic Config should store RuleKit `RuleSet` as the execution payload and compile/cache it per published config version.

Runtime evaluation should look like:

```java
CompiledRuleSet compiled = compiledRuleSetCache.get(configVersion);

EvaluationResult result = ruleKitClient.evaluate(
        compiled,
        requestFacts,
        TraceMode.COMPACT,
        EvaluationOptions.builder()
                .factResolver(dynamicConfigFactResolver)
                .segmentResolver(dynamicConfigSegmentResolver)
                .dependencyResolver(dynamicConfigDependencyResolver)
                .build()
);
```

Dynamic Config should not keep a second execution path for operators, condition evaluation, percentage split bucketing, segment condition evaluation, or gate recursion after RuleKit-native v1 is wired.

## Active Rollout Gates

These are not missing RuleKit features; they are the gates before Dynamic Config uses RuleKit on active production traffic.

| Priority | Gate | Owner | Evidence Required |
| --- | --- | --- | --- |
| P1 | Validate every authored or migrated RuleSet with RuleKit before publish. | Dynamic Config | API/admin UI tests showing validation errors block publish. |
| P1 | Evaluate only cached `CompiledRuleSet` objects on hot paths. | Dynamic Config | Server/SDK code path review plus tests proving compile is not per request. |
| P1 | Consume RuleKit contract vectors in Dynamic Config server and SDK. | Dynamic Config | CI job or test report for operator, rollout, dependency, segment, and validation vectors. |
| P1 | Archive Dynamic Config end-to-end performance artifacts before active exposure. | Dynamic Config | Load command, environment, raw output, scenario matrix, accuracy preflight, and workspace revision in Dynamic Config docs or a linked artifact store. |
| P1 | Run Dynamic Config shadow/simulation comparison before active exposure. | Dynamic Config | Mismatch rate, latency, resolver error rate, default-used rate, and trace samples per config family. |
| P2 | Add host observability wrappers. | Dynamic Config, with RuleKit docs support | Metrics around latency, validation failures, resolver failures, matched rule ID, default-used status, rollout excluded status, and dependency cycles. |

## FactResolver Ergonomics

Use a simple lambda only when the resolver needs the field name and input payload:

```java
FactResolver resolver = (fieldRef, input) -> {
    if (fieldRef.startsWith("_host.")) {
        return ResolvedFact.found(resolveHostFact(fieldRef, input));
    }
    return FactResolver.defaultResolver().resolve(fieldRef, input);
};
```

Use the contextual helper when the resolver needs rule/evaluation metadata:

```java
FactResolver resolver = FactResolver.contextual(context -> {
    if (context.fieldRef().startsWith("_host.")) {
        return ResolvedFact.found(resolveHostFact(
                context.ruleSetId(),
                context.ruleId(),
                context.conditionIndex(),
                context.fieldRef(),
                context.input()
        ));
    }
    return FactResolver.defaultResolver().resolve(context);
});
```

The contextual path is the one Dynamic Config should use for dependency/gate-style host facts where tenant/config/rule context matters.

## Performance Evidence Policy

Do not cite unlinked load-test numbers in migration decisions.

For RuleKit-local performance evidence, use:

```bash
mvn -pl rule-kit-java/rule-kit-benchmarks -am package -DskipTests
java -cp rule-kit-java/rule-kit-benchmarks/target/rule-kit-benchmarks.jar org.openjdk.jmh.Main
```

The initial RuleKit-local artifact is archived here:

- `docs/performance/artifacts/2026-05-02-rulekit-jmh/report.md`

Future reports must include:

- workspace revision or a note that the workspace is not a git checkout
- Java version
- OS and CPU summary
- exact Maven/JMH commands
- benchmark scenario matrix
- raw JMH output path
- interpretation limits

RuleKit-local benchmark evidence now exists. Dynamic Config still needs an end-to-end host load/shadow artifact before active production exposure.

## What Not To Add To RuleKit

- No database integration.
- No tenant/auth model.
- No Dynamic Config CRUD, drafts, approvals, audit, or persistence.
- No exposure logging or stats aggregation.
- No segment storage or sync transport.
- No SDK cache ownership.
- No kill switch or force override policy.
- No legacy Dynamic Config compatibility mode.

## Recommended Hand-Off Files

Share these with the Dynamic Config team:

- `docs/migration/2026-05-01-dynamic-config-v1-rulekit-native-plan.md`
- `docs/migration/2026-05-01-dynamic-config-team-rulekit-native-message.md`
- `docs/examples/2026-05-01-rule-kit-sdk-examples.md`
- `rule-kit-contract/schemas/ruleset-v1.schema.json`
- `rule-kit-contract/test-vectors/native/`

## Recommended Next Decision

Proceed with RuleKit as the Dynamic Config v1 execution plane. Dynamic Config should now plan the integration work around direct RuleSet storage, compiled cache ownership, RuleKit validation at write/publish time, and removal of duplicated evaluator/split/operator code before first production launch.

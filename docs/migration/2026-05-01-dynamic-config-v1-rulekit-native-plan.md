# Dynamic Config v1 RuleKit-Native Plan

Date: 2026-05-01
Last updated: 2026-05-02
Audience: RuleKit maintainers and Dynamic Config backend/SDK/admin UI teams
Status: Dynamic Config integration implemented; production-readiness verification completed locally

## 2026-05-02 Dynamic Config Integration Update

Dynamic Config has moved to the RuleKit-native v1 shape in its backend, Java SDK, load benchmark seeding, admin authoring flow, React SDK types, and current documentation.

Implemented integration points:

- `ruleSet` is the execution payload on config definition/entity/log records.
- Create/update require RuleKit validation and Dynamic Config host-reference validation for segments and dependencies.
- Server evaluation uses RuleKit compile/cache/evaluate with Dynamic Config resolvers.
- Java SDK local evaluation uses RuleKit for config-only RuleSets and remote fallback for segment-dependent RuleSets.
- Dynamic Config old evaluator/operator/split classes are removed from production code.
- Admin create/edit writes RuleKit-native `ruleSet`; any `conditions` shape in the UI is projection-only.
- Scheduled percentage rollouts are not part of Dynamic Config v1 live API because RuleKit rollout is the production rollout primitive.
- Production-readiness review added indirect dependency-cycle validation, disabled dependency default-used behavior, and missing local dependency remote fallback.
- Latest Dynamic Config load baseline and comparison overview are documented in `/Users/user/Desktop/cars24/projects/dynamic-config/docs/rulekit-native-v1-production-readiness.md`.

## Executive Recommendation

Dynamic Config v1 should use RuleKit as its execution plane from the first production launch.

Dynamic Config should store RuleKit's canonical `RuleSet` as the execution payload, validate it with RuleKit before publish, compile/cache it per published config version, and evaluate it with RuleKit in both server and SDK paths.

This is not a compatibility migration. Dynamic Config is also pre-operational, so we should not preserve old Dynamic Config evaluator quirks such as invalid numeric values coercing to `0.0` or duplicated string/boolean equality behavior. RuleKit v1 defines the semantics; Dynamic Config aligns with them.

## Ownership Split

RuleKit owns:

- Canonical RuleSet schema and Java model.
- Operator semantics and validation.
- Rule ordering, first-match semantics, AND conditions inside a rule, and default response fallback.
- Deterministic rollout bucketing as a generic rule-level primitive.
- Segment condition semantics, with host-provided membership resolution.
- Dependency/gate condition semantics, with host-provided RuleSet resolution.
- Compile-once immutable runtime representation.
- Structured validation errors and typed evaluation exceptions.
- Compact and verbose traces.
- Contract vectors for Dynamic Config server and SDK compliance.

Dynamic Config owns:

- Tenant scoping and auth.
- CRUD APIs, drafts, approvals, persistence, audit/history, and config versioning.
- Enabled/deleted config checks.
- Kill switch and force enable/disable overrides.
- Segment definitions, membership storage, and segment sync APIs.
- Publication workflow and any scheduled rollout business policy.
- Exposure logging and stats.
- SDK sync/cache and remote/local evaluation policy.
- Admin UI workflows.
- Product API contracts.

## RuleKit Capability Status

| Capability | RuleKit Status | Dynamic Config Action |
| --- | --- | --- |
| Canonical RuleSet schema | Implemented as `rule-kit.ruleset.v1` with strict SDK deserialization and JSON schema. | Store this payload directly as `ruleSet`. |
| Operator coverage | Implemented for equality, numeric comparison, `IN`/`NOT_IN`, contains, starts/ends-with, regex, exists/null, and boolean checks. | Remove duplicated Dynamic Config operator evaluator from native path. |
| Strict validation | Implemented for required fields, operands, numeric expected values, `BETWEEN` bounds, regex syntax, rollout, segment, dependency, schema version, and unsupported operators. | Block create/update/publish when RuleKit validation fails. |
| Compiled evaluation | Implemented through immutable `CompiledRuleSet`. | Compile once per config version and cache. Do not compile per request. |
| Rollout bucketing | Implemented with `MURMUR3_32_SALTED_V1`. | Remove server/SDK split evaluators once the native path is wired. |
| Segment hooks | Implemented with `SEGMENT` condition, `ANY`/`ALL`, `lookupRef`, and `SegmentResolver`. | Keep segment storage in Dynamic Config and expose membership through resolver. |
| Dependency/gate hooks | Implemented with `DEPENDENCY` condition, `RuleSetDependencyResolver`, lazy evaluation, cache, and cycle detection. | Resolve referenced active configs through Dynamic Config cache/persistence. |
| Traces | Implemented for compact and verbose evaluation, including condition, segment, dependency, and rollout details. | Surface verbose traces in admin simulation/debugging only. |
| Typed exceptions | Implemented for validation, JSON/input issues, resolver failures, rollout fact misses, missing resolvers, and cycles. | Map to product API/admin UI errors. |
| Java 17 support | Implemented for the bundled SDK bytecode. | Java 17 and Java 21 Dynamic Config runtimes can consume the SDK. |
| Sample/YAML loading | Implemented in sample app and tests. | Reuse examples for onboarding and integration tests. |

## Maven Dependency

Dynamic Config should consume one RuleKit dependency:

```xml
<dependency>
  <groupId>com.cars24.rulekit</groupId>
  <artifactId>rule-kit-sdk</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

`rule-kit-sdk` is the client-facing bundled artifact. It includes RuleKit's internal `rule-kit-core` classes in the SDK JAR, so Dynamic Config should not add a separate `rule-kit-core` dependency.

## Java Package Map

Dynamic Config should import RuleKit APIs from responsibility-based packages:

- `com.cars24.rulekit.core.model`: `RuleSet` and canonical schema DTOs.
- `com.cars24.rulekit.core.evaluation`: `CompiledRuleSet`, `EvaluationOptions`, `EvaluationResult`.
- `com.cars24.rulekit.core.resolver`: `FactResolver`, `SegmentResolver`, `RuleSetDependencyResolver`, and resolver context/result records.
- `com.cars24.rulekit.core.validation`: `RuleSetValidator`, `ValidationResult`, `ValidationMessage`.
- `com.cars24.rulekit.core.trace`: `TraceMode` and trace DTOs.
- `com.cars24.rulekit.core.exception`: `RuleKitExceptionCode` and typed exceptions.

## Canonical RuleSet Shape

Dynamic Config should keep product metadata outside the RuleKit payload and store `ruleSet` as the execution source of truth.

```json
{
  "tenantId": "tenant-a",
  "name": "checkout-banner",
  "enabled": true,
  "deleted": false,
  "killed": false,
  "forceEnabledUserIds": ["qa-1"],
  "forceDisabledUserIds": [],
  "requireApproval": true,
  "ruleSet": {
    "id": "tenant-a.checkout-banner",
    "schemaVersion": "rule-kit.ruleset.v1",
    "executionMode": "FIRST_MATCH",
    "defaultResponse": { "enabled": false },
    "rules": [
      {
        "id": "android-beta-users",
        "priority": 100,
        "enabled": true,
        "when": {
          "all": [
            { "kind": "FIELD", "fieldRef": "platform", "operator": "EQ", "value": "android" },
            { "kind": "SEGMENT", "segmentNames": ["beta-users", "internal-users"], "match": "ANY", "lookupRef": "userId" },
            { "kind": "DEPENDENCY", "ruleSetId": "tenant-a.checkout-enabled", "expect": "MATCHED" }
          ]
        },
        "rollout": {
          "percentage": 25,
          "unitRef": "userId",
          "algorithm": "MURMUR3_32_SALTED_V1",
          "bucketCount": 100,
          "saltRefs": ["tenantId", "configName", "ruleId", "splitSeed"]
        },
        "then": {
          "response": { "enabled": true, "variant": "A" }
        }
      }
    ]
  }
}
```

Dynamic Config should not keep separate executable versions of `conditions`, `operations`, `percentageSplit`, `targetSegments`, or `segmentGate`. If those fields still exist temporarily for UI/API convenience before launch, treat them as projections that are converted to/from `ruleSet`, not as a second evaluator model.

## Operator Semantics

RuleKit v1 is strict and predictable:

| Case | RuleKit v1 Behavior |
| --- | --- |
| Missing required `value` | Validation error. |
| Missing `valueTo` for `BETWEEN` | Validation error. |
| Invalid regex | Validation error. |
| Invalid numeric expected value | Validation error. |
| `BETWEEN` lower bound greater than upper bound | Validation error. |
| Invalid numeric actual value | Condition evaluates false. |
| Missing numeric actual value | Condition evaluates false. |
| `EQ` with two numbers | Numeric comparison. |
| `EQ` with booleans | Boolean comparison only when both sides are booleans. |
| String `"true"` vs boolean `true` | Not equal. |
| `IN` expected array | Matches against array values. |
| Missing/null actual with `NOT_*` operators | Does not match unless the operator is explicitly `NOT_EXISTS`. |

Dynamic Config should not reintroduce legacy coercion in an adapter. If old DTOs exist during development, normalize them into RuleKit's canonical operators and let RuleKit validation decide.

## Rollout Contract

RuleKit runs rollout after all `when.all` conditions pass and before returning the rule response.

```json
{
  "rollout": {
    "percentage": 25,
    "unitRef": "userId",
    "algorithm": "MURMUR3_32_SALTED_V1",
    "bucketCount": 100,
    "saltRefs": ["tenantId", "configName", "ruleId", "splitSeed"]
  }
}
```

Default Dynamic Config input should include:

- `tenantId`
- `configName` or `configId`
- `splitSeed`
- the rollout unit referenced by `unitRef`, usually `userId`

RuleKit automatically includes `ruleId` while computing the bucket input. Missing rollout unit or salt values fail with typed RuleKit exceptions rather than silently falling back to empty strings.

## Segment Contract

RuleKit owns segment condition semantics; Dynamic Config owns storage.

```json
{
  "kind": "SEGMENT",
  "segmentNames": ["beta-users", "vip-users"],
  "match": "ALL",
  "lookupRef": "userId"
}
```

Dynamic Config implements `SegmentResolver`:

```java
SegmentResolver resolver = context -> {
    Map<String, Boolean> membership = segmentService.membership(
            context.segmentNames(),
            context.lookupValue().orElseThrow()
    );
    return SegmentMembershipResult.of(membership);
};
```

Important behavior:

- The resolver is called only if the segment condition is reached.
- `ANY` matches when at least one named segment is true.
- `ALL` matches only when every named segment is true.
- Missing lookup value is a non-match.
- Verbose trace contains segment names, match type, lookup status, and matched result.

## Dependency/Gate Contract

RuleKit owns generic dependency evaluation and cycle detection; Dynamic Config owns active config lookup.

```json
{
  "kind": "DEPENDENCY",
  "ruleSetId": "tenant-a.checkout-enabled",
  "expect": "MATCHED"
}
```

Dynamic Config implements `RuleSetDependencyResolver`:

```java
RuleSetDependencyResolver resolver = context ->
        dynamicConfigCache.findCompiledRuleSet(context.ruleSetId());
```

Important behavior:

- Dependency resolution is lazy and only happens if the condition is reached.
- Dependency outcomes are cached within a single evaluation session.
- Cycles such as `A -> B -> A` fail with `DEPENDENCY_CYCLE_DETECTED`.
- Verbose trace includes dependency ruleSet ID, expectation, matched/default status, and cycle/resolver failures.

## FactResolver Contract

Use a simple `FactResolver` lambda only when field and input are enough:

```java
FactResolver resolver = (fieldRef, input) ->
        FactResolver.defaultResolver().resolve(fieldRef, input);
```

Use `FactResolver.contextual(...)` when Dynamic Config needs rule metadata:

```java
FactResolver resolver = FactResolver.contextual(context -> {
    if (context.fieldRef().startsWith("_dynamic.")) {
        return dynamicFactService.resolve(
                context.ruleSetId(),
                context.ruleId(),
                context.conditionIndex(),
                context.fieldRef(),
                context.input()
        );
    }
    return FactResolver.defaultResolver().resolve(context);
});
```

Rollout salt values and `unitRef` values are also resolved through the configured `FactResolver`, so Dynamic Config can provide literal input fields, escaped dotted keys, or host-resolved facts consistently.

## Server Integration Plan

1. Add `ruleSet` as the execution payload in the Dynamic Config entity/DTO.
2. On create/update/draft publish, deserialize and validate with `RuleKitClient.validate(ruleSet)`.
3. Map validation errors directly to API/admin UI error responses with `code`, `path`, and `message`.
4. On publish, compile with `RuleKitClient.compile(ruleSet)`.
5. Cache `CompiledRuleSet` by tenant, config name/ID, and published version.
6. In request evaluation, run product guards first: tenant/auth, enabled/deleted, kill switch, force overrides.
7. Build the RuleKit input facts: tenant/config metadata, user/entity facts, request attributes, and split seed.
8. Evaluate the cached compiled RuleSet with `EvaluationOptions`.
9. Apply exposure logging/stats outside RuleKit after the product decision is known.
10. Use verbose trace only for simulation/admin/debug paths.

Example:

```java
EvaluationOptions options = EvaluationOptions.builder()
        .factResolver(dynamicConfigFactResolver)
        .segmentResolver(dynamicConfigSegmentResolver)
        .dependencyResolver(dynamicConfigDependencyResolver)
        .build();

EvaluationResult result = ruleKitClient.evaluate(
        compiledRuleSet,
        requestFacts,
        TraceMode.COMPACT,
        options
);
```

## SDK Integration Plan

Dynamic Config SDK should use the same RuleKit evaluation behavior as the server.

1. Sync RuleKit RuleSet payloads and version metadata to the SDK cache.
2. Compile RuleSets when cache entries are loaded or updated.
3. Evaluate compiled RuleSets locally with the same RuleKit SDK.
4. Use the same rollout inputs as the server: tenant/config/rule/seed/entity.
5. Implement local segment resolver only when segment membership is available locally.
6. Fall back to remote/server evaluation for segment-dependent configs if local segment membership is unavailable.
7. Keep exposure/log flushing as Dynamic Config SDK behavior, not RuleKit behavior.

## Admin UI/API Plan

Dynamic Config admin UI should author RuleKit-native RuleSets.

Recommended UI model:

- Config metadata form remains Dynamic Config-owned.
- Rule editor writes `ruleSet.rules`.
- Rule rows remain OR/first-match ordered by priority.
- Conditions inside a rule are AND-only.
- Field conditions use canonical RuleKit operators.
- Segment conditions use `SEGMENT` with `ANY`/`ALL`.
- Gate/dependency conditions use `DEPENDENCY`.
- Rollout is a rule-level section, not a condition.
- Draft save may store invalid drafts, but publish must run RuleKit validation and block on errors.
- Simulation should request verbose traces and show RuleKit validation/evaluation details.

## Contract Test Plan

Dynamic Config should consume:

- `rule-kit-contract/schemas/ruleset-v1.schema.json`
- `rule-kit-contract/test-vectors/native/`
- existing operator mapping/reference vectors under `rule-kit-contract/dynamic-config/` only as migration reference, not as legacy semantics to preserve

Dynamic Config CI should prove:

- Server and SDK both parse the canonical schema.
- Server and SDK both pass RuleKit native operator vectors.
- Rollout buckets match RuleKit vectors.
- Segment resolver behavior matches RuleKit vectors.
- Dependency resolver behavior and cycle detection match RuleKit vectors.
- Validation errors are surfaced with structured `code`, `path`, and `message`.

## Performance And Observability Plan

RuleKit provides compiled evaluation; Dynamic Config must use it correctly.

Performance requirements:

- No per-request RuleSet compilation.
- No eager dependency/gate loading before RuleKit reaches the dependency condition.
- No eager segment lookup before RuleKit reaches the segment condition.
- Compact trace by default on hot paths.
- Verbose trace only for simulation/debugging.

Metrics Dynamic Config should emit around RuleKit:

- evaluation latency
- matched rule ID
- default-used count
- rollout included/excluded count
- validation failure count by code
- resolver failure count by code
- dependency cycle count
- segment resolver latency
- dependency resolver latency

Initial RuleKit-local benchmark evidence is archived at `docs/performance/artifacts/2026-05-02-rulekit-jmh/report.md`. Dynamic Config must still archive end-to-end host performance evidence with command, environment, raw output, scenario matrix, accuracy preflight, and workspace revision before using numbers as an active rollout decision.

## Cutover Plan

Phase 1: RuleKit-native storage and validation

- Add `ruleSet` payload.
- Validate on create/update/publish.
- Keep draft/product metadata outside RuleKit.
- Add admin UI/API error handling for RuleKit validation messages.

Phase 2: RuleKit compiled cache

- Compile on publish/cache refresh.
- Cache by tenant/config/version.
- Add eviction on publish/delete/disable.
- Add server tests proving evaluation does not compile per request.

Phase 3: RuleKit execution path

- Replace condition/operator evaluator with RuleKit evaluation.
- Replace split evaluator with RuleKit rollout.
- Replace gate recursion with RuleKit dependency resolver.
- Replace segment condition checks with RuleKit segment resolver.
- Keep kill switch, enabled/deleted checks, force overrides, and exposure logs outside RuleKit.

Phase 4: SDK alignment

- Sync RuleKit RuleSets.
- Compile locally.
- Use same rollout inputs.
- Add remote fallback for unavailable local segment data.
- Run server/SDK contract vectors.

Phase 5: Remove duplicate execution code

- Remove/deprecate Dynamic Config `Operation`.
- Remove/deprecate Dynamic Config condition evaluator.
- Remove/deprecate server split execution path.
- Remove/deprecate SDK split evaluator.
- Remove/deprecate gate recursion evaluator.
- Keep only product-control code outside RuleKit.

## Risks And Decisions For Dynamic Config

- Segment local evaluation: decide whether SDK must sync segment membership or call server for segment-dependent configs.
- Dependency exposure logging: decide whether referenced config evaluations create separate exposure events.
- Draft invalidity: decide if drafts may be invalid while publish remains strictly blocked.
- API projection: decide whether older authoring DTOs survive before launch or the UI writes RuleKit RuleSets directly.
- Rollout seed policy: choose and freeze `splitSeed` lifecycle before production.
- Benchmark gate: RuleKit-local evidence exists; Dynamic Config still needs end-to-end server/SDK performance reports before active traffic.

## Message To Pass To Dynamic Config Team

> Dynamic Config v1 should use RuleKit as the execution-plane source of truth. Dynamic Config should store a RuleKit `RuleSet` as the execution payload, validate it with RuleKit before publish, compile/cache it per config version, and evaluate it through RuleKit in server and SDK paths. RuleKit owns generic execution: schema, operators, strict validation, rollout bucketing, dependency primitives, segment primitives, traces, typed exceptions, and contract vectors. Dynamic Config owns product control: tenants/auth, CRUD, drafts, approvals, persistence, enabled/deleted checks, kill switch, force overrides, segment storage, exposure logs, SDK sync/cache, admin UI, and product APIs. Because Dynamic Config is pre-operational, do not preserve old evaluator quirks or keep a parallel execution engine.

## Files To Share

- `docs/migration/2026-05-01-dynamic-config-v1-rulekit-native-plan.md`
- `docs/migration/2026-05-01-dynamic-config-team-rulekit-native-message.md`
- `docs/migration/2026-05-01-rule-kit-sdk-review.md`
- `docs/examples/2026-05-01-rule-kit-sdk-examples.md`
- `rule-kit-contract/schemas/ruleset-v1.schema.json`
- `rule-kit-contract/test-vectors/native/`

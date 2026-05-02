# Message For Dynamic Config Team: RuleKit-Native v1 Ownership Split

Date: 2026-05-01
Last updated: 2026-05-02
Audience: Dynamic Config backend, SDK, and admin UI teams

## Shareable Message

We are moving toward a RuleKit-native Dynamic Config v1. The target is that Dynamic Config should not maintain its own rule engine, operator evaluator, condition evaluator, percentage split evaluator, or duplicated rule validation. RuleKit will become the generic execution plane, and Dynamic Config will remain the product control plane.

RuleKit will handle:

- Canonical RuleSet schema and Java models.
- Operator semantics and validation.
- Compile-once, evaluate-many execution.
- First-match rule evaluation and AND conditions inside each rule.
- RuleKit-native deterministic rollout bucketing using tenant/config/rule/seed/entity inputs.
- Dependency/gate primitives with lazy host resolution and cycle detection.
- Segment condition primitives with host-provided segment membership resolution and ANY/ALL behavior.
- Structured validation errors for API/admin UI usage.
- Compact and verbose traces for debugging and shadow mismatch analysis.
- Contract vectors for operators, rollouts, dependencies, segments, validation, and traces.
- Java SDK and Spring Boot integration surface.
- A single client-facing `rule-kit-sdk` dependency that bundles RuleKit's internal core module.

Dynamic Config will continue to handle:

- Tenant scoping, auth, permissions, and client access.
- CRUD APIs, persistence, drafts, approvals, audit/history, and config versioning.
- Enabled/deleted config checks.
- Kill switch, force-enabled users, and force-disabled users.
- Segment definition and membership storage.
- Exposure logs, evaluation stats, and observability.
- SDK sync/cache and remote/local evaluation policy.
- Admin UI workflows.
- Any one-time authoring/backfill work needed to store RuleKit RuleSets directly.

Important migration guardrail:

Dynamic Config is also pre-operational, so we do not need a legacy compatibility mode for old Dynamic Config quirks. Dynamic Config should store RuleKit RuleSets as the execution payload from v1, compile/cache them per config version, and remove duplicated operator/evaluator/split logic once the app is wired to RuleKit.

Rollout contract:

RuleKit v1 rollout input should include `tenantId`, `configName` or `configId`, `ruleId`, `splitSeed`, and the entity ID resolved from `unitRef` such as `userId`. Dynamic Config should align with this from its first release rather than preserving older bucket inputs.

## Ask From Dynamic Config Team

- Confirm that the target storage model can add a `ruleSet` execution payload beside current product metadata.
- Confirm that Dynamic Config can drop duplicated `Operation`, condition evaluator, split evaluator, and validation code before first production launch.
- Provide representative pre-production configs for operator, rollout, gate, and segment contract vectors.
- Confirm local SDK behavior for segment-dependent configs when segment membership is not available locally.
- Confirm whether referenced dependency/gate config exposure should be logged separately or only through the top-level evaluation.

## Files To Use

- `docs/migration/2026-05-01-dynamic-config-v1-rulekit-native-plan.md`
- `docs/migration/2026-05-01-rule-kit-sdk-review.md`
- `docs/examples/2026-05-01-rule-kit-sdk-examples.md`
- `rule-kit-contract/schemas/ruleset-v1.schema.json`
- `rule-kit-contract/test-vectors/native/`
- `docs/performance/artifacts/2026-05-02-rulekit-jmh/report.md`

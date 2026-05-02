# Rule Kit Research Log

Date: 2026-04-20
Status: Historical research log; not the current implementation contract
Scope: Research only. No implementation or UI mockups.

> Current-state note, 2026-05-02: this log captures early discovery. Use `../README.md`, `../examples/2026-05-01-rule-kit-sdk-examples.md`, and `../migration/2026-05-01-dynamic-config-v1-rulekit-native-plan.md` for the current SDK structure and Dynamic Config integration plan.

## Goal

Design an embeddable `rule-kit` that has:

- a backend component that can be integrated into a host application
- a frontend component that can be embedded into a host application for rule configuration
- a highly configurable rule model
- rule evaluation where:
  - rules are checked in OR order
  - conditions inside a rule are checked in AND order
  - the first satisfied rule determines the returned response
- rule responses that may be any payload shape, including number, string, JSON, or arbitrary structured data
- condition definitions that depend on key, data type, and operator semantics
- database-agnostic SDK design so the host application can persist in MongoDB, PostgreSQL, or another store

## Requirements Extracted From User Input

These are directly extracted from the request and should not be treated as assumptions:

1. The system must be embeddable into another application on both frontend and backend sides.
2. The frontend is for configuring rules rather than only viewing them.
3. Rules are evaluated with OR semantics across rules.
4. Conditions are evaluated with AND semantics within a rule.
5. Rule result selection is first-match semantics based on satisfied rule order.
6. Operators depend on the field type or value type.
7. Example string operators include `startsWith`, `endsWith`, `isNull`, `isNotNull`, and regex-like matching.
8. Example numeric operators include `greaterThan`, `lessThan`, `range`, and similar comparisons.
9. The response payload of a matched rule must support multiple data shapes.
10. The SDK must not depend on a database; host applications should remain free to persist however they choose.
11. Frontend preview and simulation must use the exact same evaluation semantics as backend execution.

## Architecture Questions Being Researched

### Backend

- What should the canonical rule AST look like?
- Should the evaluator use a custom interpreter, an existing expression engine, or a compiled policy language?
- How should typed operators be registered and versioned?
- How should arbitrary response payloads be validated without losing flexibility?
- How should rule evaluation be embedded into host backends:
  - library
  - framework module
  - sidecar service
  - central shared service
- How should auditability, rule versioning, and rollout work?

### Frontend

- What contract should exist between the frontend builder and backend evaluator?
- How should keys, data types, and available operators be discovered from the host app?
- How should arbitrary JSON responses be edited safely?
- How should validation prevent invalid operator or value combinations?
- How should the rule builder stay embeddable without coupling to one UI stack?

### Storage and Integration

- What is the canonical persistence model independent of MongoDB or Postgres?
- Which parts should be stored as structured fields versus opaque JSON documents?
- How should indexing and lookup work for active rules, versions, tenants, and environments?
- How should the system remain portable across host applications with different storage choices?

## Local Research Tracks Started

### Track 1: Internal precedent search

Current status:

- The original `asino` reference was corrected by the user to `assigno`.
- `Assigno` has been found locally under `/Users/user/Desktop/cars24/projects/c24-assigno-service`.
- The strongest internal precedents found so far are:
  - `c24-bff-service`
  - `bff-studio`
  - `c24-assigno-service`

### Track 2: Parallel external architecture research

Sub-agents launched for:

- backend rule-engine architecture options
- frontend rule-builder architecture options
- persistence and host-integration architecture options
- internal CARS24 precedent analysis

Their findings will be folded into this document when they return.

## First Internal Findings

### 1. `c24-bff-service` expression language

Relevant files:

- `/Users/user/Desktop/cars24/projects/c24-bff-service/CLAUDE.md`
- `/Users/user/Desktop/cars24/projects/c24-bff-service/internal/bff/CLAUDE.md`
- `/Users/user/Desktop/cars24/projects/c24-bff-service/internal/bff/expr/resolver.go`
- `/Users/user/Desktop/cars24/projects/c24-bff-service/internal/bff/expr/cache.go`

What looks relevant:

- It already uses an expression language layer over runtime data.
- It exposes typed helper functions for arrays, strings, time, types, and map handling.
- It has caching for compiled expressions.
- It documents environment inputs, expression evaluation, and conditional execution clearly.

Why it matters:

- It is a strong internal precedent for separating:
  - a config model
  - a runtime evaluator
  - a function/operator registry
  - compiled expression caching

Initial fit assessment:

- Good fit as inspiration for evaluator internals and operator/function registration.
- Partial fit for rule-kit because it is expression-centric and request orchestration-oriented, not explicitly first-match rule evaluation.

### 2. `bff-studio` plan schema and compiler separation

Relevant files:

- `/Users/user/Desktop/cars24/projects/bff-studio/libs/plan-schema/src/main/java/com/c24/bffstudio/plan/schema/ConditionNodeDefinition.java`
- `/Users/user/Desktop/cars24/projects/bff-studio/libs/plan-schema/src/main/java/com/c24/bffstudio/plan/schema/BindingExpression.java`
- `/Users/user/Desktop/cars24/projects/bff-studio/libs/plan-compiler/src/main/java/com/c24/bffstudio/plan/compiler/StandardPlanValidator.java`
- `/Users/user/Desktop/cars24/projects/bff-studio/libs/plan-compiler/src/main/java/com/c24/bffstudio/plan/compiler/Sha256PlanFingerprintCalculator.java`
- `/Users/user/Desktop/cars24/projects/bff-studio/libs/persistence-spi/README.md`
- `/Users/user/Desktop/cars24/projects/bff-studio/libs/persistence-mongodb/README.md`

What looks relevant:

- Clear separation between:
  - schema
  - compiler/validator
  - persistence SPI
  - Mongo adapter
- Condition-like nodes are modeled explicitly instead of being mixed into persistence details.
- Fingerprinting is built in for version identity and reproducibility.
- Persistence is storage-agnostic at the SPI layer.

Why it matters:

- This is a strong internal precedent for keeping rule-kit domain contracts independent from the database implementation.
- The fingerprinting approach may be directly reusable for immutable rule revisions.

Initial fit assessment:

- Strong fit for domain/persistence boundaries and versioning.
- Moderate fit for runtime rule evaluation because this project models flow graphs rather than ordered first-match rules.

### 3. `action-orchestration-engine` as an embeddable config-driven SDK precedent

Relevant files:

- `/Users/user/Desktop/cars24/projects/action-orchestration-engine/README.md`
- `/Users/user/Desktop/cars24/projects/action-orchestration-engine/docs/architecture.md`
- `/Users/user/Desktop/cars24/projects/action-orchestration-engine/orchestrator/src/main/java/com/c24/orchestrator/spring/OrchestratorProperties.java`
- `/Users/user/Desktop/cars24/projects/action-orchestration-engine/apps/sample-bu-app/src/main/resources/application.yml`

What looks relevant:

- It is explicitly designed as an SDK embedded into host services.
- It keeps framework-free core code separate from Spring integration.
- It models conditional configuration as data, including expulsion rules and payload conditions.
- It has tenant-aware and BU-aware configuration services.
- It shows how a sample app can wire host config into an embeddable runtime.

Why it matters:

- This is a strong precedent for packaging `rule-kit` as:
  - framework-agnostic core
  - optional host-framework adapters
  - sample integration app
- It also suggests a clean separation between:
  - core evaluator
  - storage adapters
  - host auto-configuration

Initial fit assessment:

- Strong fit for embeddable SDK architecture and host-app integration boundaries.
- Partial fit for evaluator semantics because its primary model is state orchestration and expulsion logic, not first-match rule return semantics.

### 4. `bff-studio` control-plane UI as a local condition-editor precedent

Relevant files:

- `/Users/user/Desktop/cars24/projects/bff-studio/apps/control-plane-ui/src/features/products/ProductsCanvasPage.tsx`
- `/Users/user/Desktop/cars24/projects/bff-studio/apps/control-plane-ui/src/features/products/ProductsCanvasPage.test.tsx`
- `/Users/user/Desktop/cars24/projects/bff-studio/apps/control-plane-ui/src/features/products/productsCanvasModel.ts`

What looks relevant:

- The UI already models a condition node with:
  - branch count
  - evaluation mode
  - branch aliases
  - per-branch condition expressions
  - fallback behavior
- The inspector copy explicitly distinguishes:
  - first-match evaluation
  - multi-match evaluation
- Tests validate operator-facing interactions around:
  - naming branch outputs
  - editing branch conditions
  - switching evaluation mode
- The same workspace also keeps response contract authoring and compatibility feedback close to the operator flow.

Why it matters:

- This is the closest local precedent for how your team already exposes conditional configuration to operators.
- It supports the idea that a rule-kit UI should keep:
  - condition authoring
  - branch semantics
  - contract validation
  - simulation or compatibility feedback
  close together instead of scattering them across disconnected screens.

Initial fit assessment:

- Strong fit for operator UX patterns and validation copy.
- Partial fit for direct reuse because it is canvas-oriented and route-flow-centric rather than a dedicated rule-list product.

### 5. `Assigno` as a rule-catalog and condition-builder precedent

Relevant files:

- `/Users/user/Desktop/cars24/projects/c24-assigno-service/README.md`
- `/Users/user/Desktop/cars24/projects/c24-assigno-service/docs/latest/CONSTRAINTS.md`
- `/Users/user/Desktop/cars24/projects/c24-assigno-service/docs/latest/API_REFERENCE.md`
- `/Users/user/Desktop/cars24/projects/c24-assigno-service/assigno-api/src/main/java/com/cars24/assigno/api/service/RuleEvaluationService.java`
- `/Users/user/Desktop/cars24/projects/c24-assigno-service/assigno-api/src/main/java/com/cars24/assigno/api/service/GroupResolverService.java`
- `/Users/user/Desktop/cars24/projects/c24-assigno-service/assigno-frontend/src/pages/rules/RuleBuilder.tsx`
- `/Users/user/Desktop/cars24/projects/c24-assigno-service/assigno-frontend/src/components/rules/ConditionBuilder.tsx`
- `/Users/user/Desktop/cars24/projects/c24-assigno-service/assigno-frontend/src/lib/constraintTemplates.ts`

What looks relevant:

- It has a real backend `RuleEvaluationService`.
- It supports nested AND and OR condition evaluation.
- It has first-match-style group resolution in `GroupResolverService`.
- It has a frontend rule builder and typed condition builder.
- It has a typed constraint catalog with parameter schemas and category-driven authoring.

Why it matters:

- This is the closest local example of backend condition evaluation and frontend rule authoring living in the same product.
- It is especially useful as a precedent for:
  - operator registries
  - schema-driven parameter editing
  - priority-ordered rule matching

What does not fit:

- Assigno is optimization-driven and solver-centric.
- Its core model is tenant-specific constraint solving through OptaPlanner, not first-match response return.

Initial fit assessment:

- Strong fit for registry-driven authoring and condition-builder UX patterns.
- Moderate fit for the final runtime architecture of a simpler rule-kit.

Example:

- Backend:
  - `RuleEvaluationService` evaluates typed conditions against a task or user context.
- Frontend:
  - `ConditionBuilder` offers operators by field type such as `string`, `number`, `boolean`, and `array`.
- Catalog:
  - templates such as `skill_match`, `max_capacity`, and `custom_expression` show how a typed operator or constraint registry can be exposed in the UI.

### 6. `dynamic-config` as a shared-evaluator and config-distribution precedent

Relevant files:

- `/Users/user/Desktop/cars24/projects/dynamic-config/README.md`
- `/Users/user/Desktop/cars24/projects/dynamic-config/ARCHITECTURE.md`
- `/Users/user/Desktop/cars24/projects/dynamic-config/TECHNICAL_DOCUMENTATION.md`
- `/Users/user/Desktop/cars24/projects/dynamic-config/dynamic-config-sdk/src/main/java/com/cars24/dynamicconfig/sdk/evaluator/LocalConfigEvaluator.java`
- `/Users/user/Desktop/cars24/projects/dynamic-config/dynamic-config-core/src/main/java/com/cars24/dynamicconfig/core/evaluator/ConfigEvaluationEngine.java`
- `/Users/user/Desktop/cars24/projects/dynamic-config/dynamic-config-core/src/main/java/com/cars24/dynamicconfig/core/evaluator/ConditionOperatorEvaluator.java`
- `/Users/user/Desktop/cars24/projects/dynamic-config/dynamic-config-sdk/src/main/java/com/cars24/dynamicconfig/sdk/provider/CachingConfigProvider.java`

What looks relevant:

- It has an explicit `core`, `sdk`, `starter`, `server`, and React frontend split.
- The docs explicitly describe `ConfigEvaluationEngine` as the single source of truth for evaluation logic.
- The Java SDK evaluates configs locally using the same core evaluation engine as the server-side implementation.
- It has:
  - local cached evaluation
  - config versioning
  - audit logs
  - approval workflow
  - rollback history

Why it matters:

- This is one of the strongest local precedents for:
  - shared evaluation core
  - Java SDK packaging
  - Spring Boot starter packaging
  - config lifecycle management
- It directly supports the architecture pattern we may want for rule-kit:
  - shared core evaluator
  - Java SDK
  - optional server
  - React-side consumption patterns

What does not fit:

- Its domain is feature flags and dynamic configuration, not ordered first-match response rules.
- It uses ordered conditions and rollout semantics rather than the exact rule-kit authoring model.

Initial fit assessment:

- Strong fit for packaging and distribution architecture.
- Moderate fit for the final rule domain model.

Example:

- `LocalConfigEvaluator` delegates to `ConfigEvaluationEngine`, so the SDK and server do not drift in semantics.
- `DynamicConfigLogEntity` keeps immutable version history for rollback.
- `CachingConfigProvider` shows how a Java SDK can evaluate locally after fetching definitions from a server.

## External Source Findings (Initial)

### 1. CEL as a serious candidate for safe, typed evaluation

Sources:

- `https://cel.dev/overview/cel-overview`
- `https://github.com/google/cel-spec`

Relevant points from the official overview:

- CEL is designed to be fast, portable, and safe to execute.
- The recommended pattern is parse and check at configuration time, then evaluate repeatedly at runtime.
- CEL has a typed-checking story and AST-first lifecycle.

Why it matters:

- This matches a likely rule-kit lifecycle:
  - author rule in UI
  - validate and compile at save time
  - evaluate quickly at runtime

Initial assessment:

- Strong candidate if we want a typed expression language with compile-time validation.
- Better suited for evaluator internals than for direct exposure to most business users unless wrapped by a higher-level UI model.

### 2. JsonLogic as a portable JSON AST format

Sources:

- `https://jsonlogic.com/`
- `https://jsonlogic.com/operations.html`

Relevant points from the official docs:

- JsonLogic is effectively an AST encoded as JSON.
- It supports cross-language interpretation.
- It standardizes truthiness rules for consistent execution across runtimes.

Why it matters:

- JsonLogic aligns well with a frontend-to-backend transport format for conditions.
- It is easier to serialize than a free-form expression string.

Initial assessment:

- Strong candidate for portable condition serialization.
- Potential downside is weaker ergonomics for authoring directly and more limited typing discipline than CEL unless wrapped in a richer schema.

### 3. JSONata as a stronger transformation language than a rule language

Source:

- `https://docs.jsonata.org/overview.html`

Why it matters:

- JSONata is very good for transforming JSON payloads.
- It may be useful for advanced response shaping or derived payload generation.

Initial assessment:

- Better fit for optional response transformation than for the primary rule condition language.

### 4. MongoDB validation and versioning support

Sources:

- `https://www.mongodb.com/docs/manual/core/schema-validation/specify-json-schema/`
- `https://www.mongodb.com/docs/manual/core/schema-validation/`
- `https://www.mongodb.com/docs/manual/data-modeling/design-patterns/data-versioning/schema-versioning/`

Relevant points:

- MongoDB supports collection-level validation with `$jsonSchema`.
- MongoDB documents can carry a `schemaVersion` field to support mixed-version storage.

Why it matters:

- This supports a Mongo adapter strategy where rule documents are versioned and validated without forcing migrations for every evolution.

### 5. PostgreSQL JSONB and JSON path support

Sources:

- `https://www.postgresql.org/docs/current/functions-json.html`
- `https://www.postgresql.org/docs/current/gin.html`
- `https://www.postgresql.org/docs/16/datatype-json.html`

Relevant points:

- PostgreSQL provides JSON and JSONB operators plus SQL/JSON path capabilities.
- PostgreSQL supports regex-like filtering in SQL/JSON path.
- GIN indexes are the main indexing family for JSONB query operators.

Why it matters:

- This supports a Postgres adapter strategy where canonical rule documents can live in JSONB while selected metadata is normalized into relational columns for indexing and governance.

## Sub-Agent Findings

### Backend architecture memo

Status:

- Completed

Summary:

- The strongest default recommendation is to own the public rule model ourselves and keep evaluation pluggable.
- The recommended baseline is:
  - custom JSON rule envelope
  - typed operator registry
  - optional CEL backend for advanced predicates
- The memo does not recommend making a heavyweight third-party rule DSL the primary external contract for an embeddable SDK.

Compared options:

1. Custom AST or interpreter
   - Best fit for our exact semantics:
     - OR across rules
     - AND across conditions
     - first-match result
     - typed operators
     - arbitrary JSON response payloads
   - Main downside:
     - we own validation, migration, optimization, explainability, and operator semantics

2. Existing rules engine or DSL
   - Good only if fast analyst-facing authoring or decision tables become the primary goal
   - Risks:
     - semantic mismatch for a lightweight embeddable kit
     - heavier operational model
     - weaker portability across languages and host apps

3. CEL-like expression engine
   - Best balance for safe, typed, embeddable advanced predicates
   - Risks:
     - authoring is less business-friendly
     - portability drops if we add too many custom functions

Recommended backend shape:

- `RuleSet` with ordered `rules[]`
- default hit policy `FIRST_MATCH`
- each rule contains `when: { all: [...] }`
- each condition contains:
  - `fieldRef`
  - `operator`
  - `value`
- operator availability should be derived from field metadata and type information
- response payload should be treated as any JSON value, with typed SDK accessors layered on top
- control plane and data plane should be separated:
  - validate and compile at save or publish time
  - evaluate compiled artifacts at runtime

Important implications:

- The public authoring contract should not be a raw CEL string or raw third-party DSL.
- A higher-level rule document should be the stable external API.
- If needed, advanced predicates can be compiled into CEL internally or exposed as an optional expert-only leaf.

Sources returned by the backend agent:

- `https://github.com/google/cel-spec`
- `https://github.com/google/cel-java`
- `https://openfeature.dev/specification/sections/providers/`
- `https://openfeature.dev/specification/sections/hooks/`
- `https://json-schema.org/understanding-json-schema/basics`
- `https://json-schema.org/understanding-json-schema/reference/type`
- `https://jsonlogic.com/`
- `https://github.com/CacheControl/json-rules-engine`
- `https://docs.camunda.io/docs/components/modeler/dmn/decision-table-hit-policy/`
- `https://kie.apache.org/docs/10.0.x/drools/drools/KIE/index.html`
- `https://kie.apache.org/docs/10.1.x/drools/drools/language-reference/index.html`

### Persistence and integration memo

Status:

- Completed

Summary:

- The strongest cross-store recommendation is a hybrid persistence model:
  - stable scalar metadata
  - canonical JSON rule payload
  - immutable revisions
  - explicit query projections

Recommended storage shape:

- `rule_sets_current`
  - one current row or document per logical ruleset
  - includes:
    - `tenant_id`
    - `rule_key`
    - `status`
    - `priority`
    - effective window fields
    - `schema_version`
    - `engine_version`
    - `revision`
    - `payload_hash`
    - audit fields
    - `payload`
- `rule_revisions`
  - append-only snapshots of the canonical payload
  - also stores the frontend contract snapshot used for that revision
- `rule_events`
  - semantic lifecycle events such as:
    - `created`
    - `draft_saved`
    - `published`
    - `rolled_back`
    - `archived`
- `query_projections`
  - extracted queryable fields we promise to support portably across databases

Why this matters:

- It avoids overfitting the domain model to either SQL tables or document collections.
- It preserves evolvability of the rule DSL while keeping operational queryability.

Storage strategy by backend:

#### PostgreSQL

- Store canonical payload as `jsonb`.
- Use GIN for broad containment and JSON path search.
- Use targeted expression indexes only for selected hot-path fields.
- Use generated columns sparingly and only for a small stable set of extracted fields.

Important PostgreSQL considerations:

- Large `jsonb` updates still lock the whole row.
- Therefore current records should stay compact and deep mutable blobs should be minimized.

#### MongoDB

- Keep frequently filtered fields at the top level.
- Use compound indexes for stable metadata fields.
- Avoid relying on deep arbitrary document queries as the main product contract.

Important MongoDB considerations:

- Multikey and compound-index rules become restrictive once arrays are indexed.
- Mixed-shape current documents increase index complexity and query branching.
- The 16 MiB document limit is a real ceiling for oversized embedded artifacts.

Cross-store portability rule:

- Portable product queries should depend on top-level scalar projections.
- Deep JSON or BSON querying should be treated as an admin or internal capability, not the primary public API contract.

Versioning and audit guidance:

- Separate `schema_version` from `revision`.
  - `schema_version` tracks contract or storage shape
  - `revision` tracks business changes to a ruleset
- Keep current records uniform.
- Keep older revisions in separate history storage instead of mixing multiple live shapes in current records.
- Product-level history should be application-owned, not delegated only to database audit facilities.

Frontend contract to backend storage:

- Use canonical JSON Schema as the source of truth at the application layer.
- Store separately:
  - `contract_schema`
  - `ui_hints`
  - `default_values`
  - `schema_version`
- Snapshot the contract into each stored rule revision so old revisions can still be rendered and edited correctly.

Important portability gap:

- MongoDB validation uses draft 4 JSON Schema with differences.
- The frontend contract will likely want a newer JSON Schema draft.
- Therefore the canonical contract should live in the application layer and only a compatible subset should be pushed down into MongoDB validation.

Deployment guidance from the memo:

- Library or SDK is the best default if the host app chooses the database and wants transaction-local persistence.
- A separate service is better only if centralized governance outweighs transaction and latency costs.
- Default recommendation:
  - embedded evaluator
  - repository SPI
  - Mongo and PostgreSQL adapters
  - optional control-plane service later

Sources returned by the persistence agent:

- `https://www.postgresql.org/docs/current/datatype-json.html`
- `https://www.postgresql.org/docs/current/gin.html`
- `https://www.postgresql.org/docs/current/ddl-generated-columns.html`
- `https://www.postgresql.org/docs/current/trigger-definition.html`
- `https://www.postgresql.org/docs/current/event-triggers.html`
- `https://www.postgresql.org/docs/current/logicaldecoding.html`
- `https://www.mongodb.com/docs/manual/data-modeling/design-patterns/data-versioning/schema-versioning/`
- `https://www.mongodb.com/docs/manual/data-modeling/design-patterns/data-versioning/document-versioning/`
- `https://www.mongodb.com/docs/manual/core/indexes/index-types/index-compound/`
- `https://www.mongodb.com/docs/manual/core/indexes/index-types/index-multikey/`
- `https://www.mongodb.com/docs/manual/core/schema-validation/specify-json-schema/`
- `https://www.mongodb.com/docs/manual/core/schema-validation/specify-validation-level/`
- `https://www.mongodb.com/docs/manual/core/schema-validation/handle-invalid-documents/`
- `https://www.mongodb.com/docs/manual/changestreams/`
- `https://www.mongodb.com/docs/manual/data-modeling/embedding/`
- `https://www.mongodb.com/docs/manual/data-modeling/referencing/`
- `https://json-schema.org/draft/2020-12/json-schema-core`
- `https://json-schema.org/draft/2020-12/json-schema-validation`
- `https://www.pgaudit.org/`

### Frontend architecture memo

Status:

- Completed

Summary:

- The cleanest frontend architecture is layered:
  - headless `rule-core`
  - renderer package for the visual editor
  - embed boundary chosen independently from the internal UI stack
  - canonical saved format as versioned JSON AST

Recommended frontend layering:

1. Headless `rule-core`
   - owns:
     - canonical config schema
     - migrations
     - field registry
     - operator registry
     - validation
     - import and export

2. Renderer package
   - owns:
     - visual rule tree
     - schema-driven operand editors
     - rule/group validation display

3. Embed boundary
   - React component library if host apps are mostly React
   - Custom element if hosts are mixed or unknown

4. Canonical save format
   - versioned JSON AST
   - JsonLogic and CEL are treated as export targets, not as the primary authoring format

Why it matters:

- This prevents the config schema, editor UX, and host-app integration contract from being tightly coupled.
- It also keeps future framework portability open.

Embed options evaluated:

#### React component library

- Best when most host apps are React
- Fastest path
- Best ecosystem fit for `react-querybuilder` and React-based schema forms
- Risk:
  - consumer coupling to React versions and runtime assumptions

#### Custom element package

- Best when hosts vary across frameworks
- Stable DOM-level contract
- Best long-term distribution boundary for a reusable product
- Risks:
  - theming and event contracts need care
  - shadow DOM behavior must be designed intentionally

#### Module Federation remote

- Best when all consumers live on the same SPA platform and independent deploys matter
- Weaker as a general reusable product boundary

#### `iframe` embed

- Best for hard isolation or cross-origin hosting
- Strong isolation but much higher UX and integration cost

Frontend memo recommendation:

- If the consumer set is mixed or still unknown, custom element is the safest external contract.
- If the consumer set is known to be React-heavy, start with a React package but keep the internal split so a custom-element wrapper remains possible later.

UI engine guidance:

- No single library cleanly solves the full product.
- Recommended hybrid:
  - query-tree editor for `when` and nested condition groups
  - schema-driven form engine for operands, actions, advanced rule settings, and JSON payload subforms

Key design implications:

#### Canonical saved format

- Use a versioned JSON envelope with:
  - stable IDs
  - explicit priority
  - `$schema`
  - `$id`
- Do not rely on array order alone to carry rule meaning.
- Keep extension points namespaced.

#### Operator selection by data type

- Keep operator logic in registries, not inside UI components.
- Each field should declare:
  - datatype
  - allowed operators
  - default operator
  - operand schema
- Each operator should declare:
  - arity
  - operand schema

#### Condition groups

- Boolean tree modeling should stay separate from top-level rule priority.
- Top-level authoring model should remain:
  - `{ id, priority, enabled, when, then }`
- This avoids nested condition groups accidentally becoming the execution-order model.

#### Validation UX

- Validation should be layered:
  - field-level
  - rule-level
  - group-level
  - document-level
- Raw JSON editing should be transactional:
  - parse
  - validate
  - apply
- It should not mutate the live state while invalid.

#### JSON response editing

- Keep one canonical AST in memory.
- Visual builder and raw JSON editor should both round-trip through that AST.
- JsonLogic and CEL are useful exports but poor primary authoring sources for preserving UI metadata.

Primary sources returned by the frontend agent:

- `https://json-schema.org/understanding-json-schema/keywords`
- `https://json-schema.org/understanding-json-schema/reference/combining`
- `https://json-schema.org/draft/2020-12/json-schema-core`
- `https://ajv.js.org/json-schema`
- `https://ajv.js.org/standalone.html`
- `https://ajv.js.org/keywords.html`
- `https://jsonforms.io/docs/architecture/`
- `https://jsonforms.io/docs/uischema/rules/`
- `https://jsonforms.io/docs/validation/`
- `https://rjsf-team.github.io/react-jsonschema-form/docs/usage/validation/`
- `https://rjsf-team.github.io/react-jsonschema-form/docs/advanced-customization/custom-widgets-fields/`
- `https://rjsf-team.github.io/react-jsonschema-form/docs/json-schema/arrays/`
- `https://react-querybuilder.js.org/docs/components/querybuilder`
- `https://react-querybuilder.js.org/docs/utils/validation`
- `https://react-querybuilder.js.org/docs/dnd`
- `https://react-querybuilder.js.org/docs/utils/export`
- `https://react.dev/reference/react-dom/components`
- `https://vuejs.org/guide/extras/web-components.html`
- `https://angular.dev/guide/elements`
- `https://lit.dev/docs/components/properties/`
- `https://lit.dev/docs/v2/components/events/`
- `https://webpack.js.org/concepts/module-federation/`
- `https://html.spec.whatwg.org/multipage/web-messaging.html`
- `https://html.spec.whatwg.org/dev/web-messaging.html`
- `https://html.spec.whatwg.org/dev/custom-elements.html`
- `https://jsonlogic.com/`
- `https://jsonlogic.com/operations.html`
- `https://jsonlogic.com/add_operation.html`
- `https://cel.dev/overview/cel-overview`

## Early Observations

1. Internal precedents already suggest that `rule-kit` should likely avoid direct database dependencies in the core domain model.
2. There is good evidence for introducing an explicit compiler or validator stage before runtime evaluation.
3. A typed operator registry looks preferable to scattering ad hoc condition logic in the UI and backend separately.
4. The frontend contract should probably be schema-driven so operator availability can be derived from field metadata rather than hardcoded.
5. Internal CARS24 precedents consistently separate framework-agnostic core logic from host-specific adapters, which is likely the right packaging strategy for rule-kit as well.
6. External evidence is pointing toward a layered model:
   - high-level rule schema for authoring
   - compiled or lowered evaluator representation for runtime
   - storage adapters that preserve canonical revisions independent of MongoDB or Postgres
7. The backend research strongly supports not coupling the public contract to CEL, Drools, or any third-party DSL. The stable contract should remain our own rule document model.
8. The persistence research strongly supports a hybrid storage shape:
   - canonical JSON payload for evolvability
   - extracted scalar projections for queryability and portability
   - append-only revisions for history
9. The frontend research strongly supports the same principle on the UI side:
   - own the canonical AST
   - keep the editor layered
   - treat JsonLogic and CEL as export or runtime targets rather than the source of truth
10. Local `bff-studio` UI patterns suggest that explicit evaluation-mode language and nearby contract feedback are important for operator confidence when editing conditional logic.
11. The new FE=BE requirement materially raises the architecture bar: preview and simulation should use the same evaluator core, not a second independent implementation if we can avoid it.
12. Assigno is a useful local precedent for typed condition builders, priority-ordered matching, and rule catalogs, but not for cloning the final runtime model.
13. With Java backend and mostly React frontend, the safest v1 path is likely backend-driven simulation using the same Java evaluator, unless local browser execution is an explicit product requirement for v1.
14. Dynamic-config is now a key internal precedent for shared evaluator-core plus SDK/starter/server packaging and versioned config lifecycle.
15. MongoDB/PostgreSQL research should be interpreted as host-side persistence guidance, not as evidence that rule-kit itself should ship database adapters.

## Open Questions

These remain unresolved and need user confirmation plus research evidence:

- Should custom host-defined operators be supported from day one?
- Is rule priority strictly positional, or should there also be explicit priority metadata?
- Should unmatched evaluation return a default response, `null`, or an explicit no-match object?
- How strongly typed should the response payload contract be per ruleset?
- Are backend host applications mostly JS/TS-compatible, or should we plan for a portable evaluator kernel for polyglot backends from day one?

## Research Log

### 2026-04-20 01:xx IST

- Confirmed the current `rule-kit` workspace is empty and does not yet contain implementation artifacts.
- Started internal precedent search across nearby CARS24 projects.
- Identified `c24-bff-service` expression engine as relevant evaluator precedent.
- Identified `bff-studio` schema/compiler/persistence split as relevant architecture precedent.
- Launched parallel research agents for backend, frontend, persistence, and internal precedent analysis.
- Confirmed there is currently no obvious local repo or code reference named `asino` under `/Users/user/Desktop/cars24/projects`.
- Identified `action-orchestration-engine` as a strong internal precedent for embeddable SDK packaging, conditional configuration, and host-framework separation.
- Added official external research starting points for CEL, JsonLogic, JSONata, MongoDB schema validation/versioning, and PostgreSQL JSONB support.
- Received the first sub-agent memo for backend architecture; it recommends a custom public rule schema with pluggable evaluation and optional CEL support for advanced predicates.
- Received the persistence and integration memo; it recommends a hybrid storage model with canonical JSON payloads, immutable revisions, and explicit query projections across Postgres and MongoDB.
- Received the frontend architecture memo; it recommends a headless core plus renderer split, with a custom element as the safest long-term embed contract if host apps are mixed.
- Added local `bff-studio` control-plane findings showing an existing operator-facing pattern for branch conditions, first-match vs multi-match semantics, and nearby contract feedback.
- Wrote a synthesized architecture comparison document at `/Users/user/Desktop/cars24/projects/rule-kit/docs/research/2026-04-20-rule-kit-architecture-options.md`.
- User clarified that frontend preview and simulation must use the exact same evaluation semantics as backend execution.
- User corrected the internal project reference from `asino` to `assigno`.
- Found and inspected `/Users/user/Desktop/cars24/projects/c24-assigno-service`, including its backend rule evaluation service, frontend rule builder, condition builder, and typed constraint catalog.
- User clarified that backend will be Java-based and frontend will be mostly React, which materially narrows the FE/BE shared-evaluator options.
- Found and inspected `/Users/user/Desktop/cars24/projects/dynamic-config`, including its shared evaluation engine, Java SDK, Spring starter, config history, approval workflow, and React SDK/admin packaging.
- Wrote a detailed front-end/back-end SDK blueprint at `/Users/user/Desktop/cars24/projects/rule-kit/docs/research/2026-04-20-rule-kit-detailed-blueprint.md`, including repo structure, class inventories, host integration examples, CRUD/draft flows, and UI mockups.

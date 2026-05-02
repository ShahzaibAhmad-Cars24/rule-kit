# Rule Kit Architecture Options

Date: 2026-04-20
Status: Historical research synthesis; superseded by current backend SDK implementation decisions
Purpose: Compare architecture choices for an embeddable rule-kit without prematurely locking the implementation.

> Current-state note, 2026-05-02: this file compares options considered during research. The current implemented backend SDK is Java 17-based and RuleKit-native for Dynamic Config v1. Rust/WASM and broader frontend SDK work remain historical options, not the current backend SDK contract.

## Problem Statement

We need a rule-kit that can be embedded into host applications on both backend and frontend sides.

The requested behavior is:

- Rules are evaluated in OR order.
- Conditions inside a rule are evaluated in AND order.
- The first satisfied rule determines the returned response.
- The response payload can be number, string, JSON, or any structured value we choose to support.
- Available operators depend on the type of the field being evaluated.
- The SDK itself should stay database-agnostic so host applications can persist configs in MongoDB, PostgreSQL, or any other store they choose.
- Frontend preview and simulation must use the exact same evaluation semantics as backend execution.

This document compares the major architecture decisions rather than jumping straight to one solution.

## Evaluation Criteria

Every option below is judged against the same criteria:

1. Embeddability into host applications
2. Portability across backend stacks and databases
3. Operator extensibility
4. UX quality for non-trivial rule authoring
5. Long-term schema evolution and versioning
6. Runtime safety and determinism
7. Explainability, auditability, and rollback
8. Freedom for host applications to persist in MongoDB, PostgreSQL, or another store without SDK restriction

## Decision 1: Canonical Rule Model

### Option A: Own a custom versioned JSON AST

Shape:

- `RuleSet`
- ordered `rules[]`
- each rule contains:
  - `id`
  - `priority`
  - `enabled`
  - `when`
  - `then`

Pros:

- Best fit for the exact requested semantics
- Stable public contract independent of runtime engine choice
- Easy to preserve IDs, priority, disabled state, comments, metadata, and audit diffs
- Frontend and backend can share one canonical shape

Cons:

- We own migration, validation, and exporter logic
- We must build and maintain the operator registry ourselves

Assessment:

- Best overall fit

Example:

```json
{
  "$schema": "https://cars24.dev/rule-kit/ruleset-v1.json",
  "id": "pricing-rules",
  "revision": 12,
  "executionMode": "FIRST_MATCH",
  "rules": [
    {
      "id": "vip-discount",
      "priority": 100,
      "enabled": true,
      "when": {
        "all": [
          { "fieldRef": "customer.tier", "operator": "EQ", "value": "vip" },
          { "fieldRef": "cart.total", "operator": "GTE", "value": 5000 }
        ]
      },
      "then": {
        "response": {
          "discountPercent": 12,
          "label": "VIP discount"
        }
      }
    }
  ]
}
```

### Option B: Canonical JsonLogic

Pros:

- Portable JSON AST
- Multi-language support exists
- Easy to serialize

Cons:

- Weak as a source-of-truth authoring model
- Hard to preserve rule priority, UI metadata, and editor-specific state
- Typing discipline is weaker unless we wrap it in our own schema

Assessment:

- Strong export format
- Weak primary contract

Example:

```json
{
  "and": [
    { "==": [ { "var": "customer.tier" }, "vip" ] },
    { ">=": [ { "var": "cart.total" }, 5000 ] }
  ]
}
```

This captures the predicate, but not the rule ID, priority, disabled state, or editor-only annotations cleanly.

### Option C: Canonical CEL strings

Pros:

- Safe, typed, fast runtime expression model
- Good compile-once and evaluate-many lifecycle

Cons:

- Poor round-trip authoring model for a visual UI
- Harder to preserve IDs, editor metadata, disabled state, or rule annotations
- Less approachable for operators than structured rule documents

Assessment:

- Strong runtime target
- Weak authoring source of truth

Example:

```text
customer.tier == "vip" && cart.total >= 5000
```

Good evaluator input, weak primary authoring shape.

## Decision 2: Runtime Evaluator

### Option A: Custom interpreter over the canonical AST

Pros:

- Exact fit for:
  - OR across rules
  - AND across conditions
  - first-match response
- Strongest semantic control
- Easy to make deterministic and side-effect free

Cons:

- We own performance work, optimization, and explainability

Assessment:

- Best default for v1

Example:

Given:

```json
{
  "input": {
    "customer": { "tier": "vip" },
    "cart": { "total": 6200 }
  }
}
```

The interpreter walks `rules[]` in priority order, checks every `all[]` condition in the current rule, and returns the first `then.response` that matches.

### Option B: Lower selected leaves to CEL

Pros:

- Good for advanced typed predicates
- Safe and efficient
- Allows compile-time checking

Cons:

- Adds complexity if used as the default for everything
- Custom CEL functions reduce portability

Assessment:

- Best as an optional advanced predicate layer, not the public contract

Example:

```json
{
  "fieldRef": "advanced",
  "operator": "CEL",
  "value": "customer.tier == 'vip' && cart.total >= 5000"
}
```

This can exist as an expert-mode leaf inside our AST without making CEL the whole product contract.

### Option C: Use heavyweight rules engines such as Drools or DMN

Pros:

- Strong if analyst-friendly decision tables are the main product requirement

Cons:

- Too heavy for a lightweight embeddable SDK
- Semantic mismatch with the requested model
- Operational and portability costs are high

Assessment:

- Not recommended for the current requirement set

Example:

- Assigno is useful here as a cautionary precedent: once rules become tightly coupled to OptaPlanner scoring and tenant-specific constraints, the platform becomes much heavier than a first-match response engine.

## Decision 2B: Boolean Model And Nesting

The user explicitly called out a concern here: arbitrary nesting of `AND` and `OR` becomes confusing for operators.

### Option A: Arbitrary nested boolean trees

Shape:

- any node can contain nested `all` and `any`
- users can build deep boolean trees

Pros:

- Maximum expressive power
- Closest to general-purpose logic engines

Cons:

- High cognitive load
- Harder to explain, review, and debug
- Preview traces become noisy
- Easy to create logically correct but operator-hostile rules

Example:

```json
{
  "when": {
    "all": [
      {
        "any": [
          { "fieldRef": "user.city", "operator": "EQ", "value": "Delhi" },
          {
            "all": [
              { "fieldRef": "user.city", "operator": "EQ", "value": "Gurgaon" },
              { "fieldRef": "user.segment", "operator": "IN", "value": ["vip", "gold"] }
            ]
          }
        ]
      },
      {
        "any": [
          { "fieldRef": "order.total", "operator": "GTE", "value": 5000 },
          { "fieldRef": "coupon.code", "operator": "MATCHES", "value": "VIP.*" }
        ]
      }
    ]
  }
}
```

Assessment:

- Powerful but poor default UX

### Option B: Constrained rule model matching the user requirement

Shape:

- top-level rules are OR by definition
- conditions inside each rule are AND by definition
- no arbitrary boolean nesting in normal authoring

Pros:

- Matches the requested product mental model exactly
- Easiest for operators to understand
- Easiest to explain in simulation traces
- Simplest FE/BE parity story

Cons:

- Less expressive than full boolean trees

Example:

```json
{
  "executionMode": "FIRST_MATCH",
  "rules": [
    {
      "id": "vip-discount",
      "priority": 100,
      "when": {
        "all": [
          { "fieldRef": "customer.tier", "operator": "EQ", "value": "vip" },
          { "fieldRef": "cart.total", "operator": "GTE", "value": 5000 },
          { "fieldRef": "customer.city", "operator": "IN", "value": ["Delhi", "Gurgaon"] }
        ]
      },
      "then": { "response": { "discountPercent": 12 } }
    },
    {
      "id": "default-discount",
      "priority": 10,
      "when": {
        "all": [
          { "fieldRef": "cart.total", "operator": "GTE", "value": 2000 }
        ]
      },
      "then": { "response": { "discountPercent": 5 } }
    }
  ]
}
```

Assessment:

- Best fit for this product as currently described

### Option C: Limited nesting only in expert mode

Shape:

- default authoring is constrained
- an optional expert-only group node allows one additional `any` layer

Pros:

- Keeps mainstream UX simple
- Leaves a narrow escape hatch for high-value edge cases

Cons:

- Product complexity still increases
- You now have two mental models: standard mode and expert mode

Example:

- Standard mode:
  - rule has three ANDed conditions
- Expert mode:
  - one condition row can become “match any of these values/groups”

Assessment:

- Acceptable only if strong real-world use cases emerge

Recommendation:

- Do not expose arbitrary nesting in v1.
- Keep the product model aligned with the original requirement:
  - OR across rules
  - AND inside a rule
- If future requirements force richer boolean logic, add a tightly constrained expert-mode group rather than unrestricted nesting.

## Decision 2A: Exact Same Evaluation In FE And BE

The user requirement here is strict: preview and simulation in the frontend must behave exactly like runtime evaluation in the backend.

### Option A: Same implementation package in one language

Shape:

- one evaluator package
- browser uses it directly
- backend embeds the same package

Pros:

- Strongest guarantee of identical behavior
- Lowest conformance-testing burden

Cons:

- Works best only if the backend runtime can use the same language stack

Assessment:

- Best if backend consumers are JavaScript or TypeScript compatible

Example:

- `@cars24/rule-kit-evaluator`
  - browser: imported by the rule-builder for preview
  - Node service: imported by the backend API for runtime evaluation

### Option B: One canonical spec with separate FE and BE implementations

Pros:

- Each platform can use native language and performance patterns

Cons:

- “Exact same evaluation” becomes a test problem, not an architecture guarantee
- Drift risk is high when operator semantics evolve

Assessment:

- Weak fit for the stated requirement

Example:

- TypeScript frontend evaluator implements `BETWEEN` using inclusive numeric comparison
- Java backend evaluator accidentally treats strings lexicographically for some cases
- Preview and runtime diverge

### Option C: Single portable evaluator kernel

Shape:

- one evaluator kernel compiled to a portable artifact
- frontend uses it in browser
- backend uses the same kernel through host bindings

Pros:

- Best long-term guarantee in a polyglot environment
- Keeps semantics centralized even if host backends differ

Cons:

- Higher integration complexity
- Debugging and packaging become more specialized

Assessment:

- Best long-term shape if backend hosts are polyglot

Example:

- A Rust evaluator kernel compiles to:
  - WebAssembly for browser preview
  - server-side bindings for backend SDKs

Current recommendation:

- With Java backend and mostly React frontend, the easiest “one package in one language” option is no longer the default.
- Safest v1:
  - Java evaluator is source-of-truth
  - React preview and simulation call backend simulation endpoints using in-memory draft payloads
  - evaluation traces come from the same Java engine used in runtime
- Best long-term target if local browser execution becomes mandatory:
  - move to a portable evaluator kernel
- Avoid:
  - separate Java and TypeScript evaluator implementations that try to stay in sync by tests alone

### Updated Java + React examples

#### V1 backend-driven simulation

Frontend request:

```json
{
  "draftRuleSet": {
    "id": "pricing-rules",
    "revision": 12,
    "rules": [
      {
        "id": "vip-discount",
        "priority": 100,
        "enabled": true,
        "when": {
          "all": [
            { "fieldRef": "customer.tier", "operator": "EQ", "value": "vip" },
            { "fieldRef": "cart.total", "operator": "GTE", "value": 5000 }
          ]
        },
        "then": {
          "response": { "discountPercent": 12 }
        }
      }
    ]
  },
  "input": {
    "customer": { "tier": "vip" },
    "cart": { "total": 6200 }
  }
}
```

Java backend response:

```json
{
  "matchedRuleId": "vip-discount",
  "response": { "discountPercent": 12 },
  "trace": [
    {
      "ruleId": "vip-discount",
      "matched": true,
      "conditions": [
        { "fieldRef": "customer.tier", "operator": "EQ", "expected": "vip", "actual": "vip", "matched": true },
        { "fieldRef": "cart.total", "operator": "GTE", "expected": 5000, "actual": 6200, "matched": true }
      ]
    }
  ]
}
```

#### Long-term portable-kernel direction

- Java SDK hosts the evaluator kernel for runtime execution
- React builder hosts the same evaluator kernel for local simulation
- Saved AST remains unchanged across both

## Decision 3: Frontend Authoring Architecture

### Option A: Single monolithic UI package

Pros:

- Fastest to build initially

Cons:

- Schema model, embed boundary, UI rendering, and validation become tightly coupled
- Hard to reuse across different host stacks

Assessment:

- Short-term convenience, poor long-term architecture

### Option B: Headless core plus renderer

Pros:

- Best separation of concerns
- One canonical model shared across raw JSON editing, visual editing, validation, and export
- Easier to embed into multiple hosts

Cons:

- More up-front package design work

Assessment:

- Best overall fit

Example:

- `rule-core`
  - validates `RuleSet`
  - computes available operators for `cart.total`
  - exports a simulation request payload
- `rule-builder-ui`
  - renders the rule list
  - shows operand editors
  - shows validation errors and simulation results

Recommended split:

- `rule-core`
  - schema
  - migrations
  - field registry
  - operator registry
  - validation
  - import or export
- `rule-builder-ui`
  - rule tree rendering
  - operand editors
  - validation visualization
- optional wrappers
  - React wrapper
  - custom element wrapper

## Decision 4: Frontend Embed Contract

### Option A: React component library

Pros:

- Fastest if most hosts are React
- Strong ecosystem fit for React Query Builder and React schema-form tooling

Cons:

- Host applications become React-coupled
- Versioning friction increases across teams

Assessment:

- Good if the host landscape is known to be React-heavy

Example:

```tsx
<RuleBuilder
  value={ruleSet}
  fieldRegistry={fieldRegistry}
  onChange={setRuleSet}
  onSimulate={simulateDraft}
/>
```

This option becomes much stronger when:

- frontend hosts are mostly React
- teams want to place the editor in:
  - a modal
  - a drawer
  - an inline settings view
  - a route page
  - a dedicated admin page

### Option B: Custom element

Pros:

- Stable DOM-level contract
- Works across React, Vue, Angular, and server-rendered apps
- Strong long-term portability story

Cons:

- Theming, property passing, and event behavior need more careful design

Assessment:

- Safest long-term product boundary for mixed host apps

Example:

```html
<cars24-rule-builder id="rule-builder"></cars24-rule-builder>
<script>
  const el = document.getElementById('rule-builder');
  el.fieldRegistry = registry;
  el.value = ruleSet;
  el.addEventListener('rule-change', (event) => console.log(event.detail));
</script>
```

### Option C: `iframe`

Pros:

- Strong isolation

Cons:

- Highest UX and integration cost
- Theming and state sync become complex

Assessment:

- Use only if isolation is more important than product feel

Example:

- A central control plane hosted on `rules.cars24.dev` embedded into an admin console via `iframe`, communicating over `postMessage`.

## Decision 4A: UI Packaging For Smooth Embedding

The user has now clarified that the UI should behave like SDK components:

- easy to drop into existing or new applications
- easy to restyle
- usable inside:
  - current config view
  - update-config flow
  - modal
  - page
  - new page

This strongly favors a component-first React architecture over a fixed app shell.

### Option A: Full admin app only

Pros:

- Fastest to demo

Cons:

- Hardest to embed smoothly
- Host apps must adapt around it
- Styling and interaction patterns feel foreign inside existing products

Example:

- A single `RuleKitAdminPage` app that expects its own routing, shell, and layout

Assessment:

- Not recommended as the primary SDK UX

### Option B: Component SDK with container-agnostic building blocks

Shape:

- headless hooks and state helpers
- composable presentational components
- optional higher-level assembled screens

Pros:

- Best fit for modal/page/drawer/inline embedding
- Easier design-system alignment
- Host apps can own layout and navigation
- Easier gradual adoption

Cons:

- Requires more careful component API design
- Needs stronger documentation and examples

Example package split:

- `@cars24/rule-kit-react-core`
  - hooks
  - validation adapters
  - simulation client
  - local editor state
- `@cars24/rule-kit-react-components`
  - `RuleList`
  - `RuleEditor`
  - `ConditionRow`
  - `ResponseEditor`
  - `SimulationPanel`
  - `RuleTracePanel`
- `@cars24/rule-kit-react-presets`
  - `RuleKitModal`
  - `RuleKitPage`
  - `RuleKitDrawer`

Example usage in a modal:

```tsx
<Dialog open={open} onOpenChange={setOpen}>
  <DialogContent className="max-w-5xl">
    <RuleEditor
      value={draftRuleSet}
      fieldRegistry={fieldRegistry}
      onChange={setDraftRuleSet}
      onSimulate={simulateDraft}
    />
  </DialogContent>
</Dialog>
```

Example usage in an existing page:

```tsx
<ConfigDetailsLayout>
  <RuleList value={ruleSet} onSelectRule={setSelectedRuleId} />
  <RuleEditor
    value={selectedRule}
    fieldRegistry={fieldRegistry}
    onChange={updateRule}
    onSimulate={simulateDraft}
  />
</ConfigDetailsLayout>
```

Assessment:

- Best primary SDK UX for your stated need

### Option C: Headless-only SDK

Pros:

- Maximum freedom for host teams
- Very reusable

Cons:

- Too much work for each consuming team
- Slower adoption
- Inconsistent UX across products

Example:

- only hooks like `useRuleBuilderState()` and `useRuleSimulation()`
- host teams build all UI themselves

Assessment:

- Good internal foundation
- Not enough by itself as the product surface

### Recommended UI packaging model

Use a layered React SDK:

1. Headless foundation
   - schema handling
   - local draft state
   - validation
   - simulation API integration

2. Composable UI components
   - small pieces that can be embedded anywhere

3. Optional assembled shells
   - modal preset
   - page preset
   - drawer preset

### Design customization requirements

To keep the UI easy to adapt, components should support:

- host-provided class names
- host-provided render overrides for complex regions
- host-provided theme tokens
- controlled and uncontrolled usage patterns
- no mandatory global CSS reset

Example customization surface:

```tsx
<RuleEditor
  value={draftRuleSet}
  fieldRegistry={fieldRegistry}
  components={{
    ConditionValueInput: MyCustomValueInput,
    ResponseEditor: MyJsonEditor,
  }}
  classNames={{
    root: "rounded-xl border bg-panel",
    toolbar: "sticky top-0 bg-panel/95",
  }}
/>
```

### Updated recommendation for your environment

Because your frontend is mostly React and you want smooth in-app embedding:

- Primary recommendation:
  - React component SDK first
- Secondary optional path later:
  - custom element wrapper if cross-framework embedding becomes important
- Not recommended as the primary experience:
  - iframe-only
  - monolithic admin page only

## Decision 5: UI Building Blocks

### Query-tree editor for `when`

Best candidate:

- React Query Builder

Why:

- Natural fit for nested conditions and rule groups
- Supports operator selection, validation, drag-and-drop, and multiple export formats

Limit:

- It is not a full rule-kit UI by itself

### Schema-driven form engine for operands and advanced settings

Strong candidates:

- JSON Forms
- RJSF

Why:

- Good fit for operator operand editing
- Good fit for action payload settings and JSON payload subforms
- Works well with JSON Schema and Ajv validation

Assessment:

- The practical architecture is hybrid:
  - query-tree for condition logic
  - schema-driven forms for operands, actions, and settings

Example:

- Query tree:
  - `customer.tier EQ "vip"`
  - `cart.total GTE 5000`
- Schema form:
  - edits operator-specific operand schema for `BETWEEN`
  - edits response payload JSON
  - edits rule metadata like `priority`, `enabled`, `tags`

## Decision 6: Storage Model

### Option A: Fully relational decomposition

Pros:

- Best native SQL queryability
- Strong native relational constraints

Cons:

- Poor portability to MongoDB
- High migration cost as the rule DSL evolves

Assessment:

- Not ideal for a database-agnostic embedded product

### Option B: Pure document storage with no stable scalar projections

Pros:

- Easy to start

Cons:

- Query behavior diverges quickly across MongoDB and PostgreSQL
- Indexing becomes inconsistent
- Governance and audit filtering become harder

Assessment:

- Too loose for a serious shared product

### Option C: Host-owned hybrid model with canonical payload plus projections

Shape:

- `rule_sets_current`
- `rule_revisions`
- `rule_events`
- extracted `query_projections`

Pros:

- Best balance of evolvability and operational queryability
- Works for both MongoDB and PostgreSQL
- Supports immutable revision history and rollback

Cons:

- Requires clear discipline about which fields are portable query projections

Assessment:

- Best host-side persistence reference pattern

Example:

Postgres row:

```json
{
  "tenant_id": "cars24",
  "rule_key": "pricing-rules",
  "status": "PUBLISHED",
  "revision": 12,
  "payload_hash": "sha256:abc123",
  "payload": { "...": "canonical ruleset json" }
}
```

Mongo document:

```json
{
  "tenantId": "cars24",
  "ruleKey": "pricing-rules",
  "status": "PUBLISHED",
  "revision": 12,
  "payloadHash": "sha256:abc123",
  "payload": { "...": "canonical ruleset json" }
}
```

## Decision 7: Packaging Into Host Applications

### Option A: In-process library or SDK

Pros:

- Best fit when the host app owns persistence
- Best chance of participating in host transactions
- No extra network hop

Cons:

- Each host app carries the library upgrade responsibility

Assessment:

- Best default

Example:

- `rule-kit-java`
  - embeds evaluator
  - accepts rulesets and draft payloads from the host application
- `rule-kit-js`
  - same canonical AST
  - same simulation request/response shape

### Option B: Central service

Pros:

- Central governance
- One runtime and one persistence model

Cons:

- Network hop
- Harder transaction boundaries with host data
- Can create dual-write consistency issues

Assessment:

- Better later as a control-plane or governance layer than as the v1 evaluation path

### Option C: Hybrid control plane plus embedded data plane

Shape:

- central control plane for:
  - authoring
  - publishing
  - revision history
  - governance
- embedded evaluator in each host app
- host-owned persistence in each host app

Pros:

- Keeps runtime fast and local
- Centralizes authoring and governance

Cons:

- More moving parts

Assessment:

- Strong long-term target if the product grows beyond one application family

## Decision 7A: Using Internal Systems As References Only

This matters because the user explicitly does not want rule-kit to be bound to Assigno or any other existing platform.

### Assigno

Use as reference for:

- typed condition builder UX
- catalog-driven parameter editing
- first-match-style group routing
- explainability panels

Do not inherit:

- OptaPlanner-centric solver model
- tenant/auth/callback platform shell
- assignment-domain assumptions

Example replacement path:

- current Assigno:
  - `RuleEvaluationService` computes derived fields
  - `GroupResolverService` routes task to a group
- future with rule-kit SDK:
  - `RuleKitEvaluator.evaluate(ruleSet, taskContext)` computes derived fields
  - `RuleKitEvaluator.evaluate(groupRuleSet, taskContext)` returns group decision
- everything else in Assigno stays where it is:
  - solver
  - queues
  - auth
  - callbacks
  - analytics

### Dynamic Config

Use as reference for:

- shared core evaluator used by SDK and server
- Java SDK + Spring starter packaging
- versioned config + audit log + approval workflow
- React SDK/admin separation

Do not inherit:

- feature-flag-specific domain model
- percentage-rollout-specific semantics as the core rule model

Example:

- rule-kit can copy the packaging idea:
  - `rule-kit-core`
  - `rule-kit-java-sdk`
  - `rule-kit-starter`
  - `rule-kit-server`
  - `rule-kit-react-sdk`
  - `rule-kit-admin`

But the evaluation contract remains our own rule AST, not Dynamic Config’s feature-flag condition model.

Example:

- Central authoring app publishes immutable revisions
- Host backend pulls revision `12`
- Host frontend preview simulates unsaved draft locally against the same evaluator core

## Decision 8: Versioning and Audit

Recommended principles:

- Separate `schema_version` from `revision`
- Keep revisions immutable
- Keep current records uniform
- Store semantic audit events at the application layer
- Use DB-native audit only as supporting infrastructure, not the whole product audit story

Why:

- This works across both MongoDB and PostgreSQL
- It supports rollback, diff, and explainability
- It avoids mixed-shape current records

Example:

- `schema_version = 2`
- `revision = 15`

Meaning:

- storage and contract shape is v2
- business rule content is the 15th published revision

## Decision 9: Response Payload Contract

### Option A: Truly arbitrary host-language object

Pros:

- Maximum flexibility

Cons:

- Poor portability
- Hard to store and validate consistently

Assessment:

- Not recommended as the product contract

### Option B: Any JSON value

Pros:

- Portable
- Works cleanly across frontend, backend, MongoDB, and PostgreSQL
- Can still represent scalar and structured responses

Cons:

- Host apps may still need typed accessors

Assessment:

- Best default

Recommended model:

- Canonical response payload is any JSON value
- SDKs expose typed accessors on top of it

Example:

Valid responses:

```json
42
```

```json
"route-to-manual-review"
```

```json
{
  "discountPercent": 12,
  "label": "VIP discount"
}
```

## Cross-Cutting Risks

### Risk 1: Public contract tied to a third-party DSL

Effect:

- Harder migrations
- More lock-in
- Poor preservation of UI metadata

Mitigation:

- Own the canonical AST
- Treat JsonLogic and CEL as export or runtime targets only

### Risk 2: UI knows too much about operator semantics

Effect:

- Drift between frontend and backend behavior

Mitigation:

- Central field registry plus operator registry
- Shared schema-driven operand definitions

### Risk 3: Deep JSON querying becomes the main API

Effect:

- Cross-store portability degrades
- Indexing gets inconsistent

Mitigation:

- Maintain explicit scalar query projections

### Risk 4: Runtime semantics depend only on list order

Effect:

- Harder auditability
- Harder stable diffs and merges

Mitigation:

- Persist explicit priority alongside UI order

### Risk 5: Frontend contract drift from stored revisions

Effect:

- Old rules become hard to edit or render safely

Mitigation:

- Snapshot contract schema and UI hints into each revision

## Current Research-Based Direction

This is not the final locked design yet, but the evidence currently points to:

1. Canonical model:
   - versioned JSON AST owned by us

2. Evaluator:
   - custom interpreter by default
   - optional CEL support for advanced predicates

3. Frontend:
   - headless core plus renderer
   - query-tree editor plus schema-driven form engine

4. Embed contract:
   - React component SDK first
   - optional modal/page/drawer presets
   - custom element wrapper only later if cross-framework embedding becomes necessary

5. Storage:
   - canonical JSON payload
   - stable scalar projections
   - immutable revisions
   - semantic audit events

6. Packaging:
   - embedded SDK or library first
   - optional control plane later
7. FE and BE identical evaluation:
   - one evaluator core shared across preview, simulation, and backend runtime
   - avoid dual independent implementations

## Open Questions That Still Affect the Final Recommendation

1. Are host applications mostly React, or do we need a framework-agnostic frontend boundary from day one?
2. Do we need analyst-friendly decision tables, or is a developer-operator visual builder enough?
3. Do host applications need rule persistence to participate in their own transactions?
4. Is fully local browser evaluation mandatory in v1, or is backend-driven simulation acceptable as long as semantics stay identical?

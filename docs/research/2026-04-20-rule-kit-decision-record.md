# Rule Kit Decision Record

Date: 2026-04-20
Status: Historical research; superseded by the Java 17 RuleKit-native backend SDK implementation
Purpose: Lock the major architecture decisions for rule-kit based on research and user guidance.

> Current-state note, 2026-05-02: this document records early architecture decisions and options. The current backend SDK is a Java 17 implementation with responsibility-based packages under `com.cars24.rulekit.core.*`; it does not currently use a Rust/WASM portable kernel. For the current implementation contract, use `../README.md`, `../examples/2026-05-01-rule-kit-sdk-examples.md`, and `../migration/2026-05-01-dynamic-config-v1-rulekit-native-plan.md`.

## Chosen Decisions

### Decision 1: Canonical Rule Model

Chosen:

- **Option A: Own a custom versioned JSON AST**

Why:

- Best fit for:
  - OR across rules
  - AND inside a rule
  - first-match response semantics
- Keeps the public contract independent of any third-party DSL
- Preserves IDs, priority, audit metadata, UI metadata, and migration control

Example:

```json
{
  "$schema": "https://cars24.dev/rule-kit/ruleset-v1.json",
  "id": "pricing-rules",
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
          "discountPercent": 12
        }
      }
    }
  ]
}
```

### Decision 2: Runtime Evaluator

Chosen:

- **Option A: Custom interpreter over the canonical AST**

Why:

- Exact fit for the chosen rule model
- Simplest way to keep semantics deterministic
- Easiest to explain and trace

Implication:

- We own evaluation logic, traces, and operator semantics
- We do not expose CEL or JsonLogic as the primary contract

### Decision 2B: Boolean Model And Nesting

Chosen:

- **OR across rules**
- **AND inside a rule**
- **No unrestricted nested AND/OR in v1**

Why:

- This is the clearest model for users
- This is the model the user explicitly prefers
- It reduces logical complexity and review burden

What we are explicitly rejecting:

- Arbitrary nested boolean trees as the default authoring model

Possible future escape hatch:

- tightly constrained expert-mode grouping if real use cases emerge

### Decision 2A: Exact Same Evaluation In FE And BE

Chosen:

- **Option C: Single portable evaluator kernel**

## How We Achieve A Single Portable Evaluator Kernel

Because:

- backend is Java
- frontend is React
- FE preview and simulation must use the exact same semantics as backend execution

we should not build two independent evaluators.

### Recommended implementation strategy

Build the evaluator core as a **portable, pure kernel** with:

- no database access
- no HTTP access
- no UI logic
- no host-framework dependency
- deterministic inputs and outputs

The kernel should accept:

- canonical `RuleSet`
- canonical `EvaluationInput`
- optional `TraceOptions`

and return:

- `EvaluationResult`
- `EvaluationTrace`

### Recommended technical shape

#### Kernel

- Implement evaluator kernel in a portable systems language
- Compile it to:
  - browser-consumable WebAssembly
  - server-consumable artifact for Java integration

#### Java wrapper

- `rule-kit-java-sdk` exposes ergonomic Java APIs
- Under the hood it calls the portable kernel

#### React wrapper

- `rule-kit-react-core` loads the same portable kernel in the browser
- React components call it for local preview and simulation

### Concrete implementation options

#### Option 1: Rust core + WebAssembly + Java host bindings

How it works:

- build the evaluator kernel in Rust
- compile browser target to WebAssembly
- expose a small stable JSON-based function surface:
  - `validateRuleSet`
  - `evaluate`
  - `explain`
- for Java:
  - either run the same WebAssembly module in a JVM-side wasm runtime
  - or compile the same Rust core to a native shared library and call it through Java bindings

Pros:

- strongest portability story
- best browser execution story
- clean semantic ownership in one place

Cons:

- highest tooling complexity
- Java integration takes more engineering effort

#### Option 2: Another portable core language with JVM + browser targets

How it works:

- implement the evaluator in a language that can target JVM and browser runtimes
- keep the same kernel contract across both

Pros:

- can reduce some Java-side integration complexity

Cons:

- usually weaker React/browser ergonomics than a browser-first wrapper around wasm

#### Option 3: Internal instruction VM over the AST

How it works:

- compile our JSON AST to a tiny internal instruction format
- execute that compiled form in a small runtime kernel
- Java and browser wrappers both call the same low-level evaluator core

Pros:

- excellent long-term control over semantics and performance

Cons:

- more design effort up front

### Recommended implementation choice

Recommended path:

- Rust evaluator core
- canonical JSON boundary
- WebAssembly build for browser preview/simulation
- Java wrapper for backend runtime

Why:

- React gets a clean browser story
- Java can still use the same kernel semantics
- this is the clearest way to satisfy exact FE/BE parity without dual implementations

### Kernel API surface

Recommended exported functions:

#### `validateRuleSet`

Input:

- canonical ruleset JSON
- field registry JSON

Output:

- structural errors
- semantic errors
- warnings

#### `evaluate`

Input:

- canonical ruleset JSON
- evaluation input JSON
- trace mode

Output:

- matched rule ID
- response payload
- default-used flag
- trace

#### `explain`

Input:

- canonical ruleset JSON
- evaluation input JSON

Output:

- rule-by-rule explanation optimized for diagnostics and UI display

### What the kernel should support in v1

- ordered rule evaluation
- `FIRST_MATCH`
- AND-only conditions inside a rule
- type-aware operators for:
  - string
  - number
  - boolean
  - array
  - null checks
- field-path resolution via dot notation
- response payload as any JSON value
- compact and verbose traces

### Example supported operators in v1

- `EQ`
- `NEQ`
- `GT`
- `GTE`
- `LT`
- `LTE`
- `BETWEEN`
- `IN`
- `NOT_IN`
- `CONTAINS`
- `STARTS_WITH`
- `ENDS_WITH`
- `MATCHES`
- `EXISTS`
- `NOT_EXISTS`
- `IS_TRUE`
- `IS_FALSE`

### What the kernel should not support in v1

- arbitrary code execution
- database or network access
- host-language callbacks during evaluation
- ambient time, locale, or feature state unless passed explicitly
- unrestricted nested boolean trees
- Java-only or browser-only custom operators

### Build and packaging layout

Suggested internal layout:

```text
rule-kit-kernel/
├── kernel-spec/
│   ├── ruleset.schema.json
│   ├── evaluation-input.schema.json
│   └── test-vectors/
├── kernel-core/
├── kernel-wasm/
├── kernel-java/
└── conformance-tests/
```

### How it works end-to-end

#### Frontend preview

1. React editor builds canonical draft JSON
2. Browser wrapper calls `kernel.evaluate(...)`
3. Kernel returns:
   - matched rule
   - response
   - trace
4. UI renders the result immediately

#### Backend runtime

1. Java SDK receives a published ruleset from the host application
2. Java wrapper calls the same kernel
3. Kernel returns the same result shape
4. Host app uses the response in its own business flow

### Performance approach

v1:

- JSON boundary in and out
- cache parsed or compiled rulesets by revision in the Java wrapper
- cache initialized wasm module in browser

Later:

- compile rulesets into an internal instruction form
- avoid repeated parse/validation on hot paths

### Testing strategy for parity

Create shared conformance vectors:

- input ruleset
- input payload
- expected matched rule
- expected response
- expected trace fragments

Each vector must run in:

- kernel-core tests
- Java wrapper tests
- browser wrapper tests

### Why this is the best match

Pros:

- Same evaluator logic everywhere
- Same operator behavior everywhere
- Same trace semantics everywhere
- Strongest possible FE/BE parity

Cons:

- Higher initial engineering complexity
- Stronger build/tooling requirements

### What the kernel boundary should look like

Input:

```json
{
  "ruleSet": { "...": "canonical ruleset json" },
  "input": {
    "customer": { "tier": "vip" },
    "cart": { "total": 6200 }
  },
  "traceMode": "VERBOSE"
}
```

Output:

```json
{
  "matchedRuleId": "vip-discount",
  "response": { "discountPercent": 12 },
  "trace": {
    "rules": [
      {
        "ruleId": "vip-discount",
        "matched": true,
        "conditions": [
          {
            "fieldRef": "customer.tier",
            "operator": "EQ",
            "expected": "vip",
            "actual": "vip",
            "matched": true
          }
        ]
      }
    ]
  }
}
```

### Important kernel design constraints

1. **No host-language custom operators in v1**
   - If Java and React can each inject arbitrary callbacks, parity is weakened.
   - v1 should only allow operators that are implemented in the portable kernel itself.

2. **No ambient state**
   - time, locale, or feature gates must be passed in explicitly

3. **Stable serialization**
   - use canonical JSON payloads at the kernel boundary

4. **Shared conformance tests**
   - every operator and rule example should run through:
     - kernel test suite
     - Java wrapper tests
     - React/browser wrapper tests

### Practical rollout suggestion

#### Phase 1

- Define kernel boundary
- Implement the portable evaluator
- Use it from Java
- Use it from React preview/simulation

#### Phase 2

- Add richer trace modes
- Add performance optimizations
- Add constrained extension points

## Decision 3: Frontend Authoring Architecture

Chosen:

- **Option B: Headless core plus renderer**

Why:

- Best separation of concerns
- Lets us embed UI in many host surfaces
- Keeps state/validation separate from appearance

## Decision 4: Frontend Embed Contract

Chosen:

- **Option A: React component library**

Why:

- Frontend hosts are mostly React
- Users want smooth embedding in existing apps
- React-first gives the lowest friction for v1

We are not choosing:

- custom element first
- iframe first
- standalone app first

### Future path

- If mixed-framework adoption becomes important later, we can add a wrapper
- But React component SDK is the right v1 primary contract

## Decision 4A: UI Packaging For Smooth Embedding

Chosen:

- **Option B: Component SDK with container-agnostic building blocks**

Additional requirement from the user:

- host apps must still be able to change the experience as needed
- they should not be restricted by our components

### What that means

We should ship:

1. headless hooks
2. composable components
3. optional preset shells

but the host should still be able to:

- replace inputs
- replace editors
- control layout
- override styles
- embed in modal/page/drawer/inline views

## Decision 5: UI Building Blocks

Chosen direction:

- query-tree-like editing for the rule list and condition rows
- schema-driven operand editing
- composable React building blocks

### Required building blocks

#### Headless core

- `useRuleSetEditor`
- `useRuleValidation`
- `useRuleSimulation`
- `useFieldRegistry`
- `useRuleSelection`
- `useDraftState`

#### Components

- `RuleList`
- `RuleEditor`
- `RuleHeader`
- `ConditionList`
- `ConditionRow`
- `OperatorSelect`
- `ValueInput`
- `ResponseEditor`
- `ValidationSummary`
- `SimulationPanel`
- `TracePanel`

#### Presets

- `RuleKitPage`
- `RuleKitModal`
- `RuleKitDrawer`

### Example embedding patterns

#### Modal

```tsx
<Dialog open={open} onOpenChange={setOpen}>
  <DialogContent className="max-w-6xl">
    <RuleKitModal
      value={draftRuleSet}
      fieldRegistry={fieldRegistry}
      onChange={setDraftRuleSet}
      onSimulate={simulateDraft}
      onSaveDraft={saveDraft}
    />
  </DialogContent>
</Dialog>
```

#### Inline page section

```tsx
<RuleList value={ruleSet} onSelectRule={setSelectedRuleId} />
<RuleEditor
  value={selectedRule}
  fieldRegistry={fieldRegistry}
  onChange={updateRule}
  onSimulate={simulateDraft}
/>
```

#### Fully custom host shell

```tsx
function CustomRuleScreen() {
  const editor = useRuleSetEditor(initialRuleSet);
  const validation = useRuleValidation(editor.value, fieldRegistry);

  return (
    <MyInternalLayout>
      <RuleList value={editor.value} onSelectRule={editor.selectRule} />
      <MyCustomPanel>
        <RuleEditor
          value={editor.selectedRule}
          fieldRegistry={fieldRegistry}
          onChange={editor.updateSelectedRule}
          components={{
            ResponseEditor: MyJsonEditor,
          }}
        />
      </MyCustomPanel>
      <ValidationSummary result={validation} />
    </MyInternalLayout>
  );
}
```

## Derived Decisions

The remaining design should now follow from the selected decisions:

### Persistence

- SDK remains database-agnostic
- host application owns:
  - drafts
  - revisions
  - publish state
  - rollback state
  - audit persistence

### Host APIs

- host application exposes CRUD, draft, publish, simulate, rollback APIs
- SDK consumes those APIs or is embedded inside them

### Simulation

- preview must use the portable kernel
- backend runtime must use the portable kernel
- no semantic drift between FE and BE is acceptable

### Extensibility

- field registry is host-provided
- response schema is host-provided
- UI can be partially overridden by host components
- operator extensions should be kernel-level, not host-language-only, if exact FE/BE parity is required

## Final recommendation

This chosen stack is the most aligned with your requirements:

1. Custom versioned AST
2. Custom interpreter
3. OR across rules, AND within a rule
4. Single portable evaluator kernel
5. Headless React core plus renderer
6. React component library as primary FE SDK
7. Container-agnostic, override-friendly UI building blocks
8. Host-owned persistence and lifecycle

This gives you:

- simple mental model for users
- strong FE/BE evaluation parity
- clean React embedding
- no DB coupling in the SDK
- enough flexibility for host applications to feel in control

## Pending Decisions

The major architecture direction is now mostly locked, but a few implementation-level decisions are still pending:

1. **Portable kernel technology choice**
   - Rust + WebAssembly + Java bindings is the recommended direction
   - but it is not yet formally locked

2. **v1 simulation mode**
   - local browser execution through the portable kernel
   - or backend-driven simulation first, with local browser execution in a later phase

3. **v1 operator surface**
   - exact first release operator list is not yet frozen

4. **Custom operator policy**
   - whether v1 supports any custom operators at all
   - if yes, how they are added without breaking FE/BE parity

5. **Trace contract**
   - exact shape of compact vs verbose traces still needs to be finalized

6. **Response schema strictness**
   - any JSON value is chosen
   - but whether host apps can attach optional response schemas per ruleset still needs to be finalized

7. **Host reference API**
   - CRUD/draft/publish/rollback flow is outlined
   - exact recommended endpoint shapes and DTOs are still to be finalized

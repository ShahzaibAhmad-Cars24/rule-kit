# Dynamic Config vs Assigno Rule Engine

Date: 2026-04-20
Purpose: Compare the two internal systems as references for rule-kit design. This is a comparison for learning, not a recommendation to copy either system wholesale.

> Current-state note, 2026-05-02: this is historical comparison research. The current RuleKit implementation is RuleKit-native for Dynamic Config v1 and does not copy Dynamic Config or Assigno semantics wholesale.

## Short Answer

- `dynamic-config` is stronger as a precedent for:
  - shared evaluation-core discipline
  - Java SDK + Spring starter packaging
  - config lifecycle, approval, versioning, and rollback
  - simpler operator UX with ordered rules and AND-only criteria inside each rule
- `Assigno` is stronger as a precedent for:
  - typed condition-builder UI
  - rule catalog concepts
  - richer conditional logic
  - explainability and “why did this match?” reporting

If the question is “which one is a better direct model for a user-friendly rule-kit?”, `dynamic-config` is closer in spirit.

If the question is “which one has more rule-builder features today?”, `Assigno` is richer but also riskier and more confusing.

## What Exactly Is Being Compared

### Dynamic Config

Relevant files:

- [ConfigEvaluationEngine.java](/Users/user/Desktop/cars24/projects/dynamic-config/dynamic-config-core/src/main/java/com/cars24/dynamicconfig/core/evaluator/ConfigEvaluationEngine.java)
- [ConditionOperatorEvaluator.java](/Users/user/Desktop/cars24/projects/dynamic-config/dynamic-config-core/src/main/java/com/cars24/dynamicconfig/core/evaluator/ConditionOperatorEvaluator.java)
- [Condition.java](/Users/user/Desktop/cars24/projects/dynamic-config/dynamic-config-core/src/main/java/com/cars24/dynamicconfig/core/model/Condition.java)
- [ConditionValue.java](/Users/user/Desktop/cars24/projects/dynamic-config/dynamic-config-core/src/main/java/com/cars24/dynamicconfig/core/model/ConditionValue.java)
- [LocalConfigEvaluator.java](/Users/user/Desktop/cars24/projects/dynamic-config/dynamic-config-sdk/src/main/java/com/cars24/dynamicconfig/sdk/evaluator/LocalConfigEvaluator.java)
- [ARCHITECTURE.md](/Users/user/Desktop/cars24/projects/dynamic-config/ARCHITECTURE.md)

The engine is basically:

- ordered rules
- AND logic inside each rule via `conditionValues`
- first matched rule wins
- default value fallback
- optional force-enable, force-disable, kill switch, segment targeting, and percentage rollout

Example:

```json
{
  "conditions": [
    {
      "ruleName": "premium-us-users",
      "order": 1,
      "conditionValues": [
        { "key": "plan", "operator": "EQUALS", "value": "premium" },
        { "key": "region", "operator": "EQUALS", "value": "US" }
      ],
      "percentageSplit": {
        "percentage": 100,
        "configJson": { "version": "v2" }
      }
    }
  ],
  "defaultValue": { "version": "v1" }
}
```

### Assigno

Relevant files:

- [RuleEvaluationService.java](/Users/user/Desktop/cars24/projects/c24-assigno-service/assigno-api/src/main/java/com/cars24/assigno/api/service/RuleEvaluationService.java)
- [GroupResolverService.java](/Users/user/Desktop/cars24/projects/c24-assigno-service/assigno-api/src/main/java/com/cars24/assigno/api/service/GroupResolverService.java)
- [RuleCondition.java](/Users/user/Desktop/cars24/projects/c24-assigno-service/assigno-common/src/main/java/com/cars24/assigno/common/config/RuleCondition.java)
- [DerivedFieldRule.java](/Users/user/Desktop/cars24/projects/c24-assigno-service/assigno-common/src/main/java/com/cars24/assigno/common/config/DerivedFieldRule.java)
- [RuleBuilder.tsx](/Users/user/Desktop/cars24/projects/c24-assigno-service/assigno-frontend/src/pages/rules/RuleBuilder.tsx)
- [ConditionBuilder.tsx](/Users/user/Desktop/cars24/projects/c24-assigno-service/assigno-frontend/src/components/rules/ConditionBuilder.tsx)
- [constraintTemplates.ts](/Users/user/Desktop/cars24/projects/c24-assigno-service/assigno-frontend/src/lib/constraintTemplates.ts)

Assigno actually has two related rule layers:

1. A lightweight field-based rule engine for derived fields and group routing
2. A much heavier solver-constraint system around OptaPlanner

The lightweight rule engine is the part relevant to us.

Example:

```json
{
  "name": "priority_boost",
  "outputField": "allocationTag",
  "priority": 100,
  "conditions": [
    {
      "field": "storeId",
      "operator": "IN",
      "value": ["1888", "1162"],
      "and": [
        {
          "field": "isPll",
          "operator": "EQ",
          "value": true
        }
      ]
    }
  ],
  "outputValue": "RETAIL"
}
```

## Comparison Table

| Dimension | Dynamic Config | Assigno |
|---|---|---|
| Core rule shape | Ordered rules, AND-only criteria inside a rule | Ordered rules plus nested AND/OR trees |
| Match semantics | First matching rule returns config value | First matching rule per output field; first matching group for routing |
| Evaluator reuse | Strong in Java: SDK and server share one core evaluation engine | Weak across surfaces: backend evaluator exists, frontend is a separate builder UI |
| Frontend local parity | React SDK calls server API, not same local engine | No evidence of shared FE evaluator or dry-run simulation |
| UX complexity | Lower | Higher |
| Authoring flexibility | Moderate | High |
| Packaging quality | Strong: core, sdk, starter, server, React SDK/admin split | Mixed: rule builder exists, but product is tightly coupled to broader platform concerns |
| Versioning / approval / rollback | Strong | Much weaker as a first-class rule-lifecycle system |
| Explainability | Moderate | Strong |
| Fit for your requested mental model | Closer | Further |

## Dynamic Config Strengths

### 1. Simpler authoring model

Dynamic Config’s `Condition` model is:

- rule list is ordered
- each rule contains a list of condition checks
- all condition checks inside the rule are ANDed

That is much closer to your requested product mental model:

- OR across rules
- AND inside a rule

Why this is good:

- Easier for operators to read
- Easier to explain in UI
- Easier to debug with a trace

### 2. Strong shared-core discipline

[ConfigEvaluationEngine.java](/Users/user/Desktop/cars24/projects/dynamic-config/dynamic-config-core/src/main/java/com/cars24/dynamicconfig/core/evaluator/ConfigEvaluationEngine.java) is documented as the single source of truth for evaluation logic, and [LocalConfigEvaluator.java](/Users/user/Desktop/cars24/projects/dynamic-config/dynamic-config-sdk/src/main/java/com/cars24/dynamicconfig/sdk/evaluator/LocalConfigEvaluator.java) delegates to it.

Why this is good:

- Low drift risk inside Java surfaces
- Clean extraction point for a future rule-kit evaluator core

### 3. Better packaging precedent

`dynamic-config` already has:

- `core`
- `sdk`
- `starter`
- `server`
- React SDK
- admin UI

Why this is good:

- This is a strong architectural precedent for how rule-kit itself should be packaged

### 4. Better lifecycle management

The technical docs describe:

- approval flow
- audit logs
- immutable version history
- rollback
- cache invalidation

Why this is good:

- Rule-kit will likely need exactly these lifecycle features for published rule revisions

### 5. Cleaner operator abstraction

[ConditionOperatorEvaluator.java](/Users/user/Desktop/cars24/projects/dynamic-config/dynamic-config-core/src/main/java/com/cars24/dynamicconfig/core/evaluator/ConditionOperatorEvaluator.java) centralizes operator behavior.

Why this is good:

- Easier to maintain semantic consistency
- Easier to add traceability around each operator

## Dynamic Config Gaps

### 1. It is still a config/feature-flag engine, not a generic rule-kit

It has concepts like:

- force enable
- force disable
- kill switch
- segment targeting
- scheduled rollouts
- percentage split

Why this is a gap:

- These are not the same as generic rule responses
- The engine is return-value-oriented for config delivery, not an operator-centric rule authoring product

### 2. React SDK does not appear to share the evaluator core

[hooks.ts](/Users/user/Desktop/cars24/projects/dynamic-config/frontend/packages/sdk/src/hooks.ts) calls `client.evaluate(...)`, and the React SDK appears to fetch evaluation from the server instead of using the same local engine in the browser.

Why this matters for your requirement:

- It does not fully solve “same FE and BE evaluation locally”
- It solves consistency by server evaluation, not by one local shared evaluator

### 3. Rule expressiveness is intentionally narrower

There is no arbitrary user-authored nested boolean tree in the main model.

Why this is both good and limiting:

- Good for usability
- Limiting for edge cases that need richer boolean logic

## Assigno Strengths

### 1. Richer condition model

[RuleCondition.java](/Users/user/Desktop/cars24/projects/c24-assigno-service/assigno-common/src/main/java/com/cars24/assigno/common/config/RuleCondition.java) supports:

- leaf predicates
- nested `and`
- nested `or`
- dot-path field references
- ranges, regex, collection operators, existence operators

Why this is good:

- More expressive
- More adaptable to unusual domain logic

### 2. Better rule-builder UX precedent

[ConditionBuilder.tsx](/Users/user/Desktop/cars24/projects/c24-assigno-service/assigno-frontend/src/components/rules/ConditionBuilder.tsx) already does:

- operators by field type
- nested condition editing
- multi-value inputs
- field-aware operator choices

Why this is good:

- This is a useful UI reference for a React-based rule builder

### 3. Strong explainability model

The combination of:

- [RuleEvaluationService.java](/Users/user/Desktop/cars24/projects/c24-assigno-service/assigno-api/src/main/java/com/cars24/assigno/api/service/RuleEvaluationService.java)
- [GroupResolverService.java](/Users/user/Desktop/cars24/projects/c24-assigno-service/assigno-api/src/main/java/com/cars24/assigno/api/service/GroupResolverService.java)
- `AssignmentReason` and related panels

shows a good pattern for:

- matched rules
- match reasons
- audit display

Why this is good:

- Rule-kit will benefit from similar “why did this rule win?” traces

### 4. Better replacement path for Assigno itself

Because Assigno already has:

- derived-field rule evaluation
- group routing

our future rule-kit SDK could replace those slices without replacing:

- OptaPlanner
- queues
- auth
- callbacks

That is useful if one goal is eventual internal replacement.

## Assigno Gaps

### 1. Unrestricted nested `AND`/`OR` is a UX problem

This is the concern you explicitly raised, and I agree.

Why it is a gap:

- Operators can build deeply nested logic that is technically correct but hard to review
- The mental model becomes:
  - tree reasoning
  - parent-child boolean semantics
  - nesting depth
instead of the simpler:
  - rule list
  - each rule has conditions
  - first match wins

This is one of the biggest reasons I would not use Assigno’s condition model as the default authoring contract for rule-kit.

### 2. No strong FE/BE shared evaluator story

Assigno has:

- backend evaluator
- frontend builder

But not an obvious same-core FE/BE evaluation path.

Why this is a gap:

- Your requirement is explicit:
  - preview and simulation in FE must use the same evaluation semantics as BE

### 3. Much heavier system coupling

Assigno’s rule logic lives inside a bigger platform:

- OptaPlanner
- Mongo
- Spring
- tenant and namespace configuration
- auth
- callbacks
- assignment analytics

Why this is a gap:

- Harder to extract as a clean embeddable SDK
- Harder to reuse without dragging domain baggage

### 4. Too much expressiveness for mainstream operators

What is powerful for platform engineers is often too much for day-to-day users.

Why this is a gap:

- You already flagged the main issue:
  - arbitrary nesting becomes confusing

## Pros And Cons Summary

### Dynamic Config Pros

- Simpler rule mental model
- Strong Java shared-core evaluator precedent
- Better SDK/starter/server packaging precedent
- Better approval/version/rollback precedent
- Easier operator understanding

### Dynamic Config Cons

- Not a generic rule engine by design
- React SDK is server-evaluated, not same local evaluator
- Feature-flag semantics are mixed into the model
- Less expressive for advanced boolean logic

### Assigno Pros

- Rich condition expressiveness
- Strong React rule-builder reference
- Better typed condition-builder precedent
- Better explainability patterns
- Easier future replacement target for internal Assigno rule slices

### Assigno Cons

- Too much nesting freedom
- No strong FE/BE same-evaluator path
- Heavier platform coupling
- Solver and assignment domain assumptions leak into the architecture

## Which Is Closer To The Rule-Kit You Want?

For your stated product shape:

- OR across rules
- AND inside a rule
- first satisfied rule wins
- user-friendly authoring
- same FE and BE evaluation semantics

`dynamic-config` is closer in mental model.

But `Assigno` is more useful for:

- how to build the React authoring experience
- how to expose typed operators by field type
- how to show explainability and matched-rule reasoning

## Recommendation

Use them asymmetrically:

### Borrow from Dynamic Config

- shared evaluation-core discipline
- Java SDK/starter/server packaging
- audit/version/approval lifecycle
- simpler ordered rule model

### Borrow from Assigno

- typed React condition builder patterns
- rule-catalog and template UX
- explainability and audit display ideas

### Avoid from both

- Do not adopt Dynamic Config’s feature-flag-specific semantics as the rule-kit contract
- Do not adopt Assigno’s unrestricted nested boolean trees as the default rule model

## Bottom Line

If I had to say it in one line:

- `dynamic-config` is the better architectural reference
- `Assigno` is the better UI-pattern reference

That is the cleanest way to use both without getting trapped by either.

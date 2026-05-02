# Lead Service Java RuleKit Migration Note

Date: 2026-05-01

> Scope note, 2026-05-02: this is a separate Lead Service migration note, not the Dynamic Config v1 RuleKit-native handoff. Code examples should follow the current Java package map in `docs/README.md` and `README.md`.

## Scope And Assumption

I did not find an exact `tooling gen` or `tooling-gen` symbol in `lead-service-java`. The closest lightweight rule-like implementation is the product eligibility configuration under `lead-monetization`, especially:

- `lead-monetization/src/main/java/com/cars24/leadmonetization/service/oms/product/filter/ProductEligibilityFilter.java`
- `lead-core/src/main/java/com/cars24/core/dto/internal/common/ProductConfig.java`
- `lead-monetization/src/main/resources/config/product-eligibility-config.json`
- `lead-monetization/src/main/java/com/cars24/leadmonetization/service/oms/cart/enricher/PriceFilterEnricher.java`

There is also a JOLT transformer system in `lead-core/src/main/java/com/cars24/core/service/common/impl/JoltTransformerService.java`. RuleKit should not replace JOLT transformation. RuleKit evaluates rules; it does not reshape request or response payloads.

So this migration note assumes the target is the lightweight product eligibility rule engine, not the JOLT transformer layer.

## Current Lead Service Behavior

Lead Service currently has one combined `ProductConfig` model that owns two different concerns:

- Product eligibility rules: decide whether a FusePass product should be visible or purchasable for a lead context.
- Price filter generation rules: generate FusePass `priceFilter.filterAttributes` such as `centre_<locationId>`, `state_<stateId>`, and quote buckets.

The config is loaded from AWS AppConfig key `PRODUCT_CONFIG`, falling back to the local file `config/product-eligibility-config.json`.

Eligibility evaluation currently works like this:

- `ProductEligibilityFilter.filter(...)` evaluates a list of `FusePassProductDto` products after FusePass returns them.
- `ProductEligibilityFilter.isEligible(...)` evaluates one product during product-detail flow.
- `LeadMonetizationContext.toMap()` becomes the runtime context map.
- Each product has metadata from FusePass; criteria compare context values against either product metadata values or config literal values.
- Criteria without `orGroup` are AND-ed.
- Criteria with the same `orGroup` are OR-ed inside that group.
- Different OR groups are AND-ed with each other and with ungrouped criteria.
- `SEGMENT` and `NOT_SEGMENT` use `SegmentClient.membershipCheckBulk(...)`, pre-resolved per product list.
- Optional missing context or metadata generally skips a criterion; mandatory missing context fails.

Example current rule:

```json
{
  "metadataField": "daysSinceInspection",
  "contextField": "daysSinceInspection",
  "operator": "LT",
  "orGroup": "visibility"
}
```

Meaning:

```text
context.daysSinceInspection < product.metadata.daysSinceInspection
```

## Recommended Target Shape

Do not make RuleKit own Lead Service product concepts. Lead Service should keep ownership of:

- AWS AppConfig and local fallback loading.
- FusePass product metadata.
- Segment bulk membership calls.
- Product sorting.
- Price filter attribute generation.
- Product-specific inheritance via `extendsFrom`.
- Existing API behavior and logs.

RuleKit should replace only the generic rule evaluation mechanics:

- operator evaluation
- AND evaluation inside a rule
- OR across generated RuleKit rules
- traces for debugging product exclusion mismatches
- validation of generated rulesets

Recommended target service boundary:

```text
ProductEligibilityFilter
  -> ProductEligibilityRuleKitAdapter
       -> ProductConfigRuleSetMapper
       -> RuleKitClient
       -> LeadProductFactResolver
  -> existing SegmentClient bulk prefetch remains in Lead Service
  -> existing sort remains in Lead Service

PriceFilterEnricher remains as-is
JoltTransformerService remains as-is
```

## How RuleKit Would Fit

### Option A: Adapter Generates Product-Specific RuleSets

This is the lowest-risk migration because it preserves current config shape and behavior.

For each product being evaluated:

1. Resolve inherited criteria from `ProductConfig.rules.<productCode>`.
2. Apply current skip semantics before building RuleKit conditions:
   - optional null context value: skip condition
   - missing product metadata field: skip condition
   - mandatory null context value: return not eligible or generate a failing condition
3. Resolve metadata-backed expected values into literals.
4. Convert criteria into one or more RuleKit rules.
5. Evaluate using `RuleKitClient.evaluate(...)`.

Example input to RuleKit:

```json
{
  "context": {
    "locationId": 123,
    "daysSinceInspection": 2,
    "isGsAssured": true
  },
  "product": {
    "code": "SELL_LITE"
  }
}
```

Example generated RuleKit condition:

```json
{
  "fieldRef": "context.daysSinceInspection",
  "operator": "LT",
  "value": 3
}
```

This comes from:

```json
{
  "metadataField": "daysSinceInspection",
  "contextField": "daysSinceInspection",
  "operator": "LT"
}
```

Where `value: 3` was read from `product.metadata.daysSinceInspection`.

### Representing OR Groups

Current Lead Service supports OR inside a named group and AND across groups. RuleKit supports OR across rules and AND inside each rule.

Mapping:

```text
Lead criteria:
  ungrouped A AND ungrouped B AND (C OR D OR E)

RuleKit rules:
  rule 1: A AND B AND C
  rule 2: A AND B AND D
  rule 3: A AND B AND E
```

If there are multiple OR groups, the adapter generates the cartesian product:

```text
A AND (B OR C) AND (D OR E)

RuleKit rules:
  A AND B AND D
  A AND B AND E
  A AND C AND D
  A AND C AND E
```

This is acceptable for the current config because the observed config has one group named `visibility` with a small number of alternatives. If future configs add many OR groups, rule expansion can grow quickly.

### Segment Operators

RuleKit should not call Segment Service directly.

Lead Service should keep the existing bulk prefetch:

```text
SegmentClient.membershipCheckBulk(...)
```

Then expose segment membership to RuleKit through a host `FactResolver`.

Example generated condition:

```json
{
  "fieldRef": "_segments.payLaterSegmentId.member",
  "operator": "EQ",
  "value": false
}
```

Resolver behavior:

```java
FactResolver resolver = FactResolver.contextual(context -> {
    if (context.fieldRef().startsWith("_segments.")) {
        boolean member = segmentMembershipLookup.resolve(context.fieldRef(), product, leadContext);
        return ResolvedFact.found(BooleanNode.valueOf(member));
    }
    return FactResolver.defaultResolver().resolve(context);
});
```

This preserves Lead Service ownership of segment lookup while letting RuleKit handle condition evaluation and trace output.

## Operator Mapping

| Lead `EligibilityOperator` | RuleKit operator | Notes |
| --- | --- | --- |
| `IN` | `IN` | Keep string comparison behavior. |
| `NOT_IN` | `NOT_IN` | Keep string comparison behavior. |
| `EQUALS` | `EQ` | Keep string comparison behavior. |
| `NOT_EQUALS` | `NEQ` | Keep string comparison behavior. |
| `LT` | `LT` | Context value is actual, metadata/config value is expected. |
| `LTE` | `LTE` | Same as above. |
| `GT` | `GT` | Same as above. |
| `GTE` | `GTE` | Same as above. |
| `RANGE` | `BETWEEN` | Adapter maps `{ "min": x, "max": y }` to `value=x`, `valueTo=y`. Needs parity decision for open-ended bounds. |
| `SEGMENT` | `EQ true` over resolver fact | Host resolver returns membership boolean. |
| `NOT_SEGMENT` | `EQ false` over resolver fact | Host resolver returns membership boolean. |

## Required Changes In Lead Service

### Build And Dependency Changes

Lead Service is Java 17 and Spring Boot 2.5.5. RuleKit targets Java 17 bytecode, so Lead Service can consume the bundled SDK artifact. The Spring Boot starter remains Spring Boot 3-oriented and should not be used directly in Lead Service unless a Spring Boot 2 compatible starter is added.

Recommended dependency approach:

- Use only `rule-kit-sdk` in `lead-monetization`; it bundles RuleKit's internal core module.
- Do not use `rule-kit-spring-boot-starter` in Lead Service unless a Spring Boot 2 compatible starter is added.
- Instantiate `RuleKitClient` manually as a Lead Service bean.

Lead Service module likely touched:

```text
lead-monetization/pom.xml
```

Example desired dependency shape:

```xml
<dependency>
  <groupId>com.cars24.rulekit</groupId>
  <artifactId>rule-kit-sdk</artifactId>
  <version>${rule-kit.version}</version>
</dependency>
```

### New Adapter Classes

Add these under `lead-monetization`:

```text
ProductEligibilityRuleKitAdapter
ProductConfigRuleSetMapper
LeadProductRuleKitInputBuilder
LeadProductFactResolverFactory
RuleKitProductEligibilityResult
```

Suggested responsibilities:

- `ProductEligibilityRuleKitAdapter`: high-level API used by `ProductEligibilityFilter`.
- `ProductConfigRuleSetMapper`: converts resolved `EligibilityCriterion` lists into RuleKit `RuleSet`.
- `LeadProductRuleKitInputBuilder`: builds RuleKit input JSON from `LeadMonetizationContext` and product details.
- `LeadProductFactResolverFactory`: exposes `_segments.*.member` facts from the pre-resolved segment membership map.
- `RuleKitProductEligibilityResult`: wraps eligible boolean, matched rule id, default used, and verbose trace if needed.

### ProductEligibilityFilter Changes

Keep these responsibilities in `ProductEligibilityFilter`:

- loading `ProductConfig`
- `refreshConfig()`
- local fallback
- segment bulk prefetch
- applying sort
- public methods `filter(...)`, `isEligible(...)`, and `getConfig()`

Replace this internal logic:

```text
getExclusionReason(...)
evaluateCriterionWithReason(...)
evaluate(...)
evaluateIn(...)
evaluateEquals(...)
evaluateNumericComparison(...)
evaluateRange(...)
evaluateSegment(...)
```

With:

```text
ruleKitAdapter.evaluate(product, context, config, segmentMembership)
```

### PriceFilterEnricher Changes

Do not migrate `PriceFilterEnricher` to RuleKit in this pass.

It is not a rule evaluator; it generates FusePass filter attributes from context and config:

- `PREFIX_VALUE`
- `RANGE_BUCKET`
- inheritance via `extendsFrom`
- merging existing JOLT-generated filter attributes

It can continue reading `ProductConfig.priceFilterAttributes`.

Long-term cleanup option:

```text
ProductConfig
  eligibilityRules     -> RuleKit migration path
  priceFilterRules     -> stays Lead Service owned
```

### Config Changes

Lowest-risk approach:

- Keep current `PRODUCT_CONFIG` JSON shape.
- Convert it to RuleKit in memory at runtime.
- Do not ask business/config owners to rewrite config on day one.

Later, if the team wants a cleaner config:

```json
{
  "sort": {},
  "priceFilterRules": {},
  "eligibilityRuleSets": {
    "SELL_LITE": { "schemaVersion": "rule-kit.ruleset.v1", "...": "..." }
  }
}
```

But this is a larger migration because current criteria compare context fields to product metadata fields.

## Abstract Migration Flow

1. Add the RuleKit SDK dependency to `lead-monetization`.
2. Add `ProductEligibilityRuleKitAdapter` behind the current `ProductEligibilityFilter`.
3. Keep current evaluator and RuleKit evaluator side-by-side.
4. In shadow mode, evaluate both and log mismatches:

```text
productCode
legacyEligible
ruleKitEligible
legacyReason
matchedRuleId
trace
context summary
metadata keys
```

5. Build contract tests from current `ProductEligibilityFilterTest` cases.
6. Add a feature flag:

```text
productEligibility.engine = LEGACY | SHADOW | RULEKIT
```

7. Run shadow in staging and production for representative traffic.
8. Flip reads to RuleKit only after mismatch rate is acceptable.
9. Keep legacy evaluator for rollback for at least one release.
10. Remove legacy operator methods after rollout confidence.

## Expected Code Diff Shape

Most Lead Service changes should stay inside `lead-monetization`.

Expected touched files:

```text
lead-monetization/pom.xml
lead-monetization/src/main/java/com/cars24/leadmonetization/service/oms/product/filter/ProductEligibilityFilter.java
lead-monetization/src/main/java/com/cars24/leadmonetization/service/oms/product/filter/rulekit/ProductEligibilityRuleKitAdapter.java
lead-monetization/src/main/java/com/cars24/leadmonetization/service/oms/product/filter/rulekit/ProductConfigRuleSetMapper.java
lead-monetization/src/main/java/com/cars24/leadmonetization/service/oms/product/filter/rulekit/LeadProductRuleKitInputBuilder.java
lead-monetization/src/main/java/com/cars24/leadmonetization/service/oms/product/filter/rulekit/LeadProductFactResolverFactory.java
lead-monetization/src/test/java/com/cars24/leadmonetization/service/oms/product/filter/ProductEligibilityFilterTest.java
lead-monetization/src/test/java/com/cars24/leadmonetization/service/oms/product/filter/rulekit/ProductEligibilityRuleKitAdapterTest.java
```

Files that should mostly remain unchanged:

```text
lead-core/src/main/java/com/cars24/core/service/common/impl/JoltTransformerService.java
lead-monetization/src/main/java/com/cars24/leadmonetization/service/oms/cart/enricher/PriceFilterEnricher.java
lead-monetization/src/main/resources/config/product-eligibility-config.json
```

## Challenges And Potential Blockers

### 1. Spring Boot Starter Compatibility

This is the main compatibility constraint.

Lead Service uses Spring Boot 2.5.5. The RuleKit SDK targets Java 17 bytecode and can be consumed by Lead Service, but the RuleKit Spring Boot starter is Spring Boot 3-oriented.

Resolution:

- Avoid the Spring Boot 3 starter in Lead Service.
- Create a simple Lead Service `@Configuration` class that exposes `RuleKitClient`.

### 2. Current Rules Compare Field-To-Field

Lead criteria often compare:

```text
context field vs product metadata field
```

RuleKit currently compares:

```text
fieldRef vs literal value
```

This is manageable, but it means the Lead adapter must either:

- generate a product-specific RuleSet at runtime with metadata values converted into literals, or
- RuleKit must add a generic `valueRef` / right-hand-side field reference feature.

For a lightweight migration, runtime product-specific RuleSet generation is acceptable. For high-volume usage, `valueRef` would be cleaner.

### 3. OR Group Expansion

RuleKit supports OR across rules and AND inside a rule. Lead Service supports named OR groups.

The adapter can expand OR groups into multiple RuleKit rules, but multiple large OR groups can create many rule combinations.

Current config looks small, so this is not a blocker now.

### 4. Skip Semantics Are Product-Specific

Current behavior skips optional criteria when context or metadata is missing. RuleKit generic missing-field behavior will not automatically match that.

The adapter must preserve skip semantics before generating RuleKit conditions.

### 5. Segment Evaluation Must Stay Host-Owned

RuleKit should not depend on Segment Service. Lead Service must keep bulk segment prefetch and expose boolean membership facts through a `FactResolver`.

This is not a blocker, but it is a required integration detail.

### 6. Existing Numeric And Range Behavior Needs Parity Decisions

Current numeric parse failures return `true`, effectively skipping/failing-open for invalid numeric comparisons. RuleKit strict validation would reject invalid numeric literals.

Also, current `RANGE` logic appears to check `minObj` for both lower and upper bound null handling. Migrating to RuleKit `BETWEEN` may change behavior if any config relies on open-ended ranges.

Before active rollout, add parity tests for:

- invalid numeric metadata
- invalid numeric context
- missing `min`
- missing `max`
- both bounds present
- boundary equality

### 7. Price Filter Generation Is Not RuleKit Scope

`PriceFilterEnricher` should remain host-owned. If the goal is to remove the whole `ProductConfig` concept, that is a larger design and RuleKit alone will not replace it.

## Is This Migration Challenging?

It is moderate, not high-risk, if scoped correctly.

What is easy:

- Replacing generic operator evaluation.
- Getting verbose traces for product eligibility decisions.
- Keeping current config shape and building RuleKit RuleSets in memory.
- Keeping segment lookup host-owned.

What is tricky:

- Spring Boot starter compatibility if the team wants auto-configuration instead of a manual bean.
- Preserving current skip/fail-open behavior exactly.
- Mapping context-vs-metadata comparisons without a RuleKit `valueRef` feature.
- Avoiding accidental migration of price filter generation or JOLT transformation into RuleKit scope.

My recommendation:

```text
Phase 1: Add the RuleKit SDK.
Phase 2: Add adapter in shadow mode behind ProductEligibilityFilter.
Phase 3: Build parity tests from ProductEligibilityFilterTest.
Phase 4: Run production shadow mismatch logging.
Phase 5: Flip eligibility evaluation only.
Phase 6: Keep JOLT and PriceFilterEnricher unchanged.
```

## Final Recommendation

Use RuleKit to remove the custom eligibility operator engine inside `ProductEligibilityFilter`, but do not try to remove:

- JOLT transformation
- AppConfig loading
- price filter enrichment
- segment prefetch
- product config inheritance
- product sorting

There is no Java-version blocker after targeting the RuleKit SDK to Java 17. The remaining compatibility constraint is Spring Boot starter usage: Lead Service should use a manual `RuleKitClient` bean unless a Spring Boot 2 starter is created.

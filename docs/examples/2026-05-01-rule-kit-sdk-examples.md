# RuleKit SDK Examples

Date: 2026-05-01

This document shows the backend SDK capabilities that a host application can use without giving RuleKit ownership of persistence, tenants, approvals, rollout state, exposure logs, caches, or product-specific concepts.

RuleKit targets Java 17 bytecode. Java 17 and Java 21 host services can consume the same SDK artifact.

For non-Spring host services such as Dynamic Config, add only `com.cars24.rulekit:rule-kit-sdk:1.0.0-SNAPSHOT`. The SDK JAR bundles RuleKit's internal core module, so hosts do not need a separate `rule-kit-core` dependency. Spring Boot 2 services should also create `RuleKitClient` manually instead of using the Spring Boot 3-oriented starter.

## Package Map

Use these packages in host applications:

```java
import com.cars24.rulekit.core.evaluation.CompiledRuleSet;
import com.cars24.rulekit.core.evaluation.EvaluationOptions;
import com.cars24.rulekit.core.evaluation.EvaluationResult;
import com.cars24.rulekit.core.model.RuleSet;
import com.cars24.rulekit.core.resolver.FactResolver;
import com.cars24.rulekit.core.resolver.SegmentMembershipResult;
import com.cars24.rulekit.core.trace.TraceMode;
import com.cars24.rulekit.core.validation.ValidationResult;
import com.cars24.rulekit.sdk.RuleKitClient;
```

## 1. Minimal Java Evaluation

Use this when the host already has a `RuleSet` object and request facts as `JsonNode`.

```java
ObjectMapper objectMapper = new ObjectMapper();
RuleKitClient client = new RuleKitClient(objectMapper);

RuleSet ruleSet = objectMapper.readValue(ruleSetJson, RuleSet.class);
JsonNode input = objectMapper.readTree("""
        { "plan": "gold", "cartTotal": 7500 }
        """);

EvaluationResult result = client.evaluate(ruleSet, input, TraceMode.COMPACT);
```

Example outcome:

```json
{
  "matchedRuleId": "gold-high-cart",
  "defaultUsed": false,
  "response": { "discountPercent": 15 }
}
```

## 2. Strict v1 Validation

RuleKit v1 validation is strict by default. Invalid expected operands, malformed regex, reversed ranges, and missing required values are validation errors before rollout.
The canonical payload must include `schemaVersion: "rule-kit.ruleset.v1"`, and RuleKit rejects unknown fields instead of silently accepting typos.

```java
ValidationResult validation = client.validate(ruleSet);
if (!validation.valid()) {
    throw new IllegalStateException(validation.errors().toString());
}
```

Examples of validation errors:

```text
MISSING_CONDITION_VALUE      GT without value
MISSING_CONDITION_VALUE_TO   BETWEEN without valueTo
INVALID_REGEX_PATTERN        MATCHES with malformed regex
INVALID_NUMERIC_VALUE        numeric operator has a non-numeric expected value
INVALID_RANGE                BETWEEN value is greater than valueTo
```

Operators must use RuleKit canonical names such as `EQ`, `NEQ`, `GT`, `GTE`, `IN_CASE_INSENSITIVE`, `MATCHES`, and `NOT_EXISTS`. Host-specific aliases such as `EQUALS`, `GREATER_THAN`, or typo variants are not part of RuleKit v1.

## 3. Compile Once, Evaluate Many Times

Use `CompiledRuleSet` on hot paths. It validates once, sorts enabled rules once, deep-copies source data, normalizes operators, precomputes list values and numeric constants, and precompiles regex patterns.

```java
CompiledRuleSet compiled = client.compile(ruleSet);

EvaluationResult result = client.evaluate(
        compiled,
        input,
        TraceMode.COMPACT
);
```

Host cache example:

```java
CompiledRuleSet compiled = compiledRuleSetCache.get(configVersion);
if (compiled == null) {
    compiled = client.compile(ruleSet);
    compiledRuleSetCache.put(configVersion, compiled);
}
```

## 4. Verbose Simulation Traces

Use verbose trace for preview, admin simulation, and shadow mismatch debugging.

```java
EvaluationResult result = client.evaluate(compiled, input, TraceMode.VERBOSE);
```

Verbose trace includes evaluated rules, condition field refs, operators, expected values, actual values, match result, resolved status, skipped conditions, schema version, and evaluator version.

## 5. JSON String And JsonNode APIs

String input is useful at integration boundaries.

```java
JsonNode result = client.evaluateToJson(ruleSetJson, inputJson, TraceMode.VERBOSE);
```

`JsonNode` input is useful when the host already parsed payloads.

```java
EvaluationResult result = client.evaluate(ruleSetJsonNode, inputNode, TraceMode.COMPACT);
```

Malformed JSON and invalid RuleSet payloads are surfaced with structured `RuleKitClientException.code()`.

```java
try {
    client.validate("{");
} catch (RuleKitClientException e) {
    if (e.code() == RuleKitExceptionCode.INVALID_JSON_PAYLOAD) {
        // return a host-specific 400 response
    }
}
```

## 6. YAML RuleSet Loading

RuleKit does not require YAML, but hosts can load YAML into the same canonical `RuleSet` model.

```java
ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
RuleSet ruleSet = yamlMapper.readValue(resource.getInputStream(), RuleSet.class);

CompiledRuleSet compiled = client.compile(ruleSet);
EvaluationResult result = client.evaluate(compiled, input, TraceMode.VERBOSE);
```

Runnable example:

```bash
mvn -pl rule-kit-java/rule-kit-sample-app -am spring-boot:run
curl "http://localhost:8080/rule-kit/sample/evaluate?plan=gold&city=Gurgaon&cartTotal=7500&traceMode=VERBOSE"
```

The sample YAML file lives at `rule-kit-java/rule-kit-sample-app/src/main/resources/rule-kit/sample-pricing-rules.yaml`.

## 7. Literal And Escaped Dotted Field References

RuleKit first checks for a literal key, then falls back to nested path lookup.

```json
{
  "customer.tier": "literal-vip",
  "customer": { "tier": "nested-vip" }
}
```

Examples:

```java
// Resolves the literal root key if present.
fieldRef = "customer.tier";

// Also resolves the literal key explicitly.
fieldRef = "customer\\.tier";

// Resolves nested object key with escaped segment.
fieldRef = "customer.address\\.city";
```

## 8. Lazy Host Facts

Use this for facts RuleKit must not own, such as Dynamic Config gate results.

Simple field resolver:

```java
FactResolver resolver = (fieldRef, input) -> {
    if (fieldRef.startsWith("_gates.")) {
        return ResolvedFact.found(BooleanNode.valueOf(resolveGate(fieldRef)));
    }
    return FactResolver.defaultResolver().resolve(fieldRef, input);
};
```

Context-aware resolver:

```java
FactResolver resolver = FactResolver.contextual(context -> {
    if (context.fieldRef().startsWith("_gates.")) {
        boolean matched = gateResolver.resolve(context.ruleId(), context.fieldRef());
        return ResolvedFact.found(BooleanNode.valueOf(matched));
    }
    return FactResolver.defaultResolver().resolve(context);
});
```

RuleKit only calls the resolver for conditions that are actually reached. In an `AND` rule, later conditions are skipped after the first failure.

## 9. Host-Provided RuleSetSource

Use `RuleSetSource` when a host wants RuleKit to evaluate by ID while still owning storage.

```java
RuleSetSource source = ruleSetId -> repository.findPublishedRuleSet(ruleSetId);
RuleKitClient client = new RuleKitClient(objectMapper, source);

EvaluationResult result = client.evaluateById(
        "checkout-pricing",
        input,
        TraceMode.COMPACT
);
```

Missing source or missing rulesets return structured errors:

```text
RULESET_SOURCE_NOT_CONFIGURED
RULESET_NOT_FOUND
```

## 10. Segment, Dependency, And Rollout Conditions

RuleKit v1 supports host-owned segments and dependencies without owning segment storage or config persistence.

```json
{
  "id": "tenant-a.checkout-banner",
  "schemaVersion": "rule-kit.ruleset.v1",
  "executionMode": "FIRST_MATCH",
  "defaultResponse": false,
  "rules": [
    {
      "id": "android-beta",
      "priority": 100,
      "enabled": true,
      "when": {
        "all": [
          { "kind": "FIELD", "fieldRef": "platform", "operator": "EQ", "value": "android" },
          { "kind": "SEGMENT", "segmentNames": ["beta-users"], "match": "ANY", "lookupRef": "userId" },
          { "kind": "DEPENDENCY", "ruleSetId": "tenant-a.checkout-enabled", "expect": "MATCHED" }
        ]
      },
      "rollout": {
        "percentage": 25,
        "unitRef": "userId",
        "algorithm": "MURMUR3_32_SALTED_V1",
        "bucketCount": 100
      },
      "then": { "response": true }
    }
  ]
}
```

```java
EvaluationOptions options = EvaluationOptions.builder()
        .factResolver(dynamicConfigFactResolver)
        .segmentResolver(context -> SegmentMembershipResult.of(Map.of("beta-users", true)))
        .dependencyResolver(context -> Optional.of(compiledCheckoutEnabledRuleSet))
        .build();

EvaluationResult result = client.evaluate(compiled, input, TraceMode.VERBOSE, options);
```

Verbose traces include condition kind, segment match details, dependency result details, and rollout bucket details.
Rollout resolves `tenantId`, `configName` or `configId`, `ruleId`, `splitSeed`, and the `unitRef` entity through the configured `FactResolver`; missing rollout salt or entity values fail with typed RuleKit exceptions.

## 11. Spring Boot Starter

The starter provides `RuleKitClient`, `SimulationService`, and `RuleKitProperties`.

```yaml
rule-kit:
  default-trace-mode: VERBOSE
```

```java
@Service
class PricingService {
    private final RuleKitClient ruleKitClient;

    PricingService(RuleKitClient ruleKitClient) {
        this.ruleKitClient = ruleKitClient;
    }

    EvaluationResult evaluate(CompiledRuleSet compiled, JsonNode input) {
        return ruleKitClient.evaluate(compiled, input, null);
    }
}
```

Passing `null` trace mode uses the configured default trace mode.

## 12. Sample App Endpoints

The sample app exposes:

```text
GET  /rule-kit/sample/ruleset
GET  /rule-kit/sample/evaluate?plan=gold&city=Gurgaon&cartTotal=7500&traceMode=VERBOSE
POST /rule-kit/sample/simulate
```

Simulation request example:

```json
{
  "input": {
    "plan": "gold",
    "city": "Gurgaon",
    "cartTotal": 7500
  }
}
```

The `ruleSet` field is optional in `/simulate`; if omitted, the sample YAML RuleSet is used.

## 13. Benchmark Harness

RuleKit includes a JMH module for SDK-local benchmarks.

```bash
mvn -pl rule-kit-java/rule-kit-benchmarks -am package -DskipTests
java -cp rule-kit-java/rule-kit-benchmarks/target/rule-kit-benchmarks.jar org.openjdk.jmh.Main
```

Use this to measure cold raw evaluation, warm compiled compact evaluation, warm verbose evaluation, regex cost, and no-match worst-case behavior before active rollout.

The first local benchmark artifact is archived at `docs/performance/artifacts/2026-05-02-rulekit-jmh/report.md`.

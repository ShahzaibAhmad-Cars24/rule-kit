# Rule Kit

Rule Kit is a database-agnostic rule evaluation SDK for CARS24 applications.

The current backend implementation provides:

- Java 17-compatible SDK artifact
- canonical `RuleSet` JSON contract
- Java evaluator core
- Java SDK facade
- Spring Boot starter
- sample Spring Boot app with YAML RuleSet loading
- JMH benchmark scaffolding
- dynamic-config backend migration plan
- RuleSet validation
- typed RuleKit exceptions
- reusable compiled RuleSets
- lazy host-provided fact resolution
- literal and escaped dotted field references
- strict native v1 operator semantics
- rule-level deterministic rollout bucketing
- segment and dependency condition hooks
- strict unknown-field rejection for RuleSet payloads
- verbose rollout/segment/dependency traces

The SDK does not own persistence or product control-plane behavior. Host applications own drafts, published revisions, approvals, tenants, audit logs, rollback, cache, exposure logging, and storage technology.

RuleKit targets Java 17 bytecode so Java 17 and Java 21 host services can consume the same SDK. The Spring Boot starter is Spring Boot 3-oriented; Spring Boot 2 host services should create `RuleKitClient` directly instead of using the starter.

Dynamic Config and other non-Spring host services should add one RuleKit dependency:

```xml
<dependency>
    <groupId>com.cars24.rulekit</groupId>
    <artifactId>rule-kit-sdk</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

`rule-kit-sdk` is the client-facing bundled artifact. It shades RuleKit's internal `rule-kit-core` module into the SDK JAR, while external libraries such as Jackson and Guava remain normal Maven dependencies so host applications can manage versions cleanly.

## Package Structure

- `com.cars24.rulekit.core.model`: canonical RuleSet schema records and enums.
- `com.cars24.rulekit.core.evaluation`: evaluator, compiled runtime, evaluation result, and evaluation options.
- `com.cars24.rulekit.core.resolver`: host fact, segment, and dependency resolver contracts.
- `com.cars24.rulekit.core.validation`: RuleSet validation API and validation messages.
- `com.cars24.rulekit.core.trace`: compact and verbose trace DTOs.
- `com.cars24.rulekit.core.operator`: canonical operator metadata and operand parsing helpers.
- `com.cars24.rulekit.core.rollout`: deterministic rollout evaluation.
- `com.cars24.rulekit.core.exception`: typed RuleKit exceptions and error codes.

## Build And CI

Run the full local test suite:

```bash
mvn test
```

Validate the JSON contract artifacts:

```bash
jq empty rule-kit-contract/schemas/*.json rule-kit-contract/dynamic-config/*.json rule-kit-contract/test-vectors/*.json rule-kit-contract/test-vectors/native/*.json
```

GitHub Actions runs Maven tests on Java 17 and Java 21, then validates the JSON contract and benchmark artifact files.

## Snapshot Publishing

The Maven project version is `1.0.0-SNAPSHOT`.

Snapshot packages are published to GitHub Packages from the `Publish Snapshot Packages` workflow on pushes to `main` and manual dispatches. The workflow publishes only client-facing artifacts:

- `rule-kit-sdk`
- `rule-kit-spring-boot-starter`

`rule-kit-core` remains an internal source module for maintainability and is bundled into `rule-kit-sdk`; clients should not depend on it directly.

Host applications can consume the packages from:

```xml
<repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/ShahzaibAhmad-Cars24/rule-kit</url>
</repository>
```

## Java Usage

```java
ObjectMapper objectMapper = new ObjectMapper();
RuleKitClient client = new RuleKitClient(objectMapper);

JsonNode result = client.evaluateToJson(ruleSetJson, inputJson, TraceMode.VERBOSE);
```

For high-throughput execution:

```java
ValidationResult validation = client.validate(ruleSet);
CompiledRuleSet compiled = client.compile(ruleSet);
EvaluationResult result = client.evaluate(compiled, input, TraceMode.COMPACT);
```

For host-owned facts such as dynamic-config gates:

```java
FactResolver resolver = (fieldRef, input) -> {
    if (fieldRef.startsWith("_gates.")) {
        return ResolvedFact.found(BooleanNode.valueOf(resolveGate(fieldRef)));
    }
    return FactResolver.defaultResolver().resolve(fieldRef, input);
};

EvaluationResult result = client.evaluate(compiled, input, TraceMode.VERBOSE, resolver);
```

For native v1 segment and dependency conditions:

```java
EvaluationOptions options = EvaluationOptions.builder()
        .segmentResolver(segmentResolver)
        .dependencyResolver(dependencyResolver)
        .build();

EvaluationResult result = client.evaluate(compiled, input, TraceMode.VERBOSE, options);
```

For context-aware lazy facts:

```java
FactResolver resolver = FactResolver.contextual(context -> {
    if (context.fieldRef().startsWith("_gates.")) {
        return ResolvedFact.found(BooleanNode.valueOf(resolveGate(context)));
    }
    return FactResolver.defaultResolver().resolve(context);
});
```

## Spring Boot Usage

Add `rule-kit-spring-boot-starter`; the starter exposes:

- `RuleKitClient`
- `SimulationService`
- `RuleKitProperties`

Configure the default trace mode:

```yaml
rule-kit:
  default-trace-mode: COMPACT
```

## Sample App And Examples

- Runnable sample app: `rule-kit-java/rule-kit-sample-app`
- YAML RuleSet example: `rule-kit-java/rule-kit-sample-app/src/main/resources/rule-kit/sample-pricing-rules.yaml`
- SDK examples: `docs/examples/2026-05-01-rule-kit-sdk-examples.md`
- Benchmark instructions: `docs/performance/README.md`

## Evaluation Model

- `FIRST_MATCH`
- rules are evaluated by descending `priority`
- each rule contains an `AND` list in `when.all`
- condition kinds are `FIELD`, `SEGMENT`, and `DEPENDENCY`
- optional rule rollout runs after all conditions pass
- `schemaVersion` is required and must be `rule-kit.ruleset.v1`
- operators use canonical RuleKit names only, for example `EQ`, `GTE`, `IN_CASE_INSENSITIVE`, and `NOT_EXISTS`
- missing or invalid numeric actual values are non-matches, not `0.0`
- the first matching enabled rule returns `then.response`
- if no rule matches, `defaultResponse` is returned

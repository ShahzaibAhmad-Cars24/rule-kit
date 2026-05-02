# RuleKit Research Archive

These documents capture early research and decision exploration. They are useful context, but they are not the current implementation contract.

Current RuleKit backend state:

- Java 17 core and SDK implementation.
- Canonical `rule-kit.ruleset.v1` schema.
- Strict native v1 operator semantics.
- Responsibility-based Java packages under `com.cars24.rulekit.core.*`.
- Rule-level rollout, segment condition hooks, dependency condition hooks, validation, traces, and contract vectors.

Current authoritative docs:

- `../README.md`
- `../examples/2026-05-01-rule-kit-sdk-examples.md`
- `../migration/2026-05-01-dynamic-config-v1-rulekit-native-plan.md`
- `../migration/2026-05-01-rule-kit-sdk-review.md`

Some early research mentions Rust/WASM, Java 21, compatibility adapters, or broader frontend SDK work. Those items are historical options, not the current backend SDK contract.

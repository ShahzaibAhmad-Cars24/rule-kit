# Superseded: Rule Kit Backend SDK And Dynamic Config Migration Plan

This older implementation plan has been completed and superseded by the RuleKit-native Dynamic Config v1 handoff.

Use the current docs instead:

- `docs/migration/2026-05-01-dynamic-config-v1-rulekit-native-plan.md`
- `docs/migration/2026-05-01-dynamic-config-team-rulekit-native-message.md`
- `docs/migration/2026-05-01-rule-kit-sdk-review.md`
- `docs/examples/2026-05-01-rule-kit-sdk-examples.md`

Current RuleKit package structure:

- `com.cars24.rulekit.core.model`: canonical RuleSet schema records and enums.
- `com.cars24.rulekit.core.evaluation`: evaluator, compiled runtime, evaluation result/options.
- `com.cars24.rulekit.core.resolver`: host fact, segment, and dependency resolver contracts.
- `com.cars24.rulekit.core.validation`: RuleSet validation API and messages.
- `com.cars24.rulekit.core.trace`: compact/verbose trace DTOs.
- `com.cars24.rulekit.core.operator`: canonical operator metadata and operand parsing helpers.
- `com.cars24.rulekit.core.rollout`: deterministic rollout evaluation.
- `com.cars24.rulekit.core.exception`: typed RuleKit exceptions and error codes.

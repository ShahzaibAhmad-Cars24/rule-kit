# Superseded: Dynamic Config RuleKit Team Handoff

Date: 2026-05-01
Superseded on: 2026-05-02

This document described an older compatibility-adapter handoff.

Dynamic Config is now also pre-operational, so the current target is RuleKit-native v1. RuleKit should be the execution plane for schema, operators, strict validation, rollout bucketing, dependency primitives, segment primitives, traces, typed exceptions, and contract vectors. Dynamic Config should keep only product control-plane responsibilities outside RuleKit.

Use these current files instead:

- `docs/migration/2026-05-01-dynamic-config-v1-rulekit-native-plan.md`
- `docs/migration/2026-05-01-dynamic-config-team-rulekit-native-message.md`
- `docs/migration/2026-05-01-rule-kit-sdk-review.md`

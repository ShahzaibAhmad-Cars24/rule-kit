package com.cars24.rulekit.core.trace;

public record RolloutTrace(
        boolean evaluated,
        boolean included,
        String algorithm,
        String hashInput,
        Integer bucket,
        Double percentage,
        String reason
) {

    public static RolloutTrace notConfigured() {
        return new RolloutTrace(false, true, null, null, null, null, "No rollout configured");
    }
}

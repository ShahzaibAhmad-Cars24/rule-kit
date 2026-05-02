package com.cars24.rulekit.core.resolver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;

public record ResolvedFact(boolean resolved, JsonNode value) {

    public static ResolvedFact found(JsonNode value) {
        return new ResolvedFact(true, value != null ? value : NullNode.getInstance());
    }

    public static ResolvedFact missing() {
        return new ResolvedFact(false, null);
    }
}

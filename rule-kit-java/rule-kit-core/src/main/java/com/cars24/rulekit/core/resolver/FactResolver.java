package com.cars24.rulekit.core.resolver;

import com.fasterxml.jackson.databind.JsonNode;

@FunctionalInterface
public interface FactResolver {

    ResolvedFact resolve(String fieldRef, JsonNode input);

    default ResolvedFact resolve(FactResolutionContext context) {
        return resolve(context.fieldRef(), context.input());
    }

    static FactResolver defaultResolver() {
        return (fieldRef, input) -> FieldValueResolver.resolve(input, fieldRef)
                .map(ResolvedFact::found)
                .orElseGet(ResolvedFact::missing);
    }

    static FactResolver contextual(ContextualFactResolver resolver) {
        return new FactResolver() {
            @Override
            public ResolvedFact resolve(String fieldRef, JsonNode input) {
                return defaultResolver().resolve(fieldRef, input);
            }

            @Override
            public ResolvedFact resolve(FactResolutionContext context) {
                return resolver.resolve(context);
            }
        };
    }
}

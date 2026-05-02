package com.cars24.rulekit.core.resolver;

@FunctionalInterface
public interface ContextualFactResolver {
    ResolvedFact resolve(FactResolutionContext context);
}

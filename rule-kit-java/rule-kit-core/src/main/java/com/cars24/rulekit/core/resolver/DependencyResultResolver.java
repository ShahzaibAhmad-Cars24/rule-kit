package com.cars24.rulekit.core.resolver;

import java.util.Optional;

@FunctionalInterface
public interface DependencyResultResolver {

    Optional<DependencyResult> resolve(DependencyResolutionContext context);
}

package com.cars24.rulekit.core.resolver;

import com.cars24.rulekit.core.evaluation.CompiledRuleSet;

import java.util.Optional;

@FunctionalInterface
public interface RuleSetDependencyResolver {

    Optional<CompiledRuleSet> resolve(DependencyResolutionContext context);
}

package com.cars24.rulekit.core.evaluation;

import com.cars24.rulekit.core.resolver.FactResolver;
import com.cars24.rulekit.core.resolver.RuleSetDependencyResolver;
import com.cars24.rulekit.core.resolver.SegmentResolver;

import java.util.Objects;

public record EvaluationOptions(
        FactResolver factResolver,
        SegmentResolver segmentResolver,
        RuleSetDependencyResolver dependencyResolver
) {

    public EvaluationOptions {
        factResolver = factResolver != null ? factResolver : FactResolver.defaultResolver();
    }

    public static EvaluationOptions defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private FactResolver factResolver = FactResolver.defaultResolver();
        private SegmentResolver segmentResolver;
        private RuleSetDependencyResolver dependencyResolver;

        private Builder() {
        }

        public Builder factResolver(FactResolver factResolver) {
            this.factResolver = Objects.requireNonNull(factResolver, "factResolver cannot be null");
            return this;
        }

        public Builder segmentResolver(SegmentResolver segmentResolver) {
            this.segmentResolver = segmentResolver;
            return this;
        }

        public Builder dependencyResolver(RuleSetDependencyResolver dependencyResolver) {
            this.dependencyResolver = dependencyResolver;
            return this;
        }

        public EvaluationOptions build() {
            return new EvaluationOptions(factResolver, segmentResolver, dependencyResolver);
        }
    }
}

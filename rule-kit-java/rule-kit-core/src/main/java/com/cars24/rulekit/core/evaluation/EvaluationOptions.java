package com.cars24.rulekit.core.evaluation;

import com.cars24.rulekit.core.resolver.FactResolver;
import com.cars24.rulekit.core.resolver.DependencyResultResolver;
import com.cars24.rulekit.core.resolver.PrefetchedSegmentMembershipResolver;
import com.cars24.rulekit.core.resolver.RuleSetDependencyResolver;
import com.cars24.rulekit.core.resolver.SegmentResolver;

import java.util.Objects;

public record EvaluationOptions(
        FactResolver factResolver,
        PrefetchedSegmentMembershipResolver prefetchedSegmentMembershipResolver,
        SegmentResolver segmentResolver,
        DependencyResultResolver dependencyResultResolver,
        RuleSetDependencyResolver dependencyRuleSetResolver
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
        private PrefetchedSegmentMembershipResolver prefetchedSegmentMembershipResolver;
        private SegmentResolver segmentResolver;
        private DependencyResultResolver dependencyResultResolver;
        private RuleSetDependencyResolver dependencyRuleSetResolver;

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

        public Builder prefetchedSegmentMembershipResolver(PrefetchedSegmentMembershipResolver prefetchedSegmentMembershipResolver) {
            this.prefetchedSegmentMembershipResolver = prefetchedSegmentMembershipResolver;
            return this;
        }

        public Builder dependencyResultResolver(DependencyResultResolver dependencyResultResolver) {
            this.dependencyResultResolver = dependencyResultResolver;
            return this;
        }

        public Builder dependencyRuleSetResolver(RuleSetDependencyResolver dependencyRuleSetResolver) {
            this.dependencyRuleSetResolver = dependencyRuleSetResolver;
            return this;
        }

        public EvaluationOptions build() {
            return new EvaluationOptions(
                    factResolver,
                    prefetchedSegmentMembershipResolver,
                    segmentResolver,
                    dependencyResultResolver,
                    dependencyRuleSetResolver
            );
        }
    }
}

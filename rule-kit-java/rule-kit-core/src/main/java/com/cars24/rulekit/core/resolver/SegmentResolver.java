package com.cars24.rulekit.core.resolver;

@FunctionalInterface
public interface SegmentResolver {

    SegmentMembershipResult resolve(SegmentResolutionContext context);
}

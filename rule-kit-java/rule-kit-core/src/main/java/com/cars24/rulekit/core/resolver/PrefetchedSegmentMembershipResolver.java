package com.cars24.rulekit.core.resolver;

import java.util.Optional;

@FunctionalInterface
public interface PrefetchedSegmentMembershipResolver {

    Optional<SegmentMembershipResult> resolve(SegmentResolutionContext context);
}

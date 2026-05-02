package com.cars24.rulekit.starter;

import com.cars24.rulekit.core.trace.TraceMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rule-kit")
public class RuleKitProperties {

    private TraceMode defaultTraceMode = TraceMode.COMPACT;

    public TraceMode getDefaultTraceMode() {
        return defaultTraceMode;
    }

    public void setDefaultTraceMode(TraceMode defaultTraceMode) {
        this.defaultTraceMode = defaultTraceMode;
    }
}

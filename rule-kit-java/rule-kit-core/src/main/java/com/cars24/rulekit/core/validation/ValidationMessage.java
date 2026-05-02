package com.cars24.rulekit.core.validation;

import com.cars24.rulekit.core.exception.RuleKitExceptionCode;

public record ValidationMessage(
        ValidationSeverity severity,
        RuleKitExceptionCode code,
        String path,
        String message
) {
}

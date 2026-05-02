package com.cars24.rulekit.core.exception;

import com.cars24.rulekit.core.validation.ValidationResult;

public class RuleKitValidationException extends RuleKitException {

    private final ValidationResult validationResult;

    public RuleKitValidationException(ValidationResult validationResult) {
        super(RuleKitExceptionCode.RULESET_VALIDATION_FAILED, "RuleSet validation failed");
        this.validationResult = validationResult;
    }

    public ValidationResult validationResult() {
        return validationResult;
    }
}

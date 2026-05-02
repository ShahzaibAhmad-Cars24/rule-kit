package com.cars24.rulekit.core.exception;

public class RuleKitEvaluationException extends RuleKitException {

    public RuleKitEvaluationException(RuleKitExceptionCode code, String message, Throwable cause) {
        super(code, message, cause);
    }
}

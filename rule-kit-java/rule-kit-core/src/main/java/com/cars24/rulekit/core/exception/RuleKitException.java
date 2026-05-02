package com.cars24.rulekit.core.exception;

public class RuleKitException extends RuntimeException {

    private final RuleKitExceptionCode code;

    public RuleKitException(RuleKitExceptionCode code, String message) {
        super(message);
        this.code = code;
    }

    public RuleKitException(RuleKitExceptionCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public RuleKitExceptionCode code() {
        return code;
    }
}

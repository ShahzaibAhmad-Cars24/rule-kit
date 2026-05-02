package com.cars24.rulekit.sdk;

import com.cars24.rulekit.core.exception.RuleKitException;
import com.cars24.rulekit.core.exception.RuleKitExceptionCode;

public class RuleKitClientException extends RuleKitException {

    public RuleKitClientException(RuleKitExceptionCode code, String message) {
        super(code, message);
    }

    public RuleKitClientException(RuleKitExceptionCode code, String message, Throwable cause) {
        super(code, message, cause);
    }
}

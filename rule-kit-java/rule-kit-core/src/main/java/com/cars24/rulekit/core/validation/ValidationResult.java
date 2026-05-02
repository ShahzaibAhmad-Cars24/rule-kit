package com.cars24.rulekit.core.validation;

import java.util.List;

public record ValidationResult(
        List<ValidationMessage> errors,
        List<ValidationMessage> warnings
) {
    public boolean valid() {
        return errors == null || errors.isEmpty();
    }
}

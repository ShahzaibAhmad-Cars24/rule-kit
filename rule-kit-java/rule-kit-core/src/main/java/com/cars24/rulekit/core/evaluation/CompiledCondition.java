package com.cars24.rulekit.core.evaluation;

import com.cars24.rulekit.core.model.ConditionDefinition;
import com.cars24.rulekit.core.operator.RuleKitOperator;

import java.util.List;
import java.util.regex.Pattern;

record CompiledCondition(
        ConditionDefinition source,
        RuleKitOperator operator,
        List<String> expectedValues,
        double expectedNumber,
        double expectedToNumber,
        Pattern regexPattern
) {
}

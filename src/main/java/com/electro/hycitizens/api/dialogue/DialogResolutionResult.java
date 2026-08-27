package com.electro.hycitizens.api.dialogue;

import java.util.List;

public record DialogResolutionResult(
        String dialogId,
        String matchedRuleId,
        String reason,
        List<String> trace
) {}

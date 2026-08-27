package com.electro.hycitizens.api.dialogue;

import java.util.List;

public record DialogLoadReport(
        boolean applied,
        int loadedCount,
        List<Issue> issues
) {
    public DialogLoadReport {
        issues = List.copyOf(issues);
    }

    public boolean valid() {
        return issues.stream().noneMatch(issue -> issue.severity() == Severity.ERROR);
    }

    public enum Severity { WARNING, ERROR }

    public record Issue(Severity severity, String source, String message) {}
}

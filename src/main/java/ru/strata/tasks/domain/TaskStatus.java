package ru.strata.tasks.domain;

import java.util.Set;

public enum TaskStatus {

    OPEN,
    IN_PROGRESS,
    DONE,
    CANCELLED;

    private Set<TaskStatus> allowedTransitions() {
        return switch (this) {
            case OPEN -> Set.of(IN_PROGRESS, CANCELLED);
            case IN_PROGRESS -> Set.of(DONE, CANCELLED);
            case DONE, CANCELLED -> Set.of();
        };
    }

    public boolean canTransitionTo(TaskStatus target) {
        return allowedTransitions().contains(target);
    }

    public boolean isTerminal() {
        return allowedTransitions().isEmpty();
    }
}

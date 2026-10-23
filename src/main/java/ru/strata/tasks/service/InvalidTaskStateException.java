package ru.strata.tasks.service;

import ru.strata.tasks.domain.TaskStatus;

public class InvalidTaskStateException extends RuntimeException {

    private final long taskId;
    private final TaskStatus from;
    private final TaskStatus to;

    public InvalidTaskStateException(long taskId, TaskStatus from, TaskStatus to) {
        super("Cannot move task %d from %s to %s".formatted(taskId, from, to));
        this.taskId = taskId;
        this.from = from;
        this.to = to;
    }

    public long taskId() {
        return taskId;
    }

    public TaskStatus from() {
        return from;
    }

    public TaskStatus to() {
        return to;
    }
}

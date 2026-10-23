package ru.strata.tasks.service;

public class TaskNotFoundException extends RuntimeException {

    private final long taskId;

    public TaskNotFoundException(long taskId) {
        super("Task %d not found".formatted(taskId));
        this.taskId = taskId;
    }

    public long taskId() {
        return taskId;
    }
}

package ru.strata.tasks.service;

import ru.strata.tasks.domain.TaskStatus;

public record CreateTaskCommand(String title, String description, TaskStatus status) {
}

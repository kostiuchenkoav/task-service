package ru.strata.tasks.web.dto;

import java.time.Instant;
import ru.strata.tasks.domain.Task;
import ru.strata.tasks.domain.TaskStatus;

public record TaskResponse(
        Long id,
        String title,
        String description,
        TaskStatus status,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getVersion()
        );
    }
}

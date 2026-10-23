package ru.strata.tasks.web.dto;

import jakarta.validation.constraints.NotNull;
import ru.strata.tasks.domain.TaskStatus;

public record ChangeStatusRequest(
        @NotNull(message = "status must not be null")
        TaskStatus status
) {
}

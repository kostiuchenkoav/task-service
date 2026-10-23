package ru.strata.tasks.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import ru.strata.tasks.domain.TaskEvent;
import ru.strata.tasks.domain.TaskEventType;
import ru.strata.tasks.domain.TaskStatus;

@Schema(description = "Запись журнала аудита по задаче")
public record TaskEventResponse(
        Long id,
        TaskEventType type,
        TaskStatus fromStatus,
        TaskStatus toStatus,
        @Schema(description = "Идентификатор ключа API, выполнившего действие") String actor,
        Instant occurredAt
) {

    public static TaskEventResponse from(TaskEvent event) {
        return new TaskEventResponse(
                event.getId(),
                event.getType(),
                event.getFromStatus(),
                event.getToStatus(),
                event.getActor(),
                event.getOccurredAt());
    }
}

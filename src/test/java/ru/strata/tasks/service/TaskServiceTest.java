package ru.strata.tasks.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import ru.strata.tasks.domain.IdempotencyRecord;
import ru.strata.tasks.domain.IdempotencyRecordRepository;
import ru.strata.tasks.domain.Task;
import ru.strata.tasks.domain.TaskEvent;
import ru.strata.tasks.domain.TaskEventRepository;
import ru.strata.tasks.domain.TaskEventType;
import ru.strata.tasks.domain.TaskRepository;
import ru.strata.tasks.domain.TaskStatus;
import ru.strata.tasks.security.ApiClient;

class TaskServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final ApiClient CLIENT = new ApiClient("key-a", "tenant-a");

    private final TaskRepository tasks = mock(TaskRepository.class);
    private final TaskEventRepository events = mock(TaskEventRepository.class);
    private final IdempotencyRecordRepository idempotency = mock(IdempotencyRecordRepository.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final TaskService service = new TaskService(tasks, events, idempotency, clock);

    @Test
    void createStoresTaskForTenantAndRecordsAuditEvent() {
        when(tasks.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskService.CreateResult result = service.create(CLIENT,
                new CreateTaskCommand("Написать тесты", null, null), null);

        assertThat(result.created()).isTrue();
        assertThat(result.task().getTenantId()).isEqualTo("tenant-a");
        assertThat(result.task().getStatus()).isEqualTo(TaskStatus.OPEN);
        verify(events).save(any(TaskEvent.class));
        verify(idempotency, never()).save(any());
    }

    @Test
    void createWithKnownIdempotencyKeyReturnsExistingTaskWithoutSaving() {
        Task existing = Task.restore(7L, "tenant-a", "Уже создана", null, TaskStatus.OPEN, NOW, NOW);
        when(idempotency.findByTenantIdAndIdemKey("tenant-a", "key-1"))
                .thenReturn(Optional.of(new IdempotencyRecord("tenant-a", "key-1", 7L, NOW)));
        when(tasks.findByIdAndTenantId(7L, "tenant-a")).thenReturn(Optional.of(existing));

        TaskService.CreateResult result = service.create(CLIENT,
                new CreateTaskCommand("Дубликат", null, null), "key-1");

        assertThat(result.created()).isFalse();
        assertThat(result.task().getId()).isEqualTo(7L);
        verify(tasks, never()).save(any());
    }

    @Test
    void taskOfAnotherTenantIsReportedAsNotFound() {
        when(tasks.findByIdAndTenantId(5L, "tenant-a")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(CLIENT, 5L))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void illegalTransitionIsRejectedWithoutAuditEvent() {
        Task done = Task.restore(3L, "tenant-a", "Готово", null, TaskStatus.DONE, NOW, NOW);
        when(tasks.findByIdAndTenantId(3L, "tenant-a")).thenReturn(Optional.of(done));

        assertThatThrownBy(() -> service.transition(CLIENT, 3L, TaskStatus.IN_PROGRESS))
                .isInstanceOf(InvalidTaskStateException.class);
        verify(events, never()).save(any());
    }

    @Test
    void deleteIsSoftAndRecordsDeletionEvent() {
        Task open = Task.restore(4L, "tenant-a", "Удаляем", null, TaskStatus.OPEN, NOW, NOW);
        when(tasks.findByIdAndTenantId(4L, "tenant-a")).thenReturn(Optional.of(open));

        service.delete(CLIENT, 4L);

        assertThat(open.getDeletedAt()).isEqualTo(NOW);
        verify(tasks, never()).delete(any());
        verify(events).save(any(TaskEvent.class));
    }

    @Test
    void historyOfUnknownTaskIsNotFound() {
        when(events.findByTenantIdAndTaskIdOrderByOccurredAtAscIdAsc("tenant-a", 9L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.history(CLIENT, 9L))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void historyReturnsEventsInOrder() {
        TaskEvent created = TaskEvent.of("tenant-a", 2L, TaskEventType.CREATED, null, TaskStatus.OPEN, "key-a", NOW);
        when(events.findByTenantIdAndTaskIdOrderByOccurredAtAscIdAsc("tenant-a", 2L)).thenReturn(List.of(created));

        assertThat(service.history(CLIENT, 2L)).containsExactly(created);
    }
}

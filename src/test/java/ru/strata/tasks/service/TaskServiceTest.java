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
import java.util.Optional;
import org.junit.jupiter.api.Test;
import ru.strata.tasks.domain.Task;
import ru.strata.tasks.domain.TaskRepository;
import ru.strata.tasks.domain.TaskStatus;

class TaskServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant EARLIER = NOW.minusSeconds(3600);

    private final TaskRepository repository = mock(TaskRepository.class);
    private final TaskService service = new TaskService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void createUsesOpenStatusAndClockTimestamps() {
        when(repository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Task task = service.create(new CreateTaskCommand("Собрать отчёт", "за описанием", null));

        assertThat(task.getStatus()).isEqualTo(TaskStatus.OPEN);
        assertThat(task.getCreatedAt()).isEqualTo(NOW);
        assertThat(task.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void getThrowsNotFoundForUnknownId() {
        when(repository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(42L))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessageContaining("42");
    }

    @Test
    void transitionMovesOpenTaskToInProgressAndTouchesUpdatedAt() {
        Task task = Task.restore(1L, "title", null, TaskStatus.OPEN, EARLIER, EARLIER);
        when(repository.findById(1L)).thenReturn(Optional.of(task));

        Task moved = service.transition(1L, TaskStatus.IN_PROGRESS);

        assertThat(moved.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(moved.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void transitionRejectsReopeningDoneTask() {
        Task task = Task.restore(2L, "title", null, TaskStatus.DONE, EARLIER, EARLIER);
        when(repository.findById(2L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.transition(2L, TaskStatus.IN_PROGRESS))
                .isInstanceOf(InvalidTaskStateException.class)
                .hasMessageContaining("DONE");
    }

    @Test
    void updateReplacesContent() {
        Task task = Task.restore(3L, "old", "old description", TaskStatus.OPEN, EARLIER, EARLIER);
        when(repository.findById(3L)).thenReturn(Optional.of(task));

        Task updated = service.update(3L, new UpdateTaskCommand("new", "new description"));

        assertThat(updated.getTitle()).isEqualTo("new");
        assertThat(updated.getDescription()).isEqualTo("new description");
        assertThat(updated.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void deleteMissingTaskThrowsAndDoesNotCallRepository() {
        when(repository.existsById(7L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(7L)).isInstanceOf(TaskNotFoundException.class);

        verify(repository, never()).deleteById(7L);
    }

    @Test
    void deleteExistingTaskCallsRepository() {
        when(repository.existsById(8L)).thenReturn(true);

        service.delete(8L);

        verify(repository).deleteById(8L);
    }
}

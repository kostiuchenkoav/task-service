package ru.strata.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import ru.strata.tasks.domain.Task;
import ru.strata.tasks.domain.TaskEventType;
import ru.strata.tasks.domain.TaskRepository;
import ru.strata.tasks.domain.TaskStatus;
import ru.strata.tasks.security.ApiClient;
import ru.strata.tasks.service.CreateTaskCommand;
import ru.strata.tasks.service.TaskNotFoundException;
import ru.strata.tasks.service.TaskService;

/**
 * Сквозной тест на H2: проверяет, что миграции Flyway и маппинг JPA совпадают,
 * и что жизненный цикл задачи работает целиком, включая аудит и мягкое удаление.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class TaskServiceApplicationTests {

    private static final ApiClient CLIENT = new ApiClient("test-a", "tenant-a");

    @Autowired
    TaskService taskService;

    @Autowired
    TaskRepository taskRepository;

    @Test
    void contextLoads() {
        assertThat(taskService).isNotNull();
        assertThat(taskRepository).isNotNull();
    }

    @Test
    void taskLifecycleRoundTripWithAudit() {
        String title = "Выпустить релиз " + UUID.randomUUID();
        Task created = taskService.create(CLIENT,
                new CreateTaskCommand(title, "по чек-листу", null), null).task();

        assertThat(created.getId()).isNotNull();
        assertThat(created.getVersion()).isNotNull();
        assertThat(created.getTenantId()).isEqualTo("tenant-a");
        assertThat(taskRepository.findByTenantIdAndStatus("tenant-a", TaskStatus.OPEN, PageRequest.of(0, 500))
                .getContent())
                .extracting(Task::getTitle)
                .contains(title);

        Task inProgress = taskService.transition(CLIENT, created.getId(), TaskStatus.IN_PROGRESS);
        assertThat(inProgress.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);

        taskService.transition(CLIENT, created.getId(), TaskStatus.DONE);
        assertThat(taskService.get(CLIENT, created.getId()).getStatus()).isEqualTo(TaskStatus.DONE);

        taskService.delete(CLIENT, created.getId());

        assertThatThrownBy(() -> taskService.get(CLIENT, created.getId()))
                .isInstanceOf(TaskNotFoundException.class);

        // Удалённая задача скрыта, но история по ней сохранена
        assertThat(taskService.history(CLIENT, created.getId()))
                .extracting(e -> e.getType())
                .containsExactly(TaskEventType.CREATED, TaskEventType.STATUS_CHANGED,
                        TaskEventType.STATUS_CHANGED, TaskEventType.DELETED);
    }
}

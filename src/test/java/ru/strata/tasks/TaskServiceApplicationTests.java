package ru.strata.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import ru.strata.tasks.domain.Task;
import ru.strata.tasks.domain.TaskRepository;
import ru.strata.tasks.domain.TaskStatus;
import ru.strata.tasks.service.CreateTaskCommand;
import ru.strata.tasks.service.TaskNotFoundException;
import ru.strata.tasks.service.TaskService;

/**
 * Сквозной тест на H2: проверяет, что миграция Flyway и маппинг JPA совпадают,
 * и что жизненный цикл задачи работает целиком.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class TaskServiceApplicationTests {

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
    void taskLifecycleRoundTrip() {
        Task created = taskService.create(new CreateTaskCommand("Выпустить релиз", "по чек-листу", null));

        assertThat(created.getId()).isNotNull();
        assertThat(created.getVersion()).isNotNull();
        assertThat(taskRepository.findByStatus(TaskStatus.OPEN, PageRequest.of(0, 20)).getContent())
                .extracting(Task::getTitle)
                .contains("Выпустить релиз");

        Task inProgress = taskService.transition(created.getId(), TaskStatus.IN_PROGRESS);
        assertThat(inProgress.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);

        taskService.transition(created.getId(), TaskStatus.DONE);
        assertThat(taskService.get(created.getId()).getStatus()).isEqualTo(TaskStatus.DONE);

        taskService.delete(created.getId());

        assertThatThrownBy(() -> taskService.get(created.getId()))
                .isInstanceOf(TaskNotFoundException.class);
    }
}

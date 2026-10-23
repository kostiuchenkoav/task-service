package ru.strata.tasks.service;

import java.time.Clock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.strata.tasks.domain.Task;
import ru.strata.tasks.domain.TaskRepository;
import ru.strata.tasks.domain.TaskStatus;

@Service
@Transactional(readOnly = true)
public class TaskService {

    private final TaskRepository repository;
    private final Clock clock;

    TaskService(TaskRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public Task create(CreateTaskCommand command) {
        Task task = Task.create(command.title(), command.description(), command.status(), clock.instant());
        return repository.save(task);
    }

    public Task get(long id) {
        return repository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
    }

    public Page<Task> list(TaskStatus status, Pageable pageable) {
        return status == null
                ? repository.findAll(pageable)
                : repository.findByStatus(status, pageable);
    }

    /**
     * Изменения применяются к managed-сущности и фиксируются при коммите транзакции;
     * конкурентная запись будет отклонена оптимистичной блокировкой ({@code @Version}).
     */
    @Transactional
    public Task update(long id, UpdateTaskCommand command) {
        Task task = get(id);
        task.changeContent(command.title(), command.description(), clock.instant());
        return task;
    }

    @Transactional
    public Task transition(long id, TaskStatus target) {
        Task task = get(id);
        if (!task.canMoveTo(target)) {
            throw new InvalidTaskStateException(id, task.getStatus(), target);
        }
        task.moveTo(target, clock.instant());
        return task;
    }

    @Transactional
    public void delete(long id) {
        if (!repository.existsById(id)) {
            throw new TaskNotFoundException(id);
        }
        repository.deleteById(id);
    }
}

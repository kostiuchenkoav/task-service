package ru.strata.tasks.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.strata.tasks.domain.IdempotencyRecord;
import ru.strata.tasks.domain.IdempotencyRecordRepository;
import ru.strata.tasks.domain.Task;
import ru.strata.tasks.domain.TaskEvent;
import ru.strata.tasks.domain.TaskEventRepository;
import ru.strata.tasks.domain.TaskEventType;
import ru.strata.tasks.domain.TaskRepository;
import ru.strata.tasks.domain.TaskStatus;
import ru.strata.tasks.security.ApiClient;

/**
 * Бизнес-логика задач. Каждый метод принимает клиента API: он задаёт тенанта, в рамках
 * которого выполняется операция, и попадает в журнал аудита как автор изменения.
 *
 * <p>Задача чужого тенанта неотличима от несуществующей: в обоих случаях
 * {@link TaskNotFoundException}, поэтому по ответам нельзя узнать, что чужой id занят.
 */
@Service
@Transactional(readOnly = true)
public class TaskService {

    private final TaskRepository tasks;
    private final TaskEventRepository events;
    private final IdempotencyRecordRepository idempotency;
    private final Clock clock;

    /** Результат создания: created=false означает повтор запроса с тем же Idempotency-Key. */
    public record CreateResult(Task task, boolean created) {
    }

    TaskService(TaskRepository tasks, TaskEventRepository events,
                IdempotencyRecordRepository idempotency, Clock clock) {
        this.tasks = tasks;
        this.events = events;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    /**
     * Создание. Если передан ключ идемпотентности, уже известный для этого тенанта,
     * возвращается ранее созданная задача и новая не создаётся.
     */
    @Transactional
    public CreateResult create(ApiClient client, CreateTaskCommand command, String idempotencyKey) {
        String tenant = client.tenant();
        if (idempotencyKey != null) {
            var replay = idempotency.findByTenantIdAndIdemKey(tenant, idempotencyKey);
            if (replay.isPresent()) {
                return new CreateResult(get(client, replay.get().getTaskId()), false);
            }
        }

        Instant now = clock.instant();
        Task task = tasks.save(Task.create(tenant, command.title(), command.description(), command.status(), now));
        events.save(TaskEvent.of(tenant, task.getId(), TaskEventType.CREATED,
                null, task.getStatus(), client.id(), now));
        if (idempotencyKey != null) {
            idempotency.save(new IdempotencyRecord(tenant, idempotencyKey, task.getId(), now));
        }
        return new CreateResult(task, true);
    }

    public Task get(ApiClient client, long id) {
        return tasks.findByIdAndTenantId(id, client.tenant()).orElseThrow(() -> new TaskNotFoundException(id));
    }

    public Page<Task> list(ApiClient client, TaskStatus status, Pageable pageable) {
        return status == null
                ? tasks.findByTenantId(client.tenant(), pageable)
                : tasks.findByTenantIdAndStatus(client.tenant(), status, pageable);
    }

    /**
     * Изменения применяются к managed-сущности и фиксируются при коммите транзакции;
     * конкурентная запись будет отклонена оптимистичной блокировкой ({@code @Version}).
     */
    @Transactional
    public Task update(ApiClient client, long id, UpdateTaskCommand command) {
        Task task = get(client, id);
        Instant now = clock.instant();
        task.changeContent(command.title(), command.description(), now);
        events.save(TaskEvent.of(client.tenant(), id, TaskEventType.UPDATED,
                null, null, client.id(), now));
        return task;
    }

    @Transactional
    public Task transition(ApiClient client, long id, TaskStatus target) {
        Task task = get(client, id);
        if (!task.canMoveTo(target)) {
            throw new InvalidTaskStateException(id, task.getStatus(), target);
        }
        TaskStatus from = task.getStatus();
        Instant now = clock.instant();
        task.moveTo(target, now);
        events.save(TaskEvent.of(client.tenant(), id, TaskEventType.STATUS_CHANGED,
                from, target, client.id(), now));
        return task;
    }

    /** Мягкое удаление: строка и журнал сохраняются, задача исчезает из всех выборок API. */
    @Transactional
    public void delete(ApiClient client, long id) {
        Task task = get(client, id);
        Instant now = clock.instant();
        task.softDelete(now);
        events.save(TaskEvent.of(client.tenant(), id, TaskEventType.DELETED,
                task.getStatus(), null, client.id(), now));
    }

    /**
     * История изменений. Доступна и для удалённых задач: журнал читается напрямую,
     * а не через {@link #get}, потому что удалённая задача скрыта от обычных запросов.
     */
    public List<TaskEvent> history(ApiClient client, long id) {
        List<TaskEvent> trail = events.findByTenantIdAndTaskIdOrderByOccurredAtAscIdAsc(client.tenant(), id);
        if (trail.isEmpty()) {
            throw new TaskNotFoundException(id);
        }
        return trail;
    }
}

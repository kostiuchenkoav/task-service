package ru.strata.tasks.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Запись журнала аудита. Таблица только для добавления: запись не меняется и не удаляется,
 * даже когда сама задача мягко удалена. Кто совершил действие, хранится как идентификатор
 * ключа API ({@code actorId}), а не как секрет.
 */
@Entity
@Table(name = "task_events")
public class TaskEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false, length = 64)
    private String tenantId;

    @Column(name = "task_id", nullable = false, updatable = false)
    private Long taskId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private TaskEventType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", updatable = false, length = 32)
    private TaskStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", updatable = false, length = 32)
    private TaskStatus toStatus;

    @Column(nullable = false, updatable = false, length = 64)
    private String actor;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected TaskEvent() {
    }

    private TaskEvent(String tenantId, Long taskId, TaskEventType type, TaskStatus fromStatus,
                      TaskStatus toStatus, String actor, Instant occurredAt) {
        this.tenantId = tenantId;
        this.taskId = taskId;
        this.type = type;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actor = actor;
        this.occurredAt = occurredAt;
    }

    public static TaskEvent of(String tenantId, Long taskId, TaskEventType type, TaskStatus fromStatus,
                               TaskStatus toStatus, String actor, Instant occurredAt) {
        return new TaskEvent(tenantId, taskId, type, fromStatus, toStatus, actor, occurredAt);
    }

    public Long getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public TaskEventType getType() {
        return type;
    }

    public TaskStatus getFromStatus() {
        return fromStatus;
    }

    public TaskStatus getToStatus() {
        return toStatus;
    }

    public String getActor() {
        return actor;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}

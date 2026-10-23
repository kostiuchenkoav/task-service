package ru.strata.tasks.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import org.hibernate.annotations.SQLRestriction;

/**
 * Задача. Принадлежит одному тенанту (клиенту API) и никогда не удаляется физически:
 * удаление проставляет {@code deletedAt}, а {@link SQLRestriction} скрывает такие строки
 * из всех запросов, включая {@code findById}.
 */
@Entity
@Table(name = "tasks")
@SQLRestriction("deleted_at IS NULL")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false, length = 64)
    private String tenantId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Task() {
    }

    private Task(Long id, String tenantId, String title, String description, TaskStatus status,
                 Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.tenantId = requireTenant(tenantId);
        this.title = title;
        this.description = description;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Task create(String tenantId, String title, String description, TaskStatus status, Instant now) {
        TaskStatus initial = status == null ? TaskStatus.OPEN : status;
        return new Task(null, tenantId, title, description, initial, now, now);
    }

    /**
     * Восстановление существующей задачи без обращения к БД — используется в тестах.
     */
    public static Task restore(Long id, String tenantId, String title, String description, TaskStatus status,
                               Instant createdAt, Instant updatedAt) {
        return new Task(id, tenantId, title, description, status, createdAt, updatedAt);
    }

    public void changeContent(String title, String description, Instant now) {
        this.title = title;
        this.description = description;
        this.updatedAt = now;
    }

    public void moveTo(TaskStatus target, Instant now) {
        this.status = target;
        this.updatedAt = now;
    }

    /** Мягкое удаление: строка остаётся для аудита, но перестаёт быть видимой через JPA. */
    public void softDelete(Instant now) {
        this.deletedAt = now;
        this.updatedAt = now;
    }

    public boolean canMoveTo(TaskStatus target) {
        return status != null && status.canTransitionTo(target);
    }

    public boolean belongsTo(String tenant) {
        return tenantId != null && tenantId.equals(tenant);
    }

    public Long getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Long getVersion() {
        return version;
    }

    private static String requireTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        return tenantId;
    }
}

package ru.strata.tasks.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Связь «ключ идемпотентности клиента → созданная задача». Повторный POST с тем же
 * {@code Idempotency-Key} от того же тенанта возвращает уже созданную задачу, а не создаёт
 * дубликат. Уникальность гарантируется индексом {@code (tenant_id, idem_key)}.
 */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false, length = 64)
    private String tenantId;

    @Column(name = "idem_key", nullable = false, updatable = false, length = 128)
    private String idemKey;

    @Column(name = "task_id", nullable = false, updatable = false)
    private Long taskId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(String tenantId, String idemKey, Long taskId, Instant createdAt) {
        this.tenantId = tenantId;
        this.idemKey = idemKey;
        this.taskId = taskId;
        this.createdAt = createdAt;
    }

    public Long getTaskId() {
        return taskId;
    }
}

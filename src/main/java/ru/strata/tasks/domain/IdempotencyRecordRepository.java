package ru.strata.tasks.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByTenantIdAndIdemKey(String tenantId, String idemKey);
}

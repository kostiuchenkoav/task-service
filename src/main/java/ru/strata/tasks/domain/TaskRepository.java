package ru.strata.tasks.domain;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Все выборки задач ограничены тенантом. Метода «найти по id» без тенанта здесь нет
 * намеренно: так случайная утечка между клиентами требует явного обхода репозитория.
 */
public interface TaskRepository extends JpaRepository<Task, Long> {

    Optional<Task> findByIdAndTenantId(Long id, String tenantId);

    Page<Task> findByTenantId(String tenantId, Pageable pageable);

    Page<Task> findByTenantIdAndStatus(String tenantId, TaskStatus status, Pageable pageable);
}

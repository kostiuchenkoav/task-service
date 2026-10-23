package ru.strata.tasks.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskEventRepository extends JpaRepository<TaskEvent, Long> {

    List<TaskEvent> findByTenantIdAndTaskIdOrderByOccurredAtAscIdAsc(String tenantId, Long taskId);
}

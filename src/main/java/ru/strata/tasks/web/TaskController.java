package ru.strata.tasks.web;

import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.strata.tasks.domain.TaskStatus;
import ru.strata.tasks.service.CreateTaskCommand;
import ru.strata.tasks.service.TaskService;
import ru.strata.tasks.service.UpdateTaskCommand;
import ru.strata.tasks.web.dto.ChangeStatusRequest;
import ru.strata.tasks.web.dto.CreateTaskRequest;
import ru.strata.tasks.web.dto.PagedResponse;
import ru.strata.tasks.web.dto.TaskResponse;
import ru.strata.tasks.web.dto.UpdateTaskRequest;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final TaskService service;

    TaskController(TaskService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TaskResponse> create(@Valid @RequestBody CreateTaskRequest request) {
        var task = service.create(new CreateTaskCommand(request.title(), request.description(), request.status()));
        return ResponseEntity
                .created(URI.create("/api/v1/tasks/" + task.getId()))
                .body(TaskResponse.from(task));
    }

    @GetMapping
    public PagedResponse<TaskResponse> list(
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size
    ) {
        int safeSize = (int) Math.clamp(size, 1, MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return PagedResponse.from(service.list(status, pageable).map(TaskResponse::from));
    }

    @GetMapping("/{id}")
    public TaskResponse get(@PathVariable long id) {
        return TaskResponse.from(service.get(id));
    }

    @PutMapping("/{id}")
    public TaskResponse update(@PathVariable long id, @Valid @RequestBody UpdateTaskRequest request) {
        var task = service.update(id, new UpdateTaskCommand(request.title(), request.description()));
        return TaskResponse.from(task);
    }

    /**
     * Переход статуса отдельным ресурсом: это позволяет добавлять
     * собственные правила и метрики на смену состояния.
     */
    @PostMapping("/{id}/transitions")
    public TaskResponse transition(@PathVariable long id, @Valid @RequestBody ChangeStatusRequest request) {
        return TaskResponse.from(service.transition(id, request.status()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }
}

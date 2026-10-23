package ru.strata.tasks.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.strata.tasks.domain.TaskStatus;
import ru.strata.tasks.security.ApiClient;
import ru.strata.tasks.service.CreateTaskCommand;
import ru.strata.tasks.service.TaskService;
import ru.strata.tasks.service.UpdateTaskCommand;
import ru.strata.tasks.web.dto.ChangeStatusRequest;
import ru.strata.tasks.web.dto.CreateTaskRequest;
import ru.strata.tasks.web.dto.PagedResponse;
import ru.strata.tasks.web.dto.TaskEventResponse;
import ru.strata.tasks.web.dto.TaskResponse;
import ru.strata.tasks.web.dto.UpdateTaskRequest;

@RestController
@RequestMapping("/api/v1/tasks")
@Tag(name = "tasks", description = "Задачи: создание, выдача, поиск, изменение, переходы статуса, удаление, история")
public class TaskController {

    static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final TaskService service;

    TaskController(TaskService service) {
        this.service = service;
    }

    @Operation(summary = "Создать задачу", description = """
            Статус можно не указывать — будет OPEN. Пустой title отклоняется с 400 \
            и списком ошибок в полях.

            Заголовок Idempotency-Key (до 128 символов) делает повтор безопасным: \
            повторный запрос с тем же ключом от того же клиента вернёт 200 и уже \
            созданную задачу, дубликат не появится.""")
    @PostMapping
    public ResponseEntity<TaskResponse> create(
            Authentication authentication,
            @Valid @RequestBody CreateTaskRequest request,
            @Parameter(description = "Ключ идемпотентности, до 128 символов")
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey
    ) {
        String key = validateIdempotencyKey(idempotencyKey);
        var result = service.create(client(authentication),
                new CreateTaskCommand(request.title(), request.description(), request.status()), key);
        TaskResponse body = TaskResponse.from(result.task());
        if (!result.created()) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity
                .created(URI.create("/api/v1/tasks/" + result.task().getId()))
                .body(body);
    }

    @Operation(summary = "Список задач", description = """
            Только задачи вашего клиента, без удалённых. Отсортировано по времени создания \
            (новые раньше). size ограничен 100, значения вне диапазона приводятся к границам.""")
    @GetMapping
    public PagedResponse<TaskResponse> list(
            Authentication authentication,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size
    ) {
        int safeSize = (int) Math.clamp(size, 1, MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return PagedResponse.from(service.list(client(authentication), status, pageable).map(TaskResponse::from));
    }

    @Operation(summary = "Задача по идентификатору", description = "404 в формате problem+json, если задачи нет или она не ваша.")
    @GetMapping("/{id}")
    public TaskResponse get(Authentication authentication, @PathVariable long id) {
        return TaskResponse.from(service.get(client(authentication), id));
    }

    @Operation(summary = "Обновить заголовок и описание", description = """
            Статус этим методом не меняется — для него есть /transitions. \
            version в ответе растёт (оптимистичная блокировка).""")
    @PutMapping("/{id}")
    public TaskResponse update(Authentication authentication, @PathVariable long id,
                               @Valid @RequestBody UpdateTaskRequest request) {
        var task = service.update(client(authentication), id,
                new UpdateTaskCommand(request.title(), request.description()));
        return TaskResponse.from(task);
    }

    /**
     * Переход статуса отдельным ресурсом: это позволяет добавлять
     * собственные правила и метрики на смену состояния.
     */
    @Operation(summary = "Перевести задачу в другой статус", description = """
            Конечный автомат: OPEN -> IN_PROGRESS -> DONE, до CANCELLED — из любого \
            активного состояния. Незаконный переход — 409 с from/to в теле ответа.""")
    @PostMapping("/{id}/transitions")
    public TaskResponse transition(Authentication authentication, @PathVariable long id,
                                   @Valid @RequestBody ChangeStatusRequest request) {
        return TaskResponse.from(service.transition(client(authentication), id, request.status()));
    }

    @Operation(summary = "Удалить задачу", description = """
            Мягкое удаление: задача пропадает из выборок, но запись и история сохраняются. \
            Ответ 204 без тела.""")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication, @PathVariable long id) {
        service.delete(client(authentication), id);
    }

    @Operation(summary = "История изменений задачи", description = """
            Журнал аудита в хронологическом порядке: создание, изменения, переходы статуса, \
            удаление. Доступен и для удалённой задачи. 404, если записей по задаче нет.""")
    @GetMapping("/{id}/history")
    public List<TaskEventResponse> history(Authentication authentication, @PathVariable long id) {
        return service.history(client(authentication), id).stream().map(TaskEventResponse::from).toList();
    }

    private static ApiClient client(Authentication authentication) {
        return (ApiClient) authentication.getPrincipal();
    }

    private static String validateIdempotencyKey(String key) {
        if (key == null) {
            return null;
        }
        if (key.isBlank() || key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must be 1-128 characters");
        }
        return key;
    }
}

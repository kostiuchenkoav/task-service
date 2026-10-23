package ru.strata.tasks.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.strata.tasks.domain.Task;
import ru.strata.tasks.domain.TaskStatus;
import ru.strata.tasks.service.InvalidTaskStateException;
import ru.strata.tasks.service.TaskNotFoundException;
import ru.strata.tasks.service.TaskService;

/**
 * Срез веб-слоя без поднимаемого контейнера Spring: быстрый тест контракта
 * (коды ответов, валидация, формат ProblemDetail).
 */
class TaskApiTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private final TaskService service = mock(TaskService.class);

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new TaskController(service))
            .setControllerAdvice(new TaskApiExceptionHandler())
            .build();

    @Test
    void createReturnsCreatedWithLocation() throws Exception {
        when(service.create(any()))
                .thenReturn(Task.restore(1L, "Купить кофе", null, TaskStatus.OPEN, NOW, NOW));

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Купить кофе"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/tasks/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void createWithBlankTitleReturnsProblemDetailWithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid request body"))
                .andExpect(jsonPath("$.errors[0].field").value("title"));
    }

    @Test
    void getUnknownTaskReturnsNotFoundProblem() throws Exception {
        when(service.get(9L)).thenThrow(new TaskNotFoundException(9L));

        mockMvc.perform(get("/api/v1/tasks/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.taskId").value(9));
    }

    @Test
    void illegalTransitionReturnsConflict() throws Exception {
        when(service.transition(3L, TaskStatus.IN_PROGRESS))
                .thenThrow(new InvalidTaskStateException(3L, TaskStatus.DONE, TaskStatus.IN_PROGRESS));

        mockMvc.perform(post("/api/v1/tasks/3/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "IN_PROGRESS"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.from").value("DONE"))
                .andExpect(jsonPath("$.to").value("IN_PROGRESS"));
    }

    @Test
    void unknownStatusValueIsRejectedAsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/tasks/3/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "NOT_A_STATUS"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}

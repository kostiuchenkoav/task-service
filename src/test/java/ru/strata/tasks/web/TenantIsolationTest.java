package ru.strata.tasks.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Границы тенантов и повторные запросы — на реальном стеке безопасности и H2.
 * Ключ {@code test-key} принадлежит tenant-a, {@code test-key-b} — tenant-b.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantIsolationTest {

    private static final String KEY_A = "test-key";
    private static final String KEY_B = "test-key-b";
    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

    @Autowired
    MockMvc mockMvc;

    @Test
    void taskOfAnotherTenantLooksLikeMissingTask() throws Exception {
        String title = uniqueTitle();
        long id = create(KEY_A, null, title);

        mockMvc.perform(get("/api/v1/tasks/" + id).header("X-API-Key", KEY_B))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/tasks/" + id).header("X-API-Key", KEY_B))
                .andExpect(status().isNotFound());

        // Чужое удаление не должно было затронуть задачу
        mockMvc.perform(get("/api/v1/tasks/" + id).header("X-API-Key", KEY_A))
                .andExpect(status().isOk());
    }

    @Test
    void listShowsOnlyOwnTenantTasks() throws Exception {
        String title = uniqueTitle();
        create(KEY_A, null, title);

        mockMvc.perform(get("/api/v1/tasks").param("size", "100").header("X-API-Key", KEY_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.title == '" + title + "')]").isEmpty());
    }

    @Test
    void repeatedIdempotencyKeyReturnsSameTaskWithoutDuplicate() throws Exception {
        String key = "order-" + UUID.randomUUID();
        long first = create(KEY_A, key, uniqueTitle());

        MvcResult replay = mockMvc.perform(post("/api/v1/tasks")
                        .header("X-API-Key", KEY_A)
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content("{\"title\": \"другой заголовок\"}"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(extractId(replay.getResponse().getContentAsString())).isEqualTo(first);
    }

    @Test
    void idempotencyKeyIsScopedToTenant() throws Exception {
        String key = "shared-" + UUID.randomUUID();
        long fromA = create(KEY_A, key, uniqueTitle());
        long fromB = create(KEY_B, key, uniqueTitle());

        assertThat(fromB).isNotEqualTo(fromA);
    }

    @Test
    void historyRecordsCreationAndDeletionAfterSoftDelete() throws Exception {
        long id = create(KEY_A, null, uniqueTitle());

        mockMvc.perform(delete("/api/v1/tasks/" + id).header("X-API-Key", KEY_A))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/tasks/" + id).header("X-API-Key", KEY_A))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/tasks/" + id + "/history").header("X-API-Key", KEY_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("CREATED"))
                .andExpect(jsonPath("$[1].type").value("DELETED"))
                .andExpect(jsonPath("$[0].actor").value("test-a"));
    }

    @Test
    void historyOfAnotherTenantIsNotFound() throws Exception {
        long id = create(KEY_A, null, uniqueTitle());

        mockMvc.perform(get("/api/v1/tasks/" + id + "/history").header("X-API-Key", KEY_B))
                .andExpect(status().isNotFound());
    }

    @Test
    void tooLongIdempotencyKeyIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .header("X-API-Key", KEY_A)
                        .header("Idempotency-Key", "k".repeat(129))
                        .contentType("application/json")
                        .content("{\"title\": \"x\"}"))
                .andExpect(status().isBadRequest());
    }

    private long create(String key, String idempotencyKey, String title) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/tasks")
                .header("X-API-Key", key)
                .contentType("application/json")
                .content("{\"title\": \"" + title + "\"}");
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().isCreated()).andReturn();
        return extractId(result.getResponse().getContentAsString());
    }

    private static long extractId(String json) {
        Matcher matcher = ID.matcher(json);
        assertThat(matcher.find()).as("id in response: %s", json).isTrue();
        return Long.parseLong(matcher.group(1));
    }

    private static String uniqueTitle() {
        return "Задача " + UUID.randomUUID();
    }
}

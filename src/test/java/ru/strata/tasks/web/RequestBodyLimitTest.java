package ru.strata.tasks.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Слишком большой запрос отклоняется до обработки контроллером, включая анонимные. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RequestBodyLimitTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void oversizedAuthenticatedRequestGetsPayloadTooLarge() throws Exception {
        String body = "{\"title\": \"" + "a".repeat(100_000) + "\"}";

        mockMvc.perform(post("/api/v1/tasks")
                        .header("X-API-Key", "test-key")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.status").value(413));
    }

    @Test
    void oversizedAnonymousRequestIsRejectedBeforeAuthentication() throws Exception {
        String body = "{\"title\": \"" + "a".repeat(100_000) + "\"}";

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isPayloadTooLarge());
    }
}

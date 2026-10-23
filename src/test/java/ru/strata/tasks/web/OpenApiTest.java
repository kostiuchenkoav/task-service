package ru.strata.tasks.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Спецификация OpenAPI — часть контракта: если springdoc отвалится (несовместимая
 * версия, потеря автоконфигурации), тест падает, а не молча пропадает Swagger UI.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void apiDocsDescribeTaskEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value("task-service API"))
                .andExpect(jsonPath("$.paths['/api/v1/tasks'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/tasks'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/tasks/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/tasks/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/tasks/{id}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/v1/tasks/{id}/transitions'].post").exists());
    }

    @Test
    void swaggerUiIsReachable() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void serviceEndpointsAreNotInBusinessSpec() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/actuator/health']").doesNotExist());
    }
}

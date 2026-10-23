package ru.strata.tasks.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Метаданные OpenAPI. Спецификация строится springdoc автоматически по
 * контроллерам (см. springdoc.packages-to-scan в application.yml); здесь только
 * то, что из кода не выводится: название, версия и описание контракта.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI taskServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("task-service API")
                .version("v1")
                .description("""
                        Микросервис задач. Жизненный цикл задачи: \
                        OPEN -> IN_PROGRESS -> DONE, отдельно доступен CANCELLED; \
                        переходы охраняет конечный автомат в TaskService.

                        Ошибки отдаются в формате RFC 9457 (application/problem+json): \
                        у 400 есть массив errors с полями, у 404 и 409 — дополнительные \
                        свойства (taskId, from, to).

                        Поле version у задачи — оптимистичная блокировка (@Version): \
                        его возвращает каждый ответ, при конкурентной записи \
                        обновление отклоняется."""));
    }
}

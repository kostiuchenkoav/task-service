package ru.strata.tasks.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionDataSourceGuardTest {

    private static final String POSTGRES_URL = "jdbc:postgresql://localhost:5432/tasks";

    private final MockEnvironment environment = new MockEnvironment();

    @Test
    void rejectsPostgresWithoutPassword() {
        environment.setProperty("spring.datasource.url", POSTGRES_URL);

        assertThatThrownBy(() -> ProductionDataSourceGuard.validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PASSWORD");
    }

    @Test
    void rejectsBlankPassword() {
        environment.setProperty("spring.datasource.url", POSTGRES_URL);
        environment.setProperty("DB_PASSWORD", "   ");

        assertThatThrownBy(() -> ProductionDataSourceGuard.validate(environment))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsPostgresWithPasswordFromEnvironment() {
        environment.setProperty("spring.datasource.url", POSTGRES_URL);
        environment.setProperty("DB_PASSWORD", "password-from-deployment");

        assertThatCode(() -> ProductionDataSourceGuard.validate(environment)).doesNotThrowAnyException();
    }

    @Test
    void acceptsPostgresWithSpringDatasourcePassword() {
        environment.setProperty("spring.datasource.url", POSTGRES_URL);
        environment.setProperty("SPRING_DATASOURCE_PASSWORD", "password-from-deployment");

        assertThatCode(() -> ProductionDataSourceGuard.validate(environment)).doesNotThrowAnyException();
    }

    @Test
    void ignoresEmbeddedDatabase() {
        environment.setProperty("spring.datasource.url", "jdbc:h2:mem:task-service;MODE=PostgreSQL");

        assertThatCode(() -> ProductionDataSourceGuard.validate(environment)).doesNotThrowAnyException();
    }
}

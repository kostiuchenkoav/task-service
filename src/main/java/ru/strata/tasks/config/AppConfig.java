package ru.strata.tasks.config;

import java.time.Clock;
import java.time.ZoneOffset;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;

@Configuration(proxyBeanMethods = false)
public class AppConfig {

    /**
     * Временные метки создаются через инжектируемый {@link Clock}, чтобы даты
     * были воспроизводимы в тестах и не зависели от часового пояса сервера.
     */
    @Bean
    Clock clock() {
        return Clock.system(ZoneOffset.UTC);
    }

    /**
     * Проверяет пароль боевой БД до создания {@code DataSource} и Flyway:
     * метод намеренно {@code static}, чтобы контейнер вызвал его на самом раннем шаге.
     */
    @Bean
    static BeanFactoryPostProcessor productionDataSourceGuard(ConfigurableEnvironment environment) {
        return beanFactory -> ProductionDataSourceGuard.validate(environment);
    }
}

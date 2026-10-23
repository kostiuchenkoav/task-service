package ru.strata.tasks.config;

import java.time.Clock;
import java.time.ZoneOffset;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}

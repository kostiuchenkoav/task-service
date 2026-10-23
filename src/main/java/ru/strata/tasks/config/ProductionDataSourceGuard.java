package ru.strata.tasks.config;

import org.springframework.core.env.Environment;

/**
 * Проверяет конфигурацию доступа к боевой базе данных до создания бинов.
 *
 * <p>В {@code application.yml} пароль задан как {@code ${DB_PASSWORD}} без значения по
 * умолчанию, но Spring при недоступной переменной окружения просто отдаёт {@code null}:
 * сервис стартует и падает уже на {@code password authentication failed} в PostgreSQL.
 * Проверка выполняется до инициализации {@code DataSource} и Flyway, поэтому боевой
 * запуск без пароля невозможен по построению и сопровождается понятной ошибкой.
 *
 * <p>Признаком боевого запуска служит URL источника данных, а не имя профиля: встроенные
 * базы ({@code dev}/{@code test}) работают без пароля и под запрет не попадают.
 */
final class ProductionDataSourceGuard {

    private static final String POSTGRES_PREFIX = "jdbc:postgresql";
    private static final String[] PASSWORD_KEYS = {"DB_PASSWORD", "SPRING_DATASOURCE_PASSWORD"};

    private ProductionDataSourceGuard() {
    }

    static void validate(Environment environment) {
        String url = environment.getProperty("spring.datasource.url", "");
        if (!url.startsWith(POSTGRES_PREFIX)) {
            return;
        }
        if (resolvePassword(environment).isEmpty()) {
            throw new IllegalStateException("""
                    Пароль к PostgreSQL не задан: переменная окружения DB_PASSWORD \
                    (или SPRING_DATASOURCE_PASSWORD) отсутствует или пуста. \
                    Задайте её перед запуском — шаблон .env.example, \
                    значение по умолчанию в репозитории намеренно отсутствует.""");
        }
    }

    private static String resolvePassword(Environment environment) {
        for (String key : PASSWORD_KEYS) {
            String value = environment.getProperty(key);
            // Неразрешиемый placeholder ("${DB_PASSWORD}") тоже считается отсутствующим.
            if (value != null && !value.isBlank() && !value.startsWith("${")) {
                return value;
            }
        }
        return "";
    }
}

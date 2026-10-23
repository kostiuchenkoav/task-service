package ru.strata.tasks.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ключи доступа к API. В конфигурации хранится не сам ключ, а его SHA-256 (hex),
 * поэтому утечка конфига не раскрывает ключи клиентов.
 *
 * <pre>
 * app:
 *   security:
 *     api-keys:
 *       - id: billing-service
 *         tenant: acme
 *         sha256: 9f86d0...   # printf '%s' "$KEY" | sha256sum
 * </pre>
 *
 * <p>Пустой список означает: защищённые эндпоинты недоступны никому (закрытый режим по умолчанию).
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(List<ApiKey> apiKeys) {

    public SecurityProperties {
        apiKeys = apiKeys == null ? List.of() : List.copyOf(apiKeys);
    }

    /**
     * @param id     имя клиента для журнала (не секрет)
     * @param tenant арендатор, которому принадлежат задачи клиента
     * @param sha256 SHA-256 от значения ключа, 64 hex-символа
     */
    public record ApiKey(String id, String tenant, String sha256) {
    }
}

package ru.strata.tasks.security;

/**
 * Аутентифицированный клиент API.
 *
 * @param id     имя клиента из конфигурации (для журнала аудита, не секрет)
 * @param tenant арендатор: клиент видит и меняет только задачи своего арендатора
 */
public record ApiClient(String id, String tenant) {

    public ApiClient {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id клиента не может быть пустым");
        }
        if (tenant == null || tenant.isBlank()) {
            throw new IllegalArgumentException("tenant клиента не может быть пустым");
        }
    }
}

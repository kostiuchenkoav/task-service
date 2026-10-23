package ru.strata.tasks.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.strata.tasks.security.ApiClient;

/**
 * Ограничение частоты запросов по ключу API: не более {@code limit} запросов
 * в фиксированном окне одной минуты. Превышение даёт {@code 429} с заголовком
 * {@code Retry-After}.
 *
 * <p>Фильтр стоит после аутентификации, поэтому считает только запросы с действующим ключом.
 * Анонимные запросы (в том числе с неверным ключом) он не учитывает: их отсекает 401.
 *
 * <p>Счётчики хранятся в памяти одного экземпляра. При горизонтальном масштабировании
 * лимит действует на каждую реплику отдельно; для общего лимита нужен внешний хранилище
 * (например, Redis), это задача следующего этапа.
 */
final class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_SECONDS = 60;
    /** Верхняя граница памяти: при её превышении удаляются счётчики прошлых окон. */
    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private record Window(long startSecond, AtomicInteger count) {
    }

    private final int limit;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    RateLimitFilter(int requestsPerMinute, Clock clock) {
        if (requestsPerMinute <= 0) {
            throw new IllegalArgumentException("requests-per-minute must be positive");
        }
        this.limit = requestsPerMinute;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ApiClient client = authenticatedClient();
        if (client == null) {
            chain.doFilter(request, response);
            return;
        }

        long now = clock.millis() / 1000;
        long windowStart = now - now % WINDOW_SECONDS;
        Window window = windows.compute(client.id(),
                (key, old) -> old == null || old.startSecond() != windowStart
                        ? new Window(windowStart, new AtomicInteger())
                        : old);
        if (windows.size() > MAX_TRACKED_CLIENTS) {
            windows.values().removeIf(w -> w.startSecond() != windowStart);
        }

        if (window.count().incrementAndGet() > limit) {
            long retryAfter = windowStart + WINDOW_SECONDS - now;
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType("application/problem+json");
            response.getWriter().write("""
                    {"type":"about:blank","title":"Too many requests","status":429,\
                    "detail":"Rate limit exceeded, retry after %d seconds"}""".formatted(retryAfter));
            return;
        }
        chain.doFilter(request, response);
    }

    private static ApiClient authenticatedClient() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof ApiClient client) {
            return client;
        }
        return null;
    }
}

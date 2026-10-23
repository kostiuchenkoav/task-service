package ru.strata.tasks.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.strata.tasks.security.ApiClient;

/**
 * Аутентификация клиента по заголовку {@code X-API-Key}.
 *
 * <p>Присланный ключ хешируется SHA-256 и сравнивается с хешами из конфигурации
 * через {@link MessageDigest#isEqual} (сравнение за постоянное время). Ключ нигде
 * не логируется. Если заголовка нет или ключ неизвестен, фильтр ничего не
 * устанавливает, и дальше Spring Security отвечает 401.
 */
final class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    static final String HEADER = "X-API-Key";
    static final String ROLE = "ROLE_API_CLIENT";

    private static final int SHA256_HEX_LENGTH = 64;

    /** Хеш ключа и клиент, которому он принадлежит. */
    private record KnownKey(byte[] sha256, ApiClient client) {
    }

    private final List<KnownKey> knownKeys;

    ApiKeyAuthenticationFilter(List<SecurityProperties.ApiKey> keys) {
        this.knownKeys = keys.stream().map(ApiKeyAuthenticationFilter::toKnownKey).toList();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        if (presented != null && !presented.isBlank()) {
            byte[] digest = sha256(presented.trim().getBytes(StandardCharsets.UTF_8));
            ApiClient matched = null;
            // Перебираем все ключи, не выходя при первом совпадении
            for (KnownKey known : knownKeys) {
                if (MessageDigest.isEqual(digest, known.sha256())) {
                    matched = known.client();
                }
            }
            if (matched != null) {
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        matched, null, List.of(new SimpleGrantedAuthority(ROLE))));
            }
        }
        chain.doFilter(request, response);
    }

    private static KnownKey toKnownKey(SecurityProperties.ApiKey key) {
        if (key.id() == null || key.id().isBlank()) {
            throw new IllegalStateException("app.security.api-keys: у каждого ключа должен быть id");
        }
        String hex = key.sha256() == null ? "" : key.sha256().trim().toLowerCase(Locale.ROOT);
        if (hex.length() != SHA256_HEX_LENGTH) {
            throw new IllegalStateException("app.security.api-keys[" + key.id()
                    + "].sha256 должен содержать 64 hex-символа (SHA-256 от ключа)");
        }
        byte[] bytes;
        try {
            bytes = HexFormat.of().parseHex(hex);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("app.security.api-keys[" + key.id()
                    + "].sha256 содержит не-hex символы", ex);
        }
        // Конструктор ApiClient проверит tenant и откажет при пустом значении
        return new KnownKey(bytes, new ApiClient(key.id(), key.tenant()));
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 обязан поддерживаться любой JVM
            throw new IllegalStateException(ex);
        }
    }
}

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

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

    /** id клиента → байты SHA-256 его ключа. */
    private final Map<String, byte[]> keyHashes;

    ApiKeyAuthenticationFilter(List<SecurityProperties.ApiKey> keys) {
        Map<String, byte[]> hashes = new LinkedHashMap<>();
        for (SecurityProperties.ApiKey key : keys) {
            hashes.put(key.id(), parseHash(key));
        }
        this.keyHashes = Map.copyOf(hashes);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        if (presented != null && !presented.isBlank()) {
            byte[] digest = sha256(presented.trim().getBytes(StandardCharsets.UTF_8));
            for (Map.Entry<String, byte[]> entry : keyHashes.entrySet()) {
                if (MessageDigest.isEqual(digest, entry.getValue())) {
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                            entry.getKey(), null, List.of(new SimpleGrantedAuthority(ROLE))));
                    break;
                }
            }
        }
        chain.doFilter(request, response);
    }

    private static byte[] parseHash(SecurityProperties.ApiKey key) {
        String hex = key.sha256() == null ? "" : key.sha256().trim().toLowerCase(Locale.ROOT);
        if (key.id() == null || key.id().isBlank()) {
            throw new IllegalStateException("app.security.api-keys: у каждого ключа должен быть id");
        }
        if (hex.length() != SHA256_HEX_LENGTH) {
            throw new IllegalStateException("app.security.api-keys[" + key.id()
                    + "].sha256 должен содержать 64 hex-символа (SHA-256 от ключа)");
        }
        try {
            return HexFormat.of().parseHex(hex);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("app.security.api-keys[" + key.id()
                    + "].sha256 содержит не-hex символы", ex);
        }
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

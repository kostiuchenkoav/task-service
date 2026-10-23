package ru.strata.tasks.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ограничение размера тела запроса. Без лимита JSON читается в память целиком,
 * и один большой запрос может исчерпать память сервиса.
 *
 * <p>Два уровня защиты:
 * <ul>
 *   <li>если заголовок {@code Content-Length} превышает лимит, запрос отклоняется
 *       сразу с {@code 413} и тело не читается;</li>
 *   <li>если длина не объявлена (передача chunked), поток считает прочитанные байты
 *       и обрывает чтение при превышении. Такой запрос получит {@code 400}.</li>
 * </ul>
 *
 * <p>Фильтр стоит первым в цепочке, поэтому действует и на анонимные запросы.
 */
final class RequestBodyLimitFilter extends OncePerRequestFilter {

    /** Реальные запросы задач занимают около 2 КБ, 64 КБ даёт большой запас. */
    static final long MAX_BODY_BYTES = 64L * 1024;

    private final long maxBytes;

    RequestBodyLimitFilter(long maxBytes) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        this.maxBytes = maxBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBytes) {
            reject(response);
            return;
        }
        chain.doFilter(new LimitedRequest(request, maxBytes), response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(413);
        response.setContentType("application/problem+json");
        response.getWriter().write("""
                {"type":"about:blank","title":"Payload too large","status":413,\
                "detail":"Request body exceeds %d bytes"}""".formatted(maxBytes));
    }

    /** Исключение при превышении лимита во время чтения потока. */
    static final class RequestBodyTooLargeException extends IOException {
        RequestBodyTooLargeException(long maxBytes) {
            super("Request body exceeds " + maxBytes + " bytes");
        }
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {

        private final long maxBytes;

        LimitedRequest(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new LimitedInputStream(super.getInputStream(), maxBytes);
        }
    }

    private static final class LimitedInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long maxBytes;
        private long consumed;

        LimitedInputStream(ServletInputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value != -1) {
                count(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = delegate.read(buffer, offset, length);
            if (read > 0) {
                count(read);
            }
            return read;
        }

        private void count(long bytes) throws IOException {
            consumed += bytes;
            if (consumed > maxBytes) {
                throw new RequestBodyTooLargeException(maxBytes);
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            delegate.setReadListener(listener);
        }
    }
}

package ru.strata.tasks.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestBodyLimitFilterTest {

    private static final long LIMIT = 16;

    private final RequestBodyLimitFilter filter = new RequestBodyLimitFilter(LIMIT);

    @Test
    void bodyWithinLimitPassesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/tasks");
        request.setContent("{\"a\":1}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void declaredLengthOverLimitIsRejectedBeforeReading() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/tasks");
        request.setContent("x".repeat(100).getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(response.getContentAsString()).contains("Payload too large");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void undeclaredLengthIsCutOffWhileReading() {
        // Длина не объявлена (chunked): заголовка Content-Length нет
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/tasks") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContent("x".repeat(100).getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) ->
                req.getInputStream().readAllBytes()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("exceeds");
    }

    @Test
    void constructorRejectsNonPositiveLimit() {
        assertThatThrownBy(() -> new RequestBodyLimitFilter(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

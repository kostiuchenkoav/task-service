package ru.strata.tasks.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.strata.tasks.security.ApiClient;

class RateLimitFilterTest {

    private static final ApiClient CLIENT_A = new ApiClient("key-a", "tenant-a");
    private static final ApiClient CLIENT_B = new ApiClient("key-b", "tenant-b");

    /** Часы, которые тест двигает вручную. */
    private static final class ManualClock extends Clock {
        private Instant now;

        ManualClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final ManualClock clock = new ManualClock(Instant.parse("2026-01-01T10:00:00Z"));
    private final RateLimitFilter filter = new RateLimitFilter(2, clock);

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requestsOverLimitGetTooManyRequestsWithRetryAfter() throws Exception {
        authenticate(CLIENT_A);

        assertThat(call(filter).getStatus()).isEqualTo(200);
        assertThat(call(filter).getStatus()).isEqualTo(200);

        MockHttpServletResponse third = call(filter);
        assertThat(third.getStatus()).isEqualTo(429);
        assertThat(third.getHeader("Retry-After")).isEqualTo("60");
        assertThat(third.getContentType()).isEqualTo("application/problem+json");
        assertThat(third.getContentAsString()).contains("Too many requests");
    }

    @Test
    void limitResetsWhenNewWindowStarts() throws Exception {
        authenticate(CLIENT_A);
        call(filter);
        call(filter);
        assertThat(call(filter).getStatus()).isEqualTo(429);

        clock.advance(Duration.ofSeconds(60));

        assertThat(call(filter).getStatus()).isEqualTo(200);
    }

    @Test
    void limitIsTrackedPerApiKey() throws Exception {
        authenticate(CLIENT_A);
        call(filter);
        call(filter);
        assertThat(call(filter).getStatus()).isEqualTo(429);

        authenticate(CLIENT_B);

        assertThat(call(filter).getStatus()).isEqualTo(200);
    }

    @Test
    void anonymousRequestsAreNotCounted() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(call(filter).getStatus()).isEqualTo(200);
        }

        authenticate(CLIENT_A);
        assertThat(call(filter).getStatus()).isEqualTo(200);
    }

    @Test
    void nonPositiveLimitIsRejectedAtConstruction() {
        assertThatThrownBy(() -> new RateLimitFilter(0, clock))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void authenticate(ApiClient client) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                client, null, List.of(new SimpleGrantedAuthority("ROLE_API_CLIENT"))));
    }

    private static MockHttpServletResponse call(RateLimitFilter filter) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/tasks"), response, new MockFilterChain());
        return response;
    }
}

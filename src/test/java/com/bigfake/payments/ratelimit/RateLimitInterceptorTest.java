package com.bigfake.payments.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitInterceptorTest {

    private static final Object HANDLER = new Object();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RateLimitProperties properties;
    private long nowNanos;

    @BeforeEach
    void setUp() {
        nowNanos = 0L;
        properties = new RateLimitProperties();
        properties.setCapacity(3);
        properties.setRefillPeriod(Duration.ofMinutes(1));
        properties.setPathPatterns(Collections.singletonList("/api/v1/payments/**"));
    }

    private RateLimitInterceptor interceptor() {
        return new RateLimitInterceptor(properties, objectMapper, () -> nowNanos);
    }

    private MockHttpServletRequest request(String merchantId) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/payments");
        request.setRemoteAddr("10.0.0.1");
        if (merchantId != null) {
            request.addHeader(RateLimitInterceptor.HEADER_MERCHANT_ID, merchantId);
        }
        return request;
    }

    @Test
    void requestsUnderTheLimitAreAllowed() throws Exception {
        RateLimitInterceptor interceptor = interceptor();

        for (int i = 0; i < properties.getCapacity(); i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertThat(interceptor.preHandle(request("42"), response, HANDLER)).isTrue();
            assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
            assertThat(response.getHeader(RateLimitInterceptor.HEADER_LIMIT)).isEqualTo("3");
            assertThat(response.getHeader(RateLimitInterceptor.HEADER_REMAINING))
                    .isEqualTo(String.valueOf(properties.getCapacity() - 1 - i));
        }
    }

    @Test
    void requestOverTheLimitIsRejectedWith429() throws Exception {
        RateLimitInterceptor interceptor = interceptor();
        for (int i = 0; i < properties.getCapacity(); i++) {
            interceptor.preHandle(request("42"), new MockHttpServletResponse(), HANDLER);
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request("42"), response, HANDLER)).isFalse();

        assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(response.getHeader(RateLimitInterceptor.HEADER_REMAINING)).isEqualTo("0");
        assertThat(Long.parseLong(response.getHeader(RateLimitInterceptor.HEADER_RETRY_AFTER))).isPositive();
        assertThat(Long.parseLong(response.getHeader(RateLimitInterceptor.HEADER_RESET))).isPositive();

        Map<String, Object> body = objectMapper.readValue(response.getContentAsString(), Map.class);
        assertThat(body.get("error")).isEqualTo(RateLimitInterceptor.ERROR_CODE);
        assertThat((String) body.get("message")).contains("Too many requests");
    }

    @Test
    void allowanceIsRestoredAfterTheRefillPeriod() throws Exception {
        RateLimitInterceptor interceptor = interceptor();
        for (int i = 0; i < properties.getCapacity(); i++) {
            interceptor.preHandle(request("42"), new MockHttpServletResponse(), HANDLER);
        }
        assertThat(interceptor.preHandle(request("42"), new MockHttpServletResponse(), HANDLER)).isFalse();

        nowNanos += properties.getRefillPeriod().toNanos();

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request("42"), response, HANDLER)).isTrue();
        assertThat(response.getHeader(RateLimitInterceptor.HEADER_REMAINING)).isEqualTo("2");
    }

    @Test
    void limitsAreTrackedPerClient() throws Exception {
        RateLimitInterceptor interceptor = interceptor();
        for (int i = 0; i < properties.getCapacity(); i++) {
            interceptor.preHandle(request("42"), new MockHttpServletResponse(), HANDLER);
        }
        assertThat(interceptor.preHandle(request("42"), new MockHttpServletResponse(), HANDLER)).isFalse();

        assertThat(interceptor.preHandle(request("99"), new MockHttpServletResponse(), HANDLER)).isTrue();
    }

    @Test
    void clientsWithoutIdentifyingHeadersAreKeyedByIp() throws Exception {
        RateLimitInterceptor interceptor = interceptor();
        for (int i = 0; i < properties.getCapacity(); i++) {
            interceptor.preHandle(request(null), new MockHttpServletResponse(), HANDLER);
        }
        assertThat(interceptor.preHandle(request(null), new MockHttpServletResponse(), HANDLER)).isFalse();

        MockHttpServletRequest otherIp = request(null);
        otherIp.setRemoteAddr("10.0.0.2");
        assertThat(interceptor.preHandle(otherIp, new MockHttpServletResponse(), HANDLER)).isTrue();
    }

    @Test
    void disabledLimiterAllowsEverything() throws Exception {
        properties.setEnabled(false);
        properties.setCapacity(1);
        RateLimitInterceptor interceptor = interceptor();

        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertThat(interceptor.preHandle(request("42"), response, HANDLER)).isTrue();
            assertThat(response.getHeader(RateLimitInterceptor.HEADER_LIMIT)).isNull();
        }
    }

    @Test
    void invalidConfigurationIsRejectedOnStartup() {
        properties.setCapacity(0);
        assertThatThrownBy(this::interceptor)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("capacity");

        properties.setCapacity(1);
        properties.setRefillPeriod(Duration.ZERO);
        assertThatThrownBy(this::interceptor)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("refill-period");
    }
}

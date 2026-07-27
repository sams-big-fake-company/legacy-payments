package com.bigfake.payments.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;

/**
 * Rejects requests from a client once it exceeds its configured request allowance.
 *
 * <p>Buckets are held in memory, so limits are enforced per application instance. This
 * protects a single node from bursts but is not a cluster-wide quota; the per-merchant
 * velocity check in {@code PaymentServiceImpl} remains the business-level control on how
 * many payments a merchant may actually create.
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    static final String HEADER_MERCHANT_ID = "X-Merchant-Id";
    static final String HEADER_API_KEY = "X-API-Key";
    static final String HEADER_FORWARDED_FOR = "X-Forwarded-For";
    static final String HEADER_LIMIT = "X-RateLimit-Limit";
    static final String HEADER_REMAINING = "X-RateLimit-Remaining";
    static final String HEADER_RESET = "X-RateLimit-Reset";
    static final String HEADER_RETRY_AFTER = "Retry-After";
    static final String ERROR_CODE = "RATE_LIMIT_EXCEEDED";

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final LongSupplier nanoClock;
    private final ConcurrentMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimitInterceptor(RateLimitProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, System::nanoTime);
    }

    RateLimitInterceptor(RateLimitProperties properties, ObjectMapper objectMapper, LongSupplier nanoClock) {
        if (properties.getCapacity() < 1) {
            throw new IllegalArgumentException("app.rate-limit.capacity must be at least 1");
        }
        if (properties.getRefillPeriod() == null || properties.getRefillPeriod().isZero()
                || properties.getRefillPeriod().isNegative()) {
            throw new IllegalArgumentException("app.rate-limit.refill-period must be positive");
        }
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.nanoClock = nanoClock;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!properties.isEnabled()) {
            return true;
        }

        long now = nanoClock.getAsLong();
        TokenBucket.Decision decision = bucketFor(resolveClientKey(request), now).tryConsume(now);

        response.setHeader(HEADER_LIMIT, String.valueOf(properties.getCapacity()));
        response.setHeader(HEADER_REMAINING, String.valueOf(decision.getRemaining()));
        response.setHeader(HEADER_RESET, String.valueOf(decision.getResetSeconds()));

        if (decision.isAllowed()) {
            return true;
        }

        log.warn("Rate limit exceeded for {} {}", request.getMethod(), request.getRequestURI());
        response.setHeader(HEADER_RETRY_AFTER, String.valueOf(decision.getRetryAfterSeconds()));
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("error", ERROR_CODE);
        body.put("message", "Too many requests - limit is " + properties.getCapacity() + " requests per "
                + properties.getRefillPeriod().getSeconds() + " seconds");
        body.put("retryAfterSeconds", decision.getRetryAfterSeconds());
        objectMapper.writeValue(response.getWriter(), body);
        return false;
    }

    private TokenBucket bucketFor(String clientKey, long now) {
        if (!buckets.containsKey(clientKey) && buckets.size() >= properties.getMaxTrackedClients()) {
            evict(now);
        }
        return buckets.computeIfAbsent(clientKey,
                key -> new TokenBucket(properties.getCapacity(), properties.getRefillPeriod().toNanos(), now));
    }

    /**
     * Drops clients that have regained their full allowance; a client that is dropped simply
     * starts from a fresh, full bucket on its next request.
     */
    private void evict(long now) {
        buckets.values().removeIf(bucket -> bucket.isFull(now));
        if (buckets.size() >= properties.getMaxTrackedClients()) {
            log.warn("Rate limiter tracking {} clients, resetting all buckets", buckets.size());
            buckets.clear();
        }
    }

    private String resolveClientKey(HttpServletRequest request) {
        String merchantId = trimToNull(request.getHeader(HEADER_MERCHANT_ID));
        if (merchantId != null) {
            return "merchant:" + merchantId;
        }
        String apiKey = trimToNull(request.getHeader(HEADER_API_KEY));
        if (apiKey != null) {
            return "api-key:" + apiKey;
        }
        Principal principal = request.getUserPrincipal();
        if (principal != null && trimToNull(principal.getName()) != null) {
            return "user:" + principal.getName();
        }
        return "ip:" + clientIp(request);
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = trimToNull(request.getHeader(HEADER_FORWARDED_FOR));
        if (forwardedFor != null) {
            String firstHop = trimToNull(forwardedFor.split(",")[0]);
            if (firstHop != null) {
                return firstHop;
            }
        }
        String remoteAddr = trimToNull(request.getRemoteAddr());
        return remoteAddr != null ? remoteAddr : "unknown";
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

package com.bigfake.payments.ratelimit;

/**
 * Token bucket holding the request allowance of a single client.
 *
 * <p>The bucket starts full and regains {@code capacity} tokens over one refill period,
 * so short bursts are absorbed while the sustained rate stays at capacity per period.
 */
class TokenBucket {

    private final long capacity;
    private final double tokensPerNano;

    private double tokens;
    private long lastRefillNanos;

    TokenBucket(long capacity, long refillPeriodNanos, long nowNanos) {
        this.capacity = capacity;
        this.tokensPerNano = (double) capacity / refillPeriodNanos;
        this.tokens = capacity;
        this.lastRefillNanos = nowNanos;
    }

    synchronized Decision tryConsume(long nowNanos) {
        refill(nowNanos);
        if (tokens >= 1.0d) {
            tokens -= 1.0d;
            return new Decision(true, (long) tokens, secondsUntilFull());
        }
        long retryAfterSeconds = toCeilSeconds((1.0d - tokens) / tokensPerNano);
        return new Decision(false, 0L, secondsUntilFull(), retryAfterSeconds);
    }

    synchronized boolean isFull(long nowNanos) {
        refill(nowNanos);
        return tokens >= capacity;
    }

    private void refill(long nowNanos) {
        long elapsedNanos = nowNanos - lastRefillNanos;
        if (elapsedNanos > 0) {
            tokens = Math.min(capacity, tokens + elapsedNanos * tokensPerNano);
            lastRefillNanos = nowNanos;
        }
    }

    private long secondsUntilFull() {
        return toCeilSeconds((capacity - tokens) / tokensPerNano);
    }

    private static long toCeilSeconds(double nanos) {
        return Math.max(1L, (long) Math.ceil(nanos / 1_000_000_000d));
    }

    static final class Decision {

        private final boolean allowed;
        private final long remaining;
        private final long resetSeconds;
        private final long retryAfterSeconds;

        Decision(boolean allowed, long remaining, long resetSeconds) {
            this(allowed, remaining, resetSeconds, 0L);
        }

        Decision(boolean allowed, long remaining, long resetSeconds, long retryAfterSeconds) {
            this.allowed = allowed;
            this.remaining = remaining;
            this.resetSeconds = resetSeconds;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        boolean isAllowed() {
            return allowed;
        }

        long getRemaining() {
            return remaining;
        }

        long getResetSeconds() {
            return resetSeconds;
        }

        long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }
}

package com.bigfake.payments.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Configuration for the API rate limiter.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    /** Whether inbound requests are rate limited at all. */
    private boolean enabled = true;

    /** Maximum number of requests a single client may burst. */
    private int capacity = 100;

    /** Time it takes for a client to regain its full request allowance. */
    private Duration refillPeriod = Duration.ofMinutes(1);

    /** Request paths the limiter applies to. */
    private List<String> pathPatterns = new ArrayList<>(Arrays.asList("/api/v1/payments/**"));

    /** Upper bound on the number of clients tracked in memory. */
    private int maxTrackedClients = 10_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public Duration getRefillPeriod() {
        return refillPeriod;
    }

    public void setRefillPeriod(Duration refillPeriod) {
        this.refillPeriod = refillPeriod;
    }

    public List<String> getPathPatterns() {
        return pathPatterns;
    }

    public void setPathPatterns(List<String> pathPatterns) {
        this.pathPatterns = pathPatterns;
    }

    public int getMaxTrackedClients() {
        return maxTrackedClients;
    }

    public void setMaxTrackedClients(int maxTrackedClients) {
        this.maxTrackedClients = maxTrackedClients;
    }
}

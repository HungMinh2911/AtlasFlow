package com.atlasflow.ratelimit;

public record RateLimitDecision(
        boolean allowed, // request co duoc phep hay ko
        long currentCount, // counter hien tai
        long retryAfterSeconds // can doi bao nhieu giay
) {

}

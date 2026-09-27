package com.atlasflow.ratelimit;

public class RateLimitUnavailableException extends RuntimeException {

    public RateLimitUnavailableException() {
        super("Rate-limit storage is temporarily unavailable");
    }
}

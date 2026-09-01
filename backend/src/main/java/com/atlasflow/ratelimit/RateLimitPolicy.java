package com.atlasflow.ratelimit;

public enum RateLimitPolicy {

    LOGIN(5, 60),
    SUBMIT_JOB(30, 60),
    READ_ENDPOINT(120, 60);

    private final long limit;
    private final long windowSeconds;

    RateLimitPolicy(long limit, long windowSeconds) {
        this.limit = limit;
        this.windowSeconds = windowSeconds;
    }

    public long limit() {
        return limit;
    }

    public long windowSeconds() {
        return windowSeconds;
    }

}

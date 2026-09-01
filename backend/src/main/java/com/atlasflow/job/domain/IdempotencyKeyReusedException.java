package com.atlasflow.job.domain;

public class IdempotencyKeyReusedException extends RuntimeException {
    public IdempotencyKeyReusedException(String key) {
        super("Idempotency-Key has already been used with a different payload: " + key);
    }
}

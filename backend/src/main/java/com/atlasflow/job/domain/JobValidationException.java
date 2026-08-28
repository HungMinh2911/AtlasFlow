package com.atlasflow.job.domain;

public class JobValidationException extends RuntimeException {

    public JobValidationException(String message) {
        super(message);
    }
}

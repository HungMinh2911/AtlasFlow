package com.atlasflow.message.domain;

public class NonRetryableJobException extends RuntimeException {


    public NonRetryableJobException(String message) {
        super(message);
    }
}

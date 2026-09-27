package com.atlasflow.job.execution.domain;

import java.util.UUID;

public class JobExecutionNotFoundException extends RuntimeException {

    public JobExecutionNotFoundException(UUID executionId) {
        super("Job execution does not exist with id: " + executionId);
    }
}

package com.atlasflow.job.execution.api;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateJobExcutionRequest(

        @NotNull
        UUID jobId
) {

}

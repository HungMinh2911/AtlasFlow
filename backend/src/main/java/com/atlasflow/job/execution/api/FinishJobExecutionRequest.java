package com.atlasflow.job.execution.api;

import com.atlasflow.job.execution.domain.JobExecutionStatus;
import jakarta.validation.constraints.NotNull;

public record FinishJobExecutionRequest(
        @NotNull JobExecutionStatus status
) {
}

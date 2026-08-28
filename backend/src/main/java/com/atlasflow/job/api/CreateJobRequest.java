package com.atlasflow.job.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateJobRequest(
        @NotNull UUID project_id,
        @NotNull UUID workflow_id,
        @NotNull UUID file_id
) {
}

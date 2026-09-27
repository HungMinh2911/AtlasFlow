package com.atlasflow.workflow.api;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateWorkflowRequest(
        @NotNull
        UUID project_id
) {
}

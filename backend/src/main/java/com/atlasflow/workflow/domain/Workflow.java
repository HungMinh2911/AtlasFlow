package com.atlasflow.workflow.domain;

import java.util.UUID;

public record Workflow(
        UUID id, UUID project_id,
        int version, WorkflowStatus status) {

    public static Workflow create(
            UUID id,
            UUID project_id,
            int version
    ) {
        return new Workflow(
                id,
                project_id,
                version,
                WorkflowStatus.DRAFT
        );
    }
}

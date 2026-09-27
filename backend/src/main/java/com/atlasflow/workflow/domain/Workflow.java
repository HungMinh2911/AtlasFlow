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

    public Workflow publish() {
        if (status != WorkflowStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT workflow can be published");
        }
        return new Workflow(id, project_id, version, WorkflowStatus.PUBLISHED);
    }
}

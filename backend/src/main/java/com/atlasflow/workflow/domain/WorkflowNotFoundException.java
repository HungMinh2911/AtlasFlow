package com.atlasflow.workflow.domain;

import java.util.UUID;

public class WorkflowNotFoundException extends RuntimeException {

    private final UUID workflowId;

    public WorkflowNotFoundException(UUID workflowId) {
        super("Workflow does not exist with id: " + workflowId);
        this.workflowId = workflowId;
    }

    public UUID workflowId() {
        return workflowId;
    }
}

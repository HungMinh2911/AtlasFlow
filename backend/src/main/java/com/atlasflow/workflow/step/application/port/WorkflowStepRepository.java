package com.atlasflow.workflow.step.application.port;

import com.atlasflow.workflow.step.api.WorkflowStepResponse;
import com.atlasflow.workflow.step.domain.WorkflowStep;

import java.util.List;
import java.util.UUID;

public interface WorkflowStepRepository {

    boolean existsByWorkflowIdAndPosition(UUID workflowId, int position);

    List<WorkflowStep> getListWorkflowStepByWorkflowId(UUID workflowId);

    WorkflowStep save(WorkflowStep workflowStep);
}

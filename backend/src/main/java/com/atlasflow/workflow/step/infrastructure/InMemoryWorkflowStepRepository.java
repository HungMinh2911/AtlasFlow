package com.atlasflow.workflow.step.infrastructure;

import com.atlasflow.workflow.step.api.WorkflowStepResponse;
import com.atlasflow.workflow.step.application.port.WorkflowStepRepository;
import com.atlasflow.workflow.step.domain.WorkflowStep;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryWorkflowStepRepository implements WorkflowStepRepository {

    private final Map<UUID, WorkflowStep> workflowSteps =
            new ConcurrentHashMap<>();

    @Override
    public boolean existsByWorkflowIdAndPosition(
            UUID workflowId,
            int position
    ) {
        return workflowSteps.values().stream()
                .anyMatch(step ->
                        step.workflowId().equals(workflowId)
                                && step.position() == position
                );
    }

    @Override
    public List<WorkflowStep> getListWorkflowStepByWorkflowId(UUID workflowId){
        return  workflowSteps.values().stream()
                .filter(workflowStep -> workflowStep.workflowId().equals(workflowId)).toList();

    }



    @Override
    public WorkflowStep save(WorkflowStep workflowStep) {
        workflowSteps.put(workflowStep.id(), workflowStep);
        return workflowStep;
    }
}

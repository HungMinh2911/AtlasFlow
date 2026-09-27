package com.atlasflow.workflow.infrastructure;

import com.atlasflow.workflow.application.port.WorkflowRepository;
import com.atlasflow.workflow.domain.Workflow;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryWorkflowRepository implements WorkflowRepository {

    private final Map<UUID, Workflow> workflows = new ConcurrentHashMap<>();

    @Override
    public Optional<Integer> findMaxVersionByProjectId(UUID projectId) {
        return workflows.values().stream()
                .filter(workflow ->  workflow.project_id().equals(projectId))
                .map(Workflow::version)
                .max(Integer::compareTo);
    }

    @Override
    public Optional<Workflow> findById(UUID id) {
        return Optional.ofNullable(workflows.get(id));
    }



    @Override
    public Workflow save(Workflow workflow) {
        workflows.put(workflow.id(), workflow);
        return workflow;
    }
}

package com.atlasflow.workflow.application.port;

import com.atlasflow.workflow.domain.Workflow;

import java.util.Optional;
import java.util.UUID;

public interface WorkflowRepository {

    Optional<Integer> findMaxVersionByProjectId(UUID projectId);

    Optional<Workflow> findById(UUID id);


    Workflow save(Workflow workflow);
}

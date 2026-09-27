package com.atlasflow.project.application.port;


import com.atlasflow.project.domain.Project;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface ProjectRepository {

    boolean existsByOrganizationIdAndName(UUID organizationId, String name);


    Optional<Project> findProjectById(UUID id);

    List<Project> findAll();

    Project save(Project project);
}

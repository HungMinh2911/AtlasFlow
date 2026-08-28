package com.atlasflow.project.application;

import com.atlasflow.project.api.CreateProjectRequest;
import com.atlasflow.project.api.ProjectResponse;
import com.atlasflow.project.application.port.ProjectRepository;
import com.atlasflow.project.domain.Project;
import com.atlasflow.project.domain.ProjectNameAlreadyExistsException;
import com.atlasflow.project.domain.ProjectNotExistsException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ProjectApplicationService {

    private final ProjectRepository projectRepository;

    public ProjectApplicationService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public ProjectResponse createProject(CreateProjectRequest request) {
        String normalizedName = request.name().trim();

        if (projectRepository.existsByOrganizationIdAndName(
                request.organization_id(),
                normalizedName
        )) {
            throw new ProjectNameAlreadyExistsException(normalizedName);
        }

        Project project = Project.create(
                UUID.randomUUID(),
                request.organization_id(),
                normalizedName
        );

        return ProjectResponse.from(projectRepository.save(project));
    }

    public ProjectResponse getProject(UUID id) {
        Project project = projectRepository.findProjectById(id)
                .orElseThrow(() -> new ProjectNotExistsException(id));

        return ProjectResponse.from(project);
    }

    public List<ProjectResponse> getProjects() {
        return projectRepository.findAll().stream()
                .map(ProjectResponse::from)
                .toList();
    }
}

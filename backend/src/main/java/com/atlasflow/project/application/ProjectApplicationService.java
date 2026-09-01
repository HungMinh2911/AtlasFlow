package com.atlasflow.project.application;

import com.atlasflow.project.api.CreateProjectRequest;
import com.atlasflow.project.api.ProjectResponse;
import com.atlasflow.project.application.port.ProjectRepository;
import com.atlasflow.project.domain.Project;
import com.atlasflow.project.domain.ProjectNameAlreadyExistsException;
import com.atlasflow.project.domain.ProjectNotExistsException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProjectApplicationService {

    private final ProjectRepository projectRepository;
    private final ProjectCacheService projectCacheService;

    public ProjectApplicationService(
            ProjectRepository projectRepository,
            ProjectCacheService projectCacheService
    ) {
        this.projectRepository = projectRepository;
        this.projectCacheService = projectCacheService;
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

        ProjectResponse response = ProjectResponse.from(projectRepository.save(project));
        projectCacheService.putProjectToCache(response);
        return response;
    }

    public ProjectResponse getProject(UUID id) {
        return projectCacheService.getProjectFromCache(id)
                .orElseGet(() -> loadProjectAfterCacheMiss(id));
    }

    private ProjectResponse loadProjectAfterCacheMiss(UUID id) {
        Optional<String> lockToken = Optional.ofNullable(
                projectCacheService.tryAcquireLoadLock(id)
        ).orElseGet(Optional::empty);

        if (lockToken.isEmpty()) {
            ProjectResponse response = loadProjectFromDatabase(id);
            projectCacheService.putProjectToCache(response);
            return response;
        }

        try {
            Optional<ProjectResponse> secondRead =
                    projectCacheService.getProjectFromCache(id);
            if (secondRead.isPresent()) {
                return secondRead.get();
            }

            ProjectResponse response = loadProjectFromDatabase(id);
            projectCacheService.putProjectToCache(response);
            return response;
        } finally {
            projectCacheService.releaseLoadLock(id, lockToken.get());
        }
    }

    private ProjectResponse loadProjectFromDatabase(UUID id) {
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

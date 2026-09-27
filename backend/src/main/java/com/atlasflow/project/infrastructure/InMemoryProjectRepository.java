package com.atlasflow.project.infrastructure;

import com.atlasflow.project.application.port.ProjectRepository;
import com.atlasflow.project.domain.Project;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryProjectRepository implements ProjectRepository {

    private final Map<UUID, Project> projects = new ConcurrentHashMap<>();

    @Override
    public boolean existsByOrganizationIdAndName(
            UUID organizationId,
            String name
    ) {
        return projects.values().stream()
                .anyMatch(project ->
                        project.organization_id().equals(organizationId)
                                && project.name().equalsIgnoreCase(name)
                );
    }

    @Override
    public Optional<Project> findProjectById(UUID id) {
        return Optional.ofNullable(projects.get(id));
    }

    @Override
    public List<Project> findAll() {
        return projects.values().stream()
                .sorted(java.util.Comparator.comparing(Project::created_at))
                .toList();
    }


    @Override
    public Project save(Project project) {
        projects.put(project.id(), project);
        return project;
    }
}

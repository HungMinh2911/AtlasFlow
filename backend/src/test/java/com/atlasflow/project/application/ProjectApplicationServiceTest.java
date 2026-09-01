package com.atlasflow.project.application;

import com.atlasflow.project.api.CreateProjectRequest;
import com.atlasflow.project.api.ProjectResponse;
import com.atlasflow.project.application.port.ProjectRepository;
import com.atlasflow.project.domain.Project;
import com.atlasflow.project.domain.ProjectNameAlreadyExistsException;
import com.atlasflow.project.domain.ProjectNotExistsException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectApplicationServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectCacheService projectCacheService;

    @Test
    void createProjectShouldTrimNameAndSaveActiveProject() {
        ProjectApplicationService service =
                new ProjectApplicationService(projectRepository, projectCacheService);
        UUID organizationId = UUID.randomUUID();

        when(projectRepository.existsByOrganizationIdAndName(
                organizationId,
                "AtlasFlow"
        )).thenReturn(false);
        when(projectRepository.save(any(Project.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProjectResponse response = service.createProject(
                new CreateProjectRequest(organizationId, "  AtlasFlow  ")
        );

        assertEquals("AtlasFlow", response.name());
        assertEquals("ACTIVE", response.status());

        ArgumentCaptor<Project> captor =
                ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(captor.capture());
        assertEquals("AtlasFlow", captor.getValue().name());
        verify(projectCacheService).putProjectToCache(any(ProjectResponse.class));
    }

    @Test
    void createProjectShouldRejectDuplicateNameInSameOrganization() {
        ProjectApplicationService service =
                new ProjectApplicationService(projectRepository, projectCacheService);
        UUID organizationId = UUID.randomUUID();

        when(projectRepository.existsByOrganizationIdAndName(
                organizationId,
                "AtlasFlow"
        )).thenReturn(true);

        assertThrows(
                ProjectNameAlreadyExistsException.class,
                () -> service.createProject(
                        new CreateProjectRequest(organizationId, "AtlasFlow")
                )
        );
    }

    @Test
    void getProjectsShouldMapAllProjectsToResponses() {
        ProjectApplicationService service =
                new ProjectApplicationService(projectRepository, projectCacheService);
        Project project = Project.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "AtlasFlow"
        );

        when(projectRepository.findAll()).thenReturn(List.of(project));

        List<ProjectResponse> response = service.getProjects();

        assertEquals(1, response.size());
        assertEquals(project.id(), response.get(0).id());
    }

    @Test
    void getProjectShouldReturnFromCacheWhenPresent() {
        ProjectApplicationService service =
                new ProjectApplicationService(projectRepository, projectCacheService);
        UUID projectId = UUID.randomUUID();
        ProjectResponse cachedResponse = new ProjectResponse(
                projectId,
                UUID.randomUUID(),
                "Cached Project",
                "ACTIVE",
                LocalDateTime.now()
        );

        when(projectCacheService.getProjectFromCache(projectId))
                .thenReturn(Optional.of(cachedResponse));

        ProjectResponse response = service.getProject(projectId);

        assertEquals("Cached Project", response.name());
        verify(projectCacheService).getProjectFromCache(projectId);
        verifyNoInteractions(projectRepository);
    }

    @Test
    void getProjectShouldFetchFromDbAndCacheWhenMiss() {
        ProjectApplicationService service =
                new ProjectApplicationService(projectRepository, projectCacheService);
        UUID projectId = UUID.randomUUID();
        Project project = Project.create(
                projectId,
                UUID.randomUUID(),
                "DB Project"
        );

        when(projectCacheService.getProjectFromCache(projectId))
                .thenReturn(Optional.empty());
        when(projectRepository.findProjectById(projectId))
                .thenReturn(Optional.of(project));

        ProjectResponse response = service.getProject(projectId);

        assertEquals("DB Project", response.name());
        verify(projectRepository).findProjectById(projectId);
        verify(projectCacheService).putProjectToCache(any(ProjectResponse.class));
    }

    @Test
    void getProjectShouldRejectUnknownId() {
        ProjectApplicationService service =
                new ProjectApplicationService(projectRepository, projectCacheService);
        UUID projectId = UUID.randomUUID();

        when(projectCacheService.getProjectFromCache(projectId))
                .thenReturn(Optional.empty());
        when(projectRepository.findProjectById(projectId))
                .thenReturn(Optional.empty());

        assertThrows(
                ProjectNotExistsException.class,
                () -> service.getProject(projectId)
        );
    }
}

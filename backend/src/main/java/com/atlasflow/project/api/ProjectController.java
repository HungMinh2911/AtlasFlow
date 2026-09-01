package com.atlasflow.project.api;

import com.atlasflow.project.application.ProjectApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectApplicationService projectApplicationService;

    public ProjectController(ProjectApplicationService projectApplicationService) {
        this.projectApplicationService = projectApplicationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse createProject(
            @Valid @RequestBody CreateProjectRequest request
    ) {
        return projectApplicationService.createProject(request);
    }

    @GetMapping
    public List<ProjectResponse> getProjects() {
        return projectApplicationService.getProjects();
    }

    @GetMapping("/{id}")
    public ProjectResponse getProject(
            @PathVariable UUID id
    ){
        return  projectApplicationService.getProject(id);
    }

}

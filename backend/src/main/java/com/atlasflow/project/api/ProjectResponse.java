package com.atlasflow.project.api;

import com.atlasflow.project.domain.Project;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProjectResponse(
        UUID id , UUID organization_id, String name,
        String status, LocalDateTime created_at
) {
    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.id(),
                project.organization_id(),
                project.name(),
                project.status().name(),
                project.created_at()
        );
    }
}

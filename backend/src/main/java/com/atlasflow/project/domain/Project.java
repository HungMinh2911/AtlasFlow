package com.atlasflow.project.domain;

import java.time.LocalDateTime;
import java.util.UUID;

public record Project(
        UUID id,
        UUID organization_id,
        String name,
        ProjectStatus status,
        LocalDateTime created_at
) {
    public static Project create(
            UUID id,
            UUID organization_id,
            String name
    ) {
        return new Project(
                id,
                organization_id,
                name,
                ProjectStatus.ACTIVE,
                LocalDateTime.now()
        );
    }
}

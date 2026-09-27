package com.atlasflow.project.domain;

import java.util.UUID;

public class ProjectNotExistsException extends RuntimeException {

    private final UUID id;

    public ProjectNotExistsException(UUID id) {
        super("Project does not exist with id: " + id);
        this.id = id;
    }

    public UUID id() {
        return id;
    }
}

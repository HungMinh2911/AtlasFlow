package com.atlasflow.project.api;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record GetProjectRequest(

        @NotNull
        UUID id
) {
}

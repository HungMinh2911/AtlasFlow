package com.atlasflow.project.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateProjectRequest(

        @NotNull
        UUID organization_id,

        @NotBlank(message = "name khong duoc rong")
        @Size(max = 120)
        String name
        ) {}

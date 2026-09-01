package com.atlasflow.users.domain;

import java.time.LocalDateTime;
import java.util.UUID;

public record User(
        UUID id,
        String email,
        String passwordHash,
        UserStatus status,
        LocalDateTime createdAt
) {
    public static User create(
            UUID id,
            String email,
            String passwordHash
    ) {
        return new User(
                id,
                email,
                passwordHash,
                UserStatus.ACTIVE,
                LocalDateTime.now()
        );
    }
}

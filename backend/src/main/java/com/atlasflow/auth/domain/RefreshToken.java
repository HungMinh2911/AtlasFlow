package com.atlasflow.auth.domain;

import java.time.LocalDateTime;
import java.util.UUID;

public record RefreshToken(
        UUID id,
        String token_hash,
        UUID user_id,
        LocalDateTime created_at,
        LocalDateTime expires_at,
        Boolean revoked,
        UUID replaced_by_token ,
        UUID family_id
) {

}

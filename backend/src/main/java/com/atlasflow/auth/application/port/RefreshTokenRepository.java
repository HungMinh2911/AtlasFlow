package com.atlasflow.auth.application.port;

import com.atlasflow.auth.domain.RefreshToken;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {

    Optional<RefreshToken> findByToken(String token);
    void delete(RefreshToken token);
    RefreshToken save(RefreshToken token);
    void revokeToken(String token);
    void revokeById(UUID id, UUID replacedByTokenId);
    void revokeAllByUserId(UUID userId);
    void revokeAllByFamilyId(UUID familyId);
}

package com.atlasflow.auth.infreastructure;


import com.atlasflow.auth.application.AuthService;
import com.atlasflow.auth.domain.RefreshToken;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcRefreshTokenRepository implements com.atlasflow.auth.application.port.RefreshTokenRepository {


    private final JdbcClient jdbcClient;

    public JdbcRefreshTokenRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public RefreshToken mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {


        return new RefreshToken(
                rs.getObject("id", java.util.UUID.class),
                rs.getString("token_hash"),
                rs.getObject("user_id", java.util.UUID.class),
                rs.getTimestamp("created_at").toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDateTime(),
                rs.getTimestamp("expires_at").toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDateTime(),
                rs.getBoolean("revoked"),
                rs.getObject("replaced_by_token", UUID.class),
                rs.getObject("family_id", UUID.class)
        );
    }


    @Override
    public Optional<RefreshToken> findByToken(String token) {

        String token_hash = AuthService.hash(token);

        return jdbcClient.sql("""
                 SELECT * FROM refresh_tokens
                 WHERE token_hash = :token_hash
                 """)
                .param("token_hash", token_hash)
                .query(this::mapRow)
                .optional();
    }

    @Override
    public void delete(RefreshToken token) {

        String sql = """
                DELETE FROM refresh_tokens
                WHERE id = :id
                """;
        jdbcClient.sql(sql)
                .param("id", token.id())
                .update();
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        String sql = """
                INSERT INTO refresh_tokens (id, token_hash, user_id, created_at, expires_at, revoked, replaced_by_token, family_id)
                VALUES (:id, :token_hash, :user_id, :created_at, :expires_at, :revoked, :replaced_by_token, :family_id)
                """;
        jdbcClient.sql(sql)
                .param("id", token.id())
                .param("token_hash", token.token_hash())
                .param("user_id", token.user_id())
                .param("created_at", token.created_at())
                .param("expires_at", token.expires_at())
                .param("revoked", token.revoked())
                .param("replaced_by_token", token.replaced_by_token())
                .param("family_id", token.family_id())
                .update();

        return token;
    }

    @Override
    public void revokeToken(String token) {

        String token_hash = AuthService.hash(token);

        String sql = """
                UPDATE refresh_tokens
                SET revoked = TRUE
                WHERE token_hash = :token_hash
                """;

        jdbcClient.sql(sql)
                .param("token_hash", token_hash)
                .update();
    }

    @Override
    public void revokeById(UUID id, UUID replacedByTokenId) {
        String sql = """
                UPDATE refresh_tokens
                SET revoked = TRUE, replaced_by_token = :replaced_by_token
                WHERE id = :id
                """;
        jdbcClient.sql(sql)
                .param("replaced_by_token", replacedByTokenId)
                .param("id", id)
                .update();
    }

    @Override
    public void revokeAllByUserId(UUID userId) {
        String sql = """
                UPDATE refresh_tokens
                SET revoked = TRUE
                WHERE user_id = :user_id
                """;
        jdbcClient.sql(sql)
                .param("user_id", userId)
                .update();
    }

    @Override
    public void revokeAllByFamilyId(UUID familyId) {
        String sql = """
                UPDATE refresh_tokens
                SET revoked = TRUE
                WHERE family_id = :family_id
                """;
        jdbcClient.sql(sql)
                .param("family_id", familyId)
                .update();
    }
}

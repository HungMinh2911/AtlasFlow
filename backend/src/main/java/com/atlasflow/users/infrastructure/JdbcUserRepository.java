package com.atlasflow.users.infrastructure;

import com.atlasflow.users.application.port.UserRepository;
import com.atlasflow.users.domain.User;
import com.atlasflow.users.domain.UserStatus;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Repository
@Primary
public class JdbcUserRepository implements UserRepository {

    private final JdbcClient jdbcClient;

    public JdbcUserRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    private User mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp timestamp = rs.getTimestamp("created_at");

        return new User(
                rs.getObject("id", UUID.class),
                rs.getString("email"),
                rs.getString("password_hash"),
                UserStatus.valueOf(rs.getString("status")),
                timestamp != null
                        ? timestamp.toInstant().atZone(ZoneOffset.UTC).toLocalDateTime()
                        : null
        );
    }

    @Override
    public Optional<User> findById(UUID id) {
        String sql = """
                SELECT id, email, password_hash, status, created_at
                FROM users
                WHERE id = :id
                """;

        return jdbcClient.sql(sql)
                .param("id", id)
                .query(this::mapRow)
                .optional();
    }

    @Override
    public Optional<User> findByEmail(String email) {
        String sql = """
                SELECT id, email, password_hash, status, created_at
                FROM users
                WHERE LOWER(email) = LOWER(:email)
                """;

        return jdbcClient.sql(sql)
                .param("email", email)
                .query(this::mapRow)
                .optional();
    }

    @Override
    public boolean existsByEmail(String email) {
        String sql = """
                SELECT EXISTS(
                    SELECT 1
                    FROM users
                    WHERE LOWER(email) = LOWER(:email)
                )
                """;

        return jdbcClient.sql(sql)
                .param("email", email)
                .query(Boolean.class)
                .single();
    }

    @Override
    public User save(User user) {
        String sql = """
                INSERT INTO users (id, email, password_hash, status, created_at)
                VALUES (:id, :email, :passwordHash, :status, :createdAt)
                ON CONFLICT (id) DO UPDATE
                SET email = EXCLUDED.email,
                    password_hash = EXCLUDED.password_hash,
                    status = EXCLUDED.status
                """;

        jdbcClient.sql(sql)
                .param("id", user.id())
                .param("email", user.email())
                .param("passwordHash", user.passwordHash())
                .param("status", user.status().name())
                .param("createdAt", user.createdAt() != null ? Timestamp.valueOf(user.createdAt()) : null)
                .update();

        return user;
    }
}

package com.atlasflow.outbox_events.infrastructure;

import com.atlasflow.outbox_events.application.port.OutboxEventsRepository;
import com.atlasflow.outbox_events.domain.OutboxEvents;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Repository
@Primary
public class JdbcOutboxEventsRepository implements OutboxEventsRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public JdbcOutboxEventsRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    private OutboxEvents mapRow(ResultSet rs, int rowNum) throws SQLException {

        String rawJson = rs.getString("payload");
        JsonNode payload = null;
        Timestamp occurredAt = rs.getTimestamp("occurred_at");
        Timestamp publishedAt = rs.getTimestamp("published_at");

        if (rawJson != null) {
            try {
                payload = objectMapper.readTree(rawJson);
            } catch (Exception e) {
                throw new SQLException("Failed to parse payload", e);
            }
        }

        return new OutboxEvents(
                rs.getObject("id", UUID.class),
                rs.getString("aggregate_type"),
                rs.getObject("aggregate_id", UUID.class),
                rs.getString("event_type"),
                payload,
                occurredAt != null
                        ? occurredAt.toInstant().atZone(ZoneOffset.UTC).toLocalDateTime()
                        : LocalDateTime.now(),
                rs.getString("correlation_id"),
                publishedAt != null
                        ? publishedAt.toInstant().atZone(ZoneOffset.UTC).toLocalDateTime()
                        : null,
                rs.getInt("delivery_attempts"),
                rs.getString("last_error")
        );
    }

    @Override
    public OutboxEvents save(OutboxEvents events) {

        String payloadJson = events.payload() != null ? events.payload().toString() : "{}";

        String sql = """
                INSERT INTO outbox_events (
                    id, aggregate_type, aggregate_id, event_type, payload,
                    occurred_at, correlation_id, published_at, delivery_attempts, last_error
                )
                VALUES (
                    :id, :aggregate_type, :aggregate_id, :event_type, :payload::jsonb,
                    :occurred_at, :correlation_id, :published_at, :delivery_attempts, :last_error
                )
                ON CONFLICT (id) DO UPDATE
                    SET aggregate_type = EXCLUDED.aggregate_type,
                        event_type = EXCLUDED.event_type,
                        payload = EXCLUDED.payload,
                        occurred_at = EXCLUDED.occurred_at,
                        correlation_id = EXCLUDED.correlation_id,
                        published_at = EXCLUDED.published_at,
                        delivery_attempts = EXCLUDED.delivery_attempts,
                        last_error = EXCLUDED.last_error
                """;

        jdbcClient.sql(sql)
                .param("id", events.id())
                .param("aggregate_type", events.aggregate_type())
                .param("aggregate_id", events.aggregate_id())
                .param("event_type", events.event_type())
                .param("payload", payloadJson)
                .param("occurred_at", Timestamp.valueOf(events.occurred_at()))
                .param("correlation_id", events.correlation_id())
                .param("published_at", events.published_at() != null ? Timestamp.valueOf(events.published_at()) : null)
                .param("delivery_attempts", events.delivery_attempts())
                .param("last_error", events.last_error())
                .update();

        return events;
    }

    @Override
    public List<OutboxEvents> findUnpublishedEvents(int limit) {
        String sql = """
                SELECT id, aggregate_type, aggregate_id, event_type, payload,
                       occurred_at, correlation_id, published_at, delivery_attempts, last_error
                FROM outbox_events
                WHERE published_at IS NULL
                ORDER BY id ASC
                LIMIT :limit
                """;

        return jdbcClient.sql(sql)
                .param("limit", limit)
                .query(this::mapRow)
                .list();
    }

    @Override
    public void markAsPublished(UUID id) {
        String sql = """
                UPDATE outbox_events
                SET published_at = :published_at
                WHERE id = :id
                """;

        jdbcClient.sql(sql)
                .param("id", id)
                .param("published_at", Timestamp.valueOf(LocalDateTime.now()))
                .update();
    }
}

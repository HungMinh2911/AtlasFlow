package com.atlasflow.outbox_events.infrastructure;

import com.atlasflow.outbox_events.application.port.OutboxEventsRepository;
import com.atlasflow.outbox_events.domain.OutboxEvents;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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
                publishedAt != null
                        ? publishedAt.toInstant().atZone(ZoneOffset.UTC).toLocalDateTime()
                        : null
        );
    }

    @Override
    public OutboxEvents save(OutboxEvents events) {

        String payloadJson = events.payload() != null ? events.payload().toString() : "{}";

        String sql = """
                INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, payload, published_at)
                VALUES (:id, :aggregate_type, :aggregate_id, :event_type, :payload::jsonb, :published_at)
                ON CONFLICT (id) DO UPDATE
                    SET aggregate_type = EXCLUDED.aggregate_type,
                        event_type = EXCLUDED.event_type,
                        payload = EXCLUDED.payload,
                        published_at = EXCLUDED.published_at
                """;

        jdbcClient.sql(sql)
                .param("id", events.id())
                .param("aggregate_type", events.aggregate_type())
                .param("aggregate_id", events.aggregate_id())
                .param("event_type", events.event_type())
                .param("payload", payloadJson)
                .param("published_at", events.published_at() != null ? Timestamp.valueOf(events.published_at()) : null)
                .update();

        return events;
    }

    @Override
    public List<OutboxEvents> findUnpublishedEvents(int limit) {
        String sql = """
                SELECT id, aggregate_type, aggregate_id, event_type, payload, published_at
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

package com.atlasflow.message.processed.infrastructure;

import com.atlasflow.message.processed.application.port.ProcessedMessageRepository;
import com.atlasflow.message.processed.domain.ProcessedMessage;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Repository
@Primary
public class JdbcProcessedMessageRepository implements ProcessedMessageRepository {

    private final JdbcClient jdbcClient;

    public JdbcProcessedMessageRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public boolean saveIfAbsent(ProcessedMessage message) {
        String sql = """
                INSERT INTO processed_messages (
                    message_id,
                    consumer_name,
                    aggregate_id,
                    processed_at
                )
                VALUES (
                    :messageId,
                    :consumerName,
                    :aggregateId,
                    :processedAt
                )
                ON CONFLICT (message_id) DO NOTHING
                """;

        int insertedRows = jdbcClient.sql(sql)
                .param("messageId", message.messageId())
                .param("consumerName", message.consumerName())
                .param("aggregateId", message.aggregateId())
                .param("processedAt", toTimestamp(message.processedAt()))
                .update();

        return insertedRows == 1;
    }

    @Override
    public boolean existsByMessageId(UUID messageId) {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM processed_messages
                    WHERE message_id = :messageId
                )
                """;

        return Boolean.TRUE.equals(jdbcClient.sql(sql)
                .param("messageId", messageId)
                .query(Boolean.class)
                .single());
    }

    private Timestamp toTimestamp(Instant instant) {
        return Timestamp.from(instant != null ? instant : Instant.now());
    }
}

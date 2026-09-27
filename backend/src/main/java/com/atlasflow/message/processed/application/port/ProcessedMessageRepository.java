package com.atlasflow.message.processed.application.port;

import com.atlasflow.message.processed.domain.ProcessedMessage;

import java.util.UUID;

public interface ProcessedMessageRepository {

    /**
     * Inserts a processed-message record only when the message has not been
     * recorded before.
     *
     * @return {@code true} when this call inserted the record;
     *         {@code false} when the message already existed
     */
    boolean saveIfAbsent(ProcessedMessage message);

    boolean existsByMessageId(UUID messageId);
}

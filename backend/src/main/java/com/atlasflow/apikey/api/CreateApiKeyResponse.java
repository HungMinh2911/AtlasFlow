package com.atlasflow.apikey.api;

import java.util.UUID;

/** The secret is returned only at creation time. It is never read back from storage. */
public record CreateApiKeyResponse(
        UUID id,
        UUID projectId,
        String prefix,
        String secret
) {}

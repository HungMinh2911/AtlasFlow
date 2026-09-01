-- Phase 4: API keys must be revocable without exposing the secret.
ALTER TABLE api_keys
    ADD COLUMN revoked_at TIMESTAMPTZ;

CREATE INDEX idx_api_keys_active_prefix
    ON api_keys (prefix)
    WHERE revoked_at IS NULL;

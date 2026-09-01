# Phase 4 — Security Evidence

## Scope

Phase 4 protects identity, tenant boundaries, authorization and auditability before worker/distributed execution is introduced.

## Implemented

- Passwords are stored through the configured `PasswordEncoder`; plaintext passwords are never logged.
- Access JWTs validate signature, expiration, issuer, audience and `token_type`.
- Refresh tokens are stored as hashes and rotated. Reuse of a revoked token revokes the token family/user sessions and creates an audit event.
- Failed login, lockout, token replay and unauthorized tenant access generate audit events with `request_id`.
- CORS is allowlisted and exposes only required response headers.
- Request IDs are bounded to 128 characters and control characters are rejected.
- API keys are generated once, stored as SHA-256 hashes, prefixed for lookup, and revocable through:
  - `POST /api/v1/projects/{projectId}/api-keys`
  - `DELETE /api/v1/projects/{projectId}/api-keys/{keyId}`
- API-key management requires `api_key:manage` and active membership in the project's organization.
- Known project IDOR access is blocked by `@RequireProjectAccess`.
- Authorities are loaded from active memberships and predefined roles. Unknown custom role names do not receive platform permissions implicitly.

## Threat model

| Threat | Control | Evidence |
|---|---|---|
| Password/database leak | Password encoder; no plaintext storage | `AuthService`, auth tests |
| Forged or wrong-service JWT | Signature + issuer + audience validation | `JwtTokenProviderTest`, security acceptance test |
| Refresh-token replay | Rotation, revoke-on-replay, audit | `SecurityAcceptanceIntegrationTest` |
| Cross-tenant membership access | Membership checker + tenant aspect + project aspect | `SecurityAcceptanceIntegrationTest` |
| API-key database leak | Prefix + SHA-256 hash; secret returned only at creation | `ApiKeyServiceTest` |
| API-key reuse after revoke | `revoked_at` and active-key predicate | `V5__api_key_revocation.sql` |
| Request/audit correlation loss | Request ID filter and propagation | auth/security tests |
| SQL injection through tenant context | Parameterized `set_config` | `TenantSessionContext` |

## Verification

Run from `backend`:

```powershell
.\mvnw.cmd test
```

The current suite passes with zero failures and zero errors.

## Important operational rule

PostgreSQL RLS policies are present in `V3__tenant_isolation.sql`. They must be used with a transaction-scoped tenant/user context. The application-level membership guards are the active protection for the covered HTTP paths; wiring `SET LOCAL app.current_tenant_id` for every database transaction is a separate hardening task before production deployment.

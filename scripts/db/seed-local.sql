-- AtlasFlow local/demo seed data
--
-- Mục đích: tạo dữ liệu mẫu cho môi trường local/test thủ công.
-- Không đặt file này trong db/migration vì dữ liệu demo không được tự động
-- chèn vào staging/production.
--
-- Demo login:
--   owner@atlasflow.local / password
--   operator@atlasflow.local / password
--
-- Các UUID cố định giúp script chạy lại nhiều lần mà không tạo bản ghi trùng.

BEGIN;

-- Bypass các policy RLS trong phạm vi transaction seed local.
SET LOCAL app.current_user_is_platform_admin = 'true';

-- Password hash BCrypt của password "password".
INSERT INTO users (id, email, password_hash, status, created_at)
VALUES
    ('22222222-2222-2222-2222-222222222222', 'owner@atlasflow.local',
     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ACTIVE', '2026-01-01T08:00:00Z'),
    ('33333333-3333-3333-3333-333333333333', 'operator@atlasflow.local',
     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ACTIVE', '2026-01-01T08:05:00Z'),
    ('44444444-4444-4444-4444-444444444444', 'auditor@atlasflow.local',
     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ACTIVE', '2026-01-01T08:10:00Z')
ON CONFLICT (id) DO UPDATE SET
    email = EXCLUDED.email,
    password_hash = EXCLUDED.password_hash,
    status = EXCLUDED.status;

INSERT INTO organizations (id, name, created_at)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'AtlasFlow Demo Organization', '2026-01-01T08:00:00Z'),
    ('12121212-1212-1212-1212-121212121212', 'AtlasFlow Second Organization', '2026-01-01T08:30:00Z')
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name;

INSERT INTO roles (id, organization_id, name)
VALUES
    ('55555555-5555-5555-5555-555555555555', '11111111-1111-1111-1111-111111111111', 'ORGANIZATION_OWNER'),
    ('66666666-6666-6666-6666-666666666666', '11111111-1111-1111-1111-111111111111', 'PROJECT_OPERATOR'),
    ('77777777-7777-7777-7777-777777777777', '11111111-1111-1111-1111-111111111111', 'READ_ONLY_AUDITOR')
ON CONFLICT (id) DO UPDATE SET
    organization_id = EXCLUDED.organization_id,
    name = EXCLUDED.name;

INSERT INTO memberships (user_id, organization_id, role_id, status)
VALUES
    ('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', '55555555-5555-5555-5555-555555555555', 'ACTIVE'),
    ('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', '66666666-6666-6666-6666-666666666666', 'ACTIVE'),
    ('44444444-4444-4444-4444-444444444444', '11111111-1111-1111-1111-111111111111', '77777777-7777-7777-7777-777777777777', 'ACTIVE')
ON CONFLICT (user_id, organization_id) DO UPDATE SET
    role_id = EXCLUDED.role_id,
    status = EXCLUDED.status;

-- Lookup permissions dùng đúng giá trị trong com.atlasflow.security.rbac.Permission.
INSERT INTO permissions (id, name, description)
VALUES
    ('a1000000-0000-0000-0000-000000000001', 'org:read', 'Read organization'),
    ('a1000000-0000-0000-0000-000000000002', 'org:update', 'Update organization'),
    ('a1000000-0000-0000-0000-000000000003', 'member:invite', 'Invite organization member'),
    ('a1000000-0000-0000-0000-000000000004', 'member:manage', 'Manage organization members'),
    ('a1000000-0000-0000-0000-000000000005', 'api_key:manage', 'Manage API keys'),
    ('a1000000-0000-0000-0000-000000000006', 'project:create', 'Create project'),
    ('a1000000-0000-0000-0000-000000000007', 'project:read', 'Read project'),
    ('a1000000-0000-0000-0000-000000000008', 'project:update', 'Update project'),
    ('a1000000-0000-0000-0000-000000000009', 'project:delete', 'Delete project'),
    ('a1000000-0000-0000-0000-000000000010', 'workflow:create', 'Create workflow'),
    ('a1000000-0000-0000-0000-000000000011', 'workflow:read', 'Read workflow'),
    ('a1000000-0000-0000-0000-000000000012', 'workflow:update', 'Update workflow'),
    ('a1000000-0000-0000-0000-000000000013', 'workflow:publish', 'Publish workflow'),
    ('a1000000-0000-0000-0000-000000000014', 'job:submit', 'Submit job'),
    ('a1000000-0000-0000-0000-000000000015', 'job:read', 'Read job'),
    ('a1000000-0000-0000-0000-000000000016', 'job:cancel', 'Cancel job'),
    ('a1000000-0000-0000-0000-000000000017', 'job:retry', 'Retry job'),
    ('a1000000-0000-0000-0000-000000000018', 'execution:update', 'Update execution'),
    ('a1000000-0000-0000-0000-000000000019', 'file:upload', 'Upload file'),
    ('a1000000-0000-0000-0000-000000000020', 'file:read', 'Read file'),
    ('a1000000-0000-0000-0000-000000000021', 'audit:read', 'Read audit log')
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description;

-- Owner có toàn bộ quyền; operator/auditor chỉ có quyền theo role.
INSERT INTO role_permissions (role_id, permission_id)
SELECT '55555555-5555-5555-5555-555555555555', id FROM permissions
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT '66666666-6666-6666-6666-666666666666', id
FROM permissions
WHERE name IN (
    'org:read', 'project:read', 'workflow:create', 'workflow:read',
    'workflow:update', 'workflow:publish', 'job:submit', 'job:read',
    'job:cancel', 'job:retry', 'file:upload', 'file:read'
)
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT '77777777-7777-7777-7777-777777777777', id
FROM permissions
WHERE name IN ('org:read', 'project:read', 'workflow:read', 'job:read', 'file:read', 'audit:read')
ON CONFLICT DO NOTHING;

INSERT INTO projects (id, organization_id, name, status, created_at)
VALUES
    ('88888888-8888-8888-8888-888888888888', '11111111-1111-1111-1111-111111111111', 'Demo Document Processing', 'ACTIVE', '2026-01-01T09:00:00Z'),
    ('34343434-3434-3434-3434-343434343434', '12121212-1212-1212-1212-121212121212', 'Second Tenant Project', 'ACTIVE', '2026-01-01T09:30:00Z')
ON CONFLICT (id) DO UPDATE SET
    organization_id = EXCLUDED.organization_id,
    name = EXCLUDED.name,
    status = EXCLUDED.status;

INSERT INTO api_keys (id, project_id, prefix, secret_hash, last_used_at, revoked_at)
VALUES
    ('f0000000-0000-0000-0000-000000000001', '88888888-8888-8888-8888-888888888888', 'af_demo_', 'local-demo-secret-hash', NULL, NULL)
ON CONFLICT (id) DO UPDATE SET
    project_id = EXCLUDED.project_id,
    prefix = EXCLUDED.prefix,
    secret_hash = EXCLUDED.secret_hash,
    revoked_at = EXCLUDED.revoked_at;

INSERT INTO workflows (id, project_id, version, status)
VALUES
    ('99999999-9999-9999-9999-999999999999', '88888888-8888-8888-8888-888888888888', 1, 'PUBLISHED'),
    ('98989898-9898-9898-9898-989898989898', '88888888-8888-8888-8888-888888888888', 2, 'DRAFT')
ON CONFLICT (id) DO UPDATE SET
    project_id = EXCLUDED.project_id,
    version = EXCLUDED.version,
    status = EXCLUDED.status;

INSERT INTO workflow_steps (id, workflow_id, position, task_type, config_json)
VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '99999999-9999-9999-9999-999999999999', 0, 'VALIDATE_FILE', '{"max_size_bytes": 10485760, "allowed_extensions": ["pdf", "txt"]}'),
    ('abababab-abab-abab-abab-abababababab', '99999999-9999-9999-9999-999999999999', 1, 'HTTP_REQUEST', '{"method": "POST", "url": "https://example.test/process"}'),
    ('acacacac-acac-acac-acac-acacacacacac', '98989898-9898-9898-9898-989898989898', 0, 'VALIDATE_FILE', '{"max_size_bytes": 5242880}')
ON CONFLICT (id) DO UPDATE SET
    workflow_id = EXCLUDED.workflow_id,
    position = EXCLUDED.position,
    task_type = EXCLUDED.task_type,
    config_json = EXCLUDED.config_json;

INSERT INTO files (id, project_id, file_name, object_key, checksum, size_bytes, status, created_at)
VALUES
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '88888888-8888-8888-8888-888888888888', 'sample-document.pdf', 'demo/sample-document.pdf', 'demo-checksum-001', 4096, 'AVAILABLE', '2026-01-01T09:10:00Z')
ON CONFLICT (id) DO UPDATE SET
    project_id = EXCLUDED.project_id,
    file_name = EXCLUDED.file_name,
    object_key = EXCLUDED.object_key,
    checksum = EXCLUDED.checksum,
    size_bytes = EXCLUDED.size_bytes,
    status = EXCLUDED.status;

INSERT INTO jobs (id, project_id, workflow_id, file_id, status, idempotency_key, request_fingerprint, created_at)
VALUES
    ('cccccccc-cccc-cccc-cccc-cccccccccccc', '88888888-8888-8888-8888-888888888888', '99999999-9999-9999-9999-999999999999', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'SUCCEEDED', 'seed-job-001', 'seed-fingerprint-001', '2026-01-01T09:15:00Z')
ON CONFLICT (id) DO UPDATE SET
    status = EXCLUDED.status,
    idempotency_key = EXCLUDED.idempotency_key,
    request_fingerprint = EXCLUDED.request_fingerprint;

INSERT INTO job_executions (id, job_id, attempt, status, worker_id, started_at, finished_at)
VALUES
    ('dddddddd-dddd-dddd-dddd-dddddddddddd', 'cccccccc-cccc-cccc-cccc-cccccccccccc', 1, 'SUCCEEDED', 'local-worker-1', '2026-01-01T09:15:01Z', '2026-01-01T09:15:04Z')
ON CONFLICT (id) DO UPDATE SET
    status = EXCLUDED.status,
    worker_id = EXCLUDED.worker_id,
    started_at = EXCLUDED.started_at,
    finished_at = EXCLUDED.finished_at;

INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, payload, published_at)
VALUES
    ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 'JOB', 'cccccccc-cccc-cccc-cccc-cccccccccccc', 'JOB_SUCCEEDED', '{"job_id": "cccccccc-cccc-cccc-cccc-cccccccccccc", "source": "local-seed"}', '2026-01-01T09:15:05Z')
ON CONFLICT (id) DO UPDATE SET
    event_type = EXCLUDED.event_type,
    payload = EXCLUDED.payload,
    published_at = EXCLUDED.published_at;

INSERT INTO audit_logs (id, organization_id, actor_id, action, resource_type, resource_id, request_id, created_at)
VALUES
    ('12121212-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', 'SEED_CREATED', 'PROJECT', '88888888-8888-8888-8888-888888888888', 'seed-request-001', '2026-01-01T09:20:00Z')
ON CONFLICT (id) DO UPDATE SET
    action = EXCLUDED.action,
    resource_type = EXCLUDED.resource_type,
    resource_id = EXCLUDED.resource_id,
    request_id = EXCLUDED.request_id;

COMMIT;

-- Kiểm tra nhanh sau khi seed.
SELECT 'users' AS table_name, COUNT(*) AS seeded_rows FROM users WHERE email LIKE '%@atlasflow.local'
UNION ALL
SELECT 'organizations', COUNT(*) FROM organizations WHERE id IN (
    '11111111-1111-1111-1111-111111111111',
    '12121212-1212-1212-1212-121212121212'
)
UNION ALL
SELECT 'projects', COUNT(*) FROM projects WHERE id IN (
    '88888888-8888-8888-8888-888888888888',
    '34343434-3434-3434-3434-343434343434'
)
UNION ALL
SELECT 'workflows', COUNT(*) FROM workflows WHERE project_id = '88888888-8888-8888-8888-888888888888'
UNION ALL
SELECT 'jobs', COUNT(*) FROM jobs WHERE id = 'cccccccc-cccc-cccc-cccc-cccccccccccc';

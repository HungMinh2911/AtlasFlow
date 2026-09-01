-- ============================================================
-- ATLASFLOW - LOCAL DEMO DATA
-- ============================================================
-- Chỉ được nạp khi chạy profile local.
-- Password của các tài khoản demo: password
-- Không dùng file này cho staging/production.
-- Refresh token cố ý không seed; token phải sinh qua API login.

-- V3 bật FORCE ROW LEVEL SECURITY, vì vậy migration seed local
-- cần chạy dưới context platform admin.
SET LOCAL app.current_user_is_platform_admin = 'true';

-- ============================================================
-- 1. USERS
-- ============================================================
INSERT INTO users (id, email, password_hash, status, created_at) VALUES
('22222222-2222-2222-2222-222222222222', 'owner@atlasflow.local',
 '$2a$12$PnwxEmvrIc7g6lbGh4V4Oef1ML70QFGU1Nx5NRsTh1s3bItnX18.a', 'ACTIVE', CURRENT_TIMESTAMP),
('33333333-3333-3333-3333-333333333333', 'operator@atlasflow.local',
 '$2a$12$PnwxEmvrIc7g6lbGh4V4Oef1ML70QFGU1Nx5NRsTh1s3bItnX18.a', 'ACTIVE', CURRENT_TIMESTAMP),
('44444444-4444-4444-4444-444444444444', 'auditor@atlasflow.local',
 '$2a$12$PnwxEmvrIc7g6lbGh4V4Oef1ML70QFGU1Nx5NRsTh1s3bItnX18.a', 'ACTIVE', CURRENT_TIMESTAMP),
('55555555-5555-5555-5555-555555555555', 'unassigned@atlasflow.local',
 '$2a$12$PnwxEmvrIc7g6lbGh4V4Oef1ML70QFGU1Nx5NRsTh1s3bItnX18.a', 'ACTIVE', CURRENT_TIMESTAMP);

-- ============================================================
-- 2. ORGANIZATIONS
-- ============================================================
INSERT INTO organizations (id, name, created_at) VALUES
('11111111-1111-1111-1111-111111111111', 'AtlasFlow Demo Organization', CURRENT_TIMESTAMP),
('12121212-1212-1212-1212-121212121212', 'AtlasFlow Second Tenant', CURRENT_TIMESTAMP);

-- ============================================================
-- 3. ROLES
-- ============================================================
INSERT INTO roles (id, organization_id, name) VALUES
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '11111111-1111-1111-1111-111111111111', 'ORGANIZATION_OWNER'),
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '11111111-1111-1111-1111-111111111111', 'PROJECT_OPERATOR'),
('cccccccc-cccc-cccc-cccc-cccccccccccc', '11111111-1111-1111-1111-111111111111', 'READ_ONLY_AUDITOR');

-- ============================================================
-- 4. PERMISSIONS AND ROLE PERMISSIONS
-- ============================================================
INSERT INTO permissions (id, name, description) VALUES
('d1111111-1111-1111-1111-111111111111', 'PROJECT_READ', 'Read project data'),
('d2222222-2222-2222-2222-222222222222', 'PROJECT_WRITE', 'Create or update project data'),
('d3333333-3333-3333-3333-333333333333', 'JOB_SUBMIT', 'Submit jobs for execution'),
('d4444444-4444-4444-4444-444444444444', 'JOB_READ', 'Read job data');

INSERT INTO role_permissions (role_id, permission_id) VALUES
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'd1111111-1111-1111-1111-111111111111'),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'd2222222-2222-2222-2222-222222222222'),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'd3333333-3333-3333-3333-333333333333'),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'd4444444-4444-4444-4444-444444444444'),
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'd1111111-1111-1111-1111-111111111111'),
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'd3333333-3333-3333-3333-333333333333'),
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'd4444444-4444-4444-4444-444444444444'),
('cccccccc-cccc-cccc-cccc-cccccccccccc', 'd1111111-1111-1111-1111-111111111111'),
('cccccccc-cccc-cccc-cccc-cccccccccccc', 'd4444444-4444-4444-4444-444444444444');

-- ============================================================
-- 5. MEMBERSHIPS
-- ============================================================
INSERT INTO memberships (user_id, organization_id, role_id, status) VALUES
('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'ACTIVE'),
('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'ACTIVE'),
('44444444-4444-4444-4444-444444444444', '11111111-1111-1111-1111-111111111111', 'cccccccc-cccc-cccc-cccc-cccccccccccc', 'ACTIVE');

-- ============================================================
-- 6. PROJECTS
-- ============================================================
INSERT INTO projects (id, organization_id, name, status, created_at) VALUES
('88888888-8888-8888-8888-888888888888', '11111111-1111-1111-1111-111111111111', 'Demo Document Processing', 'ACTIVE', CURRENT_TIMESTAMP),
('34343434-3434-3434-3434-343434343434', '12121212-1212-1212-1212-121212121212', 'Second Tenant Project', 'ACTIVE', CURRENT_TIMESTAMP);

-- ============================================================
-- 7. API KEY METADATA
-- ============================================================
INSERT INTO api_keys (id, project_id, prefix, secret_hash, last_used_at) VALUES
('abababab-abab-abab-abab-abababababab', '88888888-8888-8888-8888-888888888888',
 'af_demo_', '$2a$12$PnwxEmvrIc7g6lbGh4V4Oef1ML70QFGU1Nx5NRsTh1s3bItnX18.a', NULL);

-- ============================================================
-- 8. WORKFLOWS AND STEPS
-- ============================================================
INSERT INTO workflows (id, project_id, version, status) VALUES
('99999999-9999-9999-9999-999999999999', '88888888-8888-8888-8888-888888888888', 1, 'PUBLISHED'),
('98989898-9898-9898-9898-989898989898', '88888888-8888-8888-8888-888888888888', 2, 'DRAFT');

INSERT INTO workflow_steps (id, workflow_id, position, task_type, config_json) VALUES
('eeeeeee1-eeee-eeee-eeee-eeeeeeeeeee1', '99999999-9999-9999-9999-999999999999', 0, 'VALIDATE_FILE', '{"allowed_extensions":["pdf","txt"]}'),
('eeeeeee2-eeee-eeee-eeee-eeeeeeeeeee2', '99999999-9999-9999-9999-999999999999', 1, 'HTTP_REQUEST', '{"url":"http://localhost:8081/demo","method":"POST"}'),
('eeeeeee3-eeee-eeee-eeee-eeeeeeeeeee3', '98989898-9898-9898-9898-989898989898', 0, 'OCR_FILE', '{"language":"eng"}');

-- ============================================================
-- 9. FILES
-- ============================================================
INSERT INTO files (id, project_id, file_name, object_key, checksum, size_bytes, status, created_at) VALUES
('77777777-7777-7777-7777-777777777777', '88888888-8888-8888-8888-888888888888',
 'demo-document.pdf', 'local/demo-document.pdf', 'demo-checksum-001', 1024, 'AVAILABLE', CURRENT_TIMESTAMP);

-- ============================================================
-- 10. JOBS
-- ============================================================
INSERT INTO jobs (id, project_id, workflow_id, file_id, status, idempotency_key, request_fingerprint, created_at) VALUES
('66666661-6666-6666-6666-666666666661', '88888888-8888-8888-8888-888888888888',
 '99999999-9999-9999-9999-999999999999', '77777777-7777-7777-7777-777777777777',
 'SUBMITTED', 'demo-submit-001', repeat('1', 64), CURRENT_TIMESTAMP),
('66666662-6666-6666-6666-666666666662', '88888888-8888-8888-8888-888888888888',
 '99999999-9999-9999-9999-999999999999', '77777777-7777-7777-7777-777777777777',
 'SUCCEEDED', 'demo-success-001', repeat('2', 64), CURRENT_TIMESTAMP - INTERVAL '5 minutes');

-- ============================================================
-- 11. JOB EXECUTION
-- ============================================================
INSERT INTO job_executions (id, job_id, attempt, status, worker_id, started_at, finished_at) VALUES
('77777771-7777-7777-7777-777777777771', '66666662-6666-6666-6666-666666666662',
 1, 'SUCCEEDED', 'local-worker-01', CURRENT_TIMESTAMP - INTERVAL '4 minutes', CURRENT_TIMESTAMP - INTERVAL '3 minutes');

-- ============================================================
-- 12. OUTBOX EVENT
-- ============================================================
INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, payload, published_at) VALUES
('88888881-8888-8888-8888-888888888881', 'JOB', '66666661-6666-6666-6666-666666666661',
 'JOB_SUBMITTED', '{"job_id":"66666661-6666-6666-6666-666666666661","project_id":"88888888-8888-8888-8888-888888888888","workflow_id":"99999999-9999-9999-9999-999999999999"}', NULL);

-- ============================================================
-- 13. AUDIT LOGS
-- ============================================================
INSERT INTO audit_logs (id, organization_id, actor_id, action, resource_type, resource_id, request_id, created_at) VALUES
('99999991-9999-9999-9999-999999999991', '11111111-1111-1111-1111-111111111111',
 '22222222-2222-2222-2222-222222222222', 'SEED_LOCAL_DATA', 'ORGANIZATION', '11111111-1111-1111-1111-111111111111', 'local-seed-v6', CURRENT_TIMESTAMP);

-- refresh_tokens không seed. API /api/auth/login sẽ tạo token thật.

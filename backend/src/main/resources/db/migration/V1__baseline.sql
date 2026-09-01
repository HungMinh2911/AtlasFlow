-- ============================================================
-- ATLASFLOW - PHASE 3 DATABASE BASELINE
-- ============================================================
-- File này được Flyway chạy một lần để tạo database ban đầu.
--
-- Quy ước chung:
--   * ID do Java tạo bằng UUID, sau đó mới INSERT vào PostgreSQL.
--   * UUID giúp ID khó đoán và phù hợp khi chạy nhiều instance.
--   * TIMESTAMPTZ lưu thời điểm kèm timezone; ứng dụng chuẩn hóa về UTC.
--   * VARCHAR status + CHECK giúp database không nhận trạng thái sai.
--   * Không xóa/sửa migration đã chạy; thay đổi mới tạo V2, V3, ...

-- ============================================================
-- 1. USERS
-- Người dùng đăng nhập vào AtlasFlow.
-- Một user có thể tham gia nhiều organization thông qua memberships.
-- ============================================================

CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- User chỉ được sử dụng khi status là ACTIVE.
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED'))
);

-- ============================================================
-- 2. ORGANIZATIONS
-- Tenant/đơn vị sở hữu project trong AtlasFlow.
-- Một organization có nhiều project, role và membership.
-- ============================================================
CREATE TABLE organizations (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_organizations_name_not_blank CHECK (length(trim(name)) > 0)
);

-- ============================================================
-- 3. ROLES
-- Vai trò của user trong một organization, ví dụ OWNER hoặc OPERATOR.
-- Role bị giới hạn trong organization thông qua organization_id.
-- ============================================================
CREATE TABLE roles (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT fk_roles_organization
        FOREIGN KEY (organization_id) REFERENCES organizations(id),
    -- Khóa phụ này phục vụ composite FK của memberships.
    CONSTRAINT uq_roles_id_organization UNIQUE (id, organization_id),
    -- Trong cùng organization không được có hai role cùng tên.
    CONSTRAINT uq_roles_organization_name UNIQUE (organization_id, name),
    CONSTRAINT ck_roles_name_not_blank CHECK (length(trim(name)) > 0)
);

-- ============================================================
-- 4. MEMBERSHIPS
-- Bảng trung gian biểu diễn quan hệ nhiều-nhiều giữa users và organizations.
-- Một user chỉ có một membership trong cùng một organization.
-- Vì vậy bảng dùng khóa chính ghép (user_id, organization_id).
-- ============================================================
CREATE TABLE memberships (
    user_id UUID NOT NULL,
    organization_id UUID NOT NULL,
    role_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    CONSTRAINT pk_memberships PRIMARY KEY (user_id, organization_id),
    CONSTRAINT fk_memberships_user
        FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_memberships_organization
        FOREIGN KEY (organization_id) REFERENCES organizations(id),
    -- Composite FK bảo đảm role cũng thuộc đúng organization.
    CONSTRAINT fk_memberships_role_same_organization
        FOREIGN KEY (role_id, organization_id)
        REFERENCES roles(id, organization_id),
    CONSTRAINT ck_memberships_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED'))
);

-- ============================================================
-- 5. PROJECTS
-- Project là không gian chứa workflow, file, API key và job.
-- Project thuộc duy nhất một organization.
-- ============================================================
CREATE TABLE projects (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_projects_organization
        FOREIGN KEY (organization_id) REFERENCES organizations(id),
    -- Hai organization khác nhau có thể có project trùng tên.
    -- Nhưng một organization không được trùng tên project.
    CONSTRAINT uq_projects_organization_name UNIQUE (organization_id, name),
    CONSTRAINT ck_projects_name_not_blank CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_projects_status CHECK (status IN ('ACTIVE', 'ARCHIVED', 'SUSPENDED'))
);

-- ============================================================
-- 6. API_KEYS
-- API key dùng để client gọi AtlasFlow.
-- Chỉ lưu prefix và hash; tuyệt đối không lưu secret dạng plaintext.
-- ============================================================
CREATE TABLE api_keys (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    prefix VARCHAR(32) NOT NULL,
    secret_hash VARCHAR(255) NOT NULL,
    last_used_at TIMESTAMPTZ,
    CONSTRAINT fk_api_keys_project
        FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT uq_api_keys_prefix UNIQUE (prefix)
);

-- ============================================================
-- 7. WORKFLOWS
-- Một workflow là phiên bản luồng xử lý của project.
-- Workflow mới bắt đầu ở DRAFT, publish xong mới được submit job.
-- Cặp (project_id, version) phải duy nhất.
-- ============================================================
CREATE TABLE workflows (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    version INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    CONSTRAINT fk_workflows_project
        FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT uq_workflows_project_version UNIQUE (project_id, version),
    CONSTRAINT ck_workflows_version_positive CHECK (version > 0),
    CONSTRAINT ck_workflows_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

-- ============================================================
-- 8. WORKFLOW_STEPS
-- Các bước cấu hình bên trong workflow.
-- Mỗi step thuộc một workflow và được chạy theo position tăng dần.
-- config_json lưu cấu hình riêng của từng task type.
-- Ví dụ: VALIDATE_FILE, OCR_FILE, HTTP_REQUEST.
-- ============================================================
CREATE TABLE workflow_steps (
    id UUID PRIMARY KEY,
    workflow_id UUID NOT NULL,
    position INTEGER NOT NULL,
    task_type VARCHAR(64) NOT NULL,
    config_json JSONB NOT NULL,
    CONSTRAINT fk_workflow_steps_workflow
        FOREIGN KEY (workflow_id) REFERENCES workflows(id),
    CONSTRAINT uq_workflow_steps_workflow_position UNIQUE (workflow_id, position),
    CONSTRAINT ck_workflow_steps_position_non_negative CHECK (position >= 0),
    -- Chỉ nhận JSON object, không nhận array hoặc scalar.
    CONSTRAINT ck_workflow_steps_config_object CHECK (jsonb_typeof(config_json) = 'object')
);

-- ============================================================
-- 9. FILES
-- Metadata của file thuộc project.
-- Nội dung thật của file nằm ở local storage/S3; object_key là đường dẫn tới file.
-- ============================================================
CREATE TABLE files (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    file_name VARCHAR(512) NOT NULL,
    object_key VARCHAR(1024) NOT NULL,
    checksum VARCHAR(128),
    size_bytes BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_files_project
        FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT uq_files_object_key UNIQUE (object_key),
    CONSTRAINT ck_files_size_non_negative CHECK (size_bytes >= 0),
    CONSTRAINT ck_files_status CHECK (status IN ('UPLOADING', 'AVAILABLE', 'FAILED'))
);

-- ============================================================
-- 10. JOBS
-- Một yêu cầu chạy một workflow trên một file.
-- Luồng trạng thái:
--   SUBMITTED -> QUEUED -> RUNNING -> SUCCEEDED/FAILED/CANCELLED
--
-- idempotency_key do client gửi lên qua HTTP header.
-- request_fingerprint là hash của payload request.
-- Hai trường này giúp retry request không tạo job trùng.
-- ============================================================
CREATE TABLE jobs (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    workflow_id UUID NOT NULL,
    file_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_jobs_project
        FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT fk_jobs_workflow
        FOREIGN KEY (workflow_id) REFERENCES workflows(id),
    CONSTRAINT fk_jobs_file
        FOREIGN KEY (file_id) REFERENCES files(id),
    -- Cùng một key chỉ có ý nghĩa trong phạm vi một project.
    CONSTRAINT uq_jobs_project_idempotency_key
        UNIQUE (project_id, idempotency_key),
    CONSTRAINT ck_jobs_status
        CHECK (status IN ('SUBMITTED', 'QUEUED', 'RUNNING', 'SUCCEEDED',
                          'FAILED', 'CANCEL_REQUESTED', 'CANCELLED'))
);

-- ============================================================
-- 11. JOB_EXECUTIONS
-- Một job có thể có nhiều lần execution khi retry.
-- attempt bắt đầu từ 1 và duy nhất trong cùng một job.
-- worker_id cho biết worker nào đã xử lý execution.
-- ============================================================
CREATE TABLE job_executions (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL,
    attempt INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    worker_id VARCHAR(255),
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    CONSTRAINT fk_job_executions_job
        FOREIGN KEY (job_id) REFERENCES jobs(id),
    CONSTRAINT uq_job_executions_job_attempt UNIQUE (job_id, attempt),
    CONSTRAINT ck_job_executions_attempt_positive CHECK (attempt > 0),
    CONSTRAINT ck_job_executions_status
        CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_job_executions_finished_after_started
        CHECK (finished_at IS NULL OR finished_at >= started_at)
);

-- ============================================================
-- 12. OUTBOX_EVENTS
-- Event chờ được publish sang message broker trong các phase sau.
-- Phase 3 chỉ lưu event trong database; chưa cần RabbitMQ.
--
-- aggregate_id không có FK trực tiếp vì aggregate có thể là job,
-- workflow hoặc loại resource khác. aggregate_type cho biết loại đó.
-- published_at NULL nghĩa là event chưa được publish.
-- ============================================================
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL,
    published_at TIMESTAMPTZ,
    CONSTRAINT ck_outbox_payload_object CHECK (jsonb_typeof(payload) = 'object')
);

-- ============================================================
-- 13. AUDIT_LOGS
-- Lịch sử hành động phục vụ audit và truy vết.
-- actor_id nullable vì một số hành động có thể do system/worker thực hiện.
-- request_id liên kết hành động với X-Request-Id của HTTP request.
-- ============================================================
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    actor_id UUID,
    action VARCHAR(128) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id UUID,
    request_id VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_logs_organization
        FOREIGN KEY (organization_id) REFERENCES organizations(id),
    CONSTRAINT fk_audit_logs_actor
        FOREIGN KEY (actor_id) REFERENCES users(id)
);

-- ============================================================
-- 14. INDEXES
-- Index giúp các query thường dùng không phải quét toàn bộ bảng.
-- Về sau dùng EXPLAIN ANALYZE để đo hiệu quả từng index.
-- ============================================================
CREATE INDEX idx_projects_organization_id
    ON projects (organization_id);
CREATE UNIQUE INDEX uq_users_email_lower
    ON users (LOWER(email));
CREATE INDEX idx_api_keys_project_id
    ON api_keys (project_id);
CREATE INDEX idx_workflows_project_id
    ON workflows (project_id);
CREATE INDEX idx_workflow_steps_workflow_position
    ON workflow_steps (workflow_id, position);
CREATE INDEX idx_files_project_created_at
    ON files (project_id, created_at DESC);
CREATE INDEX idx_jobs_project_created_at
    ON jobs (project_id, created_at DESC);
CREATE INDEX idx_jobs_workflow_created_at
    ON jobs (workflow_id, created_at DESC);
CREATE INDEX idx_job_executions_job_id
    ON job_executions (job_id);
CREATE INDEX idx_outbox_unpublished
    ON outbox_events (published_at, id)
    WHERE published_at IS NULL;
CREATE INDEX idx_audit_logs_organization_created_at
    ON audit_logs (organization_id, created_at DESC);

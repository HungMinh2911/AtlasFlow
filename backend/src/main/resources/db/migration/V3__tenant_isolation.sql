-- ============================================================
-- V3: ROW LEVEL SECURITY — Tenant Isolation
-- ============================================================
-- Bật RLS ở tầng database cho TẤT CẢ bảng đa-tenant.
-- Đây là lớp phòng thủ tận gốc: bất kể câu SQL được gửi từ đâu,
-- database tự động lọc theo tenant, kể cả khi dev quên WHERE.
--
-- CÁCH DÙNG (Java side — phải SET LOCAL trong cùng transaction):
--   SET LOCAL app.current_tenant_id = '<org-uuid>';
--   SET LOCAL app.current_user_id   = '<user-uuid>';
--   SET LOCAL app.current_user_is_platform_admin = 'false';
--
-- NGUYÊN TẮC:
--   * current_setting('...', true) → trả NULL nếu chưa SET → mọi row bị ẩn (an toàn).
--   * FORCE ROW LEVEL SECURITY bắt buộc vì app role = table owner.
--   * Bảng con (workflows, files, jobs...) không có organization_id trực tiếp
--     → dùng subquery qua project → organizations.
-- ============================================================


-- ============================================================
-- 1) PROJECTS — có organization_id trực tiếp
-- ============================================================
ALTER TABLE projects ENABLE ROW LEVEL SECURITY;
ALTER TABLE projects FORCE ROW LEVEL SECURITY;

CREATE POLICY projects_tenant_select ON projects FOR SELECT
    USING (
        organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    );

CREATE POLICY projects_tenant_insert ON projects FOR INSERT
    WITH CHECK (
        organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    );

CREATE POLICY projects_tenant_update ON projects FOR UPDATE
    USING (
        organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    );

CREATE POLICY projects_tenant_delete ON projects FOR DELETE
    USING (
        current_setting('app.current_user_is_platform_admin', true) = 'true'
    );


-- ============================================================
-- 2) ROLES — có organization_id trực tiếp
-- ============================================================
ALTER TABLE roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE roles FORCE ROW LEVEL SECURITY;

CREATE POLICY roles_tenant_select ON roles FOR SELECT
    USING (
        organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    );

CREATE POLICY roles_tenant_insert ON roles FOR INSERT
    WITH CHECK (
        organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    );

CREATE POLICY roles_tenant_update ON roles FOR UPDATE
    USING (
        organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    );

CREATE POLICY roles_tenant_delete ON roles FOR DELETE
    USING (
        current_setting('app.current_user_is_platform_admin', true) = 'true'
    );


-- ============================================================
-- 3) MEMBERSHIPS — có organization_id trực tiếp
-- ============================================================
ALTER TABLE memberships ENABLE ROW LEVEL SECURITY;
ALTER TABLE memberships FORCE ROW LEVEL SECURITY;

CREATE POLICY memberships_tenant_select ON memberships FOR SELECT
    USING (
        organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    );

CREATE POLICY memberships_tenant_insert ON memberships FOR INSERT
    WITH CHECK (
        organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    );

CREATE POLICY memberships_tenant_update ON memberships FOR UPDATE
    USING (
        organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    );

CREATE POLICY memberships_tenant_delete ON memberships FOR DELETE
    USING (
        current_setting('app.current_user_is_platform_admin', true) = 'true'
    );

CREATE INDEX IF NOT EXISTS idx_memberships_organization_id ON memberships (organization_id);


-- ============================================================
-- 4) API_KEYS — liên kết qua project_id → projects.organization_id
-- ============================================================
ALTER TABLE api_keys ENABLE ROW LEVEL SECURITY;
ALTER TABLE api_keys FORCE ROW LEVEL SECURITY;

CREATE POLICY api_keys_tenant_select ON api_keys FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM projects p
            WHERE p.id = api_keys.project_id
              AND p.organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        )
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    );

CREATE POLICY api_keys_tenant_insert ON api_keys FOR INSERT
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM projects p
            WHERE p.id = api_keys.project_id
              AND p.organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        )
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    );

CREATE POLICY api_keys_tenant_update ON api_keys FOR UPDATE
    USING (
        EXISTS (
            SELECT 1 FROM projects p
            WHERE p.id = api_keys.project_id
              AND p.organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        )
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM projects p
            WHERE p.id = api_keys.project_id
              AND p.organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        )
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    );

CREATE POLICY api_keys_tenant_delete ON api_keys FOR DELETE
    USING (
        current_setting('app.current_user_is_platform_admin', true) = 'true'
    );


-- ============================================================
-- 5) WORKFLOWS — liên kết qua project_id → projects.organization_id
-- ============================================================
ALTER TABLE workflows ENABLE ROW LEVEL SECURITY;
ALTER TABLE workflows FORCE ROW LEVEL SECURITY;

CREATE POLICY workflows_tenant_select ON workflows FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM projects p
            WHERE p.id = workflows.project_id
              AND p.organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        )
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    );

CREATE POLICY workflows_tenant_insert ON workflows FOR INSERT
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM projects p
            WHERE p.id = workflows.project_id
              AND p.organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        )
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    );

CREATE POLICY workflows_tenant_update ON workflows FOR UPDATE
    USING (
        EXISTS (
            SELECT 1 FROM projects p
            WHERE p.id = workflows.project_id
              AND p.organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        )
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM projects p
            WHERE p.id = workflows.project_id
              AND p.organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        )
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    );

CREATE POLICY workflows_tenant_delete ON workflows FOR DELETE
    USING (
        current_setting('app.current_user_is_platform_admin', true) = 'true'
    );


-- ============================================================
-- 6) WORKFLOW_STEPS — liên kết qua workflow_id → workflows.project_id → projects
-- ============================================================
ALTER TABLE workflow_steps ENABLE ROW LEVEL SECURITY;
ALTER TABLE workflow_steps FORCE ROW LEVEL SECURITY;

CREATE POLICY wf_steps_tenant_select ON workflow_steps FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM workflows w
            JOIN projects p ON p.id = w.project_id
            WHERE w.id = workflow_steps.workflow_id
              AND p.organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        )
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    );

CREATE POLICY wf_steps_tenant_insert ON workflow_steps FOR INSERT
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM workflows w
            JOIN projects p ON p.id = w.project_id
            WHERE w.id = workflow_steps.workflow_id
              AND p.organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        )
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    );

CREATE POLICY wf_steps_tenant_update ON workflow_steps FOR UPDATE
    USING (
        EXISTS (
            SELECT 1 FROM workflows w
            JOIN projects p ON p.id = w.project_id
            WHERE w.id = workflow_steps.workflow_id
              AND p.organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        )
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM workflows w
            JOIN projects p ON p.id = w.project_id
            WHERE w.id = workflow_steps.workflow_id
              AND p.organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        )
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    );

CREATE POLICY wf_steps_tenant_delete ON workflow_steps FOR DELETE
    USING (
        current_setting('app.current_user_is_platform_admin', true) = 'true'
    );


-- ============================================================
-- 7) FILES — liên kết qua project_id → projects.organization_id
-- ============================================================
ALTER TABLE files ENABLE ROW LEVEL SECURITY;
ALTER TABLE files FORCE ROW LEVEL SECURITY;

CREATE POLICY files_tenant_select ON files FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM projects p
            WHERE p.id = files.project_id
              AND p.organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        )
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    );

CREATE POLICY files_tenant_insert ON files FOR INSERT
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM projects p
            WHERE p.id = files.project_id
              AND p.organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        )
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    );

CREATE POLICY files_tenant_update ON files FOR UPDATE
    USING (
        EXISTS (
            SELECT 1 FROM projects p
            WHERE p.id = files.project_id
              AND p.organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        )
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM projects p
            WHERE p.id = files.project_id
              AND p.organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        )
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    );

CREATE POLICY files_tenant_delete ON files FOR DELETE
    USING (
        current_setting('app.current_user_is_platform_admin', true) = 'true'
    );


-- ============================================================
-- 8) JOBS — liên kết qua project_id → projects.organization_id
-- ============================================================
ALTER TABLE jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE jobs FORCE ROW LEVEL SECURITY;

CREATE POLICY jobs_tenant_select ON jobs FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM projects p
            WHERE p.id = jobs.project_id
              AND p.organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        )
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    );

CREATE POLICY jobs_tenant_insert ON jobs FOR INSERT
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM projects p
            WHERE p.id = jobs.project_id
              AND p.organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        )
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    );

CREATE POLICY jobs_tenant_update ON jobs FOR UPDATE
    USING (
        EXISTS (
            SELECT 1 FROM projects p
            WHERE p.id = jobs.project_id
              AND p.organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        )
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM projects p
            WHERE p.id = jobs.project_id
              AND p.organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        )
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    );

CREATE POLICY jobs_tenant_delete ON jobs FOR DELETE
    USING (
        current_setting('app.current_user_is_platform_admin', true) = 'true'
    );


-- ============================================================
-- 9) JOB_EXECUTIONS — liên kết qua job_id → jobs.project_id → projects
-- ============================================================
ALTER TABLE job_executions ENABLE ROW LEVEL SECURITY;
ALTER TABLE job_executions FORCE ROW LEVEL SECURITY;

CREATE POLICY job_exec_tenant_select ON job_executions FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM jobs j
            JOIN projects p ON p.id = j.project_id
            WHERE j.id = job_executions.job_id
              AND p.organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        )
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    );

CREATE POLICY job_exec_tenant_insert ON job_executions FOR INSERT
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM jobs j
            JOIN projects p ON p.id = j.project_id
            WHERE j.id = job_executions.job_id
              AND p.organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        )
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    );

CREATE POLICY job_exec_tenant_update ON job_executions FOR UPDATE
    USING (
        EXISTS (
            SELECT 1 FROM jobs j
            JOIN projects p ON p.id = j.project_id
            WHERE j.id = job_executions.job_id
              AND p.organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        )
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    )
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM jobs j
            JOIN projects p ON p.id = j.project_id
            WHERE j.id = job_executions.job_id
              AND p.organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        )
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    );

CREATE POLICY job_exec_tenant_delete ON job_executions FOR DELETE
    USING (
        current_setting('app.current_user_is_platform_admin', true) = 'true'
    );


-- ============================================================
-- 10) AUDIT_LOGS — có organization_id trực tiếp
-- ============================================================
ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs FORCE ROW LEVEL SECURITY;

CREATE POLICY audit_logs_tenant_select ON audit_logs FOR SELECT
    USING (
        organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    );

-- Audit logs chỉ được INSERT bởi hệ thống, không check tenant
-- vì system/worker cũng cần ghi log.
CREATE POLICY audit_logs_system_insert ON audit_logs FOR INSERT
    WITH CHECK (true);

CREATE POLICY audit_logs_tenant_update ON audit_logs FOR UPDATE
    USING (
        current_setting('app.current_user_is_platform_admin', true) = 'true'
    );

CREATE POLICY audit_logs_tenant_delete ON audit_logs FOR DELETE
    USING (
        current_setting('app.current_user_is_platform_admin', true) = 'true'
    );


-- ============================================================
-- 11) REFRESH_TOKENS — liên kết qua user_id (user-scoped, not tenant-scoped)
-- ============================================================
ALTER TABLE refresh_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE refresh_tokens FORCE ROW LEVEL SECURITY;

-- Chỉ user sở hữu token hoặc platform admin mới được đọc/ghi.
CREATE POLICY rt_owner_select ON refresh_tokens FOR SELECT
    USING (
        user_id = NULLIF(current_setting('app.current_user_id', true), '')::uuid
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    );

CREATE POLICY rt_owner_insert ON refresh_tokens FOR INSERT
    WITH CHECK (
        user_id = NULLIF(current_setting('app.current_user_id', true), '')::uuid
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    );

CREATE POLICY rt_owner_modify ON refresh_tokens FOR UPDATE
    USING (
        user_id = NULLIF(current_setting('app.current_user_id', true), '')::uuid
        OR current_setting('app.current_user_is_platform_admin', true) = 'true'
    );

CREATE POLICY rt_owner_delete ON refresh_tokens FOR DELETE
    USING (
        current_setting('app.current_user_is_platform_admin', true) = 'true'
    );

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token_hash ON refresh_tokens (token_hash);


-- ============================================================
-- BẢNG KHÔNG BẬT RLS (và lý do)
-- ============================================================
-- * users          — Không có organization_id trực tiếp (user thuộc org
--                    qua memberships). Auth flow (login/register) cần
--                    truy cập users mà không cần biết tenant.
--                    → Bảo vệ ở application layer (AuthService).
--
-- * organizations  — Tenant root table. Nếu bật RLS thì không thể
--                    list tất cả org (admin dashboard), hoặc tạo org mới.
--                    → Bảo vệ ở application layer.
--
-- * permissions    — Lookup table, dữ liệu tĩnh, không chứa thông tin nhạy cảm.
--
-- * role_permissions — Liên kết role ↔ permission. Đã được bảo vệ gián tiếp
--                      qua RLS trên roles (không thể biết role_id nếu không thấy role).
--
-- * outbox_events  — Internal event queue, không phải data endpoint.
--                    Chỉ worker/scheduler đọc, không expose qua API.
-- ============================================================

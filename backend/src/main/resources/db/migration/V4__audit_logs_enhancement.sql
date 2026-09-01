-- ============================================================
-- V4: AUDIT LOGS ENHANCEMENT
-- ============================================================
-- Cho phép organization_id NULL cho các sự kiện cấp tài khoản/hệ thống
-- (như đăng nhập sai, khóa tài khoản, hoặc thay đổi mật khẩu ngoài context của org).
-- Các hành vi thuộc về tổ chức (cross-tenant unauthorized access, đổi quyền membership)
-- vẫn lưu organization_id cụ thể.

ALTER TABLE audit_logs ALTER COLUMN organization_id DROP NOT NULL;

CREATE INDEX IF NOT EXISTS idx_audit_logs_action_created_at ON audit_logs (action, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_actor_created_at ON audit_logs (actor_id, created_at DESC);

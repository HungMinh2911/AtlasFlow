package com.atlasflow.security.tenant;


import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Gán app.current_tenant_id cho TRANSACTION hiện tại để RLS policy ở Postgres
 * tự động lọc mọi query chạy sau đó trong cùng transaction.
 *
 * BẮT BUỘC dùng "SET LOCAL", TUYỆT ĐỐI KHÔNG dùng "SET" (session-level):
 * - SET LOCAL chỉ có hiệu lực trong phạm vi transaction hiện tại, Postgres tự
 *   reset khi COMMIT/ROLLBACK.
 * - SET (session-level) sẽ "dính" vào physical connection đó cho tới khi bị
 *   ghi đè. Với connection pool (HikariCP), connection được TRẢ VỀ POOL và
 *   TÁI SỬ DỤNG cho request tiếp theo của MỘT TENANT KHÁC. Nếu request đó lỡ
 *   quên gọi applyCurrentTenant(), nó sẽ vô tình "thừa kế" tenant_id của
 *   request trước - đây là kiểu rò rỉ chéo tenant còn nguy hiểm hơn cả lỗi
 *   IDOR ban đầu, vì nó âm thầm và khó phát hiện khi test (chỉ lộ ra khi có
 *   2 request liên tiếp dùng chung 1 connection từ pool).
 *
 * Vì SET LOCAL cần một transaction ĐANG MỞ, method này phải được gọi từ DÒNG
 * ĐẦU TIÊN của một method @Transactional - KHÔNG đặt trong AOP Aspect chạy
 * quanh controller, vì lúc đó transaction (nếu có) thường CHƯA bắt đầu -
 * thứ tự lồng nhau giữa Aspect tự viết và @Transactional's proxy không đảm
 * bảo chắc chắn, rất dễ set nhầm SET LOCAL trước khi BEGIN thật sự chạy.
 * Gọi tường minh, tránh "magic" ở chỗ quan trọng bảo mật này.
 */



@Component
public class TenantSessionContext {

    private final JdbcTemplate jdbcTemplate;

    public TenantSessionContext(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void applyCurrentTenant(String organizationId){
        if (organizationId == null){
            throw new IllegalStateException(
                    "organizationId null khi gọi applyCurrentTenant() - đảm bảo " +
                    "Lớp 1 (TenantAccessAspect kiểm tra membership) đã chạy và " +
                    "TenantContext.setCurrentTenant(...) đã được gọi trước đó."
            );
        }

        jdbcTemplate.queryForObject(
                "SELECT set_config(?, ?, true)",
                String.class,
                "app.current_tenant_id",
                organizationId
        );
    }

    /** Compatibility alias for the original misspelled method name. */
    @Deprecated
    public void applyCurrentTennant(String organizationId) {
        applyCurrentTenant(organizationId);
    }

}

# AtlasFlow - Database Migration History (Phase 3)

Tài liệu quản lý và theo dõi lịch sử tiến hóa lược đồ Cơ sở dữ liệu (Database Migration History) sử dụng **Flyway** trong dự án AtlasFlow.

---

## 1. Quy tắc quản lý Migration với Flyway

1. **Quy ước đặt tên file Migration:**
   - Cú pháp: `V<Version>__<Description>.sql` (sử dụng 2 dấu gạch dưới giữa Version và Description).
   - Thư mục lưu trữ: `backend/src/main/resources/db/migration/`
   - Ví dụ: `V1__baseline.sql`, `V2__add_priority_to_jobs.sql`.

2. **Quy tắc Bất biến (Immutability):**
   - Tuyệt đối **không được chỉnh sửa hoặc xóa** bất kỳ file migration nào đã được áp dụng (Applied) vào môi trường phát triển chung, staging hoặc production.
   - Bất kỳ thay đổi nào về cấu trúc bảng, thêm cột, tạo index, hoặc cập nhật dữ liệu phải được viết trong một file migration mới với version tăng dần (`V2`, `V3`...).

3. **Tính lặp lại và kiểm thử tự động (Idempotency & Testcontainers):**
   - Mỗi file migration phải có khả năng chạy thành công trên một database hoàn toàn rỗng.
   - Được tự động kiểm thử tính hợp lệ thông qua test class [`MigrationIntegrationTest`](file:///d:/Dai_Hoc/Project/AtlasFlow/backend/src/test/java/com/atlasflow/integration/MigrationIntegrationTest.java) bằng Testcontainers PostgreSQL trên mỗi lần build CI/CD.

---

## 2. Bảng theo dõi lịch sử Migration

| Version | Description | File Script | Ngày áp dụng | Trạng thái | Nội dung chính |
| :---: | :--- | :--- | :---: | :---: | :--- |
| **V1** | `baseline` | `V1__baseline.sql` | 2026-08-29 | **SUCCESS** | Khởi tạo toàn bộ cấu trúc cơ sở dữ liệu nền tảng cho Phase 3 gồm 13 bảng thực thể (`users`, `organizations`, `roles`, `memberships`, `projects`, `api_keys`, `workflows`, `workflow_steps`, `files`, `jobs`, `job_executions`, `outbox_events`, `audit_logs`) và 11 Indexes tối ưu truy vấn. |

---

## 3. Chi tiết từng Migration

### Migration V1: Baseline Schema
* **File:** [`backend/src/main/resources/db/migration/V1__baseline.sql`](file:///d:/Dai_Hoc/Project/AtlasFlow/backend/src/main/resources/db/migration/V1__baseline.sql)
* **Checksum:** Tự động tính toán bởi Flyway và lưu trong `flyway_schema_history`.
* **Execution Time:** ~96ms.
* **Chi tiết thay đổi:**
  1. Tạo 13 bảng quan hệ với định danh khóa chính `UUID`.
  2. Thiết lập ràng buộc kiểm tra toàn vẹn miền dữ liệu (`CHECK constraints`).
  3. Cấu hình bảng `memberships` với Composite Primary Key `(user_id, organization_id)` và Composite Foreign Key trỏ tới `roles(id, organization_id)`.
  4. Cấu hình bảng `jobs` với Unique Constraint `(project_id, idempotency_key)` và trường `request_fingerprint` hỗ trợ cơ chế Idempotency.
  5. Cấu hình bảng `outbox_events` hỗ trợ mẫu Transactional Outbox Pattern với Partial Index `WHERE published_at IS NULL`.
  6. Tạo 11 chỉ mục B-Tree để tối ưu hóa hiệu năng truy vấn và phân trang.

---

## 4. Quản lý trạng thái thông qua `flyway_schema_history`

Flyway tự động duy trì bảng `flyway_schema_history` trong PostgreSQL:

```sql
SELECT installed_rank, version, description, type, script, checksum, installed_on, execution_time, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

**Kết quả thực tế:**
```text
 installed_rank | version | description | type |      script       |   checksum   |          installed_on          | execution_time | success 
----------------+---------+-------------+------+-------------------+--------------+--------------------------------+----------------+---------
              1 | 1       | baseline    | SQL  | V1__baseline.sql  | 1493028405   | 2026-08-29 08:30:00.123456+00  |             96 | t
```

---

## 5. Hướng dẫn sửa lỗi khi Migration bị lỗi (Troubleshooting)

Nếu một migration mới bị lỗi trong quá trình phát triển (Local Development):
1. **Kiểm tra log lỗi:** Xem chi tiết câu lệnh SQL thất bại trong terminal Spring Boot hoặc Flyway exception.
2. **Khôi phục trạng thái Clean:**
   - Trong môi trường local: Sử dụng lệnh `mvn flyway:repair` hoặc xóa database/container và khởi động lại để chạy lại từ đầu.
3. **Môi trường Production:** Viết migration tiếp theo (`V2__fix_...sql`) để sửa chữa cấu trúc mà không can thiệp sửa trực tiếp file migration cũ.

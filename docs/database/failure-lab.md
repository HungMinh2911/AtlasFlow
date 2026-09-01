# AtlasFlow - Database Failure Lab (Phase 3)

Tài liệu tổng hợp các kịch bản lỗi cơ sở dữ liệu (Database Failure Scenarios), kiểm thử khả năng chịu lỗi (Fault Tolerance), phân tích nguyên nhân và giải pháp phòng thủ trong hệ thống AtlasFlow.

---

## 1. Kịch bản 1: Lỗi trong Transaction & Cơ chế Rollback nguyên tử

### Mô tả kịch bản
Khi client gửi yêu cầu `POST /api/v1/jobs`, ứng dụng thực hiện tạo `Job` và tạo `OutboxEvent` trong cùng một `@Transactional` boundary. Kịch bản mô phỏng lỗi xảy ra khi lưu `OutboxEvent` (ví dụ: mất kết nối, lỗi serialize JSON, hoặc ném ngoại lệ bất ngờ).

### Hành vi hệ thống
1. `Job` đã được insert vào bảng `jobs` nhưng chưa được commit.
2. Tầng ứng dụng ném ra `RuntimeException`.
3. Spring Transaction Manager kích hoạt rollback toàn bộ transaction.
4. Cả bản ghi `Job` và bản ghi `OutboxEvent` đều bị xóa/hủy hoàn toàn khỏi PostgreSQL, không để lại bất kỳ dữ liệu rác (Orphan Data) nào.

### Minh chứng kiểm thử tự động
- **Test Class:** [`RollbackIntegrationTest`](file:///d:/Dai_Hoc/Project/AtlasFlow/backend/src/test/java/com/atlasflow/integration/RollbackIntegrationTest.java)
- **Kết quả:** `assertEquals(0L, jobCount)` và `assertEquals(0L, outboxCount)` $\rightarrow$ **PASS**.

---

## 2. Kịch bản 2: Tái sử dụng Idempotency-Key với Payload khác nhau (Key Reuse)

### Mô tả kịch bản
Client sử dụng lại một `Idempotency-Key` cũ nhưng gửi kèm payload mới (ví dụ: thay đổi `file_id` hoặc `workflow_id`).

### Hành vi hệ thống
1. Ứng dụng tính toán `request_fingerprint` (SHA-256 hash của `project_id + workflow_id + file_id`).
2. Tìm kiếm Job đã tồn tại theo `(project_id, idempotency_key)`.
3. Phát hiện `request_fingerprint` mới **khác** với `request_fingerprint` đã lưu.
4. Ném ra ngoại lệ `IdempotencyKeyReusedException`.
5. Global Exception Handler chuyển đổi thành phản hồi HTTP `409 Conflict`:
   ```json
   {
     "error_code": "IDEMPOTENCY_KEY_REUSED",
     "message": "Idempotency key '...' has already been used with a different request payload."
   }
   ```

---

## 3. Kịch bản 3: Xung đột Transaction & Tránh lỗi Aborted Transaction (`25P02`)

### Mô tả kịch bản
Nhiều client gửi đồng thời (Concurrent Race Condition) các request với cùng một `Idempotency-Key`. Cả hai thread cùng vượt qua bước `SELECT` kiểm tra ban đầu và cùng cố gắng thực hiện `INSERT`.

### Vấn đề tiềm ẩn trên PostgreSQL
Nếu sử dụng `INSERT` thông thường, thread thứ hai sẽ bị lỗi vi phạm Unique Constraint `uq_jobs_project_id_idempotency_key`. Khi một lệnh SQL bên trong transaction block bị lỗi, PostgreSQL sẽ đánh dấu transaction là **ABORTED** (`SQLSTATE 25P02: current transaction is aborted, commands ignored until end of transaction block`), khiến mọi câu lệnh `SELECT` tiếp theo trong transaction đó đều bị từ chối.

### Giải pháp kỹ thuật trong AtlasFlow
Sử dụng cú pháp PostgreSQL Native:
```sql
INSERT INTO jobs (id, project_id, workflow_id, file_id, status, idempotency_key, request_fingerprint, created_at)
VALUES (:id, :projectId, :workflowId, :fileId, :status, :idempotencyKey, :requestFingerprint, :createdAt)
ON CONFLICT (project_id, idempotency_key) DO NOTHING;
```
- **Thread 1:** Thực thi `INSERT` thành công (`updateCount = 1`) $\rightarrow$ tạo Outbox Event $\rightarrow$ Commit.
- **Thread 2:** Thực thi `INSERT` không chèn thêm (`updateCount = 0`), **không ném exception** $\rightarrow$ Transaction vẫn sạch sẽ (Active) $\rightarrow$ `SELECT` lấy Job đã tồn tại $\rightarrow$ Trả về kết quả idempotent.

### Minh chứng kiểm thử tự động
- **Test Class:** [`IdempotencyConcurrencyTest`](file:///d:/Dai_Hoc/Project/AtlasFlow/backend/src/test/java/com/atlasflow/integration/IdempotencyConcurrencyTest.java) (10 threads đồng thời $\rightarrow$ Sinh đúng 1 Job và 1 Outbox Event).

---

## 4. Kịch bản 4: Bế tắc giao dịch (Deadlock Detection & Prevention)

### Mô tả kịch bản
Hai transaction khóa tài nguyên chéo nhau (Transaction A khóa `projects` rồi chờ `jobs`; Transaction B khóa `jobs` rồi chờ `projects`).

### Hành vi & Lỗi phát hiện
- PostgreSQL kích hoạt deadlock detector sau `deadlock_timeout` (1s) và hủy 1 transaction với mã lỗi:
  ```text
  ERROR: deadlock detected (SQLSTATE 40P01)
  DETAIL: Process A waits for ShareLock on transaction X; blocked by process B.
  ```

### Quy tắc chuẩn hóa phòng ngừa (Lock Hierarchy Order)
Mọi Service trong toàn hệ thống bắt buộc phải tuân theo một thứ tự xin khóa duy nhất từ trên xuống dưới:
$$\text{projects} \longrightarrow \text{workflows} \longrightarrow \text{jobs} \longrightarrow \text{job\_executions}$$

- **Tài liệu chi tiết:** [`docs/database/transaction-lab.md`](file:///d:/Dai_Hoc/Project/AtlasFlow/docs/database/transaction-lab.md)

---

## 5. Kịch bản 5: Cạn kiệt Connection Pool (HikariCP Pool Exhaustion)

### Mô tả kịch bản
Lượng request đồng thời vượt quá dung lượng connection pool của HikariCP (`maximum-pool-size = 2`), và các connection đang bị chiếm giữ bởi các truy vấn chạy chậm (`pg_sleep(5)`).

### Hành vi hệ thống
1. Các request đến sau được đưa vào hàng đợi `TIMED_WAITING`.
2. Khi vượt quá `connection-timeout` (3,000ms), HikariCP ném ra:
   ```text
   java.sql.SQLTransientConnectionException: AtlasFlowHikariTestPool - Connection is not available, request timed out after 3000ms.
   ```
3. Hệ thống trả về lỗi `500 Internal Server Error` (hoặc `503 Service Unavailable`).
4. Sau khi các request trước hoàn tất, connection được tự động giải phóng về pool và hệ thống tự phục hồi 100% mà không cần khởi động lại.
5. Áp dụng chiến lược **Exponential Backoff with Jitter** ở phía Client để ngăn chặn Cascading Failure (Bão retry làm sập hệ thống).

- **Tài liệu chi tiết:** [`docs/database/pool-exhaustion-lab.md`](file:///d:/Dai_Hoc/Project/AtlasFlow/docs/database/pool-exhaustion-lab.md)

---

## 6. Kịch bản 6: Vi phạm Ràng buộc Toàn vẹn Dữ liệu (Constraint Violations)

Hệ thống được bảo vệ bởi các CHECK constraints và Foreign Keys ở tầng CSDL:

| Ràng buộc | Mục đích kiểm tra | Ngoại lệ xảy ra khi vi phạm |
| :--- | :--- | :--- |
| `ck_workflows_status` | Workflow phải thuộc `DRAFT`, `PUBLISHED`, `ARCHIVED` | `PSQLException: check constraint "ck_workflows_status"` |
| `ck_files_status` | File phải thuộc `UPLOADING`, `AVAILABLE`, `FAILED` | `PSQLException: check constraint "ck_files_status"` |
| `ck_workflow_steps_config_object` | Cấu hình step phải là JSON Object (`{}`) | `PSQLException: check constraint "ck_workflow_steps_config_object"` |
| `ck_job_executions_finished_after_started` | Thời điểm kết thúc phải sau thời điểm bắt đầu | `PSQLException: check constraint "ck_job_executions_finished_after_started"` |
| `uq_projects_organization_name` | Tên project không được trùng trong cùng organization | `DuplicateKeyException: uq_projects_organization_name` |

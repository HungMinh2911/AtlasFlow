# AtlasFlow - Database Backup & Restore Guide (Phase 3 - Giai đoạn 14)

Tài liệu này hướng dẫn quy trình sao lưu (Backup) và phục hồi (Restore) cơ sở dữ liệu PostgreSQL cho dự án AtlasFlow.

---

## 1. Quy định chung
1. **Không restore trực tiếp lên Database chính đang chạy** (`atlasflow`) để tránh nguy cơ mất mát dữ liệu do thao tác nhầm.
2. File backup phải lưu dưới dạng Custom Format (`.dump`) của PostgreSQL để hỗ trợ nén dữ liệu và phục hồi linh hoạt bằng `pg_restore`.
3. Tên file backup tự động chứa dấu mốc thời gian (timestamp): `atlasflow-YYYY-MM-DD-HHmm.dump`.

---

## 2. Hướng dẫn Sao lưu Database (Backup)

### Lệnh thực hiện:
```powershell
pwsh -File scripts/db/backup.ps1
```

### Cách thức hoạt động:
* Script `scripts/db/backup.ps1` sẽ kiểm tra/tạo thư mục `backups/`.
* Gọi lệnh `pg_dump -F c` bên trong container `docker-db-1` để xuất dữ liệu.
* Kết quả tạo ra file nén dạng `backups/atlasflow-2026-09-02-0057.dump`.

---

## 3. Hướng dẫn Phục hồi Database (Restore)

### Lệnh thực hiện:
```powershell
pwsh -File scripts/db/restore.ps1 -BackupFile backups/atlasflow-2026-09-02-0057.dump
```
*(Hoặc truyền thêm `-TargetDbName my_test_db` nếu muốn đặt tên database khôi phục khác).*

### Các bước script tự động xử lý:
1. Xóa và khởi tạo một database độc lập mới (mặc định: `atlasflow_restore_test`).
2. Nạp dữ liệu từ file `.dump` vào database mới qua `pg_restore --no-owner`.
3. In ra bảng đối chiếu số lượng bản ghi của 5 bảng quan trọng:
   - `users`
   - `projects`
   - `workflows`
   - `jobs`
   - `outbox_events`

---

## 4. Xác nhận Dữ liệu sau Restore

Sau khi khôi phục vào database `atlasflow_restore_test`, bạn có thể đổi `DB_URL` trong `application-local.yaml` hoặc biến môi trường `DB_URL`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/atlasflow_restore_test
```

Và khởi động ứng dụng Spring Boot để kiểm tra API đọc dữ liệu đã restore thành công:
```bash
curl -i http://localhost:8080/api/v1/projects
```

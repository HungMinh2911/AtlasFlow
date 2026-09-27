# AtlasFlow - Transaction & Deadlock Lab (Phase 3 - Giai đoạn 11)

Tài liệu này ghi lại quá trình thực hành tạo Deadlock có kiểm soát trong PostgreSQL 16, phân tích nguyên nhân gốc rễ, đưa ra quy tắc khắc phục (Lock Hierarchy Order) và thực nghiệm chứng minh loại bỏ Deadlock hoàn toàn.

---

## 1. Khái niệm Deadlock trong Database

**Deadlock (Bế tắc)** xảy ra khi hai hoặc nhiều transaction giữ khóa (Lock) trên các tài nguyên khác nhau và đồng thời chờ tài nguyên mà transaction kia đang nắm giữ, tạo thành một vòng lặp phụ thuộc (Circular Dependency).

```text
Transaction A: Lock(Projects)  ────────(chờ)────────> Khóa Jobs? (do B giữ)
                                                          │
                                                          │
Transaction B: Lock(Jobs)      ────────(chờ)────────> Khóa Projects? (do A giữ)
```

PostgreSQL sử dụng trình phát hiện bế tắc (`deadlock_timeout`, mặc định 1 giây). Khi phát hiện vòng lặp chờ, PostgreSQL sẽ chủ động hủy một trong hai transaction và trả về lỗi **SQLSTATE 40P01 (`deadlock_detected`)**.

---

## 2. Việc 11.1: Tạo Deadlock có kiểm soát

### Kịch bản thử nghiệm
Thực hiện 2 transaction chạy đồng thời trên 2 kết nối CSDL:

* **Transaction A (Luồng 1):**
  1. `BEGIN;`
  2. Khóa bảng `projects` (`FOR UPDATE`) tại `id = '00000000-0000-0000-0000-000000000002'`.
  3. Dừng 2 giây (`pg_sleep(2)`).
  4. Cố gắng khóa bảng `jobs` (`FOR UPDATE`) tại `id = '4cbe697d-2c95-4683-9be1-e6dd17603c99'`.

* **Transaction B (Luồng 2 - chạy sau A 0.5s):**
  1. `BEGIN;`
  2. Khóa bảng `jobs` (`FOR UPDATE`) tại `id = '4cbe697d-2c95-4683-9be1-e6dd17603c99'`.
  3. Dừng 2 giây (`pg_sleep(2)`).
  4. Cố gắng khóa bảng `projects` (`FOR UPDATE`) tại `id = '00000000-0000-0000-0000-000000000002'`.

---

### Mã SQL thực hiện thử nghiệm

#### Transaction A:
```sql
BEGIN;
SELECT id FROM projects WHERE id = '00000000-0000-0000-0000-000000000002' FOR UPDATE;
SELECT pg_sleep(2);
SELECT id FROM jobs WHERE id = '4cbe697d-2c95-4683-9be1-e6dd17603c99' FOR UPDATE;
COMMIT;
```

#### Transaction B:
```sql
SELECT pg_sleep(0.5); -- Đợi A xin khóa projects trước
BEGIN;
SELECT id FROM jobs WHERE id = '4cbe697d-2c95-4683-9be1-e6dd17603c99' FOR UPDATE;
SELECT pg_sleep(2);
SELECT id FROM projects WHERE id = '00000000-0000-0000-0000-000000000002' FOR UPDATE;
COMMIT;
```

---

### Lỗi nhận được từ PostgreSQL (Log Output)

```text
ERROR:  deadlock detected
DETAIL:  Process 9057 waits for ShareLock on transaction 755; blocked by process 9064.
Process 9064 waits for ShareLock on transaction 754; blocked by process 9057.
HINT:  See server log for query details.
CONTEXT:  while locking tuple (0,1) in relation "jobs"
SQLSTATE: 40P01
```

---

## 3. Việc 11.2: Nguyên nhân & Quy tắc Sửa lỗi (Fix Strategy)

### Nguyên nhân gốc rễ
Deadlock xuất hiện do các Service/Transaction xin khóa các tài nguyên theo **thứ tự ngược nhau** (Inconsistent Locking Order).

### Giải pháp: Quy định thứ tự Lock thống nhất (Lock Hierarchy Order)
Mọi Service và Transaction trong AtlasFlow bắt buộc phải tuân theo thứ tự khóa từ trên xuống dưới theo phân cấp thực thể:

$$\text{projects} \longrightarrow \text{workflows} \longrightarrow \text{jobs} \longrightarrow \text{job\_executions}$$

```text
[BƯỚC 1] Lock Projects   (nếu cần)
   ↓
[BƯỚC 2] Lock Workflows  (nếu cần)
   ↓
[BƯỚC 3] Lock Jobs       (nếu cần)
   ↓
[BƯỚC 4] Lock Executions (nếu cần)
```

Khi mọi transaction đều xin khóa `projects` trước rồi mới xin khóa `jobs`, transaction đến sau sẽ phải chờ ở bước 1 cho đến khi transaction trước hoàn tất (`COMMIT`/`ROLLBACK`). Vòng lặp chờ bị loại bỏ hoàn toàn!

---

## 4. Test chứng minh Deadlock không còn

Sau khi áp dụng quy tắc thứ tự khóa thống nhất (`projects` ➔ `jobs`), ta chạy lại thử nghiệm với 2 transaction:

### Transaction A (Luồng 1):
```sql
BEGIN;
SELECT id FROM projects WHERE id = '00000000-0000-0000-0000-000000000002' FOR UPDATE;
SELECT pg_sleep(2);
SELECT id FROM jobs WHERE id = '4cbe697d-2c95-4683-9be1-e6dd17603c99' FOR UPDATE;
COMMIT;
```

### Transaction B (Luồng 2 - chạy sau A 0.5s):
```sql
SELECT pg_sleep(0.5);
BEGIN;
SELECT id FROM projects WHERE id = '00000000-0000-0000-0000-000000000002' FOR UPDATE; -- Chờ A nhả lock projects
SELECT pg_sleep(2);
SELECT id FROM jobs WHERE id = '4cbe697d-2c95-4683-9be1-e6dd17603c99' FOR UPDATE;
COMMIT;
```

### Kết quả thực nghiệm:
```text
--- Transaction A ---
BEGIN
  id = '00000000-0000-0000-0000-000000000002'
  pg_sleep
  id = '4cbe697d-2c95-4683-9be1-e6dd17603c99'
COMMIT (Hoàn tất thành công)

--- Transaction B ---
BEGIN
  id = '00000000-0000-0000-0000-000000000002' (Đợi A commit xong mới lấy được khóa)
  pg_sleep
  id = '4cbe697d-2c95-4683-9be1-e6dd17603c99'
COMMIT (Hoàn tất thành công)
```

**Kết luận:** Cả 2 transaction đều **thành công 100%**, không xảy ra bất kỳ lỗi Deadlock nào (`0 errors`).

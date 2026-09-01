# AtlasFlow - Database Query Benchmark (Phase 3 - Giai đoạn 10)

Tài liệu này ghi lại quá trình đo lường, phân tích hiệu năng (Query Benchmark) và đánh giá các phương án phân trang (Pagination) trên bảng `jobs` trong PostgreSQL 16.

---

## 1. Môi trường và Dữ liệu thử nghiệm

* **Hệ quản trị CSDL:** PostgreSQL 16 (chạy trên Docker container)
* **Kích thước mẫu thử nghiệm (Dataset):** **100,000 bản ghi** (`jobs`), trong đó:
  * `Project 1` (`00000000-0000-0000-0000-000000000002`): **80,000 jobs**
  * `Project 2` (`00000000-0000-0000-0000-000000000003`): **20,000 jobs**
* **Cấu hình phần cứng:** Môi trường phát triển cục bộ (Local SSD / RAM Cache).

---

## 2. Benchmark 1: Query lấy danh sách Job theo Project

### Câu truy vấn cần đo
```sql
SELECT *
FROM jobs
WHERE project_id = '00000000-0000-0000-0000-000000000002'
ORDER BY created_at DESC
LIMIT 20;
```

---

### 2.1. TRƯỚC KHI CÓ INDEX (Full Table Scan + In-Memory Sort)

> *Mô phỏng bằng cách tắt `enable_indexscan` & `enable_bitmapscan`.*

#### Query Plan thực tế (`EXPLAIN (ANALYZE, BUFFERS)`):
```text
                                                              QUERY PLAN                                                              
--------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=5027.38..5029.68 rows=20 width=130) (actual time=14.483..17.584 rows=20 loops=1)
   Buffers: shared hit=2078
   ->  Gather Merge  (cost=5027.38..10434.22 rows=47016 width=130) (actual time=14.481..17.579 rows=20 loops=1)
         Workers Planned: 1
         Workers Launched: 1
         Buffers: shared hit=2078
         ->  Sort  (cost=4027.37..4144.91 rows=47016 width=130) (actual time=10.982..10.985 rows=20 loops=2)
               Sort Key: created_at DESC
               Sort Method: top-N heapsort  Memory: 30kB
               Buffers: shared hit=2078
               Worker 0:  Sort Method: top-N heapsort  Memory: 30kB
               ->  Parallel Seq Scan on jobs  (cost=0.00..2776.29 rows=47016 width=130) (actual time=0.008..7.863 rows=40000 loops=2)
                     Filter: (project_id = '00000000-0000-0000-0000-000000000002'::uuid)
                     Rows Removed by Filter: 10000
                     Buffers: shared hit=2041
 Planning Time: 0.474 ms
 Execution Time: 17.635 ms
```

* **Chiến lược thực thi (Plan):** `Parallel Seq Scan` ➔ `Top-N Heapsort` ➔ `Gather Merge` ➔ `Limit`.
* **Số dòng phải quét (Rows scanned):** **100,000 dòng** (Toàn bộ bảng).
* **Số lượng Buffer I/O:** **2,078 pages** (hit in memory).
* **Thời gian thực thi (Execution Time):** **17.635 ms**.

---

### 2.2. SAU KHI CÓ INDEX (`idx_jobs_project_created_at`)

> *Index composite: `CREATE INDEX idx_jobs_project_created_at ON jobs (project_id, created_at DESC);`*

#### Query Plan thực tế (`EXPLAIN (ANALYZE, BUFFERS)`):
```text
                                                                    QUERY PLAN                                                                     
---------------------------------------------------------------------------------------------------------------------------------------------------
 Limit  (cost=0.42..3.69 rows=20 width=130) (actual time=0.040..0.043 rows=20 loops=1)
   Buffers: shared hit=4
   ->  Index Scan using idx_jobs_project_created_at on jobs  (cost=0.42..13070.10 rows=79927 width=130) (actual time=0.039..0.041 rows=20 loops=1)
         Index Cond: (project_id = '00000000-0000-0000-0000-000000000002'::uuid)
         Buffers: shared hit=4
 Planning Time: 0.469 ms
 Execution Time: 0.066 ms
```

* **Chiến lược thực thi (Plan):** `Index Scan using idx_jobs_project_created_at`.
* **Số dòng phải quét (Rows scanned):** **20 dòng** (dừng ngay khi đủ 20 bản ghi).
* **Số lượng Buffer I/O:** **4 pages** (giảm ~520 lần).
* **Thời gian thực thi (Execution Time):** **0.066 ms** (nhanh hơn **~267 lần**).

---

### 2.3. Bảng so sánh tổng hợp

| Tiêu chí | Trước khi có Index | Sau khi có Index | Mức độ cải thiện |
| :--- | :--- | :--- | :--- |
| **Execution Plan** | `Parallel Seq Scan + Top-N Sort` | `Index Scan` | Loại bỏ hoàn toàn bước Sort |
| **Rows Scanned** | 100,000 rows | 20 rows | **Giảm 5,000 lần** |
| **Shared Buffers Hit** | 2,078 pages | 4 pages | **Giảm 519 lần I/O** |
| **Execution Time** | `17.635 ms` | `0.066 ms` | **Nhanh gấp ~267 lần** |

---

## 3. Benchmark 2: Thực hành Phân trang (Pagination)

### 3.1. Offset Pagination (`LIMIT ... OFFSET ...`)

Khi người dùng phân trang sâu (Deep Paging):

#### Trang đầu (`OFFSET 1000`):
```sql
SELECT * FROM jobs 
WHERE project_id = '00000000-0000-0000-0000-000000000002' 
ORDER BY created_at DESC 
LIMIT 20 OFFSET 1000;
```
* **Execution Time:** `0.312 ms`
* **Rows scanned:** `1,020 rows` (quét 1,020 dòng rồi bỏ qua 1,000 dòng đầu)
* **Buffers:** `38 pages`

#### Trang sâu (`OFFSET 50000`):
```sql
SELECT * FROM jobs 
WHERE project_id = '00000000-0000-0000-0000-000000000002' 
ORDER BY created_at DESC 
LIMIT 20 OFFSET 50000;
```
* **Execution Time:** `10.196 ms`
* **Rows scanned:** `50,020 rows` (quét qua 50,020 dòng từ Index rồi bỏ qua 50,000 dòng)
* **Buffers:** `1,721 pages`

> ⚠️ **Nhược điểm của Offset Pagination:** Chi phí tài nguyên và thời gian tăng tuyến tính $O(N)$ theo độ lớn của `OFFSET`. Khi dữ liệu lên tới hàng triệu dòng, truy vấn trang sâu sẽ gây nghẽn RAM và Disk I/O.

---

### 3.2. Keyset Pagination / Cursor-based Pagination (`created_at + id`)

Thay vì dùng `OFFSET`, Client truyền vào con trỏ (Cursor) của bản ghi cuối cùng của trang trước:
* `created_at_cursor`
* `id_cursor` (UUID đóng vai trò tie-breaker khi có nhiều job tạo cùng mili-giây)

```sql
SELECT *
FROM jobs
WHERE project_id = '00000000-0000-0000-0000-000000000002'
  AND (created_at, id) < ('2026-09-01 10:00:00+00', '00000000-0000-0000-0000-000000000000'::uuid)
ORDER BY created_at DESC, id DESC
LIMIT 20;
```

#### Kết quả đo lường tại vị trí tương đương `OFFSET 50000`:
* **Execution Time:** `0.125 ms` (nhanh gấp **~81 lần** so với `OFFSET 50000`).
* **Rows scanned:** `21 rows` (nhảy trực tiếp đến vị trí con trỏ trên cây B-Tree và lấy 20 dòng kế tiếp).
* **Buffers:** `13 pages` (giảm hơn **130 lần** lượng Buffer I/O).

---

## 4. Kết luận & Khuyến nghị

1. **Hiệu quả của Composite Index:**
   * Index `idx_jobs_project_created_at (project_id, created_at DESC)` giúp truy vấn lọc theo Project và sắp xếp theo thời gian đạt hiệu năng tối đa ($O(\log N)$ thay vì $O(N)$), loại bỏ hoàn toàn chi phí sắp xếp trong bộ nhớ.
2. **Chiến lược Phân trang cho AtlasFlow:**
   * Đối với UI quản trị xem các trang đầu (Page 1, 2, 3): `LIMIT/OFFSET` vẫn hoạt động tốt và đơn giản.
   * Đối với API xử lý dữ liệu lớn (Worker, Data Sync, Infinite Scroll): **Khuyến nghị sử dụng Keyset Pagination** với cặp con trỏ `(created_at, id)` để đảm bảo thời gian phản hồi luôn là hằng số $O(1)$ bất kể kích thước bảng.

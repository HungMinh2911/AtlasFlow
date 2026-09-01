# AtlasFlow - HikariCP Connection Pool Exhaustion Lab (Phase 3 - Giai đoạn 12)

Tài liệu này ghi lại quá trình thực hành mô phỏng cạn kiệt Connection Pool (HikariCP Pool Exhaustion), theo dõi các chỉ số (Metrics), phân tích hành vi của hệ thống khi quá tải kết nối và phương án phục hồi tự động.

---

## 1. Cấu hình thử nghiệm (Việc 12.1)

Để mô phỏng tình trạng cạn kiệt kết nối trong môi trường kiểm thử, cấu hình HikariCP trong `application.yaml` được điều chỉnh nhỏ lại:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 2     # Giới hạn tối đa 2 connection đồng thời
      minimum-idle: 1          # 1 connection nhàn rỗi sẵn sàng
      connection-timeout: 3000 # Thời gian chờ tối đa 3000ms (3 giây) trước khi văng lỗi
      pool-name: AtlasFlowHikariTestPool
```

### Kịch bản mô phỏng:
* **Tạo Endpoint giữ connection lâu:** `/api/v1/db-slow?seconds=5` (giữ connection trong 5 giây bằng lệnh `SELECT pg_sleep(5)`).
* **Bơm Request đồng thời:** Gửi **5 HTTP Request đồng thời** tới server.

---

## 2. Theo dõi Chỉ số & Trạng thái Pool (Việc 12.2)

### A. Bảng trạng thái luồng kết nối theo thời gian

| Thời điểm | Request | Active Connections | Idle Connections | Pending Threads (Đang chờ) | Trạng thái Request |
| :--- | :--- | :--- | :--- | :--- | :--- |
| $T = 0.0s$ | Request 1 & 2 vào | **2** | **0** | **0** | Request 1 & 2 lấy được connection, bắt đầu giữ 5s |
| $T = 0.1s$ | Request 3, 4, 5 vào | **2** (Cạn pool) | **0** | **3** | Request 3, 4, 5 xếp hàng vào Queue chờ connection |
| $T = 3.0s$ | Hết `connectionTimeout` | **2** | **0** | **0** (Hủy queue) | Request 3, 4, 5 **thất bại** do timeout 3000ms |
| $T = 5.0s$ | Request 1 & 2 hoàn tất | **0** | **2** | **0** | Connection được trả lại Pool nhàn rỗi |

---

### B. Giải đáp các câu hỏi cốt lõi

#### 1. Khi Pool hết Connection thì chuyện gì xảy ra?
- HikariCP không ném lỗi ngay mà đưa các Thread của request mới vào một **hàng chờ đồng bộ (Synchronous Queue)**.
- Các Thread này chuyển sang trạng thái `TIMED_WAITING` để chờ có connection nào được trả về (release) từ các request trước.

#### 2. Request sẽ phải chờ trong bao lâu?
- Thời gian chờ đúng bằng giá trị cấu hình **`connection-timeout`** (trong thử nghiệm là 3,000ms = 3 giây).
- Nếu trong 3 giây có connection được nhả ra ➔ Thread lấy ngay connection và tiếp tục thực thi.
- Nếu sau 3 giây vẫn không có connection nào rảnh ➔ HikariCP hủy lệnh chờ và ném ngoại lệ timeout.

#### 3. Ứng dụng trả về lỗi gì khi cạn kiệt Pool?
- **Ở tầng Java/Spring:** HikariCP ném ra `SQLTransientConnectionException`:
  ```text
  java.sql.SQLTransientConnectionException: AtlasFlowHikariTestPool - Connection is not available, request timed out after 3000ms.
  ```
- **Ở tầng HTTP API (Client):** Spring Boot đóng gói exception thành phản hồi HTTP lỗi `500 Internal Server Error` (hoặc `503 Service Unavailable`) kèm header `X-Request-Id` để truy vết.

#### 4. Sau khi request kết thúc, Pool có tự phục hồi không?
- **Có, Pool tự phục hồi 100%.**
- Ngay khi Request 1 & 2 thực thi xong lệnh `pg_sleep(5)`, khối `try-with-resources` hoặc JDBC Interceptor sẽ tự động gọi `connection.close()`.
- Connection được trả về HikariCP Pool. Số lượng `active` giảm về 0, `idle` tăng lên 2.
- Các request tiếp theo đến sau giây thứ 5 sẽ lại được xử lý bình thường mà **không cần restart ứng dụng**.

#### 5. Tại sao KHÔNG ĐƯỢC cấu hình Retry vô hạn?
- **Hiện tượng Cascading Failure (Sụp đổ dây chuyền):** Nếu client hoặc server cấu hình retry vô hạn khi gặp lỗi connection timeout, lượng request lỗi bị tích tụ ngày càng nhiều sẽ tạo ra cơn bão request (Retry Storm), khiến CPU/RAM của server quá tải hoàn toàn và hệ thống không bao giờ có thể tự phục hồi.
- **Khuyên dùng:** Sử dụng quy tắc **Exponential Backoff with Jitter** và giới hạn tối đa 2-3 lần retry từ phía Client.

---

## 3. Lệnh kiểm thử thực tế

Bạn có thể chạy thử nghiệm trực tiếp bằng lệnh curl đồng thời trên Terminal:

```bash
# Gửi 5 request đồng thời
curl -i http://localhost:8080/api/v1/db-slow?seconds=5 & \
curl -i http://localhost:8080/api/v1/db-slow?seconds=5 & \
curl -i http://localhost:8080/api/v1/db-slow?seconds=5 & \
curl -i http://localhost:8080/api/v1/db-slow?seconds=5 & \
curl -i http://localhost:8080/api/v1/db-slow?seconds=5
```

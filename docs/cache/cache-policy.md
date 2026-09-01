# AtlasFlow Cache Policy

## 1. Mục đích

Tài liệu này định nghĩa cách AtlasFlow sử dụng Redis cho cache trong Phase 5.

Redis chỉ là lớp tối ưu latency và giảm tải cho PostgreSQL. PostgreSQL vẫn là source of truth cho dữ liệu nghiệp vụ. Nếu Redis mất dữ liệu hoặc bị dừng, hệ thống phải có hành vi fallback rõ ràng và không được làm sai dữ liệu trong database.

Phạm vi của policy này gồm:

- Cache dữ liệu đọc của project và workflow.
- TTL và quy tắc đặt cache key.
- Cache-aside.
- Invalidation khi dữ liệu thay đổi.
- Fallback khi Redis không khả dụng.
- Chống cache stampede.
- Giới hạn dữ liệu được phép lưu trong Redis.

Rate limiting dùng Redis nhưng có policy riêng tại `docs/cache/rate-limit-policy.md`.

## 2. Nguyên tắc bắt buộc

1. PostgreSQL là nguồn dữ liệu chính.
2. Không dùng Redis làm nơi lưu dữ liệu duy nhất.
3. Cache miss phải đọc dữ liệu từ PostgreSQL.
4. Cache không được làm thay đổi tính đúng đắn của transaction.
5. Không gọi API bên ngoài trong lúc đang giữ cache lock.
6. Mọi cache entry phải có TTL.
7. Cache key phải có namespace và version.
8. Không lưu password, token, secret hoặc secret hash trong Redis.
9. Không log toàn bộ cache value nếu value có thể chứa thông tin nhạy cảm.
10. Redis timeout phải hữu hạn; không retry vô hạn.

## 3. Phạm vi dữ liệu được cache

### 3.1 Được phép cache

Phase 5 chỉ cache các luồng đọc có thể tải lại từ PostgreSQL:

```text
GET /api/v1/projects/{projectId}
GET /api/v1/workflows/{workflowId}
```

Các object được cache chỉ chứa dữ liệu cần thiết cho response API. Không đưa thông tin xác thực hoặc bí mật vận hành vào object cache.

### 3.2 Không được cache

Không cache các dữ liệu sau:

```text
password
password_hash
JWT access token
refresh token
API key
secret_hash
database password
Redis password
file content riêng tư
request body có thông tin nhạy cảm
```

Idempotency của job vẫn do PostgreSQL đảm bảo bằng transaction và unique constraint. Không dùng Redis làm nguồn duy nhất để quyết định một job đã tồn tại hay chưa.

## 4. Cache key convention

Mọi key phải có namespace, resource, định danh, và version:

```text
atlasflow:{resource}:{identifier}:v{version}
```

Key chuẩn:

```text
atlasflow:project:{projectId}:v1
atlasflow:workflow:{workflowId}:v1
```

Ví dụ:

```text
atlasflow:project:7e6f1e27-8f9a-4b77-a5d2-1d9f7edb1c03:v1
```

### 4.1 Quy tắc key

- Không dùng email hoặc token thô trong cache key.
- Không đưa toàn bộ request body vào key.
- Không dùng key không có version.
- Không dùng tên class Java làm một phần bắt buộc của key.
- Khi thay đổi cấu trúc cached value không tương thích, tăng version từ `v1` lên `v2`.
- ID tenant/project phải được kiểm tra quyền trước khi đọc cache.

## 5. TTL policy

TTL ban đầu:

| Resource | Key | TTL ban đầu | Lý do |
|---|---|---:|---|
| Project detail | `atlasflow:project:{id}:v1` | 60 giây | Dữ liệu đọc nhiều, thay đổi không liên tục |
| Workflow detail | `atlasflow:workflow:{id}:v1` | 60 giây | Workflow cần giảm số lần đọc database |

TTL này là baseline, không phải giá trị vĩnh viễn. Việc thay đổi TTL phải dựa trên:

- Cache hit ratio.
- Database query load.
- Redis memory usage.
- Độ trễ p95 của API.
- Tần suất dữ liệu thay đổi.

Mọi entry phải có expiration. Không tạo key cache không có TTL.

## 6. Chiến lược cache-aside

AtlasFlow sử dụng cache-aside, không sử dụng Redis làm write-through database.

### 6.1 Read path

```text
HTTP request
    ↓
Kiểm tra quyền và tenant boundary
    ↓
Đọc Redis bằng cache key
    ├── Hit  → deserialize → trả response
    └── Miss
          ↓
       Đọc PostgreSQL
          ↓
       Serialize dữ liệu an toàn
          ↓
       Ghi Redis với TTL
          ↓
       Trả response
```

### 6.2 Cache hit

Khi Redis chứa dữ liệu hợp lệ:

1. Application đọc entry.
2. Deserialize entry.
3. Kiểm tra dữ liệu tối thiểu cần thiết cho response.
4. Trả response mà không truy vấn PostgreSQL.
5. Tăng metric cache hit.

### 6.3 Cache miss

Khi key không tồn tại hoặc entry không deserialize được:

1. Đọc PostgreSQL.
2. Nếu dữ liệu không tồn tại, trả lỗi 404 theo API contract.
3. Nếu dữ liệu tồn tại, ghi entry vào Redis với TTL.
4. Trả response.
5. Tăng metric cache miss.

Nếu cached value bị lỗi serialization, phải coi đó là cache miss và xóa entry lỗi. Không được trả dữ liệu không kiểm tra được cho client.

## 7. Invalidation policy

Khi dữ liệu thay đổi, database phải được cập nhật trước. Cache chỉ được invalidated sau khi database commit thành công.

```text
Update project/workflow
    ↓
Commit PostgreSQL thành công
    ↓
Xóa cache key tương ứng
```

Không xóa cache trước khi transaction database commit. Nếu transaction rollback, dữ liệu cache cũ vẫn có thể còn hợp lệ.

### 7.1 Invalidate project

```text
atlasflow:project:{projectId}:v1
```

### 7.2 Invalidate workflow

Khi workflow hoặc workflow step thay đổi, xóa:

```text
atlasflow:workflow:{workflowId}:v1
```

Nếu response project có chứa thông tin tổng hợp workflow, phải xóa thêm cache project liên quan:

```text
atlasflow:project:{projectId}:v1
```

### 7.3 Create operation

Khi tạo resource mới, không bắt buộc phải ghi cache ngay. Có thể để request đọc đầu tiên thực hiện cache-aside.

```text
Create resource
    → commit database
    → không cần preload cache
```

Điều này giúp tránh cache dữ liệu chưa cần dùng.

## 8. Redis failure policy

### 8.1 Redis không kết nối được khi đọc project/workflow

Application phải fallback về PostgreSQL:

```text
Redis timeout/error
    ↓
Ghi log ở mức warning, không ghi secret
    ↓
Đọc PostgreSQL
    ↓
Trả response bình thường nếu database hoạt động
```

Response không được lộ:

```text
Redis host
Redis password
stack trace
connection string
```

Metric lỗi Redis phải được tăng để vận hành có thể phát hiện sự cố.

### 8.2 PostgreSQL cũng không khả dụng

Nếu Redis và PostgreSQL đều không khả dụng, request phải trả lỗi service phù hợp theo error contract. Không coi dữ liệu cũ trong Redis là dữ liệu đúng tuyệt đối nếu không xác định được chính sách stale-read.

### 8.3 Redis restart

Redis có thể mất toàn bộ cache. Sau khi Redis khởi động lại:

1. Không cần khôi phục cache từ backup.
2. Request đầu tiên đọc PostgreSQL.
3. Cache được tạo lại theo cache-aside.
4. Kiểm tra hit/miss metrics để xác nhận quá trình warm-up.

Redis cache không phải dữ liệu cần backup/restore như PostgreSQL.

## 9. Cache stampede policy

Cache stampede xảy ra khi một key hết hạn và nhiều request cùng lúc truy vấn PostgreSQL.

Với các resource có nguy cơ tải cao, dùng lock có TTL:

```text
atlasflow:lock:project:{projectId}:v1
atlasflow:lock:workflow:{workflowId}:v1
```

Quy trình:

1. Request kiểm tra cache.
2. Nếu miss, request thử lấy lock bằng thao tác atomic `SET NX PX`.
3. Request lấy được lock kiểm tra cache lần hai.
4. Nếu vẫn miss, request đó đọc PostgreSQL và ghi Redis.
5. Request giải phóng lock.
6. Request khác đọc entry mới hoặc chờ trong thời gian giới hạn.

Lock bắt buộc phải có expiration để tránh lock tồn tại vĩnh viễn khi process bị crash.

Không dùng distributed lock cho mọi request nếu chưa có bằng chứng stampede. Trước tiên phải có benchmark baseline.

Có thể thêm TTL jitter nhỏ để tránh nhiều key hết hạn cùng một thời điểm:

```text
TTL thực tế = TTL cơ sở + giá trị jitter nhỏ
```

## 10. Redis timeout và retry

Redis client phải có timeout hữu hạn:

```text
connect timeout: ngắn
command timeout: ngắn
lock wait timeout: ngắn
```

Không retry vô hạn. Nếu retry được sử dụng:

- Có số lần retry tối đa.
- Có backoff.
- Không retry trong thời gian dài hơn request timeout.
- Không retry các lỗi dữ liệu không thể khắc phục bằng retry.

Nếu không lấy được cache lock trong thời gian giới hạn, application phải chọn hành vi rõ ràng: đọc database trực tiếp hoặc trả lỗi tạm thời theo endpoint.

## 11. Serialization

Cached value phải dùng JSON serialization có schema ổn định.

Khi thay đổi cached value:

1. Đánh giá compatibility với entry cũ.
2. Tăng cache key version nếu cần.
3. Deploy code hỗ trợ version mới.
4. Theo dõi deserialize error.

Không sử dụng Java native serialization cho dữ liệu Redis production.

## 12. Tenant và authorization

Cache không được làm mất tenant isolation.

Trước khi trả cached project/workflow:

1. Xác định tenant hoặc organization của request.
2. Kiểm tra quyền truy cập theo policy hiện tại.
3. Chỉ sau đó mới trả dữ liệu cache.

Không được sử dụng việc “có cache entry” như bằng chứng người dùng có quyền truy cập.

Nếu cache key có chứa project ID, repository hoặc application service vẫn phải thực hiện permission boundary cần thiết.

## 13. Metrics bắt buộc

Tối thiểu cần có các metric:

```text
atlasflow.cache.hit
atlasflow.cache.miss
atlasflow.cache.load
atlasflow.cache.error
atlasflow.cache.invalidation
atlasflow.redis.latency
atlasflow.redis.connection.error
```

Metric tag được phép:

```text
resource=project|workflow
operation=get|put|evict
result=hit|miss|error
```

Không dùng các giá trị có cardinality rất cao làm tag:

```text
user_id
request_id
email
project_id
```

## 14. Test bắt buộc

### Cache behavior

- Cache hit không truy vấn PostgreSQL lần thứ hai.
- Cache miss đọc PostgreSQL rồi ghi Redis.
- Entry hết TTL thì được load lại.
- Cached value sai format được coi là miss.
- Invalidation làm request sau đó đọc dữ liệu mới.

### Failure behavior

- Redis tắt nhưng GET project vẫn đọc PostgreSQL.
- Redis restart không làm mất dữ liệu nghiệp vụ.
- Redis timeout không làm request chờ vô hạn.
- Không log credential hoặc token.
- PostgreSQL unavailable trả error contract đúng.

### Stampede behavior

- Gửi nhiều request đồng thời sau khi cache hết hạn.
- Đo số query thực tế vào PostgreSQL.
- Xác nhận lock có TTL.
- Xác nhận không có lock tồn tại vĩnh viễn sau process failure.

## 15. Evidence cần ghi lại

Tạo tài liệu `docs/phase-5-evidence.md` và ghi:

- Redis startup command và output.
- Cache key/TTL thực tế.
- Test cache hit/miss.
- Test invalidation.
- Test Redis-down fallback.
- Metrics hit/miss/latency.
- Benchmark trước và sau cache.
- Stampede experiment.
- Failure timeline.
- Quyết định fallback và trade-off.

Mỗi kết luận phải trỏ được đến ít nhất một trong các bằng chứng:

```text
source code
test result
log
metric
SQL output
benchmark output
```

## 16. Definition of Done

Policy này được xem là đã triển khai đầy đủ khi:

- Redis chạy được bằng Docker ở local.
- Project/workflow read có cache-aside.
- Mọi cache entry có TTL.
- Invalidation xảy ra sau database commit.
- Redis down thì dữ liệu có thể đọc từ PostgreSQL.
- Không có secret hoặc token trong Redis.
- Cache stampede có test và policy rõ ràng.
- Metrics hit/miss/error/latency hoạt động.
- Có test restart và Redis unavailable.
- Có benchmark trước và sau cache.
- Có tài liệu failure và runbook.

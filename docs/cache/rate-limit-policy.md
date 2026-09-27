# AtlasFlow Rate-Limit Policy

## 1. Mục đích

Tài liệu này định nghĩa giới hạn tần suất request cho các endpoint nhạy cảm của AtlasFlow. Rate limit được lưu trong Redis và dùng để bảo vệ API khỏi brute-force, abuse và quá tải cục bộ.

Rate limit không thay thế cho authentication, authorization hoặc validation. Request vẫn phải đi qua các bước bảo mật đó.

## 2. Policy chuẩn

```yaml
login:
  limit: 5
  window: 60s
  scope: client

submit_job:
  limit: 30
  window: 60s
  scope: project

read_endpoint:
  limit: 120
  window: 60s
  scope: user_or_tenant
```

### 2.1 Login

Giới hạn:

```text
5 request / 60 giây
```

Một bucket login được xác định bởi IP và định danh đăng nhập đã chuẩn hóa. Không dùng email dạng plain text trong Redis key.

Mục đích là ngăn brute-force nhưng vẫn cho phép user hợp lệ thử lại một số lần trong thời gian ngắn.

### 2.2 Submit job

Giới hạn:

```text
30 request / 60 giây / project
```

Bucket gắn với `project_id`, vì submit job là tài nguyên thuộc project. User vẫn phải đăng nhập và có quyền trên project trước khi request được chấp nhận.

### 2.3 Read endpoint

Giới hạn:

```text
120 request / 60 giây / user hoặc tenant
```

Mặc định ưu tiên scope theo user sau khi authentication thành công. Với endpoint đọc dữ liệu tổng hợp theo organization, có thể dùng scope theo tenant để bảo vệ tổng tải của organization.

## 3. Redis key

Mọi key rate-limit phải có prefix, loại policy và version:

```text
atlasflow:rl:{policy}:{subject}:v1
```

Ví dụ:

```text
atlasflow:rl:login:{hash(ip + normalized_email)}:v1
atlasflow:rl:job:{projectId}:v1
atlasflow:rl:read:user:{userId}:v1
atlasflow:rl:read:tenant:{organizationId}:v1
```

### 3.1 Không lưu email plain text

Không được tạo key như:

```text
atlasflow:rl:login:owner@atlasflow.local:v1
```

Email phải được chuẩn hóa trước khi hash:

```text
normalized_email = trim(email).toLowerCase(Locale.ROOT)
subject = ip + "|" + normalized_email
subject_hash = SHA-256(subject)
```

Key cuối cùng:

```text
atlasflow:rl:login:{sha256(ip + "|" + normalized_email)}:v1
```

Hash chỉ dùng để tránh lộ định danh trong key; nó không thay thế password hashing và không được dùng làm credential.

## 4. Thuật toán và thao tác Redis

AtlasFlow sử dụng fixed-window counter ở phiên bản đầu tiên:

```text
INCR key
Nếu giá trị sau INCR là 1 → EXPIRE key 60 giây
Nếu giá trị > limit → từ chối request
```

Các thao tác phải được thực hiện theo cách atomic, tốt nhất bằng Lua script hoặc primitive atomic phù hợp của Redis. Không tách `GET` và `INCR` thành hai bước kiểm tra không đồng bộ vì nhiều request đồng thời có thể vượt giới hạn.

TTL phải được đặt ngay khi bucket được tạo. Không để rate-limit key tồn tại vĩnh viễn.

Ví dụ kiểm tra local:

```powershell
docker exec docker-redis-1 redis-cli TTL "atlasflow:rl:job:PROJECT_ID:v1"
docker exec docker-redis-1 redis-cli GET "atlasflow:rl:job:PROJECT_ID:v1"
```

## 5. Thứ tự xử lý request

### Login

```text
Nhận request
    ↓
Chuẩn hóa email và xác định IP
    ↓
Tính bucket key
    ↓
Tăng counter Redis
    ↓
Vượt giới hạn?
    ├── Có: trả 429
    └── Không: tiếp tục xác thực username/password
```

Rate limit login nên được kiểm tra trước password verification để giảm chi phí xử lý brute-force.

### Submit job

```text
Nhận request
    ↓
Xác thực JWT
    ↓
Kiểm tra membership và project access
    ↓
Tăng bucket theo project
    ↓
Vượt giới hạn?
    ├── Có: trả 429
    └── Không: tiếp tục idempotency và tạo job
```

Rate limit không được dùng để bỏ qua kiểm tra quyền project.

### Read endpoint

```text
Nhận request
    ↓
Xác thực JWT
    ↓
Xác định user hoặc tenant
    ↓
Tăng counter
    ↓
Vượt giới hạn?
    ├── Có: trả 429
    └── Không: tiếp tục đọc cache/database
```

## 6. Response khi vượt giới hạn

Khi vượt giới hạn, API trả:

```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/problem+json
Retry-After: 30
X-Request-Id: <request-id>
```

Body chuẩn:

```json
{
  "type": "https://atlasflow.local/problems/rate-limit-exceeded",
  "title": "Too Many Requests",
  "status": 429,
  "detail": "Rate limit exceeded. Retry later.",
  "code": "RATE_LIMIT_EXCEEDED",
  "request_id": "<request-id>"
}
```

`Retry-After` tính theo giây. Giá trị thực tế nên là số giây còn lại của bucket, không hard-code nếu có thể xác định TTL Redis.

Không đưa vào response:

- Email hoặc thông tin credential.
- Redis key nội bộ.
- Giá trị counter nội bộ nếu không cần cho client.
- Chi tiết stack trace.

## 7. Khi Redis lỗi

Rate limit là lớp bảo vệ phụ thuộc Redis, không phải source of truth của nghiệp vụ.

Policy local/development:

```text
Redis lỗi → ghi log cảnh báo → cho request đi tiếp
```

Policy production cần quyết định riêng theo endpoint:

- Login: ưu tiên fail-closed hoặc fallback giới hạn ở edge gateway.
- Submit job: có thể fail-closed để tránh tạo tải ngoài kiểm soát.
- Read endpoint: có thể fail-open trong thời gian ngắn nếu database vẫn khỏe.

Mọi fallback phải có metric và alert. Không được âm thầm bỏ qua lỗi Redis.

## 8. Quy tắc bảo mật và vận hành

- Không dùng email plain text trong key.
- Không dùng access token, refresh token hoặc password trong key.
- Không log toàn bộ Redis key login sau khi đã chứa dữ liệu nhận diện.
- Mọi key phải có version như `v1`.
- Khi đổi thuật toán hoặc cấu trúc key, tăng version lên `v2`.
- Rate-limit key phải có TTL.
- Kiểm tra authorization trước khi sử dụng `project_id` hoặc `organization_id` do client gửi.
- Không xem rate-limit counter là audit log nghiệp vụ.
- Có metric cho allowed, rejected và Redis error.

## 9. Metric đề xuất

```text
atlasflow.ratelimit.allowed{policy="login"}
atlasflow.ratelimit.rejected{policy="login"}
atlasflow.ratelimit.allowed{policy="submit_job"}
atlasflow.ratelimit.rejected{policy="submit_job"}
atlasflow.ratelimit.redis_error{policy="read"}
```

Không gắn email hoặc token vào metric label vì cardinality cao và có nguy cơ lộ dữ liệu. Có thể dùng policy, endpoint group và outcome làm label.

## 10. Acceptance criteria

- Login bị từ chối sau request thứ 6 trong cùng một cửa sổ 60 giây.
- Submit job của một project bị từ chối sau request thứ 31 trong 60 giây.
- Read endpoint bị giới hạn ở 120 request trong 60 giây theo scope đã chọn.
- Response vượt giới hạn có status `429` và header `Retry-After`.
- Response có `X-Request-Id` hoặc request ID được hệ thống gán tự động.
- Redis key login không chứa email dạng plain text.
- Rate-limit key tự hết hạn.
- Hai request đồng thời không thể cùng vượt counter do thao tác không atomic.
- Redis restart không làm mất dữ liệu PostgreSQL hoặc dữ liệu nghiệp vụ.

# AtlasFlow Cache Failure Policy

## 1. Mục đích

Tài liệu này định nghĩa hành vi của AtlasFlow khi Redis hoặc một thao tác cache bị lỗi.

Redis là lớp tối ưu hiệu năng, không phải nguồn dữ liệu nghiệp vụ chính. PostgreSQL vẫn là source of truth. Vì vậy lỗi Redis không được làm mất dữ liệu đã ghi trong PostgreSQL và không được khiến hệ thống trả dữ liệu không kiểm chứng được.

Rate limit dùng Redis có chính sách bổ sung tại `docs/cache/rate-limit-policy.md`.

## 2. Phạm vi

Policy áp dụng cho:

```text
GET /api/v1/projects/{projectId}
GET /api/v1/workflows/{workflowId}
Redis cache project/workflow
Rate-limit counter dùng Redis
```

Không áp dụng Redis như nguồn duy nhất cho:

```text
Job idempotency
Transaction nghiệp vụ
Audit log
Authentication token
File metadata bắt buộc phải lưu trong PostgreSQL
```

## 3. Nguyên tắc bắt buộc

1. PostgreSQL là nguồn dữ liệu chính.
2. Cache failure không được làm mất dữ liệu PostgreSQL.
3. Redis timeout phải hữu hạn.
4. Không retry vô hạn.
5. Cache lỗi phải được coi như cache miss.
6. Không trả cached value nếu không deserialize hoặc validate được.
7. Mọi cache key phải có TTL.
8. Không log token, password, secret hoặc toàn bộ cached value nhạy cảm.
9. Mọi fallback quan trọng phải có log và metric.
10. Không dùng việc Redis có hoặc không có key làm bằng chứng authorization.

## 4. Phân loại lỗi

### 4.1 Cache miss bình thường

Redis trả về `null` vì key chưa tồn tại hoặc đã hết TTL.

Hành vi:

```text
Đọc PostgreSQL
→ tạo response hợp lệ
→ thử ghi Redis
→ trả response cho client
```

Cache miss không phải lỗi hệ thống và không cần trả `5xx`.

### 4.2 Redis timeout hoặc connection error khi đọc

Ví dụ:

```text
RedisConnectionFailureException
RedisCommandTimeoutException
RedisConnectionException
```

Hành vi đối với project/workflow read:

```text
Ghi log cảnh báo
→ coi như cache miss
→ đọc PostgreSQL
→ trả dữ liệu từ PostgreSQL
```

Không trả lỗi Redis trực tiếp cho client nếu PostgreSQL vẫn có thể phục vụ request.

### 4.3 Lỗi deserialize cached value

Nếu JSON trong Redis không còn tương thích với model hiện tại:

```text
Đọc key thất bại
→ log cache deserialize error
→ xóa key lỗi nếu có thể
→ đọc PostgreSQL
→ ghi lại cache theo version mới
→ trả dữ liệu hợp lệ
```

Không trả object thiếu field hoặc object chưa deserialize thành công.

### 4.4 Lỗi ghi cache

Nếu PostgreSQL đã đọc thành công nhưng `SET` vào Redis thất bại:

```text
Không rollback dữ liệu PostgreSQL
Không trả lỗi chỉ vì cache SET thất bại
Ghi log cảnh báo
Tăng metric cache write error
Trả response từ PostgreSQL
```

Request tiếp theo sẽ đọc lại PostgreSQL vì cache chưa có dữ liệu.

### 4.5 PostgreSQL cũng lỗi

Nếu Redis lỗi và PostgreSQL cũng không thể đọc dữ liệu:

```text
Không có nguồn dữ liệu dự phòng
→ trả lỗi 5xx phù hợp
→ ghi request_id vào log
→ không trả cached value cũ không kiểm chứng
```

Cache cũ chỉ được trả khi application có policy rõ ràng cho stale data. AtlasFlow hiện không dùng stale cache cho response project/workflow thông thường.

## 5. Ma trận hành vi

| Tình huống | Redis | PostgreSQL | Hành vi | HTTP |
|---|---|---|---|---|
| Cache hit hợp lệ | OK | Không cần đọc | Trả Redis | 200 |
| Cache miss | OK | OK | Đọc PostgreSQL, best-effort SET Redis | 200 |
| Redis đọc lỗi | Lỗi | OK | Fallback PostgreSQL | 200 |
| Redis ghi lỗi | Lỗi | Đã đọc được | Trả PostgreSQL, ghi cảnh báo | 200 |
| Cached JSON lỗi | JSON không hợp lệ | OK | Xóa key, đọc PostgreSQL | 200 |
| Redis lỗi | Lỗi | Lỗi | Trả lỗi hệ thống | 5xx |
| Rate limit counter lỗi | Lỗi | Không liên quan | Theo policy rate-limit production | 429 hoặc fallback |

## 6. Timeout và retry

Redis phải được cấu hình timeout hữu hạn:

```yaml
spring:
  data:
    redis:
      timeout: 2s
      connect-timeout: 1s
```

Không sử dụng retry vô hạn. Nếu cần retry:

```text
Tối đa 1 lần retry ngắn
Không retry khi request đã gần hết timeout budget
Không retry lỗi serialization
Không retry vô hạn trong web request
```

Với cache read/write thông thường, fallback PostgreSQL quan trọng hơn việc retry Redis nhiều lần.

## 7. Hành vi theo loại thao tác

### 7.1 Đọc project/workflow

Đây là cache-aside:

```text
Try Redis GET
    ├── hit hợp lệ → trả cache
    └── miss/error → PostgreSQL
                         ↓
                    best-effort Redis SET
                         ↓
                    trả response
```

Cache failure không được chặn endpoint đọc nếu PostgreSQL vẫn hoạt động.

### 7.2 Thay đổi project/workflow

Thứ tự chuẩn:

```text
Ghi PostgreSQL thành công
    ↓
Commit transaction
    ↓
Evict hoặc cập nhật cache
```

Không xóa cache trước khi database commit. Nếu database rollback mà cache đã bị xóa, hệ thống vẫn đúng dữ liệu nhưng có thể tạo thêm cache miss không cần thiết.

Nếu evict Redis thất bại sau commit:

```text
Không rollback transaction PostgreSQL
Ghi metric cache invalidation error
Cho cache hết TTL hoặc thực hiện retry có giới hạn
```

### 7.3 Rate limit

Rate limit có yêu cầu khác cache đọc vì Redis là nơi lưu counter.

Development/local:

```text
Redis lỗi → log cảnh báo → cho request đi tiếp
```

Production:

- Login: ưu tiên fail-closed hoặc dùng rate limit ở API gateway.
- Submit job: nên fail-closed nếu không thể xác định counter.
- Read endpoint: có thể fail-open ngắn hạn để ưu tiên availability.

Quyết định fail-open/fail-closed phải được cấu hình rõ theo environment, không được tùy ý trong từng controller.

## 8. Fail-open và fail-closed

### Fail-open

Cho request đi tiếp khi dependency bảo vệ bị lỗi.

Phù hợp hơn với:

```text
Cache read project/workflow
Read endpoint rate limit trong local
```

Rủi ro:

```text
Tải PostgreSQL tăng
Rate limit có thể tạm thời không hoạt động
```

### Fail-closed

Từ chối request khi không thể kiểm tra điều kiện bắt buộc.

Phù hợp hơn với:

```text
Submit job khi không kiểm tra được rate limit production
Login brute-force protection trong production
```

Rủi ro:

```text
Redis lỗi có thể làm API tạm thời không dùng được
```

## 9. Error response

Cache failure không nên lộ chi tiết Redis cho client. Nếu PostgreSQL vẫn phục vụ được, client nhận response nghiệp vụ bình thường.

Khi rate limit bị từ chối:

```http
HTTP/1.1 429 Too Many Requests
Retry-After: 30
X-Request-Id: <request-id>
```

Khi cả Redis và PostgreSQL đều không phục vụ được, response phải dùng error contract chung của AtlasFlow và chứa `request_id`, nhưng không chứa:

```text
Redis host
Redis key
stack trace
database credentials
```

## 10. Logging và metric

Log nên chứa:

```text
request_id
cache namespace
operation: GET/SET/DELETE
resource type
failure category
```

Không log:

```text
password
JWT
refresh token
API key secret
toàn bộ cached JSON nhạy cảm
```

Metric đề xuất:

```text
atlasflow.cache.hit
atlasflow.cache.miss
atlasflow.cache.read_error
atlasflow.cache.write_error
atlasflow.cache.deserialize_error
atlasflow.cache.invalidation_error
atlasflow.cache.fallback_database
atlasflow.ratelimit.redis_error
```

Nên gắn label giới hạn như:

```text
resource=project|workflow
operation=read|write|evict
failure=timeout|connection|serialization
```

Không dùng email hoặc token làm metric label.

## 11. Circuit breaker và bảo vệ PostgreSQL

Nếu Redis hỏng lâu, mọi request đọc cache sẽ fallback xuống PostgreSQL. Điều này có thể tạo tải lớn.

Khi cần production hardening, bổ sung:

```text
Circuit breaker cho Redis
Giới hạn số request fallback đồng thời
Bulkhead cho database fallback
Alert khi cache error vượt ngưỡng
Cache stampede protection
```

Circuit breaker không được làm mất dữ liệu. Nó chỉ tránh việc application liên tục chờ Redis không khả dụng.

## 12. Kiểm thử bắt buộc

### Cache read

- Redis có key hợp lệ → trả cache, không gọi repository.
- Redis miss → gọi repository và best-effort ghi cache.
- Redis timeout → fallback repository.
- JSON cache không hợp lệ → xóa key và fallback repository.
- PostgreSQL lỗi sau Redis failure → trả error contract phù hợp.

### Cache write/invalidation

- PostgreSQL save thất bại → không được ghi cache thành công.
- PostgreSQL save thành công, Redis SET thất bại → response vẫn thành công.
- Publish workflow → cache cũ bị evict sau khi database update thành công.
- Tạo workflow step → workflow detail cache bị evict.

### Rate limit

- Request trong giới hạn được xử lý.
- Request vượt giới hạn trả `429`.
- Response có `Retry-After` và `X-Request-Id`.
- Counter có TTL.
- Hai request đồng thời không vượt limit do race condition.
- Redis rate-limit failure tuân theo fail-open/fail-closed của environment.

## 13. Tiêu chí hoàn thành

- Redis không phải source of truth.
- Cache read error không làm hỏng read endpoint khi PostgreSQL còn hoạt động.
- Cache write/invalidation error không rollback sai transaction PostgreSQL.
- Cached value lỗi không được trả cho client.
- Không retry vô hạn.
- Có log, metric và request ID cho failure quan trọng.
- Có test cho Redis timeout, deserialize error, cache miss và invalidation failure.
- Policy rate limit được áp dụng riêng theo `docs/cache/rate-limit-policy.md`.

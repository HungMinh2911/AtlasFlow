# AtlasFlow Message Contract

## 1. Mục đích

Tài liệu này định nghĩa contract cho các message nội bộ của AtlasFlow trong Phase 6 RabbitMQ và Outbox.

Contract giúp producer và consumer thống nhất:

- Cấu trúc message.
- Tên event và phiên bản event.
- Cách truy vết request/message.
- Cách retry và xử lý duplicate delivery.
- Dữ liệu được phép và không được phép đưa vào message.

Message được publish qua RabbitMQ nhưng dữ liệu nghiệp vụ vẫn được lưu trong PostgreSQL. RabbitMQ không thay thế database.

## 2. Nguyên tắc delivery

AtlasFlow sử dụng `at-least-once delivery`.

Điều đó có nghĩa là một message có thể được giao nhiều lần trong các tình huống như:

- Consumer xử lý xong nhưng process dừng trước khi gửi ACK.
- Connection tới RabbitMQ bị ngắt sau khi handler hoàn tất.
- Consumer bị restart trong lúc message đang được xử lý.
- Publisher gửi lại outbox event chưa được đánh dấu published.

Vì vậy, consumer bắt buộc phải idempotent. Cùng một message được xử lý nhiều lần không được tạo ra side effect nghiệp vụ nhiều lần.

## 3. Message envelope chuẩn

Mọi event phải có envelope thống nhất:

```json
{
  "message_id": "uuid",
  "event_type": "JOB_SUBMITTED",
  "event_version": 1,
  "occurred_at": "2026-09-22T10:00:00Z",
  "correlation_id": "request-id-or-trace-id",
  "aggregate_type": "JOB",
  "aggregate_id": "job-uuid",
  "payload": {}
}
```

### 3.1 Các trường envelope

| Trường | Kiểu | Bắt buộc | Ý nghĩa |
|---|---|---:|---|
| `message_id` | UUID | Có | Định danh duy nhất của một message/event instance |
| `event_type` | String | Có | Loại nghiệp vụ, ví dụ `JOB_SUBMITTED` |
| `event_version` | Integer | Có | Phiên bản schema của event |
| `occurred_at` | ISO-8601 UTC | Có | Thời điểm event được tạo |
| `correlation_id` | String | Có | Liên kết với request hoặc trace ban đầu |
| `aggregate_type` | String | Có | Loại aggregate, ví dụ `JOB` |
| `aggregate_id` | UUID | Có | ID aggregate phát sinh event |
| `payload` | JSON object | Có | Dữ liệu cần cho consumer xử lý |

### 3.2 Quy tắc envelope

- `message_id` phải là UUID hợp lệ.
- `event_type` dùng chữ in hoa và dấu gạch dưới.
- `event_version` bắt đầu từ `1` và chỉ tăng khi có thay đổi không tương thích.
- `occurred_at` phải được ghi ở UTC với hậu tố `Z`.
- `correlation_id` không được chứa password, token hoặc secret.
- `payload` phải là JSON object, không phải array hoặc scalar.
- Message không được vượt quá kích thước tối đa đã cấu hình cho broker.

## 4. Event JOB_SUBMITTED

`JOB_SUBMITTED` được tạo khi một job được commit thành công vào PostgreSQL cùng với outbox event.

Routing đề xuất:

```text
exchange: atlasflow.events
routing key: job.submitted
queue: atlasflow.job.submitted
```

### 4.1 Ví dụ message đầy đủ

```json
{
  "message_id": "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee",
  "event_type": "JOB_SUBMITTED",
  "event_version": 1,
  "occurred_at": "2026-09-22T10:00:00Z",
  "correlation_id": "req-01J8ATLASFLOW000000000001",
  "aggregate_type": "JOB",
  "aggregate_id": "cccccccc-cccc-cccc-cccc-cccccccccccc",
  "payload": {
    "job_id": "cccccccc-cccc-cccc-cccc-cccccccccccc",
    "project_id": "88888888-8888-8888-8888-888888888888",
    "workflow_id": "99999999-9999-9999-9999-999999999999",
    "file_id": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
    "idempotency_key": "client-job-001"
  }
}
```

### 4.2 Payload JOB_SUBMITTED

| Trường | Kiểu | Bắt buộc | Ý nghĩa |
|---|---|---:|---|
| `job_id` | UUID | Có | Job cần được xử lý |
| `project_id` | UUID | Có | Project sở hữu job |
| `workflow_id` | UUID | Có | Workflow cần chạy |
| `file_id` | UUID | Có | File đầu vào của job |
| `idempotency_key` | String | Khuyến nghị | Khóa truy vết request submit |

Consumer không được tin payload thay cho database. Consumer phải dùng `job_id` để đọc lại trạng thái hiện tại từ PostgreSQL và kiểm tra các foreign key/domain rule cần thiết.

## 5. Event tương lai

Các event sau có thể được thêm ở những phase tiếp theo:

```text
JOB_QUEUED
JOB_STARTED
JOB_SUCCEEDED
JOB_FAILED
JOB_CANCELLED
JOB_EXECUTION_RETRY_REQUESTED
```

Mỗi event mới phải có:

- Tên event rõ ràng.
- Payload schema riêng.
- Version ban đầu là `1`.
- Routing key riêng hoặc quy ước routing đã được tài liệu hóa.
- Consumer ownership rõ ràng.
- Test happy path và failure path.

## 6. Versioning và compatibility

### 6.1 Thay đổi tương thích

Có thể giữ nguyên `event_version` khi:

- Thêm field optional.
- Thêm metadata không bắt buộc.
- Consumer cũ vẫn xử lý được message.

### 6.2 Thay đổi không tương thích

Phải tăng `event_version` khi:

- Đổi kiểu dữ liệu của field.
- Đổi tên hoặc xóa field bắt buộc.
- Thay đổi ý nghĩa nghiệp vụ của field.
- Đổi cấu trúc `payload`.

Ví dụ:

```text
JOB_SUBMITTED v1 → JOB_SUBMITTED v2
```

Consumer phải xử lý rõ event version không hỗ trợ:

```text
Không retry vô hạn
→ ghi failure reason
→ đưa message vào DLQ
```

Không được âm thầm coi version không biết là version hiện tại.

## 7. RabbitMQ message properties

Ngoài JSON body, publisher nên gửi các AMQP properties sau:

```text
content_type: application/json
content_encoding: UTF-8
delivery_mode: persistent
message_id: envelope.message_id
type: envelope.event_type
correlation_id: envelope.correlation_id
timestamp: envelope.occurred_at
```

Các properties không thay thế field trong envelope. Envelope là contract độc lập để message vẫn có đủ thông tin khi được lưu, replay hoặc kiểm tra ngoài RabbitMQ.

## 8. Outbox mapping

Một bản ghi trong `outbox_events` được map như sau:

| Database column | Message field |
|---|---|
| `id` | `message_id` |
| `event_type` | `event_type` |
| `aggregate_type` | `aggregate_type` |
| `aggregate_id` | `aggregate_id` |
| `payload` | `payload` |
| thời điểm tạo event | `occurred_at` |
| request/trace context | `correlation_id` |

Publisher chỉ được cập nhật `published_at` sau khi RabbitMQ publisher confirm thành công.

```text
INSERT jobs
INSERT outbox_events
COMMIT
    ↓
publish RabbitMQ
    ↓
publisher confirm
    ↓
UPDATE outbox_events SET published_at = ...
```

Nếu publish thất bại:

```text
published_at vẫn là NULL
event vẫn có thể được publish lại
```

## 9. Consumer acknowledgement contract

Consumer sử dụng manual acknowledgement.

### Xử lý thành công

```text
validate
→ idempotency check
→ domain processing
→ commit database changes
→ ACK
```

### Lỗi tạm thời

```text
NACK hoặc chuyển retry queue
→ chờ retry delay
→ giao lại có giới hạn
```

Ví dụ lỗi tạm thời:

- Database connection tạm thời thất bại.
- Lock timeout.
- Downstream dependency timeout.

### Lỗi không thể khắc phục bằng retry

```text
reject/requeue=false
→ DLQ
```

Ví dụ:

- JSON không hợp lệ.
- Thiếu `job_id`.
- Event version không hỗ trợ.
- Payload không đúng kiểu.
- Job không tồn tại sau khi đã xác minh database.

## 10. Consumer idempotency

Consumer phải kiểm tra duplicate trước khi tạo side effect.

Quy trình đề xuất:

```text
Nhận message
    ↓
Kiểm tra message_id đã processed chưa
    ├── Đã processed → ACK và kết thúc
    └── Chưa processed
          ↓
       Xử lý trong transaction
          ↓
       Lưu processed message/side effect
          ↓
       Commit
          ↓
       ACK
```

Các constraint hiện có như `UNIQUE(job_id, attempt)` phải tiếp tục được giữ để bảo vệ duplicate execution ở database. Không chỉ dựa vào biến trong memory của consumer.

Nếu lưu bảng processed message, nên có cấu trúc:

```sql
processed_messages(
    message_id UUID PRIMARY KEY,
    consumer_name VARCHAR(128) NOT NULL,
    aggregate_id UUID,
    processed_at TIMESTAMPTZ NOT NULL
)
```

## 11. Retry và DLQ metadata

Khi message được retry hoặc đưa vào DLQ, phải bảo toàn metadata:

```text
message_id
event_type
event_version
correlation_id
original_exchange
original_routing_key
delivery_attempts
failure_reason
failed_at
```

Không thay đổi `message_id` khi retry. Nếu tạo message mới để retry, phải giữ `original_message_id` để truy vết.

Retry phải có giới hạn. Policy ban đầu:

```text
attempt 1 → retry sau 5 giây
attempt 2 → retry sau 30 giây
attempt 3 → retry sau 2 phút
quá 3 lần → DLQ
```

Giá trị này là baseline để test local và có thể thay đổi sau khi đo latency/backlog.

## 12. Security và dữ liệu nhạy cảm

Không đưa các dữ liệu sau vào message body hoặc log message:

```text
password
password_hash
access token
refresh token
API key secret
secret_hash
database credentials
Redis credentials
file content nhạy cảm
```

Nếu worker cần file, message chỉ chứa `file_id`. Worker đọc metadata/quyền từ database hoặc storage port theo policy hiện hành.

RabbitMQ user nên có quyền tối thiểu:

- Publisher chỉ publish vào exchange cần thiết.
- Consumer chỉ consume queue cần thiết.
- DLQ access chỉ dành cho operator hoặc tooling vận hành.

## 13. Validation checklist

Publisher phải kiểm tra trước khi gửi:

- `message_id` không null và là UUID.
- `event_type` nằm trong danh sách hỗ trợ.
- `event_version` là số dương.
- `occurred_at` hợp lệ và ở UTC.
- `aggregate_type` không rỗng.
- `aggregate_id` không null.
- `payload` là JSON object.
- `correlation_id` không chứa control character.
- Payload không chứa field bí mật.

Consumer phải kiểm tra lại contract. Không được tin rằng chỉ publisher mới có thể tạo message hợp lệ.

## 14. Test contract bắt buộc

### Producer/outbox

- Tạo `JOB_SUBMITTED` với đủ envelope.
- `message_id` được tạo duy nhất.
- Payload chứa đúng job/project/workflow/file ID.
- Job và outbox commit cùng transaction.
- Broker down không làm mất pending outbox.
- Chỉ mark `published_at` sau publisher confirm.

### Consumer

- Message hợp lệ được deserialize và xử lý.
- Message thiếu field bắt buộc bị từ chối.
- Message sai version không retry vô hạn.
- Duplicate `message_id` không tạo duplicate side effect.
- Consumer ACK sau khi transaction xử lý thành công.
- Lỗi tạm thời được retry.
- Poison message đi vào DLQ.

### Compatibility

- Consumer xử lý được event version được hỗ trợ.
- Consumer từ chối version không được hỗ trợ theo policy.
- Thêm field optional không làm consumer cũ lỗi.

## 15. Evidence cần lưu

Tạo hoặc cập nhật `docs/phase-6-evidence.md` với:

- Ví dụ message JSON hợp lệ.
- Topology exchange/queue/routing key.
- Log publisher confirm.
- Log consumer ACK/NACK.
- Test duplicate delivery.
- Test retry và DLQ.
- Test broker down và outbox recovery.
- Metrics publish/consume/retry/DLQ.
- Failure timeline và root cause.

Mỗi kết luận phải trỏ được đến code, test, log hoặc metric tương ứng.

## 16. Definition of Done

Message contract được xem là hoàn thành khi:

- Có envelope versioned.
- Có `message_id` và `correlation_id`.
- Có event `JOB_SUBMITTED` được tài liệu hóa.
- Có payload schema rõ ràng.
- Có mapping giữa outbox và message.
- Publisher confirm được định nghĩa.
- Manual ACK/NACK được định nghĩa.
- Retry và DLQ metadata được định nghĩa.
- Consumer idempotency được bắt buộc.
- Không có secret trong message.
- Có contract test cho producer, consumer, duplicate và version compatibility.

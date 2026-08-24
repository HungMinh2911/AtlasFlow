# Error Catalog

## 1. Error response contract

Mọi lỗi HTTP dùng cùng một shape tối thiểu:

```json
{
  "code": "INVALID_STATE_TRANSITION",
  "message": "Job cannot move from SUCCEEDED to RUNNING",
  "request_id": "req_01H...",
  "details": {}
}
```

`code` là giá trị ổn định để client xử lý; `message` dành cho người đọc và không được
dùng làm logic. `details` chỉ chứa thông tin an toàn, không chứa password, secret,
stack trace, SQL, access token hoặc nội dung nhạy cảm trong `config_json`.

## 2. Catalog chuẩn Phase 1

| Code | HTTP | Ý nghĩa | Retry request? | Hành động của client |
|---|---:|---|---:|---|
| `VALIDATION_ERROR` | 400 | Body/field không hợp lệ | Không | Sửa input; đọc `details.field_errors` |
| `IDEMPOTENCY_KEY_REQUIRED` | 400 | Submit job thiếu key | Không | Gửi key ổn định |
| `INVALID_IDEMPOTENCY_KEY` | 400 | Key sai format/độ dài | Không | Dùng key theo rule contract |
| `PROJECT_NOT_FOUND` | 404 | Không tìm thấy project | Không | Kiểm tra identifier/quyền |
| `WORKFLOW_NOT_FOUND` | 404 | Không tìm thấy workflow | Không | Kiểm tra identifier/quyền |
| `JOB_NOT_FOUND` | 404 | Không tìm thấy job | Không | Kiểm tra identifier/quyền |
| `DUPLICATE_IDEMPOTENCY_KEY` | 409 | Key đã gắn với logical request khác | Không | Không đổi payload tùy tiện; dùng key mới cho request mới |
| `IDEMPOTENCY_KEY_REUSE` | 409 | Key và fingerprint không khớp | Không | Dùng key mới hoặc gửi lại đúng payload |
| `WORKFLOW_PROJECT_MISMATCH` | 409 | Workflow không thuộc project request | Không | Chọn cặp project/workflow đúng |
| `PROJECT_NOT_ACTIVE` | 409 | Project không nhận job mới | Có thể, sau khi active | Chờ trạng thái phù hợp rồi retry |
| `WORKFLOW_NOT_PUBLISHED` | 409 | Workflow chưa được publish | Không | Publish hoặc chọn version đã publish |
| `INVALID_STATE_TRANSITION` | 409 | Event không được phép từ state hiện tại | Không tự động | Đọc state mới nhất rồi quyết định |
| `JOB_NOT_CANCELLABLE` | 409 | Job terminal hoặc runtime chưa xác nhận cancel | Không tự động | Poll lại/hiển thị trạng thái thực tế |
| `RETRY_NOT_ALLOWED` | 409 | Job không phải FAILED hoặc đã hết retry | Không | Không retry tiếp |
| `DEPENDENCY_UNAVAILABLE` | 503 | Dependency local/runtime tạm không dùng được | Có backoff | Retry có giới hạn; không tạo duplicate |
| `DATABASE_UNAVAILABLE` | 503 | Không lấy được database connection | Có backoff | Retry request an toàn; kiểm tra health evidence |

`DUPLICATE_IDEMPOTENCY_KEY` và `IDEMPOTENCY_KEY_REUSE` cùng thuộc nhóm conflict. Dự án
có thể hợp nhất thành một code ở Phase triển khai, nhưng phải giữ một contract duy nhất
trong OpenAPI và test.

## 3. Quy tắc chọn status

- `400`: request sai trước khi xét trạng thái domain.
- `404`: resource không tồn tại hoặc không được phép lộ existence theo policy.
- `409`: resource có tồn tại nhưng request xung đột với state/invariant hiện tại.
- `503`: dependency tạm thời unavailable; không dùng để che giấu lỗi validation.
- Không dùng `200` cho lỗi nghiệp vụ.
- Submit bất đồng bộ thành công dùng `202`, không dùng `200` hay `201` cho việc worker đã hoàn tất.

## 4. Quy tắc retry

| Nhóm lỗi | Retry? | Điều kiện |
|---|---:|---|
| Validation/not found/conflict | Không tự động | Sửa request hoặc chờ state đúng |
| Dependency/database unavailable | Có thể | Exponential backoff, giới hạn số lần, giữ idempotency key |
| Client timeout sau submit | Có | Gửi lại cùng idempotency key; không dùng key mới |
| Worker execution failure | Theo job policy | Tạo attempt mới, không xóa execution cũ |

## 5. Security và observability

- `request_id` phải xuất hiện trong response lỗi và log tương ứng.
- Log error code, resource id đã mask nếu cần, state trước/sau và actor; không log secret.
- Message ngoài API không được chứa SQL, class name, stack trace hoặc credential.
- Audit log phải ghi các hành động submit/cancel/retry và kết quả, nhưng payload chỉ lưu phần đã được redaction.

## 6. Test cases bắt buộc

| ID | Input | Expected |
|---|---|---|
| ERR-01 | Submit thiếu `Idempotency-Key` | 400 `IDEMPOTENCY_KEY_REQUIRED` |
| ERR-02 | Cùng key, cùng payload | Cùng logical job; không tạo job mới |
| ERR-03 | Cùng key, khác payload | 409 `IDEMPOTENCY_KEY_REUSE` |
| ERR-04 | Workflow khác project | 409 `WORKFLOW_PROJECT_MISMATCH` |
| ERR-05 | `SUCCEEDED -> RUNNING` | 409 `INVALID_STATE_TRANSITION` |
| ERR-06 | Cancel job đã terminal | 409 `JOB_NOT_CANCELLABLE` |
| ERR-07 | Database không kết nối được | 503 `DATABASE_UNAVAILABLE`; không stack trace |
| ERR-08 | Body thiếu field bắt buộc | 400 `VALIDATION_ERROR` |

## 7. Liên kết

- Product contract: [`product-contract.md`](./product-contract.md)
- State machine: [`job-state-machine.md`](./job-state-machine.md)
- API draft: [`openapi.yaml`](./openapi.yaml)
- Failure lab: [`phase-1-failure-lab.md`](./phase-1-failure-lab.md)

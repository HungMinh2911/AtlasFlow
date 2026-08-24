# Use-case Matrix

## 1. Mục đích

Ma trận này nối `Actor -> Use case -> State machine -> Acceptance criteria`.
Mỗi use case phải có precondition, input, kết quả thành công, lỗi dự kiến và
bằng chứng kiểm thử. Đây là contract để triển khai ở các phase sau; Phase 1
chưa yêu cầu viết business code.

## 2. Actors

| Actor | Vai trò trong Phase 1 |
|---|---|
| Organization Owner | Tạo và quản lý project/workflow trong organization |
| Project Operator | Submit, theo dõi và yêu cầu xử lý job |
| API Client | Gọi HTTP API, thường dùng API key của project |
| Read-only Auditor | Xem trạng thái và audit evidence |
| Worker Runtime | Nhận job, chạy workflow và phát kết quả execution; chỉ là actor khái niệm |
| Platform Operator | Xử lý sự cố hạ tầng; không phải actor nghiệp vụ của API |

## 3. Ma trận use case

| ID | Actor | Preconditions | Input chính | Success | Lỗi/biên cần kiểm tra |
|---|---|---|---|---|---|
| UC-01 Create project | Owner | Organization hợp lệ; tên chưa trùng | `organization_id`, `name` | `201 Created`, project ở `ACTIVE` | Tên rỗng; organization không tồn tại; tên trùng |
| UC-02 Create workflow | Owner/Operator | Project `ACTIVE`; version hợp lệ | `project_id`, `version`, `steps[]` | `201 Created`, workflow ở `DRAFT` | Step rỗng; position trùng; project không tồn tại |
| UC-03 Publish workflow | Owner/Operator | Workflow `DRAFT`; config hợp lệ | `workflow_id` | Workflow immutable để submit | Publish lần hai; workflow thiếu step; sai project |
| UC-04 Submit job | API Client/Operator | Project active; workflow đã publish; key hợp lệ | `project_id`, `workflow_id`, `input`, `Idempotency-Key` | `202 Accepted`; job ở `SUBMITTED` | Key trùng; workflow khác project; body sai; dependency unavailable |
| UC-05 Get job status | API Client/Auditor | Có `job_id` hợp lệ và quyền xem | `job_id` | `200 OK`; state và execution summary | Job không tồn tại; id sai format |
| UC-06 Cancel job | API Client/Operator | Job chưa terminal; policy cho phép | `job_id`, lý do tùy chọn | `200 OK`; job `CANCELLED` | Đã terminal; đang chạy nhưng không có confirmation; race với success |
| UC-07 Retry failed job | API Client/Operator | Job `FAILED`; còn retry; policy cho phép | `job_id`, retry reason | `202 Accepted`; job quay lại `QUEUED` | Job chưa failed; hết retry; job cancelled |
| UC-08 Audit job action | Auditor/Platform Operator | Có `request_id` hoặc resource | resource/action filter | Audit record không chứa secret | Không được lộ password, secret key, config nhạy cảm |

## 4. Hợp đồng chi tiết cho use case quan trọng

### UC-04 Submit job

**Input**

- Header `Idempotency-Key`: bắt buộc, ổn định khi client retry cùng một logical request.
- Body: `project_id`, `workflow_id`, `input`.
- `workflow_id` phải thuộc `project_id` và workflow phải ở trạng thái publishable/published.

**Success**

- HTTP `202 Accepted`.
- Response có `job_id`, `status=SUBMITTED`, `created_at`, `request_id`.
- Không chờ worker chạy xong; client dùng `GET /jobs/{jobId}` để poll.

**Failure**

- Cùng key và cùng request fingerprint: trả lại cùng logical job, không tạo job mới.
- Cùng key nhưng khác fingerprint: `409 DUPLICATE_IDEMPOTENCY_KEY` hoặc `IDEMPOTENCY_KEY_REUSE`.
- Workflow không thuộc project: `409 WORKFLOW_PROJECT_MISMATCH`.
- Project/workflow không tồn tại: `404` với error code tương ứng.

### UC-05 Get job status

Response phải phân biệt rõ:

- `status`: state hiện tại của job.
- `attempt`: execution gần nhất hoặc danh sách execution nếu có.
- `started_at`, `finished_at`: có thể null khi job chưa chạy.
- `request_id`: trace cho request đọc.

Client không được suy luận `FAILED` chỉ vì request poll timeout. Timeout mạng là lỗi vận
hành của request, không phải transition của job.

### UC-06 Cancel job

Cancel chỉ thành công khi hệ thống có thể xác nhận state transition. Nếu job đang
`RUNNING` nhưng worker không hỗ trợ cancel an toàn, API phải từ chối rõ ràng thay vì
trả `CANCELLED` trong khi execution vẫn chạy.

### UC-07 Retry failed job

Retry tạo execution attempt mới và đưa job về `QUEUED`. Không reset lịch sử attempt,
không xóa lỗi cũ, không retry `SUCCEEDED` hoặc `CANCELLED`.

## 5. Mapping use case với API draft

| Use case | Endpoint draft | HTTP success |
|---|---|---:|
| UC-01 | `POST /projects` | 201 |
| UC-02 | `POST /workflows` | 201 |
| UC-04 | `POST /jobs` | 202 |
| UC-05 | `GET /jobs/{jobId}` | 200 |
| UC-06 | `POST /jobs/{jobId}/cancel` | 200 |
| UC-07 | `POST /jobs/{jobId}/retry` | 202 |

UC-03 publish workflow và UC-08 audit action là contract nghiệp vụ; route chi tiết
có thể được chốt ở phase API/auth sau.

## 6. Non-goals của Phase 1

- Không triển khai đăng nhập, JWT, API-key verification hoặc RBAC runtime.
- Không thiết kế schema migration và repository implementation.
- Không chọn message broker, worker framework, object storage hoặc cloud provider.
- Không cam kết SLA/throughput production; chỉ định nghĩa acceptance và failure evidence.

## 7. Acceptance checklist

- [x] Mỗi use case có actor, precondition, input, success và failure.
- [x] UC-04 chứng minh `202` là asynchronous acknowledgement.
- [x] Duplicate submit không tạo job thứ hai.
- [x] Invalid transition trả error có code ổn định.
- [x] Cancel mơ hồ không được báo thành công giả.
- [x] Không ghi secret/password vào response hoặc audit evidence.

## 8. Liên kết tài liệu

- Product contract: [`product-contract.md`](./product-contract.md)
- State machine: [`job-state-machine.md`](./job-state-machine.md)
- Error catalog: [`error-catalog.md`](./error-catalog.md)
- API draft: [`openapi.yaml`](./openapi.yaml)

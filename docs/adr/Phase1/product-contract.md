# AtlasFlow Product Contract

Product Contract mô tả AtlasFlow phải nhận input gì, tạo output gì, áp dụng
quy tắc nào và chưa làm những gì trong Phase 1.

Tài liệu này là contract giữa client, API và các thành phần xử lý phía sau.
Nó được viết trước khi triển khai business code và database schema production.

## 1. Objective

AtlasFlow cho phép một client:

1. Xác định project và workflow cần sử dụng.
2. Gửi yêu cầu chạy workflow dưới dạng một `Job`.
3. Nhận kết quả chấp nhận bất đồng bộ bằng `202 Accepted`.
4. Theo dõi trạng thái của job.
5. Nhận kết quả ổn định khi client gửi lại request do timeout hoặc network retry.

Phase 1 tập trung chốt domain contract, state machine, acceptance criteria và
API draft. Phase này chưa xây worker hoặc pipeline xử lý thật.

## 2. Domain language

Các thuật ngữ phải được hiểu thống nhất:

- `Organization`: tenant sở hữu dữ liệu và tài nguyên.
- `Project`: vùng tài nguyên thuộc một organization.
- `Workflow`: định nghĩa các bước cần chạy.
- `Workflow Step`: một bước trong workflow.
- `Job`: yêu cầu chạy một workflow.
- `Job Execution`: một lần thử thực thi job.
- `Attempt`: số thứ tự của một execution.
- `Idempotency Key`: key giúp request retry không tạo job trùng.
- `Terminal State`: trạng thái kết thúc, gồm `SUCCEEDED`, `FAILED` và
  `CANCELLED`.

Chi tiết thuật ngữ nằm trong
[`domain-glossary.md`](./domain-glossary.md).

## 3. Actors

### 3.1 Organization Owner

Người quản lý organization, project và thành viên trong phạm vi organization.

Use case chính:

- Xem organization.
- Tạo hoặc quản lý project.
- Quản lý membership và role ở các phase có security.

### 3.2 Project Operator

Người vận hành workflow và job trong một project đã được authorize.

Use case chính:

- Tạo workflow contract.
- Publish workflow version.
- Submit job.
- Theo dõi job.
- Yêu cầu cancel hoặc retry theo rule.

### 3.3 API Client

Ứng dụng hoặc hệ thống bên ngoài gọi AtlasFlow qua HTTP API và API key.

Use case chính:

- Submit job.
- Gửi `Idempotency-Key`.
- Poll trạng thái job.
- Xử lý response thành công hoặc lỗi.

### 3.4 Read-only Auditor

Người được phép xem trạng thái và audit evidence nhưng không thay đổi tài
nguyên.

### 3.5 Worker Runtime

Thành phần khái niệm nhận và xử lý execution. Worker thật chưa được triển khai
trong Phase 1; actor này chỉ giúp mô tả request/data flow.

### 3.6 Platform Operator

Người vận hành môi trường, dependency, log và failure lab. Platform Operator
không tự động có quyền truy cập dữ liệu của mọi organization.

## 4. In scope

Phase 1 phải chốt các nội dung sau:

- Domain glossary.
- Actor và use-case matrix.
- Workflow và job lifecycle.
- Job state machine.
- Input/output contract cho API.
- HTTP status semantics.
- Idempotency behavior.
- Error catalog.
- Acceptance criteria.
- Threat model ở mức contract.
- Failure scenarios và cách quan sát kết quả.

## 5. Out of scope

Chưa triển khai trong Phase 1:

- Authentication và authorization thật.
- Spring Security configuration.
- Database migration và business schema production.
- Repository hoặc transaction implementation.
- Worker thật.
- RabbitMQ hoặc message publisher thật.
- Redis.
- S3 hoặc file processing thật.
- AWS runtime.
- Kubernetes.
- Retry policy đầy đủ ở infrastructure.

Phase 1 có thể dùng ví dụ hoặc stub để minh họa contract, nhưng không được
coi stub là production implementation.

## 6. Actor-to-use-case contract

| Actor | Use case | Điều kiện trước | Kết quả mong đợi |
|---|---|---|---|
| Organization Owner | Create Project | Organization hợp lệ | Project được tạo |
| Project Operator | Create Workflow | Project tồn tại và đang active | Workflow draft được tạo |
| Project Operator | Publish Workflow | Workflow hợp lệ | Một version được publish |
| API Client | Submit Job | Project và workflow hợp lệ | Nhận `202` và `job_id` |
| API Client | Get Job Status | Có `job_id` | Nhận trạng thái hiện tại |
| Project Operator | Cancel Job | Job chưa ở terminal state | Job chuyển sang `CANCELLED` |
| Project Operator | Retry Job | Job retryable và đã failed | Tạo execution/attempt mới |
| Read-only Auditor | View Evidence | Có quyền đọc | Xem trạng thái và audit evidence |

Ma trận chi tiết nằm trong [`use-case-matrix.md`](./use-case-matrix.md).

## 7. Input contract

### 7.1 Submit Job input

Request tối thiểu:

```json
{
  "project_id": "project-001",
  "workflow_id": "workflow-001",
  "input": {
    "file_id": "file-001"
  }
}
```

Header bắt buộc:

```http
Idempotency-Key: request-abc-123
```

Quy tắc input:

- `project_id` là bắt buộc.
- `workflow_id` là bắt buộc.
- `workflow_id` phải thuộc `project_id`.
- Workflow phải ở trạng thái cho phép chạy, ví dụ `PUBLISHED`.
- `input` phải phù hợp với contract của workflow.
- `Idempotency-Key` là bắt buộc đối với operation tạo job.
- Không chấp nhận credential hoặc secret trong `input` nếu contract không cho
  phép rõ ràng.

### 7.2 Create Project input

```json
{
  "name": "Customer Import"
}
```

Quy tắc:

- `name` không được rỗng.
- `name` phải nằm trong giới hạn độ dài đã thống nhất.
- Tên trùng trong cùng organization phải có behavior rõ ràng: reject hoặc
  cho phép theo contract.

### 7.3 Create Workflow input

```json
{
  "project_id": "project-001",
  "version": 1,
  "steps": [
    {
      "position": 1,
      "task_type": "VALIDATE_FILE",
      "config": {}
    }
  ]
}
```

Quy tắc:

- `steps` phải có ít nhất một step nếu workflow không cho phép empty workflow.
- `position` không được trùng.
- `task_type` phải nằm trong catalog task được hỗ trợ.
- Secret không được lưu plaintext trong `config`.

## 8. Output contract

### 8.1 Submit thành công

API không chờ workflow chạy xong. API trả ngay:

```http
HTTP/1.1 202 Accepted
```

```json
{
  "job_id": "job-001",
  "project_id": "project-001",
  "workflow_id": "workflow-001",
  "status": "SUBMITTED",
  "created_at": "2026-08-23T10:00:00Z",
  "request_id": "req-001"
}
```

Lý do dùng `202 Accepted`:

- Request đã hợp lệ và được hệ thống tiếp nhận.
- Công việc còn được xử lý bất đồng bộ.
- Client không phải giữ HTTP connection tới khi workflow hoàn tất.
- Client dùng `job_id` để poll hoặc nhận event ở phase sau.

### 8.2 Get Job Status thành công

```http
HTTP/1.1 200 OK
```

```json
{
  "job_id": "job-001",
  "status": "RUNNING",
  "workflow_id": "workflow-001",
  "latest_attempt": 1,
  "created_at": "2026-08-23T10:00:00Z",
  "updated_at": "2026-08-23T10:00:05Z"
}
```

## 9. Job lifecycle contract

Trạng thái đề xuất:

```text
SUBMITTED
QUEUED
RUNNING
SUCCEEDED
FAILED
CANCELLED
```

Luồng hợp lệ tối thiểu:

```text
SUBMITTED -> QUEUED -> RUNNING -> SUCCEEDED
                              ├-> FAILED
                              └-> CANCELLED

FAILED -> QUEUED  (khi retry được chấp nhận)
QUEUED -> CANCELLED
```

Terminal states:

```text
SUCCEEDED
FAILED
CANCELLED
```

Không được tự ý thực hiện các transition sau:

```text
SUCCEEDED -> RUNNING
SUCCEEDED -> CANCELLED
CANCELLED -> RUNNING
CANCELLED -> SUCCEEDED
```

State machine chi tiết nằm trong [`job-state-machine.md`](./job-state-machine.md).

## 10. Idempotency contract

Client phải gửi key duy nhất cho mỗi operation tạo job trong phạm vi project.

### Cùng key, cùng request

Hệ thống trả lại job đã tạo trước đó, không tạo job mới.

```text
Request 1 + key abc -> job-001
Request 2 + key abc -> job-001
```

### Cùng key, request khác nội dung

Hệ thống trả lỗi conflict vì key đã được dùng cho request khác.

```http
HTTP/1.1 409 Conflict
```

### Key rỗng hoặc thiếu

Hệ thống trả lỗi validation.

```http
HTTP/1.1 400 Bad Request
```

Invariant database dự kiến:

```text
UNIQUE(project_id, idempotency_key)
```

## 11. HTTP status semantics

| Status | Ý nghĩa trong contract |
|---:|---|
| `200` | Đọc resource hoặc operation đồng bộ thành công |
| `201` | Tạo resource thành công và hoàn tất ngay |
| `202` | Đã nhận job, xử lý bất đồng bộ |
| `400` | Request thiếu field hoặc sai format |
| `401` | Chưa xác thực; triển khai ở phase Security |
| `403` | Đã xác thực nhưng không đủ quyền |
| `404` | Không tìm thấy resource hoặc không được expose existence |
| `409` | Conflict, duplicate key hoặc invalid state transition |
| `422` | Dữ liệu đúng format nhưng vi phạm rule nghiệp vụ |
| `503` | Dependency chưa sẵn sàng |

Response lỗi phải có format ổn định:

```json
{
  "code": "DUPLICATE_IDEMPOTENCY_KEY",
  "message": "The idempotency key has already been used.",
  "request_id": "req-001"
}
```

Không trả stack trace, password, token hoặc secret cho client.

## 12. Error contract

Các lỗi Phase 1 tối thiểu:

| Code | HTTP | Điều kiện | Retryable |
|---|---:|---|---|
| `VALIDATION_ERROR` | 400 | Thiếu hoặc sai input | No |
| `IDEMPOTENCY_KEY_REQUIRED` | 400 | Submit job thiếu idempotency key | No |
| `INVALID_IDEMPOTENCY_KEY` | 400 | Idempotency key sai format/độ dài | No |
| `PROJECT_NOT_FOUND` | 404 | Project không tồn tại | No |
| `WORKFLOW_NOT_FOUND` | 404 | Workflow không tồn tại | No |
| `JOB_NOT_FOUND` | 404 | Job không tồn tại | No |
| `WORKFLOW_PROJECT_MISMATCH` | 409 | Workflow không thuộc project | No |
| `DUPLICATE_IDEMPOTENCY_KEY` | 409 | Key đã được dùng cho request khác | No |
| `IDEMPOTENCY_KEY_REUSE` | 409 | Key được dùng lại với payload khác | No |
| `PROJECT_NOT_ACTIVE` | 409 | Project không nhận job mới | Yes, sau khi active |
| `WORKFLOW_NOT_PUBLISHED` | 409 | Workflow chưa được publish | No |
| `INVALID_STATE_TRANSITION` | 409 | Transition không hợp lệ | No |
| `JOB_NOT_CANCELLABLE` | 409 | Job đã ở terminal state | No |
| `RETRY_NOT_ALLOWED` | 409 | Job không retry được theo policy | No |
| `DEPENDENCY_UNAVAILABLE` | 503 | Dependency tạm thời không sẵn sàng | Yes, có backoff |
| `DATABASE_UNAVAILABLE` | 503 | Dependency chưa sẵn sàng | Yes |

Danh sách chi tiết nằm trong [`error-catalog.md`](./error-catalog.md).

## 13. Acceptance criteria

### AC-01: Submit job thành công

```text
Given project tồn tại
And workflow thuộc project
And workflow ở trạng thái PUBLISHED
And idempotency key chưa được dùng
When client gửi POST /jobs
Then API trả 202 Accepted
And response có job_id
And job có status SUBMITTED hoặc QUEUED
```

### AC-02: Submit lại cùng request

```text
Given request trước đã tạo job-001 với key abc
When client gửi lại cùng project và key abc
Then API không tạo job mới
And response tham chiếu job-001
```

### AC-03: Reuse key với request khác

```text
Given key abc đã tạo job-001
When client dùng key abc cho workflow khác
Then API trả 409 Conflict
And error code là DUPLICATE_IDEMPOTENCY_KEY
```

### AC-04: Workflow không thuộc project

```text
Given workflow thuộc project-A
When client submit job với project-B và workflow đó
Then API từ chối request
And không tạo job
```

### AC-05: Invalid state transition

```text
Given job đã ở trạng thái SUCCEEDED
When client yêu cầu cancel job
Then API trả 409 Conflict
And error code là INVALID_STATE_TRANSITION hoặc JOB_NOT_CANCELLABLE
```

### AC-06: Theo dõi trạng thái

```text
Given job_id tồn tại
When client gọi GET /jobs/{id}
Then API trả status hiện tại
And response có request_id hoặc correlation information
```

## 14. Failure scenarios

Phase 1 phải thực hành tối thiểu các failure sau:

### Failure 1: Duplicate submit

- Gửi hai request cùng `Idempotency-Key`.
- Kiểm tra chỉ có một job được tạo.
- Ghi request, response và log evidence.

### Failure 2: Invalid state transition

- Thử chuyển `SUCCEEDED` về `RUNNING`.
- Kỳ vọng nhận `409`.
- Kiểm tra trạng thái cũ không bị thay đổi.

### Failure 3: Ambiguous cancellation

- Gửi cancel khi job vừa chuyển sang terminal state.
- Hệ thống phải có rule deterministic: thành công nếu chưa terminal hoặc
  trả conflict nếu đã terminal.

### Failure 4: Workflow/project mismatch

- Gửi `project_id` của project A cùng `workflow_id` của project B.
- Request phải bị từ chối và không tạo job.

Mỗi failure card cần ghi:

```text
Impact
Input/command
Expected result
Actual result
Root cause
Recovery
Evidence
```

## 15. Security and trust boundary

Phase 1 chưa triển khai authentication thật nhưng phải mô tả threat model:

- Client không được truy cập project ngoài organization của mình.
- API key không được log hoặc lưu plaintext.
- `project_id`, `workflow_id` và resource id phải được kiểm tra ownership.
- Error response không được lộ stack trace hoặc thông tin credential.
- Input JSON không được phép chứa secret nếu contract không yêu cầu.
- Audit log phải ghi actor, action, resource và request id khi có thể.

Authorization thật sẽ được triển khai ở Phase 4.

## 16. Configuration contract

Phase 1 cần phân biệt các môi trường:

```text
local
test
staging
```

Quy tắc:

- Secret đi qua environment variable hoặc secret manager.
- Không commit password, API key hoặc token.
- Timeout phải được đặt tên và ghi rõ trong config khi có runtime dependency.
- Log không chứa credential.
- OpenAPI và README phải cho biết cách chạy local từ trạng thái rỗng.

## 17. API draft

API tối thiểu cần được mô tả trong `openapi.yaml`:

```http
POST /projects
POST /workflows
POST /jobs
GET /jobs/{id}
GET /workflows/{id}
POST /jobs/{id}/cancel
POST /jobs/{id}/retry
```

Phase 1 chỉ cần API draft và acceptance scenarios. Việc triển khai controller,
service, repository và database sẽ thực hiện ở Phase 2 và Phase 3.

## 18. Local-first evidence

Trước khi chuyển sang phase tiếp theo, cần có:

- Diagram request/data flow.
- Domain glossary.
- State machine.
- Use-case matrix.
- OpenAPI draft.
- Error catalog.
- Acceptance test scenarios.
- Ít nhất một failure card có evidence.
- Phase 1 evidence report và script có thể chạy lại.
- README command có thể chạy lại.
- Git commit mô tả kết quả Phase 1.

Phase 1 không cần AWS. AWS chỉ là bước kiểm chứng môi trường sau khi local
gate đã xanh.

## 19. Definition of Done

Phase 1 được coi là hoàn thành khi:

- Có thể giải thích rõ Workflow, Job và Job Execution khác nhau thế nào.
- Có product contract cho project, workflow và job.
- Có state machine với transition hợp lệ và không hợp lệ.
- Có quy tắc idempotency cho duplicate submit.
- Có quy tắc `202 Accepted` cho xử lý bất đồng bộ.
- Có error code và HTTP status nhất quán.
- Có acceptance criteria cho happy path và invalid path.
- Có ít nhất một failure lab đã tạo và khôi phục.
- Không thêm business schema production hoặc cloud runtime ngoài scope.
- Một người khác có thể đọc docs và hiểu hệ thống phải hoạt động như thế nào.

## 20. Câu hỏi kiểm tra mentor

Vì sao submit job trả `202 Accepted` thay vì chờ đến khi job chạy xong?

Câu trả lời phải chỉ ra được:

1. API chỉ tiếp nhận yêu cầu.
2. Job được xử lý bất đồng bộ.
3. Client dùng `job_id` để theo dõi trạng thái.
4. Giữ HTTP request ngắn giúp tránh timeout và giới hạn connection.
5. Kết quả thực thi thuộc về Job/Execution, không thuộc về request submit ban
   đầu.

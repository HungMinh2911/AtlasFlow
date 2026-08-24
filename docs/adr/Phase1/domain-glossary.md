# AtlasFlow Domain Glossary

Tài liệu này định nghĩa các thuật ngữ nghiệp vụ và quan hệ cốt lõi của
AtlasFlow trong Phase 1: Product Contract.

Mục tiêu là để mọi người hiểu cùng một ý nghĩa trước khi viết API, business
logic và database migration.

## Quy ước đọc tài liệu

- Tên thuật ngữ dùng dạng số ít: `User`, `Project`, `Workflow`, `Job`.
- `1 -> N` nghĩa là một bản ghi có thể liên quan tới nhiều bản ghi khác.
- `N <-> N` nghĩa là quan hệ nhiều-nhiều, thường cần một bảng nối.
- `PK` là primary key, định danh duy nhất của bản ghi.
- `FK` là foreign key, tham chiếu tới bản ghi ở bảng khác.
- Các giá trị status dưới đây là giá trị đề xuất; API contract và migration sẽ
  chốt chính thức ở các bước tiếp theo.

## 1. User

`User` là một tài khoản người dùng có thể đăng nhập hoặc gọi AtlasFlow thông
qua API.

Các thuộc tính chính:

- `id`: định danh duy nhất của user.
- `email`: địa chỉ dùng để nhận diện hoặc đăng nhập.
- `password_hash`: mật khẩu đã được băm; không lưu mật khẩu gốc.
- `status`: trạng thái tài khoản, ví dụ `ACTIVE`, `LOCKED`, `DISABLED`.
- `created_at`: thời điểm tạo tài khoản.

Quan hệ:

- Một `User` có thể thuộc nhiều `Organization` thông qua `Membership`.
- Một `User` có thể tạo nhiều `Audit Log`.
- Một `User` không trực tiếp sở hữu `Role`; role được xác định trong phạm vi
  organization thông qua membership.

## 2. Organization

`Organization` là tenant hoặc tổ chức sở hữu dữ liệu và tài nguyên trong
AtlasFlow.

Các thuộc tính chính:

- `id`: định danh organization.
- `name`: tên hiển thị của tổ chức.
- `created_at`: thời điểm tạo organization.

Quan hệ:

- Một `Organization` có nhiều `Membership`.
- Một `Organization` có nhiều `Role`.
- Một `Organization` có nhiều `Project`.
- Một `Organization` có nhiều `Audit Log`.

Ranh giới tenant:

> Một user chỉ được truy cập project, workflow, job và file thông qua
> organization mà user có membership hợp lệ.

## 3. Membership

`Membership` là bản ghi thể hiện một user là thành viên của một organization.
Đây là bảng nối giữa `User` và `Organization`.

Các thuộc tính chính:

- `user_id`: FK tới `users.id`.
- `organization_id`: FK tới `organizations.id`.
- `role_id`: FK tới `roles.id`.
- `status`: trạng thái membership, ví dụ `ACTIVE`, `SUSPENDED`, `REVOKED`.

Quan hệ:

- Một `Membership` thuộc đúng một `User`.
- Một `Membership` thuộc đúng một `Organization`.
- Một `Membership` tham chiếu một `Role`.
- Một `User` và một `Organization` có thể liên kết với nhau qua membership.

Quan hệ tổng quát:

```text
User N <-> N Organization
thông qua Membership
```

Invariant:

- Không có hai membership giống nhau cho cùng một `user_id` và
  `organization_id`.
- `role_id` phải thuộc cùng `organization_id` với membership.
- Membership bị `REVOKED` không được dùng để authorize request mới.

Constraint đề xuất:

```text
UNIQUE(user_id, organization_id)
```

## 4. Role

`Role` mô tả quyền của một thành viên trong phạm vi organization.

Các thuộc tính chính:

- `id`: định danh role.
- `organization_id`: FK tới `organizations.id`.
- `name`: tên role, ví dụ `ADMIN`, `MEMBER`, `VIEWER`.

Quan hệ:

- Một `Organization` có nhiều `Role`.
- Một `Role` có thể được dùng bởi nhiều `Membership`.

Invariant:

- Role chỉ có ý nghĩa trong organization chứa role đó.
- `ADMIN` của organization A không tự động có quyền trong organization B.

Constraint đề xuất:

```text
UNIQUE(organization_id, name)
```

## 5. Project

`Project` là vùng tài nguyên thuộc một organization. Workflow, job, API key
và file đều được quản lý trong project.

Các thuộc tính chính:

- `id`: định danh project.
- `organization_id`: FK tới `organizations.id`.
- `name`: tên project.
- `status`: ví dụ `ACTIVE`, `ARCHIVED`, `SUSPENDED`.

Quan hệ:

- Một `Project` thuộc đúng một `Organization`.
- Một `Project` có nhiều `Api Key`.
- Một `Project` có nhiều `Workflow`.
- Một `Project` có nhiều `Job`.
- Một `Project` có nhiều `File`.

Invariant:

- Project không được dùng nếu organization hoặc project đã bị suspend.
- Dữ liệu của project không được truy cập xuyên organization nếu không có
  authorization hợp lệ.

## 6. API Key

`Api Key` là credential để một client hoặc hệ thống bên ngoài gọi AtlasFlow.

Các thuộc tính chính:

- `id`: định danh key.
- `project_id`: FK tới `projects.id`.
- `prefix`: phần đầu dùng để nhận diện key.
- `secret_hash`: hash của secret; không lưu secret gốc.
- `last_used_at`: thời điểm key được sử dụng gần nhất.

Quan hệ:

- Một `Project` có thể có nhiều `Api Key`.
- Một `Api Key` chỉ thuộc một `Project`.

Quy tắc bảo mật:

- Secret chỉ hiển thị một lần khi tạo.
- Database chỉ lưu `secret_hash`.
- Không ghi secret vào log, exception hoặc audit log.
- Key bị revoke không được dùng để gọi API.

## 7. Workflow

`Workflow` là bản định nghĩa mô tả các bước cần thực hiện và thứ tự thực
hiện. Workflow không phải là một lần chạy cụ thể.

Các thuộc tính chính:

- `id`: định danh workflow.
- `project_id`: FK tới `projects.id`.
- `version`: phiên bản workflow.
- `status`: ví dụ `DRAFT`, `PUBLISHED`, `ARCHIVED`.

Quan hệ:

- Một `Project` có nhiều `Workflow`.
- Một `Workflow` có nhiều `Workflow Step`.
- Một `Workflow` có thể được tham chiếu bởi nhiều `Job`.

Phân biệt version:

```text
Workflow version 1: validate -> transform
Workflow version 2: validate -> transform -> publish
```

Workflow đã `PUBLISHED` nên được xem là immutable. Nếu muốn thay đổi, tạo
version mới thay vì sửa nội dung version đang được job sử dụng.

## 8. Workflow Step

`Workflow Step` là một bước cụ thể bên trong workflow.

Các thuộc tính chính:

- `id`: định danh step.
- `workflow_id`: FK tới `workflows.id`.
- `position`: thứ tự thực hiện.
- `task_type`: loại task, ví dụ `VALIDATE_FILE`, `TRANSFORM_DATA`.
- `config_json`: cấu hình của task.

Quan hệ:

- Một `Workflow` có nhiều `Workflow Step`.
- Một `Workflow Step` thuộc đúng một `Workflow`.

Invariant:

- Trong cùng một workflow, mỗi `position` chỉ xuất hiện một lần.
- `position` phải xác định được thứ tự thực hiện.
- `config_json` không được chứa secret plaintext.

Constraint đề xuất:

```text
UNIQUE(workflow_id, position)
```

## 9. Job

`Job` là một yêu cầu cụ thể để chạy một workflow.

Phân biệt:

```text
Workflow = định nghĩa cần làm gì
Job      = yêu cầu chạy workflow
Execution = một lần thử chạy job
```

Các thuộc tính chính:

- `id`: định danh job.
- `project_id`: FK tới `projects.id`.
- `workflow_id`: FK tới `workflows.id`.
- `status`: trạng thái hiện tại của job.
- `idempotency_key`: key chống tạo job trùng.
- `created_at`: thời điểm tạo job.

Trạng thái đề xuất:

```text
SUBMITTED
QUEUED
RUNNING
SUCCEEDED
FAILED
CANCELLED
```

Quan hệ:

- Một `Project` có nhiều `Job`.
- Một `Workflow` có thể được dùng bởi nhiều `Job`.
- Một `Job` có nhiều `Job Execution`.

Invariant:

- Job phải thuộc một project.
- Workflow phải thuộc cùng project với job.
- Job ở trạng thái terminal không được tự ý quay lại `RUNNING`.
- Một idempotency key trong cùng project không được tạo hai job khác nhau.

## 10. Job Execution

`Job Execution` là một lần thử thực thi cụ thể của job.

Các thuộc tính chính:

- `id`: định danh execution.
- `job_id`: FK tới `jobs.id`.
- `attempt`: số lần thử, bắt đầu từ `1`.
- `status`: trạng thái của lần thử.
- `worker_id`: worker thực hiện.
- `started_at`: thời điểm bắt đầu.
- `finished_at`: thời điểm kết thúc.

Quan hệ:

- Một `Job` có thể có nhiều `Job Execution` do retry.
- Một `Job Execution` thuộc đúng một `Job`.

Ví dụ:

```text
job-001 / attempt 1 / FAILED
job-001 / attempt 2 / FAILED
job-001 / attempt 3 / SUCCEEDED
```

Constraint đề xuất:

```text
UNIQUE(job_id, attempt)
```

## 11. Attempt

`Attempt` không phải là một bảng riêng. Đây là số lần thử được lưu trong
trường `job_executions.attempt`.

Ví dụ:

```text
attempt = 1: lần chạy đầu tiên
attempt = 2: retry lần thứ nhất
attempt = 3: retry lần thứ hai
```

Attempt giúp biết execution nào là lần chạy đầu tiên và execution nào được
tạo do retry.

## 12. Idempotency Key

`Idempotency Key` là một giá trị do client gửi để bảo đảm một request được xử
lý an toàn khi client retry.

Ví dụ:

```http
POST /jobs
Idempotency-Key: request-abc-123
```

Nếu request bị timeout và client gửi lại cùng key, hệ thống phải trả lại job
đã tạo trước đó thay vì tạo job mới.

Luồng đúng:

```text
Request 1 + key abc-123 -> tạo job-001
Request 2 + key abc-123 -> trả lại job-001
```

Luồng sai:

```text
Request 1 + key abc-123 -> tạo job-001
Request 2 + key abc-123 -> tạo job-002
```

Constraint đề xuất:

```text
UNIQUE(project_id, idempotency_key)
```

## 13. File

`File` lưu metadata của file thuộc project. Nội dung file thường nằm trong
object storage như S3 hoặc MinIO, không nằm trực tiếp trong database.

Các thuộc tính chính:

- `id`: định danh file.
- `project_id`: FK tới `projects.id`.
- `object_key`: đường dẫn file trong object storage.
- `checksum`: giá trị kiểm tra tính toàn vẹn.
- `size_bytes`: kích thước file.
- `status`: trạng thái lifecycle, ví dụ `UPLOADING`, `READY`, `DELETED`.

Quan hệ:

- Một `Project` có nhiều `File`.
- Một `File` thuộc đúng một `Project`.

Invariant:

- File `READY` phải có object tồn tại trong storage.
- Checksum phải được kiểm tra trước khi coi file là hợp lệ.
- Không cho phép file vượt quá giới hạn kích thước đã cấu hình.

## 14. Outbox Event

`Outbox Event` là bản ghi event được lưu trong database trước khi publish sang
message broker như RabbitMQ.

Các thuộc tính chính:

- `id`: định danh event.
- `aggregate_type`: loại đối tượng, ví dụ `JOB`, `WORKFLOW`, `FILE`.
- `aggregate_id`: định danh đối tượng phát sinh event.
- `event_type`: loại event, ví dụ `JOB_SUBMITTED`.
- `payload`: dữ liệu event.
- `published_at`: thời điểm event được publish; `null` nghĩa là chưa publish.

Ví dụ:

```text
aggregate_type: JOB
aggregate_id: job-001
event_type: JOB_SUBMITTED
published_at: null
```

Quan hệ của `aggregate_id` là polymorphic. Nghĩa là nó có thể tham chiếu tới
project, workflow, job hoặc file tùy theo `aggregate_type`; vì vậy không nên
coi nó là một FK trực tiếp tới duy nhất một bảng.

Mục đích:

```text
Transaction ghi Job
        + ghi Outbox Event
        ↓
Commit thành công
        ↓
Publisher đọc event và gửi message
```

Nhờ vậy tránh tình trạng database đã ghi job nhưng message bị mất.

## 15. Audit Log

`Audit Log` là lịch sử bất biến về các hành động quan trọng trong hệ thống.

Các thuộc tính chính:

- `id`: định danh log.
- `organization_id`: FK tới `organizations.id`.
- `actor_id`: FK tới `users.id`, người thực hiện hành động.
- `action`: hành động, ví dụ `CREATE_PROJECT`, `CANCEL_JOB`.
- `resource_type`: loại resource bị tác động.
- `resource_id`: định danh resource bị tác động.
- `request_id`: định danh request để truy vết.
- `created_at`: thời điểm xảy ra hành động.

Ví dụ:

```text
actor_id: user-001
action: CANCEL_JOB
resource_type: JOB
resource_id: job-001
request_id: req-abc-123
```

Quan hệ:

- Một `Organization` có nhiều `Audit Log`.
- Một `User` có thể tạo nhiều `Audit Log`.
- `resource_type` và `resource_id` tạo quan hệ polymorphic tới resource bị tác
  động.

Quy tắc:

- Audit log không nên bị sửa hoặc xóa tùy tiện.
- Không ghi password, API secret hoặc token vào audit log.
- Phải ghi đủ actor, action, resource và request id khi có thể.

## 16. Terminal State

`Terminal State` là trạng thái kết thúc của một job hoặc execution.

Các terminal state đề xuất:

```text
SUCCEEDED
FAILED
CANCELLED
```

Khi đã ở terminal state:

- Không được tự động quay lại `RUNNING`.
- Nếu muốn chạy lại, tạo execution mới hoặc tạo job retry theo rule đã định.
- Kết quả phải có thể truy vết trong audit log và execution history.

## Tổng quan quan hệ

```text
User N <-> N Organization
       thông qua Membership

Organization 1 -> N Project
Project      1 -> N Workflow
Workflow     1 -> N Workflow Step
Project      1 -> N Job
Workflow     1 -> N Job
Job          1 -> N Job Execution
Project      1 -> N File
Project      1 -> N Api Key
Organization 1 -> N Audit Log
User         1 -> N Audit Log
```

## Các invariant cấp hệ thống

- User chỉ truy cập dữ liệu trong organization mà user có membership hợp lệ.
- Role phải thuộc cùng organization với membership.
- Project phải thuộc organization đang được authorize.
- Workflow phải thuộc cùng project với job sử dụng workflow đó.
- Workflow đã publish không bị sửa trực tiếp; thay đổi tạo version mới.
- Một idempotency key không tạo hai job trong cùng project.
- Một job có thể có nhiều execution nhưng mỗi attempt chỉ xuất hiện một lần.
- Terminal state không được chuyển ngược tùy ý.
- API secret chỉ lưu dưới dạng hash.
- Outbox event phải được ghi cùng transaction với thay đổi nghiệp vụ liên quan.
- Audit log không chứa credential và phải có khả năng truy vết hành động.



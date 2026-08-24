# Phase 1 Failure Lab

## 1. Mục đích

Đây là workbook thực hành theo đúng yêu cầu Phase 1: chạy từ trạng thái rỗng,
kiểm tra happy path, boundary path và ba lỗi có chủ đích. Lab có thể thực hiện
bằng bảng giấy, test in-memory hoặc gọi API draft; chưa cần database migration.

## 2. Chuẩn bị

- Có một organization, một project `ACTIVE` và một workflow `PUBLISHED`.
- Có một `Idempotency-Key` mới, ví dụ `lab-submit-001`.
- Ghi mọi lần thử với `request_id`, input, state trước/sau, HTTP status và error code.
- Không ghi password, API secret hoặc toàn bộ payload nhạy cảm vào evidence.

Có thể chạy contract lab in-memory bằng lệnh sau từ repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\phase1-contract-check.ps1
```

Evidence của lần chạy được ghi tại [`phase-1-evidence.md`](./phase-1-evidence.md).

## 3. Happy path

| Bước | Hành động | Kết quả mong đợi |
|---:|---|---|
| 1 | Tạo project | `201`, project `ACTIVE` |
| 2 | Tạo workflow draft có ít nhất một step | `201`, workflow `DRAFT` |
| 3 | Publish workflow | Workflow `PUBLISHED`, version không sửa trực tiếp |
| 4 | Submit job với key mới | `202`, job `SUBMITTED` |
| 5 | Enqueue | `SUBMITTED -> QUEUED` |
| 6 | Worker claim | `QUEUED -> RUNNING`, tạo attempt 1 |
| 7 | Cho execution thành công | `RUNNING -> SUCCEEDED` |
| 8 | Poll job | `200`, state terminal; không chạy lại |

## 4. Failure A — duplicate submit

**Mục tiêu:** chứng minh retry của client không tạo job thứ hai.

1. Gửi `POST /jobs` với key `lab-submit-dup` và payload P.
2. Giả lập client mất response sau khi server đã tạo job.
3. Gửi lại chính payload P với cùng key.
4. Kiểm tra `job_id` trả về giống lần đầu và số logical job vẫn là một.
5. Gửi payload Q khác với cùng key; mong đợi `409 IDEMPOTENCY_KEY_REUSE`.

Evidence tối thiểu:

```text
key=lab-submit-dup
first_job_id=<id>
retry_job_id=<id>
same_logical_job=true
different_payload_error=409 IDEMPOTENCY_KEY_REUSE
```

## 5. Failure B — invalid state transition

**Mục tiêu:** terminal state không bị quay ngược.

1. Đưa job tới `SUCCEEDED`.
2. Thử event `WORKER_CLAIMED` hoặc `RETRY_APPROVED` không theo policy.
3. Mong đợi `409 INVALID_STATE_TRANSITION` hoặc lỗi policy tương ứng.
4. Kiểm tra state vẫn là `SUCCEEDED`, không tạo execution mới.

Evidence tối thiểu:

```text
before=SUCCEEDED
event=WORKER_CLAIMED
http_status=409
error_code=INVALID_STATE_TRANSITION
after=SUCCEEDED
new_execution_created=false
```

## 6. Failure C — ambiguous cancellation

**Mục tiêu:** không trả `CANCELLED` nếu runtime chưa xác nhận đã dừng.

1. Đưa job tới `RUNNING`.
2. Gọi cancel trong lúc worker không có cơ chế confirmation.
3. Mong đợi `409 JOB_NOT_CANCELLABLE` hoặc trạng thái policy tương đương.
4. Poll lại job; không được tự suy luận từ timeout của request.
5. Nếu runtime xác nhận cancel, chỉ khi đó mới ghi `RUNNING -> CANCELLED`.

Evidence tối thiểu:

```text
before=RUNNING
runtime_cancel_confirmation=false
http_status=409
error_code=JOB_NOT_CANCELLABLE
after=RUNNING
```

## 7. Boundary cases

| Case | Expected |
|---|---|
| Thiếu `Idempotency-Key` | 400 `IDEMPOTENCY_KEY_REQUIRED` |
| Key dài hơn giới hạn contract | 400 `INVALID_IDEMPOTENCY_KEY` |
| Workflow thuộc project khác | 409 `WORKFLOW_PROJECT_MISMATCH` |
| Workflow chưa publish | 409 `WORKFLOW_NOT_PUBLISHED` |
| Body thiếu `workflow_id` | 400 `VALIDATION_ERROR` |
| Poll job không tồn tại | 404 `JOB_NOT_FOUND` |
| Database unavailable | 503 `DATABASE_UNAVAILABLE`; không lộ stack trace |

## 8. Kết luận lab

Phase 1 được coi là đạt khi có evidence cho:

- Một happy path hoàn chỉnh từ submit đến terminal state.
- Duplicate submit trả cùng logical job.
- Invalid transition bị chặn và state không đổi.
- Cancellation mơ hồ bị từ chối, không báo thành công giả.
- Các error code và HTTP status khớp [`error-catalog.md`](./error-catalog.md).

## 9. Liên kết

- State rules: [`job-state-machine.md`](./job-state-machine.md)
- Use cases: [`use-case-matrix.md`](./use-case-matrix.md)
- API draft: [`openapi.yaml`](./openapi.yaml)
- Flow diagram: [`phase-1-flow.mmd`](./phase-1-flow.mmd)

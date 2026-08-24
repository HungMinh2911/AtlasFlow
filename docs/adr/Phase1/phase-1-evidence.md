# Phase 1 Evidence

## 1. Thông tin lần chạy

| Field | Value |
|---|---|
| Date | 2026-08-24 |
| Environment | Windows PowerShell, local workspace |
| Lab type | In-memory contract lab; không tạo production schema |
| Command | `powershell -NoProfile -ExecutionPolicy Bypass -File .\\scripts\\phase1-contract-check.ps1` |

## 2. Contract check output

```text
[PASS] valid submit -> HTTP 202, SUBMITTED, job-001
[PASS] duplicate submit -> same logical job job-001
[PASS] same key with different payload -> IDEMPOTENCY_KEY_REUSE
[PASS] ambiguous cancellation while RUNNING -> JOB_NOT_CANCELLABLE
[PASS] invalid transition SUCCEEDED to RUNNING -> INVALID_STATE_TRANSITION
[PASS] terminal state remains SUCCEEDED
PHASE1_CONTRACT_CHECK=PASS
```

## 3. Ý nghĩa evidence

### Happy path

- Submit mới tạo đúng một job ở `SUBMITTED`.
- HTTP status là `202`, thể hiện server đã nhận request bất đồng bộ.

### Duplicate submit

- Cùng `Idempotency-Key` và cùng fingerprint trả lại `job-001`.
- Không tạo logical job thứ hai.
- Cùng key nhưng payload khác bị từ chối bằng `IDEMPOTENCY_KEY_REUSE`.

### Invalid transition

- Job đã ở `SUCCEEDED` không thể quay lại `RUNNING`.
- Error code là `INVALID_STATE_TRANSITION`.
- Terminal state vẫn giữ nguyên sau request lỗi.

### Ambiguous cancellation

- Job đang `RUNNING` nhưng chưa có confirmation từ runtime không được đánh dấu `CANCELLED`.
- Error code là `JOB_NOT_CANCELLABLE`.

## 4. Backend regression check

Command:

```powershell
cd backend
.\mvnw.cmd test
```

Result lần kiểm tra ngày 2026-08-24:

```text
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Các test hiện tại xác nhận Spring Boot context, database controller contract ở Phase 0
và ping controller. Business API của Phase 2 chưa được đưa vào evidence này.

## 5. OpenAPI syntax check

Command:

```powershell
python -c "import yaml; yaml.safe_load(open('docs/adr/Phase1/openapi.yaml', encoding='utf-8')); print('OpenAPI YAML: valid')"
```

Result:

```text
OpenAPI YAML: valid
```

## 6. Giới hạn evidence

Evidence này chứng minh contract và state rules ở local in-memory, đúng phạm vi Phase 1.
Nó chưa chứng minh persistence, worker thật, queue, authentication hoặc production runtime.
Các nội dung đó thuộc Phase 2 và Phase 3.

## 7. Liên kết

- Lab instructions: [`phase-1-failure-lab.md`](./phase-1-failure-lab.md)
- State machine: [`job-state-machine.md`](./job-state-machine.md)
- Product contract: [`product-contract.md`](./product-contract.md)
- Re-runnable script: [`phase1-contract-check.ps1`](../../../scripts/phase1-contract-check.ps1)

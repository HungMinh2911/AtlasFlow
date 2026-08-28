# AtlasFlow Phase 2 Evidence

## Scope

Phase 2 is the local Spring Boot backend core. It uses a modular monolith and
in-memory repositories. RabbitMQ, Outbox, distributed workers and AWS are out
of scope for this phase.

## Vertical slice

```text
HTTP request
    -> Controller
    -> Application Service
    -> Domain rule
    -> Repository port
    -> In-memory adapter
```

Implemented modules:

- `project`: create, get one and list projects.
- `workflow`: create a workflow and get workflow details with steps.
- `file`: local upload and file metadata validation.
- `job`: create a Job, queue it, create a local JobExecution and finish it.

## Job state evidence

```text
SUBMITTED -> QUEUED -> RUNNING -> SUCCEEDED
                              -> FAILED
                              -> CANCELLED
```

The local phase-2 simulation uses:

```text
POST /api/v1/jobs
POST /api/v1/jobs/{jobId}/queue
POST /api/v1/job-executions
PATCH /api/v1/job-executions/{executionId}/finish
```

The execution endpoint is a local simulation. A RabbitMQ consumer replaces it
in Phase 6-7.

## API surface

```text
POST /api/v1/projects
GET  /api/v1/projects
GET  /api/v1/projects/{id}
POST /api/v1/workflows
GET  /api/v1/workflows/{workflowId}
POST /api/v1/workflows/{workflowId}/steps
GET  /api/v1/workflows/{workflowId}/steps
POST /api/v1/jobs
```

## Error and request identity

- Bean validation returns HTTP 400 with `VALIDATION_ERROR`.
- Missing resources return HTTP 404 with a module-specific error code.
- Invalid business state returns HTTP 400.
- Unexpected errors do not expose stack traces or secrets.
- Every response has an `X-Request-Id`; a caller-provided value is preserved.
- Basic security headers are added by `RequestIdFilter`.

## Configuration and observability

- Default profile: `local`.
- Test profile: `test`.
- Database and storage values can be supplied through environment variables.
- Actuator exposes `health`, `info` and `metrics`.
- Spring MVC metrics can be inspected through `/actuator/metrics`.

## Test evidence

Run from `backend`:

```powershell
.\mvnw.cmd test
```

Latest result:

```text
Tests run: 31
Failures: 0
Errors: 0
BUILD SUCCESS
```

Coverage includes domain rules, in-memory repositories, application services,
controller validation, request ID propagation and failure cases.

## Phase 2 gate

The backend runs locally with no RabbitMQ or AWS dependency. The next phase is
PostgreSQL persistence. RabbitMQ and Outbox belong to Phase 6, and the real
worker process belongs to Phase 7.

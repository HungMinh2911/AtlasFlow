# Phase 6 Evidence - Messaging and Outbox

## Scope

Phase 6 verifies the `JOB_SUBMITTED` event path:

```text
POST /api/v1/jobs
  -> jobs + outbox_events (one database transaction)
  -> OutboxPublisher
  -> RabbitMQ atlasflow.events
  -> atlasflow.job.submitted
  -> JobSubmittedConsumer
  -> JobExecution + WorkflowWorker
  -> ACK or retry/DLQ
```

## Required services

Start PostgreSQL, RabbitMQ and Redis:

```powershell
docker compose -f docker/compose.yml up -d
```

Start the backend:

```powershell
cd backend
./mvnw.cmd spring-boot:run
```

RabbitMQ Management UI: `http://localhost:15672`.

## Contract checklist

- `message_id` is a UUID and is also sent as the AMQP `message_id` property.
- `event_type` is `JOB_SUBMITTED`.
- `event_version` is `1`.
- `occurred_at` is copied from the outbox record.
- `correlation_id` is copied from the outbox record.
- `aggregate_type` is `JOB`.
- `aggregate_id` is the job ID.
- Payload contains job, project, workflow and file IDs.

## Manual verification scenarios

### 1. Successful workflow

Submit a job using a workflow whose steps can complete successfully. Verify:

```text
outbox_events.published_at IS NOT NULL
jobs.status = 'SUCCEEDED'
job_executions.status = 'SUCCEEDED'
atlasflow.job.submitted has 0 ready messages
```

### 2. HTTP transient failure

Use an `HTTP_REQUEST` step pointing to an unavailable local service. Verify:

```text
first failure -> retry.5s
second failure -> retry.30s
third failure -> retry.2m
next failure -> atlasflow.job.submitted.dlq
```

The job execution is failed and the job is re-queued before a retry delivery.

### 3. Duplicate delivery

Deliver the same message ID twice. Verify that the second delivery is ACKed without creating another business side effect. Check:

```sql
SELECT message_id, consumer_name, processed_at
FROM processed_messages;
```

There must be one row for the message ID.

### 4. Invalid contract

Publish a message with an unsupported `event_version` or missing required field. Verify that the consumer rejects it without retrying and RabbitMQ places it in the DLQ.

## Evidence to attach before closing Phase 6

- Backend startup log showing one RabbitMQ consumer.
- Outbox row before and after publisher confirm.
- RabbitMQ queue/exchange screenshot.
- Successful job and execution query result.
- Retry queue/DLQ query or screenshot.
- `processed_messages` duplicate-delivery result.
- Automated test output from `./mvnw.cmd test`.

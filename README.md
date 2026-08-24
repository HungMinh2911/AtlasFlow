# AtlasFlow

Spring Boot backend and local infrastructure for the AtlasFlow learning project.

## Phase 0 local run

Create local configuration:

```powershell
Copy-Item .env.example .env
```

Start PostgreSQL from the repository root:

```powershell
docker compose --env-file .env -f docker/compose.yml up -d
docker compose --env-file .env -f docker/compose.yml ps
```

Run the backend:

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Check the endpoints:

```powershell
curl.exe http://localhost:8080/api/v1/ping
curl.exe http://localhost:8080/actuator/health
curl.exe http://localhost:8080/api/v1/db-test
```

Expected database result:

```text
Database OK: 1
```

The `.env` file is local-only and must not be committed. Do not add business
schema, authentication, workers, Redis or RabbitMQ during Phase 0.

## Phase 1 contract check

Run the in-memory Product Contract lab from the repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\phase1-contract-check.ps1
```

The lab checks the `202 Accepted` submit contract, idempotent duplicate submit,
invalid state transition and ambiguous cancellation. Evidence is recorded in
`docs/adr/Phase1/phase-1-evidence.md`.

Validate the OpenAPI draft:

```powershell
python -c "import yaml; yaml.safe_load(open('docs/adr/Phase1/openapi.yaml', encoding='utf-8')); print('OpenAPI YAML: valid')"
```

Phase 1 is contract-only. Project/workflow/job business APIs and persistence are
implemented in Phase 2 and later.

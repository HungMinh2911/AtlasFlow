# ============================================================
# ATLASFLOW - DATABASE RESTORE SCRIPT (Phase 3 - Giai đoạn 14)
# ============================================================
Param(
    [Parameter(Mandatory=$true)]
    [string]$BackupFile,
    [string]$ContainerName = "docker-db-1",
    [string]$TargetDbName = "atlasflow_restore_test",
    [string]$DbUser = "atlasflow"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $BackupFile)) {
    Write-Host "[ERROR] File backup '$BackupFile' không tồn tại!" -ForegroundColor Red
    exit 1
}

Write-Host "[RESTORE] Bắt đầu restore '$BackupFile' vào database mới '$TargetDbName'..." -ForegroundColor Cyan

# 1. Tạo Database mới (nếu chưa có) để tránh restore đè lên DB chính đang dùng
docker exec -i $ContainerName psql -U $DbUser -d postgres -c "DROP DATABASE IF EXISTS $TargetDbName;" | Out-Null
docker exec -i $ContainerName psql -U $DbUser -d postgres -c "CREATE DATABASE $TargetDbName;" | Out-Null

# 2. Restore file dump vào Database target
cmd /c "docker exec -i $ContainerName pg_restore -U $DbUser -d $TargetDbName --no-owner --role=$DbUser < $BackupFile 2>NUL"

Write-Host "[SUCCESS] Restore thành công vào database '$TargetDbName'!" -ForegroundColor Green

# 3. Thống kê số lượng bản ghi sau restore
Write-Host "`n--- DỮ LIỆU SAU RESTORE ($TargetDbName) ---" -ForegroundColor Yellow
$statsSql = "
SELECT 'users' AS table_name, COUNT(*) AS count FROM users
UNION ALL SELECT 'projects', COUNT(*) FROM projects
UNION ALL SELECT 'workflows', COUNT(*) FROM workflows
UNION ALL SELECT 'jobs', COUNT(*) FROM jobs
UNION ALL SELECT 'outbox_events', COUNT(*) FROM outbox_events;
"
docker exec -i $ContainerName psql -U $DbUser -d $TargetDbName -c $statsSql

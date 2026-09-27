# ============================================================
# ATLASFLOW - DATABASE BACKUP SCRIPT (Phase 3 - Giai đoạn 14)
# ============================================================
Param(
    [string]$ContainerName = "docker-db-1",
    [string]$DbName = "atlasflow",
    [string]$DbUser = "atlasflow",
    [string]$BackupDir = "backups"
)

$ErrorActionPreference = "Stop"

# Tạo thư mục backups nếu chưa có
if (-not (Test-Path $BackupDir)) {
    New-Item -ItemType Directory -Path $BackupDir | Out-Null
}

$timestamp = Get-Date -Format "yyyy-MM-dd-HHmm"
$filename = "atlasflow-$timestamp.dump"
$backupPath = Join-Path $BackupDir $filename

Write-Host "[BACKUP] Bắt đầu backup database '$DbName' từ container '$ContainerName'..." -ForegroundColor Cyan

# Thực hiện pg_dump trong container với format Custom (-F c)
docker exec -i $ContainerName pg_dump -U $DbUser -d $DbName -F c > $backupPath

if (Test-Path $backupPath) {
    $size = (Get-Item $backupPath).Length
    Write-Host "[SUCCESS] Backup thành công!" -ForegroundColor Green
    Write-Host "File backup: $backupPath ($size bytes)" -ForegroundColor Yellow
} else {
    Write-Host "[ERROR] Backup thất bại!" -ForegroundColor Red
}

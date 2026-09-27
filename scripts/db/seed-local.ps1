param(
    [string]$EnvFile = ".env",
    [string]$ComposeFile = "docker/compose.yml",
    [string]$DbService = "db",
    [string]$DbUser = "${env:DB_USERNAME}",
    [string]$DbName = "${env:DB_NAME}"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $EnvFile)) {
    throw "Không tìm thấy $EnvFile. Hãy tạo file env local theo cấu hình docker/compose.yml trước khi seed."
}

if ([string]::IsNullOrWhiteSpace($DbUser)) {
    $DbUser = "atlasflow"
}

if ([string]::IsNullOrWhiteSpace($DbName)) {
    $DbName = "atlasflow"
}

$seedFile = Join-Path $PSScriptRoot "seed-local.sql"

if (-not (Test-Path -LiteralPath $seedFile)) {
    throw "Không tìm thấy seed file: $seedFile"
}

Write-Host "Đang seed dữ liệu local vào database '$DbName' qua service '$DbService'..."

Get-Content -Raw -LiteralPath $seedFile |
    docker compose --env-file $EnvFile -f $ComposeFile exec -T $DbService psql `
        -U $DbUser `
        -d $DbName `
        -v ON_ERROR_STOP=1 `
        -f -

if ($LASTEXITCODE -ne 0) {
    throw "Seed thất bại. Kiểm tra PostgreSQL đã chạy và migration đã hoàn tất chưa."
}

Write-Host "Seed thành công. Có thể đăng nhập bằng owner@atlasflow.local / password trong môi trường local." -ForegroundColor Green

$ErrorActionPreference = 'Stop'

# Phase 1 lab only: this is an in-memory contract check, not production code.
# It proves the documented submit/idempotency/state/cancellation rules without a database.

$jobs = @{}
$idempotency = @{}

function Throw-ContractError {
    param([Parameter(Mandatory)][string]$Code)
    throw [System.InvalidOperationException]::new($Code)
}

function Submit-ContractJob {
    param(
        [Parameter(Mandatory)][string]$IdempotencyKey,
        [Parameter(Mandatory)][string]$Fingerprint
    )

    if ([string]::IsNullOrWhiteSpace($IdempotencyKey)) {
        Throw-ContractError 'IDEMPOTENCY_KEY_REQUIRED'
    }

    if ($idempotency.ContainsKey($IdempotencyKey)) {
        $existing = $idempotency[$IdempotencyKey]
        if ($existing.Fingerprint -eq $Fingerprint) {
            return [pscustomobject]@{
                JobId = $existing.Id
                Status = $existing.Status
                HttpStatus = 202
                Replayed = $true
            }
        }

        Throw-ContractError 'IDEMPOTENCY_KEY_REUSE'
    }

    $jobId = 'job-{0:D3}' -f ($jobs.Count + 1)
    $job = [pscustomobject]@{
        Id = $jobId
        Status = 'SUBMITTED'
        Fingerprint = $Fingerprint
    }
    $jobs[$jobId] = $job
    $idempotency[$IdempotencyKey] = $job

    return [pscustomobject]@{
        JobId = $job.Id
        Status = $job.Status
        HttpStatus = 202
        Replayed = $false
    }
}

function Move-ContractJob {
    param(
        [Parameter(Mandatory)][string]$JobId,
        [Parameter(Mandatory)][string]$To,
        [switch]$CancellationConfirmed
    )

    if (-not $jobs.ContainsKey($JobId)) {
        Throw-ContractError 'JOB_NOT_FOUND'
    }

    $job = $jobs[$JobId]
    $from = $job.Status
    $allowed = @{
        'SUBMITTED|QUEUED' = $true
        'QUEUED|RUNNING' = $true
        'RUNNING|SUCCEEDED' = $true
        'RUNNING|FAILED' = $true
        'FAILED|QUEUED' = $true
        'SUBMITTED|CANCELLED' = $true
        'QUEUED|CANCELLED' = $true
    }

    if ($To -eq 'CANCELLED' -and $from -eq 'RUNNING' -and -not $CancellationConfirmed) {
        Throw-ContractError 'JOB_NOT_CANCELLABLE'
    }

    if ($To -eq 'CANCELLED' -and $from -eq 'RUNNING' -and $CancellationConfirmed) {
        $job.Status = $To
        return $job
    }

    if (-not $allowed.ContainsKey("$from|$To")) {
        Throw-ContractError 'INVALID_STATE_TRANSITION'
    }

    $job.Status = $To
    return $job
}

function Assert-ContractError {
    param(
        [Parameter(Mandatory)][scriptblock]$Action,
        [Parameter(Mandatory)][string]$ExpectedCode,
        [Parameter(Mandatory)][string]$Scenario
    )

    try {
        & $Action | Out-Null
        throw "Expected $ExpectedCode but action succeeded"
    }
    catch {
        if ($_.Exception.Message -ne $ExpectedCode) {
            throw "${Scenario}: expected $ExpectedCode, got $($_.Exception.Message)"
        }
        Write-Output "[PASS] $Scenario -> $ExpectedCode"
    }
}

$first = Submit-ContractJob -IdempotencyKey 'lab-submit-001' -Fingerprint 'payload-A'
Write-Output "[PASS] valid submit -> HTTP $($first.HttpStatus), $($first.Status), $($first.JobId)"

$replay = Submit-ContractJob -IdempotencyKey 'lab-submit-001' -Fingerprint 'payload-A'
if ($replay.JobId -ne $first.JobId -or -not $replay.Replayed) {
    throw 'Duplicate submit created a different logical job'
}
Write-Output "[PASS] duplicate submit -> same logical job $($replay.JobId)"

Assert-ContractError `
    -Scenario 'same key with different payload' `
    -ExpectedCode 'IDEMPOTENCY_KEY_REUSE' `
    -Action { Submit-ContractJob -IdempotencyKey 'lab-submit-001' -Fingerprint 'payload-B' }

$job = Move-ContractJob -JobId $first.JobId -To 'QUEUED'
$job = Move-ContractJob -JobId $first.JobId -To 'RUNNING'
Assert-ContractError `
    -Scenario 'ambiguous cancellation while RUNNING' `
    -ExpectedCode 'JOB_NOT_CANCELLABLE' `
    -Action { Move-ContractJob -JobId $first.JobId -To 'CANCELLED' }

$job = Move-ContractJob -JobId $first.JobId -To 'SUCCEEDED'
Assert-ContractError `
    -Scenario 'invalid transition SUCCEEDED to RUNNING' `
    -ExpectedCode 'INVALID_STATE_TRANSITION' `
    -Action { Move-ContractJob -JobId $first.JobId -To 'RUNNING' }

if ($job.Status -ne 'SUCCEEDED') {
    throw "Terminal job changed unexpectedly: $($job.Status)"
}
Write-Output '[PASS] terminal state remains SUCCEEDED'
Write-Output 'PHASE1_CONTRACT_CHECK=PASS'

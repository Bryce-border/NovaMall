param([switch]$Infrastructure)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
foreach ($service in @('mall-gateway','mall-auth','mall-user','mall-product','mall-inventory','mall-order')) {
    $jar = Join-Path $projectRoot "$service/target/$service-0.1.0-SNAPSHOT.jar"
    $processes = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | Where-Object {
        $_.CommandLine -and $_.CommandLine.IndexOf($jar, [StringComparison]::OrdinalIgnoreCase) -ge 0
    })
    foreach ($process in $processes) {
        Stop-Process -Id $process.ProcessId
        Wait-Process -Id $process.ProcessId -Timeout 15 -ErrorAction SilentlyContinue
        Write-Host "Stopped $service (PID $($process.ProcessId))"
    }
}
if ($Infrastructure) {
    Push-Location $projectRoot
    try {
        docker compose stop
        if ($LASTEXITCODE -ne 0) { throw 'Failed to stop infrastructure.' }
    } finally { Pop-Location }
}

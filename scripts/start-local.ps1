param(
    [ValidateRange(1024, 65535)][int]$GatewayPort = 18080,
    [string]$JavaExecutable,
    [switch]$Build,
    [switch]$Restart,
    [switch]$SkipInfrastructure
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$logDirectory = Join-Path $projectRoot 'logs'
$socketDirectory = Join-Path $logDirectory 'java-tmp'
$services = [ordered]@{
    'mall-user' = 8082; 'mall-product' = 8083; 'mall-inventory' = 8084
    'mall-auth' = 8081; 'mall-order' = 8085; 'mall-gateway' = $GatewayPort
}
if ($GatewayPort -in @(8081,8082,8083,8084,8085)) { throw 'GatewayPort conflicts with a business service.' }
New-Item -ItemType Directory -Force -Path $socketDirectory | Out-Null

function Find-ServiceProcess([string]$JarPath) {
    # Match the full checkout path, not a saved PID that may have been reused.
    @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | Where-Object {
        $_.CommandLine -and $_.CommandLine.IndexOf($JarPath, [StringComparison]::OrdinalIgnoreCase) -ge 0
    })
}

if (-not $JavaExecutable) {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += Join-Path $env:JAVA_HOME 'bin/java.exe' }
    $javaCommand = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCommand) { $candidates += $javaCommand.Source }
    foreach ($candidate in $candidates) {
        if (-not (Test-Path -LiteralPath $candidate)) { continue }
        $versionText = (& $candidate -version 2>&1 | Out-String)
        if ($versionText -match 'version "(\d+)' -and [int]$Matches[1] -ge 17) {
            $JavaExecutable = $candidate; break
        }
    }
}
if (-not $JavaExecutable -or -not (Test-Path -LiteralPath $JavaExecutable)) {
    throw 'Set JAVA_HOME to JDK 17+ or pass -JavaExecutable with its java.exe path.'
}
$versionText = (& $JavaExecutable -version 2>&1 | Out-String)
if ($versionText -notmatch 'version "(\d+)' -or [int]$Matches[1] -lt 17) { throw 'Java 17 or newer is required.' }

Push-Location $projectRoot
try {
    if (-not $SkipInfrastructure) {
        docker compose up -d --wait --wait-timeout 180 mysql redis nacos rabbitmq elasticsearch
        if ($LASTEXITCODE -ne 0) { throw 'Infrastructure not ready. Inspect docker compose ps and logs.' }
    }

    foreach ($service in $services.Keys) {
        $jar = Join-Path $projectRoot "$service/target/$service-0.1.0-SNAPSHOT.jar"
        $existing = @(Find-ServiceProcess $jar)
        if ($existing.Count -gt 0 -and $Build -and -not $Restart) {
            throw 'Use -Build -Restart to rebuild while this checkout is running.'
        }
        if ($Restart) {
            foreach ($process in $existing) {
                Stop-Process -Id $process.ProcessId
                Wait-Process -Id $process.ProcessId -Timeout 15 -ErrorAction SilentlyContinue
            }
        }
    }

    if ($Build) {
        $mavenArgs = @('-DskipTests','package')
        if (Test-Path .mvn-local-settings.xml) { $mavenArgs = @('-s','.mvn-local-settings.xml') + $mavenArgs }
        & mvn @mavenArgs
        if ($LASTEXITCODE -ne 0) { throw 'Build failed; inspect Maven output.' }
    }
    foreach ($service in $services.Keys) {
        $jar = Join-Path $projectRoot "$service/target/$service-0.1.0-SNAPSHOT.jar"
        if (-not (Test-Path -LiteralPath $jar)) { throw "Missing $jar. Run with -Build." }
        $owners = @(Get-NetTCPConnection -State Listen -LocalPort $services[$service] -ErrorAction SilentlyContinue)
        $matching = @(Find-ServiceProcess $jar)
        if ($owners.Count -gt 0 -and @($owners | Where-Object { $_.OwningProcess -notin $matching.ProcessId }).Count -gt 0) {
            throw "Port $($services[$service]) is owned by another program. No unrelated process was stopped."
        }
    }

    $runtime = @()
    foreach ($service in $services.Keys) {
        $jar = Join-Path $projectRoot "$service/target/$service-0.1.0-SNAPSHOT.jar"
        $existing = @(Find-ServiceProcess $jar)
        if ($existing.Count -gt 1) { throw "Multiple processes for $service; run again with -Restart." }
        if ($existing.Count -eq 1) {
            $servicePid = $existing[0].ProcessId
            Write-Host "Reusing $service (PID $servicePid)"
        } else {
            # Quote arguments containing paths for Windows Start-Process.
            $arguments = @('-Xms64m','-Xmx384m','-Dfile.encoding=UTF-8',
                "`"-Djdk.net.unixdomain.tmpdir=$socketDirectory`"", "`"-Djava.io.tmpdir=$socketDirectory`"",
                '-jar', "`"$jar`"", "--server.port=$($services[$service])")
            $process = Start-Process -FilePath $JavaExecutable -ArgumentList $arguments -WorkingDirectory $projectRoot `
                -WindowStyle Hidden -RedirectStandardOutput "$logDirectory/$service-runtime.out.log" `
                -RedirectStandardError "$logDirectory/$service-runtime.err.log" -PassThru
            $servicePid = $process.Id
            Write-Host "Starting $service (PID $servicePid)"
        }
        $runtime += [PSCustomObject]@{ Service=$service; Pid=$servicePid; Port=$services[$service]; Jar=$jar }
    }
    $runtime | ConvertTo-Json | Set-Content "$logDirectory/runtime-processes.json"

    $deadline = (Get-Date).AddSeconds(150)
    $pending = @($runtime)
    while ($pending.Count -gt 0 -and (Get-Date) -lt $deadline) {
        $pending = @($pending | Where-Object {
            $entry = $_
            if (-not (Get-Process -Id $entry.Pid -ErrorAction SilentlyContinue)) {
                throw "$($entry.Service) exited. See logs/$($entry.Service)-runtime.out.log"
            }
            try {
                $health = Invoke-RestMethod "http://127.0.0.1:$($entry.Port)/actuator/health" -TimeoutSec 2
                if ($health.status -eq 'UP') { Write-Host "$($entry.Service): UP"; $false } else { $true }
            } catch { $true }
        })
        if ($pending.Count -gt 0) { Start-Sleep -Seconds 2 }
    }
    if ($pending.Count -gt 0) { throw "Services not ready: $($pending.Service -join ', '). Inspect logs/." }
    $url = "http://127.0.0.1:$GatewayPort/shop/index.html"
    $page = Invoke-WebRequest $url -UseBasicParsing -TimeoutSec 10
    if ($page.Content -notmatch '<title>NovaMall') { throw 'Unexpected storefront page. Check gateway artifact and port.' }
    Write-Host "Storefront: $url"
    Write-Host "Administrator: $url#admin"
} finally { Pop-Location }

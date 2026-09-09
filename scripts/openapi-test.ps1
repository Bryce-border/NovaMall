$ErrorActionPreference = 'Stop'

$ports = 8080..8085
$results = foreach ($port in $ports) {
    $response = Invoke-WebRequest -Uri ("http://localhost:{0}/v3/api-docs" -f $port) -UseBasicParsing -TimeoutSec 10
    $document = $response.Content | ConvertFrom-Json
    [PSCustomObject]@{
        Port = $port
        Status = [int]$response.StatusCode
        OpenApi = $document.openapi
        Paths = @($document.paths.PSObject.Properties).Count
    }
}

if (@($results | Where-Object { $_.Status -ne 200 -or [string]::IsNullOrWhiteSpace($_.OpenApi) }).Count -gt 0) {
    $results | Format-Table -AutoSize
    throw 'OpenAPI verification failed'
}

$results | Format-Table -AutoSize

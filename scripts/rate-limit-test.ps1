param([string]$GatewayUrl = "http://localhost:8080")
$ErrorActionPreference = "Stop"
$statusCodes = [System.Collections.Generic.List[int]]::new()
1..20 | ForEach-Object {
    try {
        $response = Invoke-WebRequest -Method Get -Uri "$GatewayUrl/api/auth/ping" -UseBasicParsing
        $statusCodes.Add([int]$response.StatusCode)
    } catch {
        if ($_.Exception.Response -ne $null) {
            $statusCodes.Add([int]$_.Exception.Response.StatusCode)
        } else { throw }
    }
}
$limited = @($statusCodes | Where-Object { $_ -eq 429 }).Count
if ($limited -eq 0) { throw "Rate limit test failed: no HTTP 429 response was observed" }
[PSCustomObject]@{
    Result = "PASS"
    TotalRequests = $statusCodes.Count
    LimitedRequests = $limited
    StatusCodes = ($statusCodes -join ",")
}

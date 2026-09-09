param([string]$GatewayUrl = "http://localhost:8080")
$ErrorActionPreference = "Stop"

$expected = "trace-" + [Guid]::NewGuid().ToString("N")
$response = Invoke-WebRequest -Method Get -Uri "$GatewayUrl/api/auth/ping" `
    -Headers @{ "X-Trace-Id" = $expected } -UseBasicParsing
$forwarded = $response.Headers["X-Trace-Id"]
if ($forwarded -ne $expected) {
    throw "Trace ID propagation failed: expected '$expected', received '$forwarded'"
}

$generatedResponse = Invoke-WebRequest -Method Get -Uri "$GatewayUrl/api/auth/ping" -UseBasicParsing
$generated = $generatedResponse.Headers["X-Trace-Id"]
if ([string]::IsNullOrWhiteSpace($generated) -or $generated -notmatch '^[a-f0-9]{32}$') {
    throw "Trace ID generation failed: received '$generated'"
}

[PSCustomObject]@{
    Result = "PASS"
    ForwardedTraceId = $forwarded
    GeneratedTraceId = $generated
}

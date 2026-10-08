# Shared login for local demo setup. This file does not make requests when sourced.
function Get-DemoAdminHeaders {
    param(
        [string]$GatewayUrl,
        [string]$Username = "admin",
        [string]$Password = "AdminPass123!"
    )

    try {
        $response = Invoke-RestMethod -Method Post -Uri "$($GatewayUrl.TrimEnd('/'))/api/auth/login" `
            -ContentType "application/json" -TimeoutSec 30 `
            -Body (@{ username = $Username; password = $Password } | ConvertTo-Json)
        if (-not $response.success -or [string]::IsNullOrWhiteSpace($response.data.accessToken)) {
            throw "Login did not return an access token"
        }
    } catch {
        throw "Demo administrator login failed. Initialize database/seed/dev-admin.sql or supply -AdminUsername and -AdminPassword. $($_.Exception.Message)"
    }

    return @{ Authorization = "Bearer $($response.data.accessToken)" }
}

param([string]$GatewayUrl = "http://localhost:8080")

$ErrorActionPreference = "Stop"

function Invoke-Api {
    param([string]$Method, [string]$Path, [hashtable]$Headers = @{}, [object]$Body)
    $params = @{ Method = $Method; Uri = "$GatewayUrl$Path"; Headers = $Headers; ContentType = "application/json" }
    if ($null -ne $Body) { $params.Body = $Body | ConvertTo-Json -Depth 8 }
    Invoke-RestMethod @params
}

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw "Seckill test failed: $Message" }
}

$suffix = Get-Date -Format "yyyyMMddHHmmssfff"
$username = "seckill$suffix"
$password = "SmokePass123"

$registered = Invoke-Api Post "/api/users/register" @{} @{
    username = $username; password = $password; nickname = "Seckill Test"
}
$login = Invoke-Api Post "/api/auth/login" @{} @{
    username = $username; password = $password
}
$headers = @{ Authorization = "Bearer $($login.data.accessToken)" }

$category = Invoke-Api Post "/api/categories" $headers @{
    parentId = 0; name = "Seckill Category $suffix"; status = 1; sortOrder = 0
}
$product = Invoke-Api Post "/api/products" $headers @{
    categoryId = $category.data.id; name = "Seckill Product $suffix"
    subtitle = "Redis Lua test"; price = 19.99; status = 1
}
Invoke-Api Post "/api/inventory" $headers @{ productId = $product.data.id; quantity = 2 } | Out-Null

$prepared = Invoke-Api Post "/api/seckill/$($product.data.id)/prepare" $headers @{ stock = 1 }
Assert-True ($prepared.data.stock -eq 1) "seckill stock was not prepared"

$accepted = Invoke-Api Post "/api/seckill/$($product.data.id)" $headers
Assert-True ($accepted.data.status -eq "ACCEPTED") "seckill request was not accepted"
$seckillNo = $accepted.data.seckillNo

$duplicateRejected = $false
try {
    Invoke-Api Post "/api/seckill/$($product.data.id)" $headers | Out-Null
} catch {
    $duplicateRejected = $true
}
Assert-True $duplicateRejected "duplicate participation was not rejected"

$order = $null
for ($attempt = 0; $attempt -lt 15; $attempt++) {
    Start-Sleep -Seconds 1
    try {
        $order = Invoke-Api Get "/api/seckill/orders/$seckillNo" $headers
        if ($order.data.status -eq "SUCCESS") { break }
    } catch { }
}
Assert-True ($null -ne $order -and $order.data.status -eq "SUCCESS") "seckill event was not consumed"

$stock = Invoke-Api Get "/api/inventory/$($product.data.id)" $headers
Assert-True ($stock.data.availableStock -eq 1) "database available stock should be 1"
Assert-True ($stock.data.lockedStock -eq 1) "database locked stock should be 1"

[PSCustomObject]@{
    Result = "PASS"
    UserId = $registered.data.id
    ProductId = $product.data.id
    SeckillNo = $seckillNo
    Status = $order.data.status
    AvailableStock = $stock.data.availableStock
    LockedStock = $stock.data.lockedStock
}

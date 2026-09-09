param(
    [string]$GatewayUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw "Smoke test failed: $Message"
    }
}

function Invoke-Json {
    param(
        [string]$Method,
        [string]$Path,
        [hashtable]$Headers = @{},
        [object]$Body
    )

    $parameters = @{
        Method = $Method
        Uri = "$GatewayUrl$Path"
        Headers = $Headers
        ContentType = "application/json"
    }
    if ($null -ne $Body) {
        $parameters.Body = $Body | ConvertTo-Json -Depth 8
    }
    Invoke-RestMethod @parameters
}

$health = Invoke-RestMethod -Uri "$GatewayUrl/actuator/health"
Assert-True ($health.status -eq "UP") "gateway health is not UP"

$suffix = Get-Date -Format "yyyyMMddHHmmssfff"
$username = "smoke$suffix"
$password = "SmokePass123"

$registration = Invoke-Json -Method Post -Path "/api/users/register" -Body @{
    username = $username
    password = $password
    nickname = "Smoke Test"
}
Assert-True $registration.success "user registration failed"

$login = Invoke-Json -Method Post -Path "/api/auth/login" -Body @{
    username = $username
    password = $password
}
Assert-True $login.success "login failed"
$headers = @{ Authorization = "Bearer $($login.data.accessToken)" }

$category = Invoke-Json -Method Post -Path "/api/categories" -Headers $headers -Body @{
    parentId = 0
    name = "Smoke Category $suffix"
    status = 1
    sortOrder = 0
}
Assert-True $category.success "category creation failed"

$product = Invoke-Json -Method Post -Path "/api/products" -Headers $headers -Body @{
    categoryId = $category.data.id
    name = "Smoke Product $suffix"
    subtitle = "NovaMall integration check"
    price = 99.99
    status = 1
}
Assert-True $product.success "product creation failed"

$stock = Invoke-Json -Method Post -Path "/api/inventory" -Headers $headers -Body @{
    productId = $product.data.id
    quantity = 10
}
Assert-True ($stock.data.availableStock -eq 10) "initial available stock should be 10"

$order = Invoke-Json -Method Post -Path "/api/orders" -Headers $headers -Body @{
    items = @(@{ productId = $product.data.id; quantity = 2 })
}
Assert-True ($order.data.status -eq "PENDING_PAYMENT") "order should await payment"
Assert-True ([decimal]$order.data.totalAmount -eq 199.98) "server-calculated order amount is incorrect"

$reservedStock = Invoke-Json -Method Get -Path "/api/inventory/$($product.data.id)" -Headers $headers
Assert-True ($reservedStock.data.availableStock -eq 8) "available stock should be 8 after reservation"
Assert-True ($reservedStock.data.lockedStock -eq 2) "locked stock should be 2 after reservation"

$cancelled = Invoke-Json -Method Post -Path "/api/orders/$($order.data.orderNo)/cancel" -Headers $headers
Assert-True ($cancelled.data.status -eq "CANCELLED") "order cancellation failed"

$releasedStock = $null
for ($attempt = 0; $attempt -lt 15; $attempt++) {
    Start-Sleep -Seconds 1
    $releasedStock = Invoke-Json -Method Get -Path "/api/inventory/$($product.data.id)" -Headers $headers
    if ($releasedStock.data.availableStock -eq 10 -and $releasedStock.data.lockedStock -eq 0) {
        break
    }
}
Assert-True ($releasedStock.data.availableStock -eq 10) "cancel event did not restore available stock"
Assert-True ($releasedStock.data.lockedStock -eq 0) "cancel event did not clear locked stock"

[PSCustomObject]@{
    Result = "PASS"
    Username = $username
    UserId = $registration.data.id
    CategoryId = $category.data.id
    ProductId = $product.data.id
    OrderNo = $order.data.orderNo
    FinalAvailableStock = $releasedStock.data.availableStock
    FinalLockedStock = $releasedStock.data.lockedStock
}

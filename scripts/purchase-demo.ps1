param(
    [string]$GatewayUrl = "http://localhost:8080",
    [string]$AdminUsername = "admin",
    [string]$AdminPassword = "AdminPass123!"
)

$ErrorActionPreference = "Stop"
$GatewayUrl = $GatewayUrl.TrimEnd('/')
. "$PSScriptRoot/demo-common.ps1"

function Invoke-DemoApi {
    param([string]$Method, [string]$Path, [hashtable]$Headers = @{}, [object]$Body)
    $parameters = @{
        Method = $Method; Uri = "$GatewayUrl$Path"; Headers = $Headers
        ContentType = "application/json"; TimeoutSec = 30
    }
    if ($null -ne $Body) { $parameters.Body = $Body | ConvertTo-Json -Depth 8 }
    try {
        $response = Invoke-RestMethod @parameters
        if (-not $response.success) { throw $response.message }
        return $response.data
    } catch {
        throw "Demo stopped at $Method $Path. Existing demo records are retained. $($_.Exception.Message)"
    }
}

Write-Host "NovaMall purchase demo: payment and fulfillment are simulated; no money is charged."
$adminHeaders = Get-DemoAdminHeaders -GatewayUrl $GatewayUrl -Username $AdminUsername -Password $AdminPassword
$suffix = Get-Date -Format "yyyyMMddHHmmssfff"
$username = "buyer$suffix"
$buyerPassword = [guid]::NewGuid().ToString('N')

Write-Host "[1/7] Administrator creates a product and SKU stock."
$category = Invoke-DemoApi Post "/api/categories" $adminHeaders @{
    parentId = 0; name = "Demo $suffix"; status = 1; sortOrder = 0
}
$product = Invoke-DemoApi Post "/api/products" $adminHeaders @{
    categoryId = $category.id; name = "Demo Headphones $suffix"
    subtitle = "Local purchase demo"; price = 99.90; status = 1
}
$skus = @(Invoke-DemoApi Get "/api/products/$($product.id)/skus" $adminHeaders)
if ($skus.Count -eq 0) { throw "Product has no SKU; check database initialization." }
$skuId = $skus[0].skuId
Invoke-DemoApi Post "/api/inventory" $adminHeaders @{
    productId = $product.id; skuId = $skuId; quantity = 10
} | Out-Null

Write-Host "[2/7] Buyer registers, logs in and saves an address."
Invoke-DemoApi Post "/api/users/register" @{} @{
    username = $username; password = $buyerPassword; nickname = "Demo Buyer"
} | Out-Null
$login = Invoke-DemoApi Post "/api/auth/login" @{} @{ username = $username; password = $buyerPassword }
$buyerHeaders = @{ Authorization = "Bearer $($login.accessToken)" }
$address = Invoke-DemoApi Post "/api/users/addresses" $buyerHeaders @{
    receiverName = "Demo Buyer"; receiverPhone = "13800000000"; province = "Zhejiang"
    city = "Hangzhou"; district = "Xihu"; detailAddress = "Demo Road 1"
    postalCode = "310000"; isDefault = $true
}

Write-Host "[3/7] Buyer adds two units to the cart and checks out at the server price."
Invoke-DemoApi Post "/api/users/cart/items" $buyerHeaders @{ skuId = $skuId; quantity = 2 } | Out-Null
$order = Invoke-DemoApi Post "/api/orders/checkout" $buyerHeaders @{
    requestKey = [guid]::NewGuid().ToString(); addressId = $address.id
}
$orderNo = $order.orderNo
Write-Host "Order $orderNo | $($order.status) | CNY $($order.totalAmount)"

Write-Host "[4/7] Simulate payment (MOCK_PAYMENT_ENABLED=true)."
$paid = Invoke-DemoApi Post "/api/orders/$orderNo/pay" $buyerHeaders
Write-Host "Status: $($paid.status)"
Write-Host "[5/7] Simulate shipment (MOCK_FULFILLMENT_ENABLED=true)."
# The current Mock fulfillment API deliberately operates on the buyer's own order.
$shipped = Invoke-DemoApi Post "/api/orders/$orderNo/ship" $buyerHeaders
Write-Host "Status: $($shipped.status)"
Write-Host "[6/7] Buyer confirms receipt."
$completed = Invoke-DemoApi Post "/api/orders/$orderNo/complete" $buyerHeaders
Write-Host "Status: $($completed.status)"

Write-Host "[7/7] Read order details and wait up to 20 seconds for inventory events."
$detail = Invoke-DemoApi Get "/api/orders/$orderNo/detail" $buyerHeaders
Write-Host "Order timeline:"
$detail.history | Select-Object occurredAt, fromStatus, toStatus, actorType, note | Format-Table -AutoSize | Out-Host
Write-Host "Mock payment and shipment records:"
$detail.payments | Select-Object paymentNo, channel, simulated, amount, status | Format-Table -AutoSize | Out-Host
$detail.shipments | Select-Object trackingNo, company, simulated, status | Format-Table -AutoSize | Out-Host
$inventorySettled = $false
for ($attempt = 0; $attempt -lt 20; $attempt++) {
    $stock = Invoke-DemoApi Get "/api/inventory/skus/$skuId" $buyerHeaders
    if ($stock.availableStock -eq 8 -and $stock.lockedStock -eq 0 -and $stock.totalStock -eq 8) {
        $inventorySettled = $true
        break
    }
    Start-Sleep -Seconds 1
}
if (-not $inventorySettled) {
    Write-Warning "Order status is $($detail.status), but inventory has not settled. Check RabbitMQ and the order Outbox logs."
}

[PSCustomObject]@{
    Mode = "MOCK - no real charge or shipment"
    Username = $username
    ProductId = $product.id
    SkuId = $skuId
    OrderNo = $orderNo
    TotalAmount = $detail.totalAmount
    Statuses = @($order.status, $paid.status, $shipped.status, $completed.status)
    FinalStatus = $detail.status
    PaymentCount = @($detail.payments).Count
    ShipmentCount = @($detail.shipments).Count
    HistoryCount = @($detail.history).Count
    AvailableActions = @($detail.actions | ForEach-Object { $_.code })
    AvailableStock = $stock.availableStock
    LockedStock = $stock.lockedStock
    InventorySettled = $inventorySettled
}

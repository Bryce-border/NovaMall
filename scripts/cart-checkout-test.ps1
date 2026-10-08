param(
    [string]$GatewayUrl = "http://localhost:8080",
    [string]$AdminUsername = "admin",
    [string]$AdminPassword = "AdminPass123!"
)

$ErrorActionPreference = "Stop"
. "$PSScriptRoot/demo-common.ps1"
$adminHeaders = Get-DemoAdminHeaders -GatewayUrl $GatewayUrl -Username $AdminUsername -Password $AdminPassword

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "Cart checkout check failed: $Message" }
}

function Invoke-Api([string]$Method, [string]$Path, [hashtable]$Headers = @{}, [object]$Body) {
    $parameters = @{ Method = $Method; Uri = "$GatewayUrl$Path"; Headers = $Headers; ContentType = "application/json" }
    if ($null -ne $Body) { $parameters.Body = $Body | ConvertTo-Json -Depth 8 }
    $response = Invoke-RestMethod @parameters
    Assert-True $response.success "API failure for $Path"
    return $response
}

$suffix = Get-Date -Format "yyyyMMddHHmmssfff"
$username = "cart$suffix"
Invoke-Api Post "/api/users/register" @{} @{username=$username;password="CartPass123";nickname="Cart Check"} | Out-Null
$login = Invoke-Api Post "/api/auth/login" @{} @{username=$username;password="CartPass123"}
$headers = @{Authorization="Bearer $($login.data.accessToken)"}
$category = Invoke-Api Post "/api/categories" $adminHeaders @{parentId=0;name="Cart $suffix";status=1;sortOrder=0}
$product = Invoke-Api Post "/api/products" $adminHeaders @{categoryId=$category.data.id;name="Cart Phone $suffix";subtitle="Cart check";price=10.00;status=1}
$skus = Invoke-Api Get "/api/products/$($product.data.id)/skus" $headers
$skuId = $skus.data[0].skuId
Invoke-Api Post "/api/inventory" $adminHeaders @{productId=$product.data.id;skuId=$skuId;quantity=10} | Out-Null
$address = Invoke-Api Post "/api/users/addresses" $headers @{
    receiverName="Cart Check";receiverPhone="13800000000";province="Zhejiang";city="Hangzhou"
    district="Xihu";detailAddress="Test Road 1";postalCode="310000";isDefault=$true
}
Invoke-Api Post "/api/users/cart/items" $headers @{skuId=$skuId;quantity=2} | Out-Null
# Change the price after adding to cart: checkout must use the current SKU price.
Invoke-Api Put "/api/products/$($product.data.id)" $adminHeaders @{
    categoryId=$category.data.id;name="Updated Phone $suffix";subtitle="Cart check";price=12.50;status=1
} | Out-Null
$request = @{requestKey=[guid]::NewGuid().ToString();addressId=$address.data.id}
$order = Invoke-Api Post "/api/orders/checkout" $headers $request
Assert-True ([decimal]$order.data.totalAmount -eq 25.00) "checkout must use current price"
Assert-True ($order.data.items[0].skuId -eq $skuId) "order must preserve SKU"
$retry = Invoke-Api Post "/api/orders/checkout" $headers $request
Assert-True ($retry.data.orderNo -eq $order.data.orderNo) "retry must return original order"
$stock = Invoke-Api Get "/api/inventory/skus/$skuId" $headers
Assert-True ($stock.data.availableStock -eq 8 -and $stock.data.lockedStock -eq 2) "retry must not reserve twice"

for ($attempt = 0; $attempt -lt 20; $attempt++) {
    $cart = Invoke-Api Get "/api/users/cart" $headers
    if (@($cart.data).Count -eq 0) { break }
    Start-Sleep -Seconds 1
}
Assert-True (@($cart.data).Count -eq 0) "committed order cleanup did not clear purchased items"
$afterCleanupRetry = Invoke-Api Post "/api/orders/checkout" $headers $request
Assert-True ($afterCleanupRetry.data.orderNo -eq $order.data.orderNo) "retry must work after cart cleanup"

Invoke-Api Put "/api/products/$($product.data.id)" $adminHeaders @{
    categoryId=$category.data.id;name="Later Phone $suffix";subtitle="Cart check";price=20.00;status=1
} | Out-Null
$history = Invoke-Api Get "/api/orders/$($order.data.orderNo)" $headers
Assert-True ([decimal]$history.data.totalAmount -eq 25.00) "history must retain purchase price"
Assert-True ($history.data.items[0].productName -eq "Updated Phone $suffix") "history must retain purchase name"
Invoke-Api Post "/api/orders/$($order.data.orderNo)/cancel" $headers | Out-Null

for ($attempt = 0; $attempt -lt 20; $attempt++) {
    Start-Sleep -Seconds 1
    $stock = Invoke-Api Get "/api/inventory/skus/$skuId" $headers
    if ($stock.data.availableStock -eq 10 -and $stock.data.lockedStock -eq 0) { break }
}
Assert-True ($stock.data.availableStock -eq 10 -and $stock.data.lockedStock -eq 0) "cancellation did not release SKU stock"
[PSCustomObject]@{Result="PASS";Username=$username;SkuId=$skuId;OrderNo=$order.data.orderNo;TotalAmount=$order.data.totalAmount}

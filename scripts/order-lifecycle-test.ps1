param(
    [string]$GatewayUrl = "http://localhost:8080",
    [string]$AdminUsername = "admin",
    [string]$AdminPassword = "AdminPass123!"
)
$ErrorActionPreference = "Stop"
. "$PSScriptRoot/demo-common.ps1"
$adminHeaders = Get-DemoAdminHeaders -GatewayUrl $GatewayUrl -Username $AdminUsername -Password $AdminPassword
function Invoke-Api { param([string]$Method,[string]$Path,[hashtable]$Headers=@{},[object]$Body)
    $p=@{Method=$Method;Uri="$GatewayUrl$Path";Headers=$Headers;ContentType="application/json"}
    if($null -ne $Body){$p.Body=$Body|ConvertTo-Json -Depth 8}
    try { Invoke-RestMethod @p } catch { throw "API request failed: $Method $Path; $($_.Exception.Message)" }
}
function Assert-True { param([bool]$Condition,[string]$Message); if(-not $Condition){throw "Lifecycle test failed: $Message"} }
$suffix=Get-Date -Format "yyyyMMddHHmmssfff"; $username="lifecycle$suffix"; $password="SmokePass123"
$user=Invoke-Api Post "/api/users/register" @{} @{username=$username;password=$password;nickname="Lifecycle Test"}
$login=Invoke-Api Post "/api/auth/login" @{} @{username=$username;password=$password}
$headers=@{Authorization="Bearer $($login.data.accessToken)"}
$category=Invoke-Api Post "/api/categories" $adminHeaders @{parentId=0;name="Lifecycle Category $suffix";status=1;sortOrder=0}
$product=Invoke-Api Post "/api/products" $adminHeaders @{categoryId=$category.data.id;name="Lifecycle Product $suffix";subtitle="Order lifecycle";price=18.00;status=1}
$skus=Invoke-Api Get "/api/products/$($product.data.id)/skus" $headers
Invoke-Api Post "/api/inventory" $adminHeaders @{productId=$product.data.id;skuId=$skus.data[0].skuId;quantity=3}|Out-Null
$order=Invoke-Api Post "/api/orders" $headers @{items=@(@{productId=$product.data.id;quantity=1})}
Assert-True ($order.data.status -eq "PENDING_PAYMENT") "order should start pending payment"
$paid=Invoke-Api Post "/api/orders/$($order.data.orderNo)/pay" $headers
Assert-True ($paid.data.status -eq "PAID") "payment should produce PAID"
$shipped=Invoke-Api Post "/api/orders/$($order.data.orderNo)/ship" $headers
Assert-True ($shipped.data.status -eq "SHIPPED") "shipment should produce SHIPPED"
$completed=Invoke-Api Post "/api/orders/$($order.data.orderNo)/complete" $headers
Assert-True ($completed.data.status -eq "COMPLETED") "receipt should produce COMPLETED"
$invalid=$false; try { Invoke-Api Post "/api/orders/$($order.data.orderNo)/complete" $headers|Out-Null } catch { $invalid=$true }
Assert-True $invalid "completed order should not be completed twice"
[PSCustomObject]@{Result="PASS";OrderNo=$order.data.orderNo;Statuses=@($order.data.status,$paid.data.status,$shipped.data.status,$completed.data.status)}

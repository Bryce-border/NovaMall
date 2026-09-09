param([string]$GatewayUrl = "http://localhost:8080")
$ErrorActionPreference = "Stop"
function Invoke-Api { param([string]$Method,[string]$Path,[hashtable]$Headers=@{},[object]$Body)
    $p=@{Method=$Method;Uri="$GatewayUrl$Path";Headers=$Headers;ContentType="application/json"}
    if($null -ne $Body){$p.Body=$Body|ConvertTo-Json -Depth 8}
    try { Invoke-RestMethod @p }
    catch { throw "API request failed: $Method $Path; $($_.Exception.Message)" }
}
function Assert-True { param([bool]$Condition,[string]$Message); if(-not $Condition){throw "Payment test failed: $Message"} }
$suffix=Get-Date -Format "yyyyMMddHHmmssfff"; $username="payment$suffix"; $password="SmokePass123"
$user=Invoke-Api Post "/api/users/register" @{} @{username=$username;password=$password;nickname="Payment Test"}
$login=Invoke-Api Post "/api/auth/login" @{} @{username=$username;password=$password}
$headers=@{Authorization="Bearer $($login.data.accessToken)"}
$category=Invoke-Api Post "/api/categories" $headers @{parentId=0;name="Payment Category $suffix";status=1;sortOrder=0}
$product=Invoke-Api Post "/api/products" $headers @{categoryId=$category.data.id;name="Payment Product $suffix";subtitle="Payment test";price=12.50;status=1}
Invoke-Api Post "/api/inventory" $headers @{productId=$product.data.id;quantity=2}|Out-Null
$order=Invoke-Api Post "/api/orders" $headers @{items=@(@{productId=$product.data.id;quantity=1})}
Assert-True ($order.data.status -eq "PENDING_PAYMENT") "new order should be pending payment"
$paid=Invoke-Api Post "/api/orders/$($order.data.orderNo)/pay" $headers
Assert-True ($paid.data.status -eq "PAID") "order should become paid"
$duplicateRejected=$false; try { Invoke-Api Post "/api/orders/$($order.data.orderNo)/pay" $headers|Out-Null } catch { $duplicateRejected=$true }
Assert-True $duplicateRejected "duplicate payment should be rejected"
$cancelRejected=$false; try { Invoke-Api Post "/api/orders/$($order.data.orderNo)/cancel" $headers|Out-Null } catch { $cancelRejected=$true }
Assert-True $cancelRejected "paid order should not be cancelled"
[PSCustomObject]@{Result="PASS";OrderNo=$order.data.orderNo;InitialStatus=$order.data.status;FinalStatus=$paid.data.status}

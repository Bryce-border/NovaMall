param([string]$GatewayUrl = "http://localhost:8080")
$ErrorActionPreference = "Stop"
function Invoke-Api { param([string]$Method,[string]$Path,[hashtable]$Headers=@{},[object]$Body)
    $p=@{Method=$Method;Uri="$GatewayUrl$Path";Headers=$Headers;ContentType="application/json"}
    if($null -ne $Body){$p.Body=$Body|ConvertTo-Json -Depth 8}
    try { Invoke-RestMethod @p } catch { throw "API request failed: $Method $Path; $($_.Exception.Message)" }
}
function Assert-True { param([bool]$Condition,[string]$Message); if(-not $Condition){throw "Sales ranking test failed: $Message"} }
$suffix=Get-Date -Format "yyyyMMddHHmmssfff"; $username="sales$suffix"; $password="SmokePass123"
$user=Invoke-Api Post "/api/users/register" @{} @{username=$username;password=$password;nickname="Sales Ranking Test"}
$login=Invoke-Api Post "/api/auth/login" @{} @{username=$username;password=$password}
$headers=@{Authorization="Bearer $($login.data.accessToken)"}
$category=Invoke-Api Post "/api/categories" $headers @{parentId=0;name="Sales Category $suffix";status=1;sortOrder=0}
$first=Invoke-Api Post "/api/products" $headers @{categoryId=$category.data.id;name="Sales First $suffix";subtitle="Sales ranking first";price=10.00;status=1}
$second=Invoke-Api Post "/api/products" $headers @{categoryId=$category.data.id;name="Sales Second $suffix";subtitle="Sales ranking second";price=20.00;status=1}
$firstSkus=Invoke-Api Get "/api/products/$($first.data.id)/skus" $headers
$secondSkus=Invoke-Api Get "/api/products/$($second.data.id)/skus" $headers
Invoke-Api Post "/api/inventory" $headers @{productId=$first.data.id;skuId=$firstSkus.data[0].skuId;quantity=3}|Out-Null
Invoke-Api Post "/api/inventory" $headers @{productId=$second.data.id;skuId=$secondSkus.data[0].skuId;quantity=2}|Out-Null

function Complete-Order([long]$ProductId,[int]$Quantity) {
    $order=Invoke-Api Post "/api/orders" $headers @{items=@(@{productId=$ProductId;quantity=$Quantity})}
    Assert-True ($order.data.status -eq "PENDING_PAYMENT") "order should start pending payment"
    $paid=Invoke-Api Post "/api/orders/$($order.data.orderNo)/pay" $headers
    Assert-True ($paid.data.status -eq "PAID") "payment should succeed"
    $shipped=Invoke-Api Post "/api/orders/$($order.data.orderNo)/ship" $headers
    Assert-True ($shipped.data.status -eq "SHIPPED") "shipment should succeed"
    $completed=Invoke-Api Post "/api/orders/$($order.data.orderNo)/complete" $headers
    Assert-True ($completed.data.status -eq "COMPLETED") "completion should succeed"
}

Complete-Order $first.data.id 2
Complete-Order $second.data.id 1
$ranking=$null
for($attempt=0;$attempt -lt 20;$attempt++) {
    Start-Sleep -Seconds 1
    $ranking=Invoke-Api Get "/api/products/sales?limit=50" $headers
    $firstRow=@($ranking.data | Where-Object productId -eq $first.data.id)
    $secondRow=@($ranking.data | Where-Object productId -eq $second.data.id)
    if($firstRow.Count -eq 1 -and $secondRow.Count -eq 1){break}
}
Assert-True ($firstRow.Count -eq 1 -and $secondRow.Count -eq 1) "completed order was not consumed by ranking service"
Assert-True ([double]$firstRow[0].sales -eq 2 -and [double]$secondRow[0].sales -eq 1) "sales quantities are incorrect"
$firstRank=@($ranking.data | ForEach-Object productId).IndexOf($first.data.id)
$secondRank=@($ranking.data | ForEach-Object productId).IndexOf($second.data.id)
Assert-True ($firstRank -lt $secondRank) "higher sales product was not ranked first"
[PSCustomObject]@{Result="PASS";FirstProductId=$first.data.id;FirstSales=$firstRow[0].sales;SecondProductId=$second.data.id;SecondSales=$secondRow[0].sales;FirstRank=$firstRank+1;SecondRank=$secondRank+1}

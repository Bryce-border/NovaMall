param(
    [string]$GatewayUrl = "http://localhost:18080",
    [string]$AdminUsername = "admin",
    [string]$AdminPassword = "AdminPass123!"
)
$ErrorActionPreference = "Stop"
$GatewayUrl = $GatewayUrl.TrimEnd('/')
. "$PSScriptRoot/demo-common.ps1"
$adminHeaders = Get-DemoAdminHeaders -GatewayUrl $GatewayUrl -Username $AdminUsername -Password $AdminPassword
function Invoke-DigitalApi {
    param([string]$Method, [string]$Path, [object]$Body)
    $p = @{Method=$Method;Uri="$GatewayUrl$Path";Headers=$adminHeaders;ContentType='application/json; charset=utf-8';TimeoutSec=30}
    if ($null -ne $Body) { $p.Body = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 8)) }
    $response = Invoke-RestMethod @p
    if (-not $response.success) { throw $response.message }
    return $response.data
}
# Unicode escapes keep this file compatible with Windows PowerShell 5.1 encoding defaults.
$categoryName = ConvertFrom-Json '"\u624b\u673a\u6570\u7801\u4ea7\u54c1"'
$categories = Invoke-DigitalApi Get '/api/categories'
$category = @($categories.items | Where-Object { $_.name -eq $categoryName -and $_.status -eq 1 } | Select-Object -First 1)
if ($category.Count -eq 0) {
    $category = @(Invoke-DigitalApi Post '/api/categories' @{parentId=0;name=$categoryName;status=1;sortOrder=0})
}
$existing = Invoke-DigitalApi Get '/api/products?keyword=iPhone%2017%20Pro&size=100'
$product = @($existing.items | Where-Object { $_.name -eq 'iPhone 17 Pro' -and $_.categoryId -eq $category[0].id } | Select-Object -First 1)
if ($product.Count -eq 0) {
    $product = @(Invoke-DigitalApi Post '/api/products' @{
        categoryId=$category[0].id;name='iPhone 17 Pro';subtitle='Demo price - not an official retail quote';price=10000.00;status=1
    })
    $skus = @(Invoke-DigitalApi Get "/api/products/$($product[0].id)/skus")
    Invoke-DigitalApi Post '/api/inventory' @{productId=$product[0].id;skuId=$skus[0].skuId;quantity=100} | Out-Null
    Write-Host 'Created iPhone 17 Pro with 100 units of demo stock. Sales are driven by completed orders.'
} else {
    Write-Host 'Existing sample retained. Price, status and stock were not reset.'
}
[PSCustomObject]@{Name=$product[0].name;Category=$categoryName;Price=$product[0].price;ProductId=$product[0].id}

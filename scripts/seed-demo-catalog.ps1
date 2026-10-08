param(
    [string]$GatewayUrl = "http://localhost:8080",
    [string]$AdminUsername = "admin",
    [string]$AdminPassword = "AdminPass123!"
)

$ErrorActionPreference = "Stop"
$GatewayUrl = $GatewayUrl.TrimEnd('/')
. "$PSScriptRoot/demo-common.ps1"
$adminHeaders = Get-DemoAdminHeaders -GatewayUrl $GatewayUrl -Username $AdminUsername -Password $AdminPassword

function Invoke-CatalogApi {
    param([string]$Method, [string]$Path, [object]$Body)
    $parameters = @{
        Method = $Method; Uri = "$GatewayUrl$Path"; Headers = $adminHeaders
        ContentType = "application/json; charset=utf-8"; TimeoutSec = 30
    }
    if ($null -ne $Body) {
        $parameters.Body = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 8))
    }
    $response = Invoke-RestMethod @parameters
    if (-not $response.success) { throw "Catalog setup failed at $Path : $($response.message)" }
    return $response.data
}

# Each run creates a fresh batch. Existing products, prices and stock are never reset.
$suffix = Get-Date -Format "yyyyMMddHHmmssfff"
$category = Invoke-CatalogApi Post '/api/categories' @{
    parentId = 0; name = "Everyday Collection $suffix"; status = 1; sortOrder = 0
}
$products = @(
    @{ name = 'NOVA Studio Headphones'; subtitle = 'A little quiet, a little more focus.'; price = 199.00 },
    @{ name = 'NOVA Everyday Tote'; subtitle = 'Room for everything that matters.'; price = 69.90 },
    @{ name = 'NOVA Ceramic Mug'; subtitle = 'Make your morning a small ritual.'; price = 49.90 },
    @{ name = 'NOVA Desk Light'; subtitle = 'A warm corner for your everyday.'; price = 129.00 }
)
foreach ($item in $products) {
    $product = Invoke-CatalogApi Post '/api/products' @{
        categoryId = $category.id; name = $item.name; subtitle = $item.subtitle
        price = $item.price; status = 1
    }
    $skus = @(Invoke-CatalogApi Get "/api/products/$($product.id)/skus")
    if ($skus.Count -eq 0) { throw "No default SKU for product $($product.id)" }
    $stock = Invoke-CatalogApi Post '/api/inventory' @{
        productId = $product.id; skuId = $skus[0].skuId; quantity = 20
    }
    [PSCustomObject]@{
        Product = $product.name; ProductId = $product.id; SkuId = $skus[0].skuId
        Price = $item.price; AvailableStock = $stock.availableStock
    }
}
Write-Host "Catalog ready. Open $GatewayUrl/shop/index.html and register a buyer account."

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
    if (-not $Condition) { throw "Search test failed: $Message" }
}

$suffix = Get-Date -Format "yyyyMMddHHmmssfff"
$username = "search$suffix"
$password = "SmokePass123"

$registered = Invoke-Api Post "/api/users/register" @{} @{
    username = $username; password = $password; nickname = "Search Test"
}
$login = Invoke-Api Post "/api/auth/login" @{} @{
    username = $username; password = $password
}
$headers = @{ Authorization = "Bearer $($login.data.accessToken)" }

$phoneCategory = Invoke-Api Post "/api/categories" $headers @{
    parentId = 0; name = "Search Phones $suffix"; status = 1; sortOrder = 0
}
$tabletCategory = Invoke-Api Post "/api/categories" $headers @{
    parentId = 0; name = "Search Tablets $suffix"; status = 1; sortOrder = 0
}

$alpha = Invoke-Api Post "/api/products" $headers @{
    categoryId = $phoneCategory.data.id; name = "Search Phone Alpha $suffix"
    subtitle = "Phone search alpha"; price = 10.00; status = 1
}
$beta = Invoke-Api Post "/api/products" $headers @{
    categoryId = $phoneCategory.data.id; name = "Search Phone Beta $suffix"
    subtitle = "Phone search beta"; price = 20.00; status = 1
}
$tablet = Invoke-Api Post "/api/products" $headers @{
    categoryId = $tabletCategory.data.id; name = "Search Tablet Gamma $suffix"
    subtitle = "Tablet search gamma"; price = 30.00; status = 1
}
$hidden = Invoke-Api Post "/api/products" $headers @{
    categoryId = $phoneCategory.data.id; name = "Search Phone Hidden $suffix"
    subtitle = "Should not be searchable"; price = 15.00; status = 0
}

$keywordResult = Invoke-Api Get "/api/products/search?keyword=Search%20Phone%20$suffix&size=20" $headers
Assert-True ($keywordResult.data.total -eq 2) "keyword search should return two active phone products"
Assert-True (@($keywordResult.data.items | ForEach-Object id) -notcontains $hidden.data.id) "inactive product appeared in keyword search"

$categoryResult = Invoke-Api Get "/api/products/search?categoryId=$($phoneCategory.data.id)&size=20" $headers
Assert-True ($categoryResult.data.total -eq 2) "category filter should return two active phone products"

$priceResult = Invoke-Api Get "/api/products/search?keyword=Search%20Phone%20$suffix&minPrice=15&maxPrice=25" $headers
Assert-True ($priceResult.data.total -eq 1) "price range should return one product"
Assert-True ($priceResult.data.items[0].id -eq $beta.data.id) "price range returned the wrong product"

$pageOne = Invoke-Api Get "/api/products/search?keyword=Search%20Phone%20$suffix&page=1&size=1" $headers
$pageTwo = Invoke-Api Get "/api/products/search?keyword=Search%20Phone%20$suffix&page=2&size=1" $headers
Assert-True ($pageOne.data.total -eq 2 -and $pageTwo.data.total -eq 2) "pagination total is incorrect"
Assert-True (@($pageOne.data.items).Count -eq 1 -and @($pageTwo.data.items).Count -eq 1) "pagination page size is incorrect"
Assert-True ($pageOne.data.items[0].id -ne $pageTwo.data.items[0].id) "pagination returned the same product twice"

[PSCustomObject]@{
    Result = "PASS"
    KeywordMatches = $keywordResult.data.total
    CategoryMatches = $categoryResult.data.total
    PriceMatches = $priceResult.data.total
    PageOneProductId = $pageOne.data.items[0].id
    PageTwoProductId = $pageTwo.data.items[0].id
}

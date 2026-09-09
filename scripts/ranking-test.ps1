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
    if (-not $Condition) { throw "Ranking test failed: $Message" }
}

$suffix = Get-Date -Format "yyyyMMddHHmmssfff"
$username = "ranking$suffix"
$password = "SmokePass123"

Invoke-Api Post "/api/users/register" @{} @{ username = $username; password = $password; nickname = "Ranking Test" } | Out-Null
$login = Invoke-Api Post "/api/auth/login" @{} @{ username = $username; password = $password }
$headers = @{ Authorization = "Bearer $($login.data.accessToken)" }
$category = Invoke-Api Post "/api/categories" $headers @{ parentId = 0; name = "Ranking Category $suffix"; status = 1; sortOrder = 0 }

$first = Invoke-Api Post "/api/products" $headers @{
    categoryId = $category.data.id; name = "Ranking First $suffix"; subtitle = "Hot ranking test"; price = 10.00; status = 1
}
$second = Invoke-Api Post "/api/products" $headers @{
    categoryId = $category.data.id; name = "Ranking Second $suffix"; subtitle = "Hot ranking test"; price = 20.00; status = 1
}

1..3 | ForEach-Object { Invoke-Api Get "/api/products/$($first.data.id)" $headers | Out-Null }
Invoke-Api Get "/api/products/$($second.data.id)" $headers | Out-Null

$ranking = Invoke-Api Get "/api/products/hot?limit=50" $headers
$firstRank = @($ranking.data | ForEach-Object productId).IndexOf($first.data.id)
$secondRank = @($ranking.data | ForEach-Object productId).IndexOf($second.data.id)
Assert-True ($firstRank -ge 0 -and $secondRank -ge 0) "test products are missing from the ranking"
Assert-True ($firstRank -lt $secondRank) "higher view count was not ranked first"

[PSCustomObject]@{
    Result = "PASS"
    FirstProductId = $first.data.id
    SecondProductId = $second.data.id
    FirstRank = $firstRank + 1
    SecondRank = $secondRank + 1
}

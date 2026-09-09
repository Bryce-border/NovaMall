param(
    [string]$InventoryUrl = "http://localhost:8084",
    [int]$InitialStock = 20,
    [int]$RequestCount = 80
)

$ErrorActionPreference = "Stop"
if ($InitialStock -lt 1 -or $RequestCount -lt $InitialStock) {
    throw "InitialStock must be positive and RequestCount must be at least InitialStock"
}

function Invoke-Json {
    param([string]$Method, [string]$Path, [object]$Body)
    $parameters = @{
        Method = $Method
        Uri = "$InventoryUrl$Path"
        ContentType = "application/json"
    }
    if ($null -ne $Body) {
        $parameters.Body = $Body | ConvertTo-Json -Depth 8
    }
    Invoke-RestMethod @parameters
}

$productId = [long](Get-Date -Format "yyyyMMddHHmmssfff")
$created = Invoke-Json Post "/api/inventory" @{ productId = $productId; quantity = $InitialStock }
if ($created.data.availableStock -ne $InitialStock) {
    throw "Failed to initialize concurrency test inventory"
}

Add-Type -AssemblyName System.Net.Http
[System.Net.ServicePointManager]::DefaultConnectionLimit = $RequestCount
$client = [System.Net.Http.HttpClient]::new()
$url = "$InventoryUrl/internal/inventory/reserve"
$tasks = [System.Collections.Generic.List[System.Threading.Tasks.Task[System.Net.Http.HttpResponseMessage]]]::new()

try {
    1..$RequestCount | ForEach-Object {
        $body = @{ productId = $productId; quantity = 1; orderNo = "CONCURRENCY-$($_)-$productId" } |
            ConvertTo-Json -Compress
        $content = [System.Net.Http.StringContent]::new(
            $body, [System.Text.Encoding]::UTF8, "application/json")
        $tasks.Add($client.PostAsync($url, $content))
    }
    [System.Threading.Tasks.Task]::WaitAll($tasks.ToArray())
    $responses = $tasks | ForEach-Object { $_.Result.Content.ReadAsStringAsync().Result | ConvertFrom-Json }
} finally {
    $client.Dispose()
}

$successCount = @($responses | Where-Object { $_.success -eq $true }).Count
$stock = Invoke-Json Get "/api/inventory/$productId"
$available = [int]$stock.data.availableStock
$locked = [int]$stock.data.lockedStock
$total = [int]$stock.data.totalStock

if ($successCount -gt $InitialStock) { throw "Inventory oversold: $successCount successful reservations" }
if ($locked -ne $successCount) { throw "Reservation mismatch: locked=$locked successful=$successCount" }
if (($available + $locked) -ne $total -or $total -ne $InitialStock) {
    throw "Inventory invariant failed: total=$total available=$available locked=$locked"
}

[PSCustomObject]@{
    Result = "PASS"
    ProductId = $productId
    InitialStock = $InitialStock
    RequestCount = $RequestCount
    SuccessfulReservations = $successCount
    FailedReservations = $RequestCount - $successCount
    AvailableStock = $available
    LockedStock = $locked
}

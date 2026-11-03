param(
    [Parameter(Mandatory = $true)]
    [string]$LiffId,

    [switch]$PreviewOnly
)

$ErrorActionPreference = "Stop"

if ($LiffId -notmatch '^\d+-[A-Za-z0-9_-]+$') {
    throw "Invalid LIFF ID. Specify the LIFF ID from LINE Developers Console."
}

function ConvertFrom-CodePoints {
    param([int[]]$CodePoints)
    return -join ($CodePoints | ForEach-Object { [char]$_ })
}

Add-Type -AssemblyName System.Drawing

$imagePath = Join-Path $env:TEMP ("catsflow-rich-menu-{0}.png" -f [Guid]::NewGuid().ToString("N"))
$bitmap = New-Object System.Drawing.Bitmap(2500, 843)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
$graphics.Clear([System.Drawing.Color]::FromArgb(28, 57, 46))

$whiteBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
$mutedBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(210, 226, 216))
$accentBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(226, 165, 75))
$inkBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(28, 57, 46))
$brandFont = New-Object System.Drawing.Font("Yu Gothic UI", 48, [System.Drawing.FontStyle]::Bold)
$headlineFont = New-Object System.Drawing.Font("Yu Gothic UI", 112, [System.Drawing.FontStyle]::Bold)
$captionFont = New-Object System.Drawing.Font("Yu Gothic UI", 38, [System.Drawing.FontStyle]::Regular)
$buttonFont = New-Object System.Drawing.Font("Yu Gothic UI", 46, [System.Drawing.FontStyle]::Bold)
$headlineText = ConvertFrom-CodePoints @(0x5BB6, 0x8A08, 0x30FB, 0x8CC7, 0x7523, 0x3092, 0x30C1, 0x30A7, 0x30C3, 0x30AF)
$captionText = (ConvertFrom-CodePoints @(0x30BF, 0x30C3, 0x30D7, 0x3057, 0x3066)) + " Catsflow" + (ConvertFrom-CodePoints @(0x3092, 0x958B, 0x304F))
$buttonText = (ConvertFrom-CodePoints @(0x30A2, 0x30D7, 0x30EA, 0x3092, 0x958B, 0x304F)) + "  >"
$chatBarText = "Catsflow" + (ConvertFrom-CodePoints @(0x3092, 0x958B, 0x304F))

try {
    $graphics.FillEllipse($accentBrush, 2180, -180, 480, 480)
    $graphics.FillEllipse((New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(50, 255, 255, 255))), 2040, 590, 300, 300)
    $graphics.DrawString("CATSFlow", $brandFont, $whiteBrush, 150, 112)
    $graphics.DrawString($headlineText, $headlineFont, $whiteBrush, 145, 260)
    $graphics.DrawString($captionText, $captionFont, $mutedBrush, 155, 470)
    $graphics.FillRectangle($accentBrush, 1740, 310, 610, 190)
    $graphics.DrawString($buttonText, $buttonFont, $inkBrush, 1790, 365)
    $bitmap.Save($imagePath, [System.Drawing.Imaging.ImageFormat]::Png)
}
finally {
    $brandFont.Dispose()
    $headlineFont.Dispose()
    $captionFont.Dispose()
    $buttonFont.Dispose()
    $whiteBrush.Dispose()
    $mutedBrush.Dispose()
    $accentBrush.Dispose()
    $inkBrush.Dispose()
    $graphics.Dispose()
    $bitmap.Dispose()
}

if ($PreviewOnly) {
    Write-Output $imagePath
    return
}

$confirmation = Read-Host "This replaces the current default menu for all friends. Continue (y/N)"
if ($confirmation -notmatch '^(y|yes|\uFF59|\uFF39)$') {
    Remove-Item $imagePath -Force
    Write-Output "Cancelled. The Official Account was not changed."
    return
}

$secureToken = Read-Host "Enter the Messaging API channel access token" -AsSecureString
$tokenPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureToken)
$accessToken = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($tokenPointer)
[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($tokenPointer)

if ([string]::IsNullOrWhiteSpace($accessToken)) {
    Remove-Item $imagePath -Force
    throw "The channel access token is empty."
}

$headers = @{ Authorization = "Bearer $accessToken" }
$richMenuId = $null
$isDefault = $false
$liffUrl = "https://liff.line.me/$LiffId"
$richMenu = @{
    size = @{ width = 2500; height = 843 }
    selected = $true
    name = "Catsflow Mini App"
    chatBarText = $chatBarText
    areas = @(
        @{
            bounds = @{ x = 0; y = 0; width = 2500; height = 843 }
            action = @{ type = "uri"; label = $chatBarText; uri = $liffUrl }
        }
    )
}

try {
    $body = $richMenu | ConvertTo-Json -Depth 8
    $created = Invoke-RestMethod `
        -Method Post `
        -Uri "https://api.line.me/v2/bot/richmenu" `
        -Headers $headers `
        -ContentType "application/json; charset=utf-8" `
        -Body ([System.Text.Encoding]::UTF8.GetBytes($body))
    $richMenuId = $created.richMenuId

    Invoke-RestMethod `
        -Method Post `
        -Uri "https://api-data.line.me/v2/bot/richmenu/$richMenuId/content" `
        -Headers $headers `
        -ContentType "image/png" `
        -InFile $imagePath | Out-Null

    Invoke-RestMethod `
        -Method Post `
        -Uri "https://api.line.me/v2/bot/user/all/richmenu/$richMenuId" `
        -Headers $headers | Out-Null
    $isDefault = $true

    Write-Output "Catsflow rich menu is now the Official Account default."
    Write-Output "Reopen the Official Account chat to check the menu."
}
catch {
    if ($richMenuId -and -not $isDefault) {
        try {
            Invoke-RestMethod `
                -Method Delete `
                -Uri "https://api.line.me/v2/bot/richmenu/$richMenuId" `
                -Headers $headers | Out-Null
        }
        catch {
            Write-Warning "Could not clean up the new rich menu. Check Rich menus in LINE Developers Console."
        }
    }
    throw "Failed to configure the rich menu. Check the Messaging API token and Official Account link. Details: $($_.Exception.Message)"
}
finally {
    Remove-Item $imagePath -Force -ErrorAction SilentlyContinue
    $accessToken = $null
    $headers = $null
}
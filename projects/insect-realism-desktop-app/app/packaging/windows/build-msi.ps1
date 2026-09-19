# Native x64 MSVC build and per-user MSI. Requires Rust, Python 3.11+, .NET SDK, Windows SDK.
[CmdletBinding()]
param([string]$OutputDirectory = '')
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if ($env:OS -ne 'Windows_NT') { throw 'A Windows host with MSVC and Windows SDK is required.' }
$Root = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$App = Join-Path $Root 'app'
function Invoke-Checked([scriptblock]$Command) { & $Command; if ($LASTEXITCODE -ne 0) { throw "Command failed with exit code $LASTEXITCODE" } }
$Version = & python -c 'import tomllib,sys; print(tomllib.load(open(sys.argv[1],"rb"))["workspace"]["package"]["version"])' (Join-Path $App 'Cargo.toml')
if ($LASTEXITCODE -ne 0) { throw 'Cannot read application version' }
if (-not $OutputDirectory) { $OutputDirectory = Join-Path $Root "dist/windows-x64-$Version" }
New-Item -ItemType Directory -Force $OutputDirectory | Out-Null
$OutputDirectory = (Resolve-Path -LiteralPath $OutputDirectory).Path
$Stage = Join-Path ([System.IO.Path]::GetTempPath()) ('insect-package-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory $Stage | Out-Null
try {
    Push-Location $App
    try {
        Invoke-Checked { cargo build --locked --release -p desktop-app --target x86_64-pc-windows-msvc }
        $Metadata = cargo metadata --no-deps --format-version=1 | ConvertFrom-Json
        if ($LASTEXITCODE -ne 0) { throw 'Cargo metadata failed' }
    } finally { Pop-Location }
    $Licenses = Join-Path $Stage 'notices'
    Invoke-Checked { python (Join-Path $Root 'scripts/collect_licenses.py') --manifest (Join-Path $App 'Cargo.toml') --target x86_64-pc-windows-msvc --output $Licenses }
    $Payload = Join-Path $Stage 'Insect Realism'
    $Binary = Join-Path $Metadata.target_directory 'x86_64-pc-windows-msvc/release/desktop-app.exe'
    Invoke-Checked { python (Join-Path $Root 'scripts/stage_package.py') --platform windows --binary $Binary --output $Payload --licenses $Licenses }
    $Exe = Join-Path $Payload 'InsectRealism.exe'
    if ($env:WINDOWS_CERT_SHA1) {
        Invoke-Checked { signtool sign /fd SHA256 /sha1 $env:WINDOWS_CERT_SHA1 /tr http://timestamp.digicert.com /td SHA256 $Exe }
        Invoke-Checked { signtool verify /pa $Exe }
    }
    Invoke-Checked { python (Join-Path $Root 'scripts/verify_package.py') $Payload --platform windows --write-manifest --verify-manifest --json (Join-Path $OutputDirectory 'payload-inspection.json') }
    $Fragment = Join-Path $Stage 'Payload.wxs'
    Invoke-Checked { python (Join-Path $Root 'scripts/wix_payload.py') $Payload $Fragment }
    $Msi = Join-Path $OutputDirectory "InsectRealism-$Version-x64.msi"
    Push-Location $PSScriptRoot
    try {
        Invoke-Checked { dotnet tool restore }
        Invoke-Checked { dotnet tool run wix build (Join-Path $PSScriptRoot 'wix/Product.wxs') $Fragment -arch x64 -d "AppVersion=$Version" -d "AppIcon=$(Join-Path $Payload 'Resources/InsectRealism.ico')" -o $Msi }
    } finally { Pop-Location }
    if ($env:WINDOWS_CERT_SHA1) {
        Invoke-Checked { signtool sign /fd SHA256 /sha1 $env:WINDOWS_CERT_SHA1 /tr http://timestamp.digicert.com /td SHA256 $Msi }
        Invoke-Checked { signtool verify /pa $Msi }
    }
    Compress-Archive -Path $Payload -DestinationPath (Join-Path $OutputDirectory "InsectRealism-$Version-x64-portable.zip") -Force
    Get-ChildItem $OutputDirectory -File | Where-Object { $_.Extension -in '.msi','.zip' } |
        Get-FileHash -Algorithm SHA256 | ForEach-Object { "$($_.Hash.ToLower())  $([System.IO.Path]::GetFileName($_.Path))" } |
        Set-Content -Encoding ascii (Join-Path $OutputDirectory 'SHA256SUMS.txt')
    Write-Host "Built $Msi. Native interactive/install acceptance remains a separate release gate."
} finally {
    Remove-Item -Recurse -Force $Stage
}

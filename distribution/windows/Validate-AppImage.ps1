[CmdletBinding()]
param(
    # The release version. The script changes it as Build-AppImage.ps1 does to get the jpackage --app-version.
    [Parameter(Mandatory = $true)]
    [string]$Version,
    # The app image folder. The default is the output folder of Build-AppImage.ps1.
    [string]$AppHome
)

# Checks the build information and the runtime modules of the app image (see "Build expiry" in
# docs/licensing/build-expiry-spec.md). The release workflow runs this script directly after
# Build-AppImage.ps1. Do not put these checks in the packaged diagnostics: users also run the
# diagnostics, and the check of the build date fails for them 7 days after the build.

$ErrorActionPreference = "Stop"
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
if (-not $AppHome) {
    $AppHome = Join-Path $repoRoot "target\package\app-image\BananaShot"
}

$packageVersion = ($Version -replace '^v', '') -replace '-.*$', ''
$javaCommand = Join-Path $AppHome "runtime\bin\java.exe"
if (-not (Test-Path -LiteralPath $javaCommand)) {
    throw "The bundled java.exe was not found: $javaCommand"
}

# Windows PowerShell stops at a native stderr line when ErrorActionPreference is Stop.
# The script checks the exit codes of java.exe instead.
function Invoke-Java([string[]]$Arguments) {
    $ErrorActionPreference = "Continue"
    $lines = & $javaCommand @Arguments 2>&1 | ForEach-Object { "$_" }
    return [pscustomobject]@{ ExitCode = $LASTEXITCODE; Lines = @($lines) }
}

# Build information.
$classPath = Join-Path $AppHome "app\*"
$result = Invoke-Java @("-cp", $classPath, "org.litvin.license.BuildInfoPrinter")
$output = $result.Lines
if ($result.ExitCode -ne 0) {
    throw "BuildInfoPrinter did not run (exit code $($result.ExitCode)):`n$($output -join "`n")"
}
$values = @{}
foreach ($line in $output) {
    if ($line -match '^(buildDate|expiryDate|version)=(.*)$') {
        $values[$Matches[1]] = $Matches[2].Trim()
    }
}
foreach ($key in @("buildDate", "expiryDate", "version")) {
    if (-not $values[$key]) {
        throw "BuildInfoPrinter printed no $key. Output:`n$($output -join "`n")"
    }
}

$culture = [Globalization.CultureInfo]::InvariantCulture
function ConvertTo-Date([string]$Name, [string]$Text) {
    $date = [DateTime]::MinValue
    if (-not [DateTime]::TryParseExact($Text, "yyyy-MM-dd", $culture, [Globalization.DateTimeStyles]::None, [ref]$date)) {
        throw "The $Name '$Text' is not a yyyy-MM-dd date."
    }
    return $date
}
$buildDate = ConvertTo-Date "build date" $values["buildDate"]
$expiryDate = ConvertTo-Date "expiry date" $values["expiryDate"]

# DateTime.AddMonths and LocalDate.plusMonths both give the last day of the month when the day does not exist.
$expectedExpiry = $buildDate.AddMonths(6)
if ($expiryDate -ne $expectedExpiry) {
    throw "The expiry date $($values["expiryDate"]) is not 6 calendar months after the build date $($values["buildDate"]) (expected $($expectedExpiry.ToString("yyyy-MM-dd", $culture)))."
}

$today = [DateTime]::UtcNow.Date
if ($buildDate -lt $today.AddDays(-7)) {
    throw "The build date $($values["buildDate"]) is more than 7 days before today ($($today.ToString("yyyy-MM-dd", $culture)) UTC). Build the app again."
}
# The 1 day covers the time zones.
if ($buildDate -gt $today.AddDays(1)) {
    throw "The build date $($values["buildDate"]) is later than today ($($today.ToString("yyyy-MM-dd", $culture)) UTC) plus 1 day."
}

if ($values["version"] -ne $packageVersion) {
    throw "The app version $($values["version"]) is not the jpackage --app-version $packageVersion."
}

# Runtime modules for the request of the rules file (see "Trust for the HTTPS connection" in the spec).
$runtimeRelease = Join-Path $AppHome "runtime\release"
if (-not (Test-Path -LiteralPath $runtimeRelease)) {
    throw "The runtime release file was not found: $runtimeRelease"
}
$javaVersionLine = Get-Content -LiteralPath $runtimeRelease | Where-Object { $_ -like "JAVA_VERSION=*" } | Select-Object -First 1
if ($javaVersionLine -notmatch '^JAVA_VERSION="(\d+)') {
    throw "The runtime release file has no valid JAVA_VERSION: $javaVersionLine"
}
$javaFeature = [int]$Matches[1]

$moduleResult = Invoke-Java @("--list-modules")
$moduleOutput = $moduleResult.Lines
if ($moduleResult.ExitCode -ne 0) {
    throw "java.exe --list-modules failed (exit code $($moduleResult.ExitCode)):`n$($moduleOutput -join "`n")"
}
$modules = $moduleOutput | ForEach-Object { ($_ -split '@')[0].Trim() } | Where-Object { $_ }

$requiredModules = @("jdk.crypto.mscapi", "java.net.http")
# From JDK 22, the elliptic-curve algorithms are in java.base, and jdk.crypto.ec is deprecated for removal.
if ($javaFeature -lt 22) {
    $requiredModules += "jdk.crypto.ec"
}
$missingModules = @($requiredModules | Where-Object { $modules -notcontains $_ })
if ($missingModules.Count -gt 0) {
    throw "The bundled runtime (JDK $javaFeature) does not contain these modules: $($missingModules -join ', '). Add them with --add-modules in Build-AppImage.ps1, with the full list of modules that the app needs."
}

Write-Host "Build date: $($values["buildDate"]) UTC"
Write-Host "Expiry date: $($values["expiryDate"])"
Write-Host "Version: $($values["version"])"
Write-Host "Runtime: JDK $javaFeature, modules present: $($requiredModules -join ', ')"
Write-Host "The application image is valid."

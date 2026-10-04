[CmdletBinding()]
param(
    # The output of Build-VelopackRelease.ps1: the setup EXE and the update packages.
    [string]$VelopackDirectory
)

# Checks the code signatures of a signed release (B-22). Smart App Control blocks each unsigned EXE or DLL
# that it does not know, and the user cannot allow it. One unsigned native file is enough to break the
# preview or the export. So each EXE and DLL in the setup and in the update packages must have a valid
# signature. The files of the Java runtime keep the signatures of their vendors; the other files have
# the signature of the Certum certificate.
#
# Known gap: JNA extracts jnidispatch.dll from its JAR at run time. This check does not see that file.

$ErrorActionPreference = "Stop"
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
if (-not $VelopackDirectory) { $VelopackDirectory = Join-Path $repoRoot "target\package\velopack" }
$VelopackDirectory = [IO.Path]::GetFullPath($VelopackDirectory)
$extractRoot = Join-Path $repoRoot "target\signature-check"
Add-Type -AssemblyName System.IO.Compression.FileSystem

$failures = [Collections.Generic.List[string]]::new()
function Test-Signature([string]$Path, [string]$DisplayName) {
    $signature = Get-AuthenticodeSignature -LiteralPath $Path
    if ($signature.Status -ne "Valid") {
        $failures.Add("$DisplayName`: $($signature.Status)")
    } elseif (-not $signature.TimeStamperCertificate) {
        # Without a timestamp, the signature becomes invalid when the certificate expires.
        $failures.Add("$DisplayName`: no timestamp")
    }
}

$setup = Join-Path $VelopackDirectory "BananaShot-win-Setup.exe"
if (-not (Test-Path -LiteralPath $setup)) { throw "The setup EXE is missing: $setup" }
Test-Signature $setup "BananaShot-win-Setup.exe"

$packages = @(Get-ChildItem -LiteralPath $VelopackDirectory -Filter "*.nupkg")
if ($packages.Count -eq 0) { throw "No update package in $VelopackDirectory." }
$checked = 0
$checkedNames = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
foreach ($package in $packages) {
    if (Test-Path -LiteralPath $extractRoot) { Remove-Item -LiteralPath $extractRoot -Recurse -Force }
    [IO.Compression.ZipFile]::ExtractToDirectory($package.FullName, $extractRoot)
    $binaries = Get-ChildItem -LiteralPath $extractRoot -Recurse -File |
        Where-Object { $_.Extension -in @(".exe", ".dll") }
    foreach ($binary in $binaries) {
        $relative = $binary.FullName.Substring($extractRoot.Length + 1)
        Test-Signature $binary.FullName "$($package.Name)!$relative"
        $checkedNames.Add($binary.Name) | Out-Null
        $checked++
    }
}
Remove-Item -LiteralPath $extractRoot -Recurse -Force

# The check must not pass because it found no files.
foreach ($required in @("BananaShot.exe", "libmpv-2.dll", "ffmpeg.exe", "ffprobe.exe", "jvm.dll")) {
    if (-not $checkedNames.Contains($required)) {
        $failures.Add("$required was not found in the update package")
    }
}

if ($failures.Count -gt 0) {
    throw "$($failures.Count) file(s) are not correctly signed:`n  $($failures -join "`n  ")"
}
Write-Host "Signature check passed: the setup EXE and $checked EXE and DLL files in the update package."

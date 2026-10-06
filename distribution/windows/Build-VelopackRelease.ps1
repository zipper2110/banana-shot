[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Version,
    # The vpk command. The release workflow installs it as a .NET global tool (see windows-release.yml).
    [string]$Vpk = "vpk",
    # The signing command for vpk (B-22). {{file...}} stands for the files to sign. Without it, the files are unsigned.
    # vpk signs each EXE and DLL in the app image that does not have a trusted signature yet (the Java runtime keeps
    # its signatures), then the setup EXE, Update.exe, and the start stub.
    [string]$SignTemplate
)

# Makes the Velopack installer and the update package from the app image of Build-AppImage.ps1 (B-24).
# Velopack installs for each user in %LocalAppData%\BananaShot with no administrator rights. The app
# data (%APPDATA%\BananaShot) and the preferences (HKCU\Software\JavaSoft\Prefs) are in other places,
# so an update or an uninstall does not delete them.
#
# Output in target\package\velopack:
# - BananaShot-win-Setup.exe: the installer. The name is the same in each release, because the app
#   downloads releases/latest/download/BananaShot-win-Setup.exe ("Update and restart" in
#   docs/licensing/build-expiry-spec.md).
# - BananaShot-<version>-full.nupkg: the update package.
# - releases.win.json: the release feed.

$ErrorActionPreference = "Stop"
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$appImage = Join-Path $repoRoot "target\package\app-image\BananaShot"
$outputDirectory = Join-Path $repoRoot "target\package\velopack"
$icon = Join-Path $PSScriptRoot "assets\bananashot.ico"
$setupName = "BananaShot-win-Setup.exe"
$packageVersion = ($Version -replace '^v', '') -replace '-.*$', ''

if ($packageVersion -notmatch '^\d+\.\d+\.\d+$') {
    throw "Velopack requires a MAJOR.MINOR.PATCH version; got '$packageVersion'."
}
if (-not (Test-Path -LiteralPath (Join-Path $appImage "BananaShot.exe"))) {
    throw "The application image is missing: $appImage. Run Build-AppImage.ps1 first."
}
if (-not (Get-Command $Vpk -ErrorAction SilentlyContinue)) {
    throw "vpk was not found. Install it with: dotnet tool install --global vpk --version <the version of windows-release.yml>"
}

$resolvedOutput = [IO.Path]::GetFullPath($outputDirectory)
$resolvedTarget = [IO.Path]::GetFullPath((Join-Path $repoRoot "target")).TrimEnd('\') + '\'
if (-not $resolvedOutput.StartsWith($resolvedTarget, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to replace the Velopack output outside target: $resolvedOutput"
}
if (Test-Path -LiteralPath $outputDirectory) {
    Remove-Item -LiteralPath $outputDirectory -Recurse -Force
}
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null

$arguments = @(
    "pack",
    "--packId", "BananaShot",
    "--packVersion", $packageVersion,
    "--packDir", $appImage,
    "--mainExe", "BananaShot.exe",
    "--packTitle", "BananaShot",
    "--packAuthors", "BananaShot",
    "--icon", $icon,
    "--outputDir", $outputDirectory,
    "--channel", "win",
    "--runtime", "win-x64",
    "--shortcuts", "Desktop,StartMenuRoot",
    "--noPortable",
    # The app does not use the Velopack SDK. SwingMainApp exits at once for the --veloapp-* hook
    # arguments ("Velopack hook processes" in build-expiry-spec.md). Without this option, vpk refuses
    # an app with no VelopackApp builder.
    "--skipVeloAppCheck"
)
if ($SignTemplate) {
    if ($SignTemplate -notmatch '\{\{file(\.\.\.)?\}\}') {
        throw "-SignTemplate must contain {{file}} or {{file...}}."
    }
    $arguments += @("--signTemplate", $SignTemplate)
    # jpackage makes the launcher EXE read-only. vpk keeps this attribute in its copy, and signtool then fails
    # with "Access is denied".
    Get-ChildItem -LiteralPath $appImage -Recurse -File | Where-Object IsReadOnly | ForEach-Object { $_.IsReadOnly = $false }
}
& $Vpk @arguments
if ($LASTEXITCODE -ne 0) { throw "vpk pack failed." }

$setup = Join-Path $outputDirectory $setupName
if (-not (Test-Path -LiteralPath $setup)) {
    $found = @(Get-ChildItem -LiteralPath $outputDirectory -Filter "*Setup.exe")
    throw "vpk did not make $setupName. Found: $($found.Name -join ', '). The app needs this fixed name."
}
$package = Join-Path $outputDirectory "BananaShot-$packageVersion-full.nupkg"
if (-not (Test-Path -LiteralPath $package)) { throw "vpk did not make the update package $package." }
if (-not (Test-Path -LiteralPath (Join-Path $outputDirectory "releases.win.json"))) {
    throw "vpk did not make the release feed releases.win.json."
}
Write-Host "Velopack installer created: $setup"

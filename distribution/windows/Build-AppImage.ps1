[CmdletBinding()]
param(
    [string]$Version,
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$targetRoot = Join-Path $repoRoot "target"
$inputDirectory = Join-Path $targetRoot "distribution\input"
$nativeDirectory = Join-Path $targetRoot "native\windows-x64"
$packageRoot = Join-Path $targetRoot "package"
$appImageRoot = Join-Path $packageRoot "app-image"
$icon = Join-Path $PSScriptRoot "assets\bananashot.ico"

if (-not $IsWindows -and $PSVersionTable.PSEdition -eq "Core") {
    throw "The Windows application image must be built on Windows."
}
if (-not [Environment]::Is64BitOperatingSystem) {
    throw "Windows x64 is required."
}
if (-not (Get-Command jpackage.exe -ErrorAction SilentlyContinue)) {
    throw "jpackage.exe was not found. Install and select JDK 25."
}
$jdkRelease = Join-Path $env:JAVA_HOME "release"
if (-not (Test-Path -LiteralPath $jdkRelease)) {
    throw "JAVA_HOME must point to a JDK 25 installation."
}
$javaVersionLine = Get-Content -LiteralPath $jdkRelease | Where-Object { $_ -like "JAVA_VERSION=*" } | Select-Object -First 1
if ($javaVersionLine -notmatch 'JAVA_VERSION="25(\.|")') {
    throw "JAVA_HOME must point to JDK 25; found $javaVersionLine."
}

if (-not $Version) {
    $Version = (& mvn "-Dexpression=project.version" "-DforceStdout" -q help:evaluate | Select-Object -Last 1).Trim()
}
$packageVersion = ($Version -replace '^v', '') -replace '-.*$', ''
if ($packageVersion -notmatch '^\d+(\.\d+){0,3}$') {
    throw "jpackage requires a numeric version; got '$packageVersion'."
}

if (-not $SkipBuild) {
    $resolvedInput = [IO.Path]::GetFullPath($inputDirectory)
    $resolvedTargetForInput = [IO.Path]::GetFullPath($targetRoot).TrimEnd('\') + '\'
    if (-not $resolvedInput.StartsWith($resolvedTargetForInput, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to replace distribution input outside target: $resolvedInput"
    }
    if (Test-Path -LiteralPath $inputDirectory) {
        Remove-Item -LiteralPath $inputDirectory -Recurse -Force
    }
    & mvn -B -Pdistribution -DskipTests "-Drevision=$packageVersion" package
    if ($LASTEXITCODE -ne 0) { throw "Maven distribution build failed." }
}
if (-not (Test-Path -LiteralPath (Join-Path $nativeDirectory "mpv\libmpv-2.dll"))) {
    throw "Native dependencies are missing. Run distribution/windows/Get-NativeDependencies.ps1."
}
if (-not (Test-Path -LiteralPath $icon)) {
    & (Join-Path $PSScriptRoot "New-AppIcon.ps1") -OutputPath $icon
}

$mainJar = Get-ChildItem -LiteralPath $inputDirectory -Filter "bananashot-*.jar" |
    Where-Object { $_.Name -notlike "*sources*" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $mainJar) {
    throw "Application JAR was not found in $inputDirectory."
}

$resolvedAppImageRoot = [IO.Path]::GetFullPath($appImageRoot)
$resolvedTargetRoot = [IO.Path]::GetFullPath($targetRoot).TrimEnd('\') + '\'
if (-not $resolvedAppImageRoot.StartsWith($resolvedTargetRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to replace app-image outside target: $resolvedAppImageRoot"
}
if (Test-Path -LiteralPath $appImageRoot) {
    Remove-Item -LiteralPath $appImageRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $appImageRoot -Force | Out-Null

# The modules of the bundled runtime. --add-modules replaces the default list of jpackage, so this
# list must contain each module that the app needs. To find the list, run on the distribution input:
#   jdeps --print-module-deps --ignore-missing-deps --multi-release 25 --class-path "<input>\*" "<input>ananashot-*.jar"
# Then add the modules that jdeps cannot find because the code loads them only through a service:
# - jdk.crypto.mscapi: the Windows-ROOT key store (build-expiry-spec.md, "Trust for the HTTPS connection").
# - jdk.localedata: the date and number formats of other locales than English.
# - jdk.charsets: the code pages of Windows that java.base does not have.
# - jdk.accessibility: the Java Access Bridge for screen readers.
# Validate-AppImage.ps1 checks the modules of the expiry spec in the result.
# jdeps result of 2026-10-03 (JDK 25): java.base, java.desktop, java.naming, java.net.http, java.prefs,
# java.sql. java.logging and java.xml come with them; the list names them because the libraries use them.
$runtimeModules = @(
    "java.base",
    "java.desktop",
    "java.logging",
    "java.naming",
    "java.net.http",
    "java.prefs",
    "java.sql",
    "java.xml",
    "jdk.accessibility",
    "jdk.charsets",
    "jdk.crypto.mscapi",
    "jdk.localedata"
)

$arguments = @(
    "--type", "app-image",
    "--name", "BananaShot",
    "--dest", $appImageRoot,
    "--input", $inputDirectory,
    "--main-jar", $mainJar.Name,
    "--main-class", "org.litvin.SwingMainApp",
    "--app-version", $packageVersion,
    "--vendor", "BananaShot",
    "--description", "Turn tennis match recordings into compact scored videos.",
    "--copyright", "Copyright (c) 2026 BananaShot",
    "--icon", $icon,
    "--add-modules", ($runtimeModules -join ","),
    "--java-options", "-Dfile.encoding=UTF-8",
    # JNA calls native code. From JDK 24, the JVM shows a warning for this without the option.
    "--java-options", "--enable-native-access=ALL-UNNAMED"
)
& jpackage @arguments
if ($LASTEXITCODE -ne 0) { throw "jpackage app-image creation failed." }

$appHome = Join-Path $appImageRoot "BananaShot"
$javaCommand = Join-Path $env:JAVA_HOME "bin\java.exe"
if (-not (Test-Path -LiteralPath $javaCommand)) {
    throw "JDK 25 java.exe was not found: $javaCommand"
}
Copy-Item -LiteralPath $javaCommand -Destination (Join-Path $appHome "runtime\bin\java.exe")
New-Item -ItemType Directory -Path (Join-Path $appHome "natives") -Force | Out-Null
Copy-Item -LiteralPath $nativeDirectory -Destination (Join-Path $appHome "natives") -Recurse -Force
New-Item -ItemType Directory -Path (Join-Path $appHome "legal") -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $repoRoot "LICENSE") -Destination (Join-Path $appHome "legal\LICENSE.txt")
Copy-Item -LiteralPath (Join-Path $repoRoot "LICENSE-NOTICE") -Destination (Join-Path $appHome "legal\LICENSE-NOTICE.txt")
Copy-Item -LiteralPath (Join-Path $repoRoot "distribution\THIRD-PARTY-NOTICES.txt") -Destination (Join-Path $appHome "legal\THIRD-PARTY-NOTICES.txt")
Copy-Item -LiteralPath (Join-Path $PSScriptRoot "BananaShot Diagnostics.cmd") -Destination (Join-Path $appHome "BananaShot Diagnostics.cmd")

Write-Host "Application image created: $appHome"

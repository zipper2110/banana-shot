[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Version
)

$ErrorActionPreference = "Stop"
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$appImage = Join-Path $repoRoot "target\package\app-image\BananaShot"
$installerDirectory = Join-Path $repoRoot "target\package\installer"
$tempDirectory = Join-Path $repoRoot "target\package\jpackage-installer-temp"
$installerLicense = Join-Path $repoRoot "target\package\BananaShot-License.rtf"
$packageVersion = ($Version -replace '^v', '') -replace '-.*$', ''

if (-not (Test-Path -LiteralPath (Join-Path $appImage "BananaShot.exe"))) {
    throw "Signed application image is missing: $appImage"
}
if (-not (Get-Command candle.exe -ErrorAction SilentlyContinue)) {
    throw "WiX Toolset 3 is required to build the Windows EXE installer."
}

$resolvedInstaller = [IO.Path]::GetFullPath($installerDirectory)
$resolvedTarget = [IO.Path]::GetFullPath((Join-Path $repoRoot "target")).TrimEnd('\') + '\'
if (-not $resolvedInstaller.StartsWith($resolvedTarget, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to replace installer output outside target: $resolvedInstaller"
}
if (Test-Path -LiteralPath $installerDirectory) {
    Remove-Item -LiteralPath $installerDirectory -Recurse -Force
}
New-Item -ItemType Directory -Path $installerDirectory -Force | Out-Null

# jpackage converts a plain-text license to RTF itself. That conversion shows
# each character above U+00FF as "?" and joins all lines of a paragraph, so
# list items run together. Thus this script writes the RTF file.
function ConvertTo-RtfText([string]$Text) {
    $builder = New-Object System.Text.StringBuilder
    foreach ($character in $Text.ToCharArray()) {
        $code = [int]$character
        if ($character -eq '\' -or $character -eq '{' -or $character -eq '}') {
            [void]$builder.Append('\').Append($character)
        } elseif ($code -eq 9) {
            [void]$builder.Append('\tab ')
        } elseif ($code -gt 127) {
            # RTF writes \uN as a signed 16-bit number. "?" is the fallback character.
            if ($code -gt 32767) { $code -= 65536 }
            [void]$builder.Append('\u').Append($code).Append('?')
        } else {
            [void]$builder.Append($character)
        }
    }
    $builder.ToString()
}

# The source files are wrapped for width. Join a line to the next line, so the
# text wraps to the width of the dialog. Keep the line break before a list item,
# after a short line (a title or a copyright line), and after a URL line.
function ConvertTo-RtfParagraphs([string[]]$Lines) {
    $width = ($Lines | Measure-Object -Property Length -Maximum).Maximum
    $shortLine = [int]($width * 2 / 3)
    $builder = New-Object System.Text.StringBuilder
    for ($index = 0; $index -lt $Lines.Count; $index++) {
        $line = $Lines[$index].TrimEnd()
        [void]$builder.Append((ConvertTo-RtfText $line))
        $next = if ($index + 1 -lt $Lines.Count) { $Lines[$index + 1].Trim() } else { "" }
        if ($line.Length -eq 0 -or $next.Length -eq 0) {
            [void]$builder.Append("\par`r`n")
        } elseif ($line.Length -lt $shortLine -or $line -match '^https?://\S+$' -or
                  $next -match '^([-*]|\d+\.)\s') {
            [void]$builder.Append("\line`r`n")
        } else {
            [void]$builder.Append(" `r`n")
        }
    }
    $builder.ToString()
}

$licenseRtf = New-Object System.Text.StringBuilder
[void]$licenseRtf.Append("{\rtf1\ansi\ansicpg1252\deff0{\fonttbl{\f0\fswiss\fcharset0 Arial;}}`r`n")
[void]$licenseRtf.Append("\viewkind4\uc1\pard\sa0\f0\fs18`r`n")
$licenseSources = @(
    (Join-Path $repoRoot "LICENSE-NOTICE"),
    (Join-Path $repoRoot "LICENSE"),
    (Join-Path $repoRoot "distribution\THIRD-PARTY-NOTICES.txt")
)
foreach ($source in $licenseSources) {
    $lines = @(Get-Content -LiteralPath $source -Encoding utf8)
    [void]$licenseRtf.Append((ConvertTo-RtfParagraphs $lines)).Append("\par`r`n")
}
[void]$licenseRtf.Append("}`r`n")
[IO.File]::WriteAllText($installerLicense, $licenseRtf.ToString(), [Text.Encoding]::ASCII)
$resolvedTemp = [IO.Path]::GetFullPath($tempDirectory)
if (-not $resolvedTemp.StartsWith($resolvedTarget, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to replace jpackage temp directory outside target: $resolvedTemp"
}
if (Test-Path -LiteralPath $tempDirectory) {
    Remove-Item -LiteralPath $tempDirectory -Recurse -Force
}
$arguments = @(
    "--type", "exe",
    "--name", "BananaShot",
    "--app-image", $appImage,
    "--dest", $installerDirectory,
    "--temp", $tempDirectory,
    "--verbose",
    "--app-version", $packageVersion,
    "--vendor", "BananaShot",
    "--description", "Turn tennis match recordings into compact scored videos.",
    "--copyright", "Copyright (c) 2026 BananaShot",
    "--license-file", $installerLicense,
    "--win-per-user-install",
    "--win-menu",
    "--win-menu-group", "BananaShot",
    "--win-shortcut",
    "--win-shortcut-prompt",
    "--win-upgrade-uuid", "fc715967-3696-4f32-8690-4df9742077c6"
)
& jpackage @arguments
if ($LASTEXITCODE -ne 0) { throw "jpackage EXE installer creation failed." }

$installer = Get-ChildItem -LiteralPath $installerDirectory -Filter "*.exe" |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $installer) { throw "jpackage did not produce an EXE installer." }
Write-Host "Installer created: $($installer.FullName)"

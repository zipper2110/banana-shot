[CmdletBinding()]
param(
    # A dry run of the release workflow. The L-5.2 story check gives a warning, not an error.
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$repoRoot = Join-Path $PSScriptRoot "..\.."

function Read-RepoFile([string]$RelativePath) {
    return Get-Content -LiteralPath (Join-Path $repoRoot $RelativePath) -Raw -Encoding utf8
}

$licenseText = Read-RepoFile "LICENSE"
if ($licenseText -notmatch "^Elastic License 2\.0" -or
    $licenseText -notmatch "You may not move, change, disable, or circumvent the license key functionality") {
    throw "The repository LICENSE must contain the Elastic License 2.0 text."
}

$notice = Read-RepoFile "LICENSE-NOTICE"
foreach ($required in @(
    "Copyright \(c\) \d{4} Dmitrii Litvin",
    "Elastic License 2\.0",
    "LICENSE KEY FUNCTIONALITY",
    "THIRD-PARTY COMPONENTS",
    '"last-gpl"'
)) {
    if ($notice -notmatch $required) {
        throw "LICENSE-NOTICE must contain '$required'."
    }
}

$pom = Read-RepoFile "pom.xml"
if ($pom -notmatch "<name>Elastic License 2\.0</name>") {
    throw "pom.xml must declare the Elastic License 2.0."
}

# The app loads libmpv into its own process. Only an LGPL build is compatible with ELv2.
$manifest = Read-RepoFile "distribution\windows\native-dependencies.json" | ConvertFrom-Json
if ($manifest.mpv.license -notmatch "^LGPL-") {
    throw "native-dependencies.json mpv.license must be an LGPL license: '$($manifest.mpv.license)'"
}
# The GPL and the LGPL require the corresponding source. The natives release of this
# repository keeps the bundled binaries and their source together.
if ($manifest.nativesRelease.tag -notmatch "^natives-") {
    throw "native-dependencies.json nativesRelease.tag must name a natives-* release."
}
$nativesDownload = "https://github.com/zipper2110/banana-shot/releases/download/$($manifest.nativesRelease.tag)/"
foreach ($name in @("mpv", "ffmpeg")) {
    $dependency = $manifest.$name
    foreach ($field in @("sourceUrl", "sourceSha256", "sourceArchiveName")) {
        if (-not $dependency.$field) {
            throw "native-dependencies.json $name.$field is required."
        }
    }
    foreach ($pair in @(@("url", "archiveName"), @("sourceUrl", "sourceArchiveName"))) {
        $expected = $nativesDownload + $dependency.($pair[1])
        if ($dependency.($pair[0]) -ne $expected) {
            throw "native-dependencies.json $name.$($pair[0]) must be $expected"
        }
    }
}

$thirdParty = Read-RepoFile "distribution\THIRD-PARTY-NOTICES.txt"
if ($thirdParty -notmatch [regex]::Escape($manifest.mpv.variant)) {
    throw "THIRD-PARTY-NOTICES.txt must name the bundled libmpv variant '$($manifest.mpv.variant)'."
}
if ($thirdParty -notmatch "GNU Lesser General Public License" -or $thirdParty -notmatch "License: Elastic License 2\.0") {
    throw "THIRD-PARTY-NOTICES.txt must list libmpv under the LGPL and BananaShot under ELv2."
}
if ($thirdParty -notmatch [regex]::Escape($manifest.nativesRelease.url)) {
    throw "THIRD-PARTY-NOTICES.txt must link the natives release $($manifest.nativesRelease.url)."
}

Write-Host "Elastic License 2.0 distribution license validation passed."

# The build expiry spec forbids a release with only some of the L-5.2 measures.
# Each story in the epics file has a line "- Status: <value>". A value other than
# "done" blocks the release. Remove this check in E11-S4.
$epicsFile = "docs\licensing\l-5.2-epics.md"
$notDoneStories = @()
$story = "(no story)"
foreach ($line in (Read-RepoFile $epicsFile) -split "\r?\n") {
    if ($line -match '^### (E\d+-S\d+)\b') {
        $story = $Matches[1]
    } elseif ($line -match '^- Status:\s*(.*?)\s*$' -and $Matches[1] -ne "done") {
        $notDoneStories += "$story ($($Matches[1]))"
    }
}
if ($notDoneStories.Count -gt 0) {
    $message = "$epicsFile has stories that are not done: $($notDoneStories -join ', ')."
    if ($DryRun) {
        Write-Warning "$message A release with this commit fails."
    } else {
        throw "$message Do not release L-5.2 with only some of the measures."
    }
} else {
    Write-Host "All L-5.2 stories are done."
}

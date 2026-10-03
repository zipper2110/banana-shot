[CmdletBinding()]
param(
    [string]$ExecutablePath,
    # Test the app that the Velopack setup installed (%LocalAppData%\BananaShot\current), not the app image.
    [switch]$Installed,
    [switch]$KeepArtifacts,
    [string]$ReportPath,
    [switch]$ValidateOnly,
    [switch]$SkipNativeChecks
)

$ErrorActionPreference = "Stop"

function Resolve-UiSmokeLiteralPath {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [switch]$AllowMissing
    )

    if (-not $AllowMissing -and -not (Test-Path -LiteralPath $Path)) {
        throw "UI smoke path was not found: $Path"
    }

    return [IO.Path]::GetFullPath($Path)
}

function Test-UiSmokeChildPath {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Parent
    )

    $resolvedPath = [IO.Path]::GetFullPath($Path)
    $trimCharacters = [char[]]@([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
    $resolvedParent = [IO.Path]::GetFullPath($Parent).TrimEnd($trimCharacters) + [IO.Path]::DirectorySeparatorChar
    return $resolvedPath.StartsWith($resolvedParent, [StringComparison]::OrdinalIgnoreCase)
}

function Assert-UiSmokeFixtureFile {
    param([Parameter(Mandatory = $true)][string]$FixturePath)

    if (-not (Test-Path -LiteralPath $FixturePath -PathType Leaf)) {
        throw "UI smoke fixture was not found: $FixturePath"
    }

    $fixture = Get-Item -LiteralPath $FixturePath
    if ($fixture.Length -gt 5MB) {
        throw "UI smoke fixture must be at most 5 MiB: $($fixture.FullName)"
    }
    if ($fixture.Extension -ine ".mp4") {
        throw "UI smoke fixture must be an MP4 file: $($fixture.FullName)"
    }

    return $fixture.FullName
}

function Assert-UiSmokeProbeMetadata {
    param([Parameter(Mandatory = $true)][string]$ProbeJson)

    try {
        $probe = $ProbeJson | ConvertFrom-Json
    }
    catch {
        throw "UI smoke ffprobe metadata is not valid JSON: $($_.Exception.Message)"
    }

    $video = @($probe.streams | Where-Object { $_.codec_type -eq "video" })
    if (-not ($video | Where-Object { $_.codec_name -eq "h264" })) {
        throw "UI smoke fixture must contain H.264 video."
    }

    $duration = 0.0
    if (-not [double]::TryParse(
            [string]$probe.format.duration,
            [Globalization.NumberStyles]::Float,
            [Globalization.CultureInfo]::InvariantCulture,
            [ref]$duration
        )) {
        throw "UI smoke fixture duration is missing or invalid."
    }
    if ($duration -lt 3 -or $duration -gt 15) {
        throw "UI smoke fixture duration must be between 3 and 15 seconds; was $duration."
    }
    if ([string]$probe.format.format_name -notmatch "mp4") {
        throw "UI smoke fixture must be an MP4 container."
    }
}

function Invoke-UiSmokeProbe {
    param([Parameter(Mandatory = $true)][string]$FixturePath)

    $ffprobe = Get-Command ffprobe.exe -ErrorAction SilentlyContinue
    if (-not $ffprobe) {
        $ffprobe = Get-Command ffprobe -ErrorAction SilentlyContinue
    }
    if (-not $ffprobe) {
        throw "ffprobe was not found. Install FFmpeg or run the packaged smoke from an app image that provides it."
    }

    $probeJson = & $ffprobe.Source -v error -show_entries "format=duration,format_name,size" -show_entries "stream=codec_name,codec_type" -of json -- $FixturePath
    if ($LASTEXITCODE -ne 0) {
        throw "ffprobe failed for UI smoke fixture: $FixturePath"
    }
    Assert-UiSmokeProbeMetadata -ProbeJson ($probeJson -join [Environment]::NewLine)
}

function Resolve-UiSmokeExecutable {
    param([Parameter(Mandatory = $true)][string]$ExecutablePath)

    if (-not (Test-Path -LiteralPath $ExecutablePath -PathType Leaf)) {
        throw "UI smoke executable was not found: $ExecutablePath"
    }
    return (Get-Item -LiteralPath $ExecutablePath).FullName
}

function New-UiSmokeSession {
    param(
        [Parameter(Mandatory = $true)][string]$RunsRoot,
        [Parameter(Mandatory = $true)][string]$ReportsRoot,
        [string]$ReportPath
    )

    $resolvedRunsRoot = Resolve-UiSmokeLiteralPath -Path $RunsRoot -AllowMissing
    $resolvedReportsRoot = Resolve-UiSmokeLiteralPath -Path $ReportsRoot -AllowMissing
    New-Item -ItemType Directory -Path $resolvedRunsRoot -Force | Out-Null
    New-Item -ItemType Directory -Path $resolvedReportsRoot -Force | Out-Null

    $runRoot = Join-Path $resolvedRunsRoot ("run-{0:yyyyMMdd-HHmmss}-{1}" -f (Get-Date), [Guid]::NewGuid().ToString("N"))
    New-Item -ItemType Directory -Path $runRoot | Out-Null
    $appDataDirectory = Join-Path $runRoot "app-data"
    $artifactDirectory = Join-Path $runRoot "artifacts"
    New-Item -ItemType Directory -Path $appDataDirectory | Out-Null
    New-Item -ItemType Directory -Path $artifactDirectory | Out-Null

    if ($ReportPath) {
        $resolvedReportPath = Resolve-UiSmokeLiteralPath -Path $ReportPath -AllowMissing
        $reportDirectory = Split-Path -Parent $resolvedReportPath
        if ($reportDirectory) {
            New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null
        }
    }
    else {
        $resolvedReportPath = Join-Path $resolvedReportsRoot ("ui-smoke-{0:yyyyMMdd-HHmmss}-{1}.md" -f (Get-Date), [Guid]::NewGuid().ToString("N"))
    }

    @"
# Packaged Windows UI Smoke Report

Started: $(Get-Date -Format o)

## Scenario results

| Step | Status | Screenshot path | Expected result | Actual result |
| --- | --- | --- | --- | --- |
| Projects launch | Pending |  |  |  |
| Import fixture | Pending |  |  |  |
| Video playback | Pending |  |  |  |
| Editing and scoring | Pending |  |  |  |
| Adjustment controls | Pending |  |  |  |
| FFmpeg export | Pending |  |  |  |
| Relaunch and recents | Pending |  |  |  |

## Failure details

- Reproduction steps:
- Severity:
- Triage summary:
"@ | Set-Content -LiteralPath $resolvedReportPath -Encoding utf8

    return [pscustomobject]@{
        RunsRoot = $resolvedRunsRoot
        RunRoot = [IO.Path]::GetFullPath($runRoot)
        AppDataDirectory = [IO.Path]::GetFullPath($appDataDirectory)
        ArtifactDirectory = [IO.Path]::GetFullPath($artifactDirectory)
        ReportPath = [IO.Path]::GetFullPath($resolvedReportPath)
    }
}

function Start-UiSmokeChildProcess {
    param(
        [Parameter(Mandatory = $true)][string]$ExecutablePath,
        [string]$Arguments = "",
        [Parameter(Mandatory = $true)][string]$AppDataDirectory
    )

    $resolvedExecutable = Resolve-UiSmokeExecutable -ExecutablePath $ExecutablePath
    $resolvedAppData = Resolve-UiSmokeLiteralPath -Path $AppDataDirectory
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $resolvedExecutable
    $startInfo.Arguments = $Arguments
    $startInfo.UseShellExecute = $false
    $startInfo.EnvironmentVariables["BANANASHOT_APP_DATA_DIR"] = $resolvedAppData

    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    if (-not $process.Start()) {
        throw "UI smoke process did not start: $resolvedExecutable"
    }
    $process.WaitForExit()
    return $process.ExitCode
}

function Assert-UiSmokeLogFile {
    param(
        [Parameter(Mandatory = $true)][string]$AppDataDirectory
    )

    $logFile = Join-Path $AppDataDirectory "logs\bananashot.log"
    if (-not (Test-Path -LiteralPath $logFile -PathType Leaf) -or (Get-Item -LiteralPath $logFile).Length -eq 0) {
        throw "Packaged application did not write a log file: $logFile"
    }
}

function Get-UiSmokeNativePaths {
    param([Parameter(Mandatory = $true)][string]$AppDirectory)

    $resolvedAppDirectory = Resolve-UiSmokeLiteralPath -Path $AppDirectory
    $nativeRoot = Join-Path $resolvedAppDirectory "natives\windows-x64"
    $paths = [pscustomobject]@{
        AppDirectory = $resolvedAppDirectory
        MpvDirectory = Join-Path $nativeRoot "mpv"
        FfmpegExecutable = Join-Path $nativeRoot "ffmpeg\bin\ffmpeg.exe"
        FfprobeExecutable = Join-Path $nativeRoot "ffmpeg\bin\ffprobe.exe"
    }
    foreach ($required in @(
            (Join-Path $paths.MpvDirectory "libmpv-2.dll"),
            $paths.FfmpegExecutable,
            $paths.FfprobeExecutable
        )) {
        if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
            throw "The packaged app does not contain the native file: $required"
        }
    }
    return $paths
}

function Get-UiSmokeNativeMavenArguments {
    param(
        [Parameter(Mandatory = $true)]$NativePaths,
        [Parameter(Mandatory = $true)][string]$ResultsDirectory
    )

    # The release checklist runs the unit tests before the smoke test, so only the NativeSmokeIT execution runs here.
    return @(
        "-B",
        "-Pui-smoke",
        "test-compile",
        "failsafe:integration-test@ui-smoke",
        "failsafe:verify@ui-smoke",
        "-Dui.smoke.appDir=$($NativePaths.AppDirectory)",
        "-Dui.smoke.mpvPath=$($NativePaths.MpvDirectory)",
        "-Dui.smoke.ffmpegPath=$($NativePaths.FfmpegExecutable)",
        "-Dui.smoke.ffprobePath=$($NativePaths.FfprobeExecutable)",
        "-Dui.smoke.resultsDir=$ResultsDirectory"
    )
}

function Read-UiSmokeNativeResults {
    param([Parameter(Mandatory = $true)][string]$ResultsDirectory)

    $results = [ordered]@{}
    $resultsFile = Join-Path $ResultsDirectory "native-results.txt"
    if (Test-Path -LiteralPath $resultsFile -PathType Leaf) {
        foreach ($line in Get-Content -LiteralPath $resultsFile -Encoding utf8) {
            $separator = $line.IndexOf("=")
            if ($separator -gt 0) {
                $results[$line.Substring(0, $separator)] = $line.Substring($separator + 1)
            }
        }
    }
    return $results
}

function Set-UiSmokeReportRow {
    param(
        [Parameter(Mandatory = $true)][string]$ReportPath,
        [Parameter(Mandatory = $true)][string]$Step,
        [Parameter(Mandatory = $true)][string]$Status,
        [string]$Screenshot = "",
        [string]$Expected = "",
        [string]$Actual = ""
    )

    $prefix = "| $Step | Pending |"
    $lines = @(Get-Content -LiteralPath $ReportPath -Encoding utf8)
    $index = [Array]::FindIndex([string[]]$lines, [Predicate[string]] { param($line) $line.StartsWith($prefix) })
    if ($index -lt 0) {
        throw "The UI smoke report has no pending row for the step '$Step'."
    }
    $cells = @($Step, $Status, $Screenshot, $Expected, $Actual) | ForEach-Object { ([string]$_).Replace("|", "/") }
    $lines[$index] = "| " + ($cells -join " | ") + " |"
    $lines | Set-Content -LiteralPath $ReportPath -Encoding utf8
}

function Invoke-UiSmokeNativeChecks {
    param(
        [Parameter(Mandatory = $true)][string]$RepoRoot,
        [Parameter(Mandatory = $true)][string]$AppDirectory,
        [Parameter(Mandatory = $true)][string]$ResultsDirectory,
        [Parameter(Mandatory = $true)][string]$ReportPath
    )

    $nativePaths = Get-UiSmokeNativePaths -AppDirectory $AppDirectory
    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
        throw "Maven (mvn) was not found. The native checks run with Maven."
    }
    New-Item -ItemType Directory -Path $ResultsDirectory -Force | Out-Null
    $arguments = Get-UiSmokeNativeMavenArguments -NativePaths $nativePaths -ResultsDirectory $ResultsDirectory

    Write-Host "UI smoke native checks: the test moves the mouse. Do not use the mouse or the keyboard until the checks finish."
    Push-Location -LiteralPath $RepoRoot
    try {
        & mvn @arguments
        $exitCode = $LASTEXITCODE
    }
    finally {
        Pop-Location
    }

    $results = Read-UiSmokeNativeResults -ResultsDirectory $ResultsDirectory
    $passed = $exitCode -eq 0
    $status = if ($passed) { "Pass" } else { "Fail" }
    $failureNote = "Failed. See target\failsafe-reports and target\ui-smoke-artifacts."
    $adjustments = "brightness lift $($results['preview.brightness.lift']), preview luma $($results['preview.brightness.lumaBefore']) -> $($results['preview.brightness.lumaAfter']); rotation $($results['preview.rotation.degrees']) degrees, correlation with the unrotated preview $($results['preview.rotation.correlationWithUnrotated'])"
    $export = "encoder $($results['export.encoder']), $($results['export.stream']); correlation with the expected geometry $($results['export.rotation.correlationWithExpected']), with the unrotated source $($results['export.rotation.correlationWithUnrotated']); luma change $($results['export.brightness.lumaChange']) (lift $($results['export.brightness.expectedLift']))"

    Set-UiSmokeReportRow -ReportPath $ReportPath -Step "Adjustment controls" -Status $status -Screenshot $ResultsDirectory `
        -Expected "Brightness 20 and rotation 15 degrees change the mpv preview and are saved in the project." `
        -Actual $(if ($passed) { $adjustments } else { "$failureNote $adjustments" })
    Set-UiSmokeReportRow -ReportPath $ReportPath -Step "FFmpeg export" -Status $status -Screenshot $ResultsDirectory `
        -Expected "The export completes with the source size and duration, and shows the brightness and the rotation." `
        -Actual $(if ($passed) { $export } else { "$failureNote $export" })

    if (-not $passed) {
        throw "The native checks failed (Maven exit code $exitCode)."
    }
}

function Complete-UiSmokeSession {
    param(
        [Parameter(Mandatory = $true)]$Session,
        [Parameter(Mandatory = $true)][bool]$Succeeded,
        [switch]$KeepArtifacts
    )

    if (-not $Succeeded -or $KeepArtifacts) {
        return
    }
    if (-not (Test-UiSmokeChildPath -Path $Session.RunRoot -Parent $Session.RunsRoot)) {
        throw "Refusing to clean a UI smoke run outside the runner-created runs directory: $($Session.RunRoot)"
    }
    if (Test-Path -LiteralPath $Session.RunRoot) {
        Remove-Item -LiteralPath $Session.RunRoot -Recurse -Force
    }
}

function Write-UiSmokeReportStatus {
    param(
        [Parameter(Mandatory = $true)][string]$ReportPath,
        [Parameter(Mandatory = $true)][string]$Status,
        [string]$Detail
    )

    "`nRunner status: $Status`nDetail: $Detail`nFinished: $(Get-Date -Format o)" |
        Add-Content -LiteralPath $ReportPath -Encoding utf8
}

function Invoke-UiSmokeRunner {
    $repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
    $fixturePath = Join-Path $repoRoot "src\test\resources\media\ui-smoke.mp4"
    $targetRoot = Join-Path $repoRoot "target\ui-smoke"
    $runsRoot = Join-Path $targetRoot "runs"
    $reportsRoot = Join-Path $targetRoot "reports"

    $fixturePath = Assert-UiSmokeFixtureFile -FixturePath $fixturePath
    Invoke-UiSmokeProbe -FixturePath $fixturePath

    if ($ExecutablePath) {
        $resolvedExecutable = Resolve-UiSmokeExecutable -ExecutablePath $ExecutablePath
    }
    elseif ($Installed) {
        # The Velopack setup installs for each user and keeps the app in the "current" folder.
        $installedExecutable = Join-Path $env:LOCALAPPDATA "BananaShot\current\BananaShot.exe"
        $resolvedExecutable = Resolve-UiSmokeExecutable -ExecutablePath $installedExecutable
    }
    else {
        $defaultExecutable = Join-Path $repoRoot "target\package\app-image\BananaShot\BananaShot.exe"
        if (-not (Test-Path -LiteralPath $defaultExecutable -PathType Leaf)) {
            & (Join-Path $repoRoot "distribution\windows\Build-AppImage.ps1")
            if ($LASTEXITCODE -ne 0) {
                throw "Windows app-image build failed."
            }
        }
        $resolvedExecutable = Resolve-UiSmokeExecutable -ExecutablePath $defaultExecutable
    }

    $session = New-UiSmokeSession -RunsRoot $runsRoot -ReportsRoot $reportsRoot -ReportPath $ReportPath
    Write-Host "UI smoke fixture: $fixturePath"
    Write-Host "UI smoke report: $($session.ReportPath)"
    Write-Host "UI smoke artifacts: $($session.ArtifactDirectory)"
    Write-Host "UI smoke app data: $($session.AppDataDirectory)"

    if ($ValidateOnly) {
        Write-UiSmokeReportStatus -ReportPath $session.ReportPath -Status "Validated" -Detail "Fixture, probe metadata, and executable were validated; application was not launched."
        Complete-UiSmokeSession -Session $session -Succeeded $true -KeepArtifacts:$KeepArtifacts
        return
    }

    try {
        if ($SkipNativeChecks) {
            Write-Host "UI smoke native checks: skipped."
        }
        else {
            Invoke-UiSmokeNativeChecks `
                -RepoRoot $repoRoot `
                -AppDirectory (Split-Path -Parent $resolvedExecutable) `
                -ResultsDirectory (Join-Path $session.ArtifactDirectory "native") `
                -ReportPath $session.ReportPath
        }
        $exitCode = Start-UiSmokeChildProcess -ExecutablePath $resolvedExecutable -AppDataDirectory $session.AppDataDirectory
        if ($exitCode -ne 0) {
            throw "Packaged application exited with code $exitCode."
        }
        Assert-UiSmokeLogFile -AppDataDirectory $session.AppDataDirectory
        Write-UiSmokeReportStatus -ReportPath $session.ReportPath -Status "Process exited" -Detail "Complete the checklist before declaring the smoke scenario passed."
        Complete-UiSmokeSession -Session $session -Succeeded $true -KeepArtifacts:$KeepArtifacts
    }
    catch {
        Write-UiSmokeReportStatus -ReportPath $session.ReportPath -Status "Failed" -Detail $_.Exception.Message
        Write-Host "UI smoke failed; retained QA root: $($session.RunRoot)"
        throw
    }
}

if ($MyInvocation.InvocationName -ne ".") {
    Invoke-UiSmokeRunner
}

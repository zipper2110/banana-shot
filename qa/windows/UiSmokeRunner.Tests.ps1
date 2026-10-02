$here = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $here "Run-UiSmoke.ps1")

Describe "UI smoke fixture validation" {
    It "rejects a missing fixture" {
        $missing = Join-Path $TestDrive "missing.mp4"

        $failure = $null
        try { Assert-UiSmokeFixtureFile -FixturePath $missing } catch { $failure = $_ }
        if (-not $failure) { throw "Expected missing fixture validation to fail." }
        $failure.Exception.Message | Should Match "fixture was not found"
    }

    It "rejects a fixture larger than five MiB" {
        $oversized = Join-Path $TestDrive "oversized.mp4"
        $stream = [IO.File]::Create($oversized)
        try {
            $stream.SetLength((5MB) + 1)
        }
        finally {
            $stream.Dispose()
        }

        $failure = $null
        try { Assert-UiSmokeFixtureFile -FixturePath $oversized } catch { $failure = $_ }
        if (-not $failure) { throw "Expected oversized fixture validation to fail." }
        $failure.Exception.Message | Should Match "at most 5 MiB"
    }

    It "rejects probe metadata without H.264 video" {
        $metadata = '{"format":{"duration":"4.48","format_name":"mov,mp4,m4a,3gp,3g2,mj2"},"streams":[{"codec_type":"video","codec_name":"vp9"}]}'

        $failure = $null
        try { Assert-UiSmokeProbeMetadata -ProbeJson $metadata } catch { $failure = $_ }
        if (-not $failure) { throw "Expected codec validation to fail." }
        $failure.Exception.Message | Should Match "H.264"
    }

    It "rejects probe metadata outside the duration contract" {
        $metadata = '{"format":{"duration":"2.99","format_name":"mov,mp4,m4a,3gp,3g2,mj2"},"streams":[{"codec_type":"video","codec_name":"h264"}]}'

        $failure = $null
        try { Assert-UiSmokeProbeMetadata -ProbeJson $metadata } catch { $failure = $_ }
        if (-not $failure) { throw "Expected duration validation to fail." }
        $failure.Exception.Message | Should Match "between 3 and 15 seconds"
    }
}

Describe "UI smoke executable validation" {
    It "requires a supplied packaged executable to exist" {
        $missing = Join-Path $TestDrive "BananaShot.exe"

        $failure = $null
        try { Resolve-UiSmokeExecutable -ExecutablePath $missing } catch { $failure = $_ }
        if (-not $failure) { throw "Expected executable validation to fail." }
        $failure.Exception.Message | Should Match "executable was not found"
    }

    It "returns the absolute path of a supplied executable" {
        $executable = Join-Path $TestDrive "BananaShot.exe"
        Set-Content -LiteralPath $executable -Value "placeholder"

        $resolved = Resolve-UiSmokeExecutable -ExecutablePath $executable

        $resolved | Should Be ([IO.Path]::GetFullPath($executable))
    }
}

Describe "UI smoke isolated run lifecycle" {
    It "creates separate app-data and artifact directories below a unique run root" {
        $runsRoot = Join-Path $TestDrive "runs"
        $reportsRoot = Join-Path $TestDrive "reports"

        $session = New-UiSmokeSession -RunsRoot $runsRoot -ReportsRoot $reportsRoot

        $session.RunRoot | Should Exist
        $session.AppDataDirectory | Should Exist
        $session.ArtifactDirectory | Should Exist
        $session.ReportPath | Should Exist
        $session.AppDataDirectory.StartsWith($session.RunRoot, [StringComparison]::OrdinalIgnoreCase) | Should Be $true
        $session.ArtifactDirectory.StartsWith($session.RunRoot, [StringComparison]::OrdinalIgnoreCase) | Should Be $true
    }

    It "injects app data only into the launched child process" {
        $appData = Join-Path $TestDrive "isolated-app-data"
        New-Item -ItemType Directory -Path $appData | Out-Null
        $capture = Join-Path $TestDrive "child-environment.txt"
        $childScript = Join-Path $TestDrive "capture-environment.ps1"
        Set-Content -LiteralPath $childScript -Value 'param($OutputPath) [IO.File]::WriteAllText($OutputPath, $env:BANANASHOT_APP_DATA_DIR)'
        $parentBefore = $env:BANANASHOT_APP_DATA_DIR
        $arguments = '-NoProfile -NonInteractive -ExecutionPolicy Bypass -File "{0}" "{1}"' -f $childScript, $capture

        $exitCode = Start-UiSmokeChildProcess `
            -ExecutablePath (Get-Process -Id $PID).Path `
            -Arguments $arguments `
            -AppDataDirectory $appData

        $exitCode | Should Be 0
        (Get-Content -LiteralPath $capture -Raw) | Should Be ([IO.Path]::GetFullPath($appData))
        $env:BANANASHOT_APP_DATA_DIR | Should Be $parentBefore
    }

    It "removes a passing run while preserving its report" {
        $session = New-UiSmokeSession `
            -RunsRoot (Join-Path $TestDrive "runs") `
            -ReportsRoot (Join-Path $TestDrive "reports")

        Complete-UiSmokeSession -Session $session -Succeeded $true

        $session.RunRoot | Should Not Exist
        $session.ReportPath | Should Exist
    }

    It "retains a failed run and its report" {
        $session = New-UiSmokeSession `
            -RunsRoot (Join-Path $TestDrive "runs") `
            -ReportsRoot (Join-Path $TestDrive "reports")

        Complete-UiSmokeSession -Session $session -Succeeded $false

        $session.RunRoot | Should Exist
        $session.ReportPath | Should Exist
    }
}

Describe "UI smoke log file check" {
    It "accepts an app-data directory with a log file" {
        $appData = Join-Path $TestDrive "with-log"
        New-Item -ItemType Directory -Path (Join-Path $appData "logs") | Out-Null
        Set-Content -LiteralPath (Join-Path $appData "logs\bananashot.log") -Value "started" -Encoding utf8

        Assert-UiSmokeLogFile -AppDataDirectory $appData
    }

    It "rejects an app-data directory without a log file" {
        $appData = Join-Path $TestDrive "without-log"
        New-Item -ItemType Directory -Path $appData | Out-Null

        $failure = $null
        try { Assert-UiSmokeLogFile -AppDataDirectory $appData } catch { $failure = $_ }
        if (-not $failure) { throw "Expected the log file check to fail." }
        $failure.Exception.Message | Should Match "did not write a log file"
    }
}

Describe "UI smoke native checks" {
    function New-FakeAppDirectory([string]$Name) {
        $appDirectory = Join-Path $TestDrive $Name
        $nativeRoot = Join-Path $appDirectory "natives\windows-x64"
        New-Item -ItemType Directory -Path (Join-Path $nativeRoot "mpv") -Force | Out-Null
        New-Item -ItemType Directory -Path (Join-Path $nativeRoot "ffmpeg\bin") -Force | Out-Null
        foreach ($file in @("mpv\libmpv-2.dll", "ffmpeg\bin\ffmpeg.exe", "ffmpeg\bin\ffprobe.exe")) {
            Set-Content -LiteralPath (Join-Path $nativeRoot $file) -Value "placeholder"
        }
        return $appDirectory
    }

    It "returns the natives of the packaged app" {
        $appDirectory = New-FakeAppDirectory "BananaShot"

        $paths = Get-UiSmokeNativePaths -AppDirectory $appDirectory

        $paths.AppDirectory | Should Be ([IO.Path]::GetFullPath($appDirectory))
        $paths.MpvDirectory | Should Be (Join-Path $appDirectory "natives\windows-x64\mpv")
        $paths.FfmpegExecutable | Should Be (Join-Path $appDirectory "natives\windows-x64\ffmpeg\bin\ffmpeg.exe")
        $paths.FfprobeExecutable | Should Be (Join-Path $appDirectory "natives\windows-x64\ffmpeg\bin\ffprobe.exe")
    }

    It "rejects a packaged app without FFmpeg" {
        $appDirectory = New-FakeAppDirectory "without-ffmpeg"
        Remove-Item -LiteralPath (Join-Path $appDirectory "natives\windows-x64\ffmpeg\bin\ffmpeg.exe")

        $failure = $null
        try { Get-UiSmokeNativePaths -AppDirectory $appDirectory } catch { $failure = $_ }
        if (-not $failure) { throw "Expected the native path check to fail." }
        $failure.Exception.Message | Should Match "does not contain the native file"
    }

    It "runs only the native smoke execution with the packaged natives" {
        $paths = Get-UiSmokeNativePaths -AppDirectory (New-FakeAppDirectory "arguments")
        $results = Join-Path $TestDrive "results"

        $arguments = Get-UiSmokeNativeMavenArguments -NativePaths $paths -ResultsDirectory $results

        $arguments -contains "-Pui-smoke" | Should Be $true
        $arguments -contains "failsafe:integration-test@ui-smoke" | Should Be $true
        $arguments -contains "failsafe:verify@ui-smoke" | Should Be $true
        $arguments -contains "verify" | Should Be $false
        $arguments -contains "-Dui.smoke.mpvPath=$($paths.MpvDirectory)" | Should Be $true
        $arguments -contains "-Dui.smoke.ffmpegPath=$($paths.FfmpegExecutable)" | Should Be $true
        $arguments -contains "-Dui.smoke.ffprobePath=$($paths.FfprobeExecutable)" | Should Be $true
        $arguments -contains "-Dui.smoke.resultsDir=$results" | Should Be $true
    }

    It "reads the measured values of the native test" {
        $results = Join-Path $TestDrive "measured"
        New-Item -ItemType Directory -Path $results | Out-Null
        Set-Content -LiteralPath (Join-Path $results "native-results.txt") -Encoding utf8 -Value @(
            "export.encoder=H.264 (libx264)",
            "export.stream={codec_name=h264, width=720}"
        )

        $values = Read-UiSmokeNativeResults -ResultsDirectory $results

        $values["export.encoder"] | Should Be "H.264 (libx264)"
        $values["export.stream"] | Should Be "{codec_name=h264, width=720}"
    }

    It "writes the result of an automated step into its report row" {
        $session = New-UiSmokeSession `
            -RunsRoot (Join-Path $TestDrive "report-runs") `
            -ReportsRoot (Join-Path $TestDrive "report-reports")

        Set-UiSmokeReportRow -ReportPath $session.ReportPath -Step "FFmpeg export" -Status "Pass" `
            -Screenshot "C:\shots" -Expected "Export completes" -Actual "a | b"

        $report = Get-Content -LiteralPath $session.ReportPath -Raw
        $report | Should Match ([regex]::Escape("| FFmpeg export | Pass | C:\shots | Export completes | a / b |"))
        $report | Should Match ([regex]::Escape("| Adjustment controls | Pending |"))
    }

    It "rejects a step without a pending report row" {
        $session = New-UiSmokeSession `
            -RunsRoot (Join-Path $TestDrive "unknown-runs") `
            -ReportsRoot (Join-Path $TestDrive "unknown-reports")

        $failure = $null
        try { Set-UiSmokeReportRow -ReportPath $session.ReportPath -Step "Unknown step" -Status "Pass" } catch { $failure = $_ }
        if (-not $failure) { throw "Expected the report row update to fail." }
        $failure.Exception.Message | Should Match "no pending row"
    }
}

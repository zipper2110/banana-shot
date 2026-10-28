<#
.SYNOPSIS
Makes the screenshots of all tabs and windows in one app language, for the translators (B-11 E4-T4).

.DESCRIPTION
Runs the UI flow tests with the app language and the screenshots on. The screenshots go to
target\translator-screenshots\<language>\<test class>\<test method>\. The folder of AllWindowsUiFlowIT has a
named screenshot of each tab, each Help page, each More section, and the feedback form. The folders of the
other tests have the windows and the dialogs that these tests open.

The tests move the mouse and type. Do not use the PC during the run.

A failed test does not stop the run. The script tells the failed tests at the end.

.EXAMPLE
.\qa\Make-TranslatorScreenshots.ps1 -Language es

.EXAMPLE
.\qa\Make-TranslatorScreenshots.ps1 -Language qps -Tests AllWindowsUiFlowIT
#>
[CmdletBinding()]
param(
    # The app language, for example es, es-419, or qps.
    [Parameter(Mandatory = $true)][string]$Language,
    # The UI flow test classes to run, for example AllWindowsUiFlowIT. Without it, all UI flow tests run.
    [string[]]$Tests,
    # The JDK of the build (JDK 25). Without it, Maven uses JAVA_HOME.
    [string]$JavaHome
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$output = Join-Path $root "target\translator-screenshots\$Language"
$reports = Join-Path $root "target\failsafe-reports"

# Delete the old screenshots of the tests that run. With -Tests, the screenshots of the other tests stay.
$oldFolders = if ($Tests) { $Tests | ForEach-Object { Join-Path $output $_ } } else { @($output) }
$oldFolders | Where-Object { Test-Path -LiteralPath $_ } | ForEach-Object { Remove-Item -LiteralPath $_ -Recurse -Force }
if ($JavaHome) {
    $env:JAVA_HOME = $JavaHome
}

# Quoted values, because PowerShell 5.1 splits an unquoted -Dname.with.dots=value.
$arguments = @(
    "-B",
    "-Pui-flow",
    "verify",
    "-Dbananashot.language=$Language",
    "-Dbananashot.uiFlow.screenshots=true",
    # Only the UI flow tests run. The unit tests do not run.
    "-Dtest=NoSuchTest",
    "-Dsurefire.failIfNoSpecifiedTests=false",
    "-DfailIfNoTests=false",
    # A failed test does not stop the other tests.
    "-Dmaven.test.failure.ignore=true"
)
if ($Tests) {
    $arguments += "-Dit.test=$($Tests -join ',')"
}

$started = Get-Date
Push-Location $root
try {
    # PowerShell 5.1 makes an error from each stderr line of Maven when the output is redirected. The exit code
    # tells the result.
    $ErrorActionPreference = "Continue"
    & mvn @arguments
    $mavenExit = $LASTEXITCODE
} finally {
    $ErrorActionPreference = "Stop"
    Pop-Location
}
if ($mavenExit -ne 0) {
    throw "Maven stopped with exit code $mavenExit. See the output above."
}

$files = @(Get-ChildItem -LiteralPath $output -Recurse -Filter *.png -ErrorAction SilentlyContinue)
Write-Host ""
Write-Host "Screenshots: $($files.Count) in $output"

$failed = @(Get-ChildItem -LiteralPath $reports -Filter "TEST-*.xml" -ErrorAction SilentlyContinue | Where-Object {
    # Only the reports of this run. Older reports of other classes stay in the folder.
    if ($_.LastWriteTime -lt $started) { return $false }
    $suite = ([xml](Get-Content -LiteralPath $_.FullName -Raw -Encoding UTF8)).testsuite
    ([int]$suite.failures + [int]$suite.errors) -gt 0
})
if ($failed.Count -gt 0) {
    Write-Warning "Failed test classes (the screenshots of these tests can be incomplete):"
    $failed | ForEach-Object { Write-Warning "  $($_.BaseName -replace '^TEST-', '')" }
}
if ($files.Count -eq 0) {
    throw "The run made no screenshots."
}

#requires -Version 7.0
<#
.SYNOPSIS
Run compilation, the complete unit suite and Lint in one Gradle invocation.
.DESCRIPTION
Uses normal incremental task checks and the local build cache. Does not clean,
filter tests, alter Lint rules, build an APK, or publish results. All logs stay
under the ignored app/build/reports/verification directory.
.PARAMETER RerunTests
Force only testDebugUnitTest to execute, preserving compilation and Lint caches.
.PARAMETER GradleUserHome
Optional machine-local Gradle cache; omitted to follow Gradle's normal defaults.
.PARAMETER TestForks
Optional bounded worker count for measurements or constrained machines (1 or 2).
.EXAMPLE
pwsh -File scripts/verify.ps1 -GradleUserHome <local-cache-directory>
#>
[CmdletBinding()]
param(
    [string]$GradleUserHome,
    [switch]$RerunTests,
    [switch]$VerboseTests,
    [ValidateRange(1, 2)][int]$TestForks
)

$ErrorActionPreference = 'Stop'
# Native failure is handled below so reports and the original exit code survive.
$PSNativeCommandUseErrorActionPreference = $false
$projectDirectory = Split-Path -Parent $PSScriptRoot
$reportDirectory = Join-Path $projectDirectory 'app/build/reports/verification'
[IO.Directory]::CreateDirectory($reportDirectory) | Out-Null

function Get-VerificationInputSnapshot {
    # Enumerate only verification inputs, never generated build reports/caches.
    $inputFiles = @(
        foreach ($relativeDirectory in @('app/src', 'app/schemas', 'gradle', 'scripts')) {
            $directory = Join-Path $projectDirectory $relativeDirectory
            if (Test-Path -LiteralPath $directory) {
                Get-ChildItem -LiteralPath $directory -File -Recurse
            }
        }
        foreach ($relativeFile in @('build.gradle.kts', 'settings.gradle.kts', 'gradle.properties',
            'gradlew', 'gradlew.bat', 'app/build.gradle.kts', 'app/lint.xml', 'app/proguard-rules.pro')) {
            $file = Join-Path $projectDirectory $relativeFile
            if (Test-Path -LiteralPath $file) { Get-Item -LiteralPath $file }
        }
    )
    return ($inputFiles | Sort-Object FullName -Unique | ForEach-Object {
        '{0}:{1}' -f [IO.Path]::GetRelativePath($projectDirectory, $_.FullName),
            (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
    }) -join "`n"
}

$lockStream = $null
$pushedLocation = $false
$exitCode = 1
try {
    try {
        $lockStream = [IO.File]::Open((Join-Path $reportDirectory '.verify.lock'),
            [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
    } catch {
        throw 'Another verification is running, or its local report directory is not writable.'
    }
    $runId = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
    $runDirectory = Join-Path $reportDirectory $runId
    [IO.Directory]::CreateDirectory($runDirectory) | Out-Null
    $timingPath = Join-Path $runDirectory 'task-timings.json'
    $gradleArguments = @('compileDebugKotlin', 'testDebugUnitTest')
    if ($RerunTests) { $gradleArguments += '--rerun' }
    $gradleArguments += @('lintDebug', '--no-daemon', '--console=plain', '--build-cache',
        '--no-configuration-cache', '--init-script',
        (Join-Path $PSScriptRoot 'verification-timing.init.gradle'),
        "-Decho.verification.report=$timingPath")
    if ($GradleUserHome) { $gradleArguments += @('-g', $GradleUserHome) }
    if ($VerboseTests) { $gradleArguments += '-PechoVerboseTests=true' }
    if ($PSBoundParameters.ContainsKey('TestForks')) {
        $gradleArguments += "-PechoTestForks=$TestForks"
    }
    Push-Location -LiteralPath $projectDirectory
    $pushedLocation = $true
    $inputsBefore = Get-VerificationInputSnapshot
    $timer = [Diagnostics.Stopwatch]::StartNew()
    & (Join-Path $projectDirectory 'gradlew.bat') @gradleArguments 2>&1 |
        Tee-Object -FilePath (Join-Path $runDirectory 'gradle.log')
    $gradleExitCode = $LASTEXITCODE
    $exitCode = $gradleExitCode
    $timer.Stop()
    $inputsChanged = $inputsBefore -cne (Get-VerificationInputSnapshot)

    $timingReport = if (Test-Path -LiteralPath $timingPath) {
        Get-Content -LiteralPath $timingPath -Raw | ConvertFrom-Json
    } else { $null }
    $tasks = @($timingReport.tasks | Where-Object { $null -ne $_ })
    Write-Host "`nVerification task times (dependencies listed separately; concurrent times are not additive):"
    $tasks | Where-Object { $_.task -match 'ksp|compile|testDebugUnitTest|lint' } |
        Select-Object task, outcome, @{ Name = 'seconds'; Expression = { [Math]::Round($_.milliseconds / 1000, 3) } } |
        Format-Table -AutoSize | Out-Host

    $testTask = $tasks | Where-Object task -eq ':app:testDebugUnitTest'
    $testSummary = $null
    if ($testTask -and $testTask.outcome -in @('EXECUTED', 'UP-TO-DATE', 'FROM-CACHE', 'FAILED')) {
        $suites = @(Get-ChildItem -LiteralPath (Join-Path $projectDirectory 'app/build/test-results/testDebugUnitTest') `
            -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue | ForEach-Object {
                [xml]$document = Get-Content -LiteralPath $_.FullName -Raw
                $document.testsuite
            })
        $testSummary = [ordered]@{
            outcome = $testTask.outcome
            suites = $suites.Count
            tests = ($suites | Measure-Object -Property tests -Sum).Sum
            failures = ($suites | Measure-Object -Property failures -Sum).Sum
            errors = ($suites | Measure-Object -Property errors -Sum).Sum
            skipped = ($suites | Measure-Object -Property skipped -Sum).Sum
        }
        Write-Host ("Tests: {0} suites, {1} tests, {2} failures, {3} errors, {4} skipped [{5}]" -f
            $testSummary.suites, $testSummary.tests, $testSummary.failures,
            $testSummary.errors, $testSummary.skipped, $testSummary.outcome)
        if ($testTask.outcome -eq 'FAILED') {
            Write-Host 'The failing task may leave partial XML; this is not a full successful run.'
        }
    } else {
        Write-Host 'Tests did not run in this invocation; previous XML is not reported as current verification.'
    }
    $requiredTasks = @(':app:compileDebugKotlin', ':app:testDebugUnitTest', ':app:lintDebug')
    $missingTasks = @($requiredTasks | Where-Object { $_ -notin $tasks.task })
    $incompleteTasks = @($tasks | Where-Object {
        $_.task -in $requiredTasks -and $_.outcome -notin @('EXECUTED', 'UP-TO-DATE', 'FROM-CACHE')
    })
    if ($exitCode -eq 0 -and ($missingTasks.Count -gt 0 -or $incompleteTasks.Count -gt 0 -or
        $timingReport.successful -ne $true -or $null -eq $testSummary -or $testSummary.tests -eq 0 -or
        $testSummary.failures -gt 0 -or $testSummary.errors -gt 0 -or $testSummary.skipped -gt 0)) {
        Write-Host 'Verification is incomplete or contains failed/skipped tests; refusing to report success.'
        $exitCode = 1
    }
    if ($inputsChanged) {
        Write-Host 'Verification inputs changed during this run. Freeze edits before final verification.'
        $exitCode = 1
    }
    $summary = [ordered]@{
        gradleExitCode = $gradleExitCode
        verificationExitCode = $exitCode
        wallSeconds = [Math]::Round($timer.Elapsed.TotalSeconds, 3)
        inputsChangedDuringRun = $inputsChanged
        tests = $testSummary
        missingRequiredTasks = $missingTasks
        incompleteRequiredTasks = @($incompleteTasks | ForEach-Object { $_.task })
    }
    $summary | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $runDirectory 'summary.json') -Encoding utf8
    Write-Host "Verification exit=$exitCode, wall=$($summary.wallSeconds)s; local reports: $runDirectory"
} catch {
    Write-Host "Verification failed: $($_.Exception.Message)"
    $exitCode = 1
} finally {
    if ($pushedLocation) { Pop-Location }
    if ($lockStream) { $lockStream.Dispose() }
}
exit $exitCode

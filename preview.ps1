param(
    [string]$Java = "C:\Program Files\Java\jdk-22\bin\java.exe",
    [string]$Javac = "C:\Program Files\Java\jdk-22\bin\javac.exe",
    [switch]$TestOnly
)

$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$verifyDir = Join-Path $projectRoot ".verify\standalone-classes"

if (-not (Test-Path -LiteralPath $Java)) {
    throw "Java runtime not found at: $Java"
}
if (-not (Test-Path -LiteralPath $Javac)) {
    throw "Java compiler not found at: $Javac"
}

$sources = @(
    (Join-Path $projectRoot "ugs-job-manager\src\main\java\com\bashworks\ugs\jobmanager\PluginInfo.java"),
    (Join-Path $projectRoot "ugs-job-manager\src\main\java\com\bashworks\ugs\jobmanager\model\JobOrigin.java"),
    (Join-Path $projectRoot "ugs-job-manager\src\main\java\com\bashworks\ugs\jobmanager\model\JobBounds.java"),
    (Join-Path $projectRoot "ugs-job-manager\src\main\java\com\bashworks\ugs\jobmanager\model\JobAnalysis.java"),
    (Join-Path $projectRoot "ugs-job-manager\src\main\java\com\bashworks\ugs\jobmanager\model\JobEntry.java"),
    (Join-Path $projectRoot "ugs-job-manager\src\main\java\com\bashworks\ugs\jobmanager\model\JobSource.java"),
    (Join-Path $projectRoot "ugs-job-manager\src\main\java\com\bashworks\ugs\jobmanager\service\GcodeAnalyzer.java"),
    (Join-Path $projectRoot "ugs-job-manager\src\main\java\com\bashworks\ugs\jobmanager\service\JobLibraryService.java"),
    (Join-Path $projectRoot "ugs-job-manager\src\main\java\com\bashworks\ugs\jobmanager\service\WebJobService.java"),
    (Join-Path $projectRoot "ugs-job-manager\src\main\java\com\bashworks\ugs\jobmanager\machine\JobManagerListener.java"),
    (Join-Path $projectRoot "ugs-job-manager\src\main\java\com\bashworks\ugs\jobmanager\machine\JobManagerGateway.java"),
    (Join-Path $projectRoot "ugs-job-manager\src\main\java\com\bashworks\ugs\jobmanager\machine\SimulatedJobManagerGateway.java"),
    (Join-Path $projectRoot "ugs-job-manager\src\main\java\com\bashworks\ugs\jobmanager\ui\UITheme.java"),
    (Join-Path $projectRoot "ugs-job-manager\src\main\java\com\bashworks\ugs\jobmanager\ui\DarkButton.java"),
    (Join-Path $projectRoot "ugs-job-manager\src\main\java\com\bashworks\ugs\jobmanager\ui\JobTableRenderer.java"),
    (Join-Path $projectRoot "ugs-job-manager\src\main\java\com\bashworks\ugs\jobmanager\ui\JobDetailsPanel.java"),
    (Join-Path $projectRoot "ugs-job-manager\src\main\java\com\bashworks\ugs\jobmanager\ui\LocalJobsPanel.java"),
    (Join-Path $projectRoot "ugs-job-manager\src\main\java\com\bashworks\ugs\jobmanager\ui\WebJobsPanel.java"),
    (Join-Path $projectRoot "ugs-job-manager\src\main\java\com\bashworks\ugs\jobmanager\ui\JobManagerPanel.java"),
    (Join-Path $projectRoot "ugs-job-manager\src\test\java\com\bashworks\ugs\jobmanager\service\GcodeAnalyzerTest.java"),
    (Join-Path $projectRoot "ugs-job-manager\src\test\java\com\bashworks\ugs\jobmanager\ui\JobManagerStandalone.java")
)

Write-Host "Compiling UGS Job Manager standalone preview classes..."
New-Item -ItemType Directory -Force -Path $verifyDir | Out-Null
& $Javac -encoding UTF-8 -d $verifyDir $sources
if ($LASTEXITCODE -ne 0) {
    throw "Compilation failed (exit $LASTEXITCODE)."
}

Write-Host "Running GcodeAnalyzer unit tests..."
& $Java -cp $verifyDir com.bashworks.ugs.jobmanager.service.GcodeAnalyzerTest
if ($LASTEXITCODE -ne 0) {
    throw "GcodeAnalyzer tests failed."
}

if (-not $TestOnly) {
    Write-Host "Launching G-BASHWARE Job Manager standalone preview..."
    & $Java -cp $verifyDir com.bashworks.ugs.jobmanager.ui.JobManagerStandalone
}

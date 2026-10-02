param(
    [int]$Iterations = 12,
    [int]$StartupSamples = 3
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$agent = Join-Path $root "provguard-agent\target\provguard-agent-1.0.0.jar"
$demo = Join-Path $root "provguard-demo-app\target\provguard-demo-app-1.0.0.jar"
if (-not (Test-Path $agent) -or -not (Test-Path $demo)) {
    Write-Error "Build first: mvn -DskipTests package"
}

$java = "C:\Program Files\Java\jdk-26.0.2\bin\java.exe"
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
    $java = Join-Path $env:JAVA_HOME "bin\java.exe"
}
$events = Join-Path $root "reports\output\perf-events.jsonl"
New-Item -ItemType Directory -Force -Path (Split-Path $events) | Out-Null

function Median([double[]]$values) {
    $sorted = $values | Sort-Object
    return $sorted[[int][math]::Floor($sorted.Count / 2)]
}

function Time-Startup([string]$agentArg) {
    $samples = @()
    for ($i = 0; $i -lt $StartupSamples; $i++) {
        $sw = [Diagnostics.Stopwatch]::StartNew()
        if ($agentArg) {
            & $java $agentArg -cp $demo com.provguard.demo.perf.StartupProbe | Out-Null
        } else {
            & $java -cp $demo com.provguard.demo.perf.StartupProbe | Out-Null
        }
        $sw.Stop()
        $samples += $sw.Elapsed.TotalMilliseconds
    }
    return Median $samples
}

function Time-Calls([string]$agentArg) {
    if (Test-Path $events) { Remove-Item $events }
    $output = if ($agentArg) {
        & $java $agentArg -cp $demo com.provguard.demo.perf.PerformanceHarness $Iterations
    } else {
        & $java -cp $demo com.provguard.demo.perf.PerformanceHarness $Iterations
    }
    $avg = ($output | Where-Object { $_ -like "avgMs=*" }) -replace "avgMs=", ""
    $mem = ($output | Where-Object { $_ -like "usedMemoryBytes=*" }) -replace "usedMemoryBytes=", ""
    return @{ Avg = [double]$avg; Memory = [double]$mem }
}

$agentArg = "-javaagent:$agent=mode=MONITOR;verbose=false;events=$events"
$startupWithout = Time-Startup ""
$startupWith = Time-Startup $agentArg
$callsWithout = Time-Calls ""
$callsWith = Time-Calls $agentArg

function Overhead($without, $with) {
    $diff = $with - $without
    $pct = if ($without -eq 0) { 0 } else { 100.0 * $diff / $without }
    return @{ Diff = $diff; Pct = $pct }
}

$startup = Overhead $startupWithout $startupWith
$calls = Overhead $callsWithout.Avg $callsWith.Avg
$memory = Overhead $callsWithout.Memory $callsWith.Memory

$report = @"
# Performance measurements

Measured on this machine with $Iterations process calls and $StartupSamples startup samples. The median startup sample is reported. These numbers are observations, not a claim that overhead is low.

| Metric | Without agent | With agent | Difference | Percentage overhead |
| --- | ---: | ---: | ---: | ---: |
| Startup median (ms) | $([math]::Round($startupWithout, 1)) | $([math]::Round($startupWith, 1)) | $([math]::Round($startup.Diff, 1)) | $([math]::Round($startup.Pct, 1))% |
| Average process call (ms) | $([math]::Round($callsWithout.Avg, 1)) | $([math]::Round($callsWith.Avg, 1)) | $([math]::Round($calls.Diff, 1)) | $([math]::Round($calls.Pct, 1))% |
| Used heap after the run (bytes) | $([math]::Round($callsWithout.Memory, 0)) | $([math]::Round($callsWith.Memory, 0)) | $([math]::Round($memory.Diff, 0)) | $([math]::Round($memory.Pct, 1))% |

Startup includes extracting the bootstrap runtime JAR and installing Byte Buddy advice. The per-call figure is the cost after that work. Monitor, Alert, and Block modes share the same fast path for an allowed echo. Block mode adds a throw only when a rule match is confirmed, which this harness does not trigger.

The bounded queue drops events instead of blocking the application when it is full. That behaviour is covered by ``StorageTest`` rather than by this process loop, because a short ``echo`` loop does not fill a 1024-slot queue.
"@

$path = Join-Path $root "reports\performance-results.md"
Set-Content -Path $path -Value $report -Encoding utf8
Write-Output $report

param(
    [ValidateSet("MONITOR", "ALERT", "BLOCK")]
    [string]$Mode = "MONITOR",
    [ValidateSet("safe", "suspicious", "blocked")]
    [string]$Demo = "safe"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$agent = Join-Path $root "provguard-agent\target\provguard-agent-1.0.0.jar"
$demo = Join-Path $root "provguard-demo-app\target\provguard-demo-app-1.0.0.jar"

if (-not (Test-Path $agent) -or -not (Test-Path $demo)) {
    Write-Error "Build first: mvn -DskipTests package"
}

$events = Join-Path $root "events.jsonl"
$main = switch ($Demo) {
    "safe" { "com.provguard.demo.safe.SafeDemoApplication" }
    "suspicious" { "com.provguard.demo.suspicious.SuspiciousPathDemo" }
    "blocked" { "com.provguard.demo.safe.DeserializationDemo" }
}
$appArgs = @()
if ($Demo -eq "blocked") {
    $appArgs = @("blocked")
}

$java = "C:\Program Files\Java\jdk-26.0.2\bin\java.exe"
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
    $java = Join-Path $env:JAVA_HOME "bin\java.exe"
}
if (-not (Test-Path $java)) {
    $java = "java"
}

& $java "-javaagent:$agent=mode=$Mode;events=$events;verbose=true" -cp $demo $main @appArgs
Write-Host "Events: $events"

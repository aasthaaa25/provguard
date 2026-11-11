param(
    [string]$Events = "",
    [int]$Port = 8765,
    [switch]$Demo,
    [switch]$Open
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $root "provguard-web\target\provguard-web-1.0.0.jar"

if (-not (Test-Path $jar)) {
    Write-Error "Build first: mvn -DskipTests package"
}

if ($Events -eq "") {
    $Events = Join-Path $root "events.jsonl"
}

$java = "C:\Program Files\Java\jdk-26.0.2\bin\java.exe"
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
    $java = Join-Path $env:JAVA_HOME "bin\java.exe"
}
if (-not (Test-Path $java)) {
    $java = "java"
}

$arguments = @("-jar", $jar, "--port", "$Port", "--reports", (Join-Path $root "reports"))
if ($Demo) {
    $arguments += "--demo"
} else {
    $arguments += @("--events", $Events)
}

$url = "http://127.0.0.1:$Port/"
Write-Host "Dashboard: $url  (Ctrl+C to stop)"
if ($Open) {
    Start-Process $url
}
& $java @arguments

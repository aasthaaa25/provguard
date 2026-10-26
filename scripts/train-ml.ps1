$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
if (Test-Path "$env:USERPROFILE\.provguard-cacerts") {
    $env:MAVEN_OPTS = "-Djavax.net.ssl.trustStore=$env:USERPROFILE\.provguard-cacerts -Djavax.net.ssl.trustStorePassword=changeit"
}
$mvn = "C:\Users\vt903\Tools\apache-maven-3.9.9\bin\mvn.cmd"
if (-not (Test-Path $mvn)) {
    $mvn = "mvn"
}
& $mvn -f (Join-Path $root "pom.xml") -pl provguard-ml -am install -DskipTests
& $mvn -f (Join-Path $root "pom.xml") -pl provguard-ml compile exec:java
Write-Host "Java Isolation Forest written to provguard-ml\src\main\resources\com\provguard\ml\isolation-forest.json"

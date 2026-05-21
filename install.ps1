# GenAI JMeter Plugin — PowerShell Install Script
# Usage: .\install.ps1 [-JMeterHome "C:\path\to\jmeter"]

param(
    [string]$JMeterHome = ""
)

$pluginJar = "target\genai-jmeter-plugin-1.0.0-jmeter.jar"

Write-Host "`n GenAI JMeter Plugin Installer" -ForegroundColor Cyan
Write-Host " =================================" -ForegroundColor Cyan

# Build first
Write-Host "`n Building plugin..." -ForegroundColor Yellow
$mvn = Get-Command mvn -ErrorAction SilentlyContinue
if (-not $mvn) {
    Write-Host " ERROR: Maven not found. Install from https://maven.apache.org/" -ForegroundColor Red
    exit 1
}

mvn clean package -q
if ($LASTEXITCODE -ne 0) {
    Write-Host " Build FAILED" -ForegroundColor Red
    exit 1
}

Write-Host " Build successful!" -ForegroundColor Green
Write-Host " JAR: $pluginJar"

# Auto-detect JMeter
if (-not $JMeterHome) {
    $candidates = @(
        "C:\Program Files\Apache JMeter",
        "C:\tools\jmeter",
        "C:\jmeter",
        "$env:USERPROFILE\jmeter",
        "C:\apache-jmeter*"
    )
    foreach ($c in $candidates) {
        $resolved = Get-Item $c -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($resolved -and (Test-Path "$($resolved.FullName)\bin\jmeter.bat")) {
            $JMeterHome = $resolved.FullName
            Write-Host " Auto-detected JMeter at: $JMeterHome" -ForegroundColor Green
            break
        }
    }
}

if (-not $JMeterHome) {
    $JMeterHome = Read-Host "`n Enter JMeter home path (or ENTER to skip install)"
}

if ($JMeterHome -and (Test-Path "$JMeterHome\lib\ext")) {
    Copy-Item $pluginJar "$JMeterHome\lib\ext\" -Force
    Write-Host "`n Plugin installed to: $JMeterHome\lib\ext\" -ForegroundColor Green
    Write-Host " Restart JMeter and look in: Tools > GenAI Correlation Plugin" -ForegroundColor Green
} else {
    Write-Host "`n Skipping install. Copy $pluginJar to JMETER_HOME\lib\ext\ manually." -ForegroundColor Yellow
}

Write-Host "`n Done!`n" -ForegroundColor Cyan

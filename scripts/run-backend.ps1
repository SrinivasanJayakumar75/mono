# Starts the Monotron backend with values loaded from backend/.env.
#
#   .\scripts\run-backend.ps1        # Gradle bootRun (development)
#   .\scripts\run-backend.ps1 -Jar   # run the built jar
#
# The .env file is optional; any variables already set in your shell win only
# when .env does not define them.

param(
    [switch]$Jar
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $root 'backend'
$envFile = Join-Path $backend '.env'

if (-not $env:JAVA_HOME -and (Test-Path 'C:\Program Files\Java\jdk-21.0.10')) {
    $env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.10'
}

if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith('#')) { return }
        $separator = $line.IndexOf('=')
        if ($separator -lt 1) { return }
        $name = $line.Substring(0, $separator).Trim()
        $value = $line.Substring($separator + 1).Trim().Trim('"').Trim("'")
        [Environment]::SetEnvironmentVariable($name, $value, 'Process')
    }
    Write-Host "Loaded environment from $envFile"
} else {
    Write-Host "No backend/.env found; using shell environment only."
}

if (-not $env:OPENAI_API_KEY) {
    Write-Warning 'OPENAI_API_KEY is empty - the bundled local AI providers will be used.'
} else {
    Write-Host 'OPENAI_API_KEY is set - the remote OpenAI provider will be used.'
}

Set-Location $backend

if ($Jar) {
    $jarFile = Join-Path $backend 'build\libs\monotron-backend.jar'
    if (-not (Test-Path $jarFile)) {
        throw "Jar not found at $jarFile. Run .\gradlew.bat bootJar first."
    }
    & java -jar $jarFile
} else {
    & .\gradlew.bat bootRun
}

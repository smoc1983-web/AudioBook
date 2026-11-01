param([string[]]$GradleArguments = @('--version'))
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
$env:ANDROID_HOME = [Environment]::GetEnvironmentVariable('ANDROID_HOME', 'User')
$env:Path = [Environment]::GetEnvironmentVariable('Path', 'User') + ';' + $env:Path
$gradle = Join-Path $env:LOCALAPPDATA 'Programs\Gradle\gradle-8.9\bin\gradle.bat'
if (-not (Test-Path $gradle)) { throw 'Gradle 8.9 is not installed on this host.' }
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    & $gradle '-Pandroid.overridePathCheck=true' @GradleArguments
    exit $LASTEXITCODE
} finally { Pop-Location }

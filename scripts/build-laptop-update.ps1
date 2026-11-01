param(
    [Parameter(Mandatory = $true)][ValidateRange(89, 9999999)][int]$ReleaseNumber,
    [string]$PublicDirectory = (Join-Path (Split-Path $PSScriptRoot -Parent) '..\laptop-updates-public')
)
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
$env:ANDROID_HOME = [Environment]::GetEnvironmentVariable('ANDROID_HOME', 'User')
$env:Path = [Environment]::GetEnvironmentVariable('Path', 'User') + ';' + $env:Path
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    python scripts/tests/test-laptop-update-server.py
    if ($LASTEXITCODE -ne 0) { throw 'Public update server tests failed' }
    python scripts/test-permission-audit.py
    if ($LASTEXITCODE -ne 0) { throw 'Permission audit tests failed' }
    & "$PSScriptRoot\windows-development.ps1" -GradleArguments @(
        ':app:testReleaseUnitTest', ':app:assembleRelease', ':app:lintRelease',
        "-PaudiobookReleaseNumber=$ReleaseNumber", '--console=plain', '--no-daemon',
        '-Dorg.gradle.jvmargs=-Xmx2304m -Dfile.encoding=UTF-8', '--max-workers=2'
    )
    if ($LASTEXITCODE -ne 0) { throw 'Android build or tests failed; nothing published' }
    python scripts/check-android-lint.py app/build/reports/lint-results-release.xml
    if ($LASTEXITCODE -ne 0) { throw 'Android lint failed; nothing published' }
    New-Item -ItemType Directory -Force -Path $PublicDirectory | Out-Null
    python scripts/publish-laptop-update.py --apk app/build/outputs/apk/release/app-release.apk `
        --directory $PublicDirectory --build-tools (Join-Path $env:ANDROID_HOME 'build-tools\35.0.0') `
        --release-number $ReleaseNumber
    if ($LASTEXITCODE -ne 0) { throw 'APK verification/publication failed' }
} finally {
    Pop-Location
}

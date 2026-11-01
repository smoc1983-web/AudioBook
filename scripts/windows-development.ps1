param([string[]]$GradleArguments = @('--version'))
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
$env:ANDROID_HOME = [Environment]::GetEnvironmentVariable('ANDROID_HOME', 'User')
if (-not $env:PREVIEW_KEYSTORE) {
    $previewKey = Join-Path $env:LOCALAPPDATA 'Audiobook\signing\preview.keystore'
    if (Test-Path -LiteralPath $previewKey) { $env:PREVIEW_KEYSTORE = $previewKey }
}
$env:Path = [Environment]::GetEnvironmentVariable('Path', 'User') + ';' + $env:Path
$gradle = Join-Path $env:LOCALAPPDATA 'Programs\Gradle\gradle-8.9\bin\gradle.bat'
if (-not (Test-Path $gradle)) { throw 'Gradle 8.9 is not installed on this host.' }
$projectDirectory = Split-Path $PSScriptRoot -Parent
$projectItem = Get-Item -LiteralPath $projectDirectory
if ($projectItem.LinkType -eq 'Junction') { $projectDirectory = @($projectItem.Target)[0] }
# Java 17 reads Windows @argfiles using the native code page; Gradle writes UTF-8.
# ASCII junctions keep Cyrillic paths out of worker classpaths without moving files.
$aliasRoot = Join-Path $env:PUBLIC 'Documents\AudiobookDev'
New-Item -ItemType Directory -Force -Path $aliasRoot | Out-Null
function Use-DirectoryAlias([string]$Name, [string]$Target) {
    $aliasPath = Join-Path $aliasRoot $Name
    if (-not (Test-Path -LiteralPath $aliasPath)) {
        New-Item -ItemType Junction -Path $aliasPath -Target $Target | Out-Null
    }
    $existing = Get-Item -LiteralPath $aliasPath
    if ($existing.LinkType -ne 'Junction' -or @($existing.Target)[0] -ne $Target) {
        throw "Development alias points to another directory: $aliasPath"
    }
    return $aliasPath
}
$env:GRADLE_USER_HOME = Use-DirectoryAlias 'gradle-cache' (Join-Path $env:USERPROFILE '.gradle')
$projectAlias = Use-DirectoryAlias 'AudioBook' $projectDirectory
Pus
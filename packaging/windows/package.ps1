param(
    [ValidateSet('app-image', 'exe', 'msi')]
    [string]$Type = 'app-image',

    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'

function Resolve-CommandPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$CommandName,

        [string]$PreferredPath
    )

    if ($PreferredPath -and (Test-Path $PreferredPath)) {
        return (Resolve-Path $PreferredPath).Path
    }

    $command = Get-Command $CommandName -ErrorAction SilentlyContinue
    if ($null -eq $command) {
        throw "Command not found: $CommandName"
    }
    return $command.Source
}

function Resolve-Wix3BinDirectory {
    $candle = Get-Command 'candle.exe' -ErrorAction SilentlyContinue
    $light = Get-Command 'light.exe' -ErrorAction SilentlyContinue
    if ($candle -and $light) {
        return Split-Path -Parent $candle.Source
    }

    $candidateDirs = @(
        'C:\Program Files (x86)\WiX Toolset v3.14\bin',
        'C:\Program Files\WiX Toolset v3.14\bin',
        'C:\Program Files (x86)\WiX Toolset v3.11\bin',
        'C:\Program Files\WiX Toolset v3.11\bin'
    )

    foreach ($dir in $candidateDirs) {
        if ((Test-Path (Join-Path $dir 'candle.exe')) -and (Test-Path (Join-Path $dir 'light.exe'))) {
            return $dir
        }
    }

    return $null
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $scriptDir '..\..')).Path
$assetDir = Join-Path $scriptDir 'assets'
$pomPath = Join-Path $repoRoot 'pom.xml'
[xml]$pom = Get-Content $pomPath

$artifactId = $pom.project.artifactId
$version = $pom.project.version
$mainClass = $pom.project.properties.'main.class'
$appName = 'ApexCal'

$targetDir = Join-Path $repoRoot 'target'
$inputDir = Join-Path $targetDir 'jpackage-input'
$distDir = Join-Path $scriptDir 'dist'
$packageOutputDir = Join-Path $distDir $appName

$mvnPath = Resolve-CommandPath -CommandName 'mvn'
$preferredJPackage = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\jpackage.exe' } else { $null }
$jpackagePath = Resolve-CommandPath -CommandName 'jpackage' -PreferredPath $preferredJPackage

if (-not $SkipBuild) {
    Push-Location $repoRoot
    try {
        & $mvnPath 'clean' 'package'
    }
    finally {
        Pop-Location
    }
}

$mainJar = Join-Path $targetDir "$artifactId-$version.jar"
if (-not (Test-Path $mainJar)) {
    throw "Main jar not found: $mainJar"
}

$dependencyDir = Join-Path $targetDir 'dependency'
if (-not (Test-Path $dependencyDir)) {
    throw "Runtime dependency directory not found: $dependencyDir"
}

Remove-Item $inputDir -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $inputDir | Out-Null
New-Item -ItemType Directory -Path $distDir -Force | Out-Null

if (Test-Path $packageOutputDir) {
    Remove-Item $packageOutputDir -Recurse -Force
}

Copy-Item $mainJar $inputDir
Copy-Item (Join-Path $dependencyDir '*.jar') $inputDir

$iconPath = $null
foreach ($candidate in (Get-ChildItem -Path $assetDir -Filter '*.ico' -File -ErrorAction SilentlyContinue | Sort-Object Name)) {
    $iconPath = $candidate.FullName
    break
}

if ($Type -eq 'exe' -or $Type -eq 'msi') {
    $wix3Bin = Resolve-Wix3BinDirectory
    $wix = Get-Command 'wix.exe' -ErrorAction SilentlyContinue

    if ($null -eq $wix3Bin) {
        if ($wix) {
            throw 'WiX 6 was detected, but JDK 21 jpackage still requires WiX 3 style tools: candle.exe and light.exe. Install WiX 3.14 alongside WiX 6, or use app-image packaging.'
        }
        throw 'WiX 3 style tools are required to build exe or msi packages. Install WiX 3.14 and make sure candle.exe and light.exe are on PATH.'
    }

    if (-not (($env:PATH -split ';') -contains $wix3Bin)) {
        $env:PATH = "$wix3Bin;$env:PATH"
    }
}

$arguments = @(
    '--type', $Type,
    '--input', $inputDir,
    '--dest', $distDir,
    '--name', $appName,
    '--main-jar', (Split-Path $mainJar -Leaf),
    '--main-class', $mainClass,
    '--app-version', $version,
    '--vendor', $appName,
    '--java-options', '-Dfile.encoding=UTF-8'
)

if ($iconPath) {
    $arguments += @('--icon', $iconPath)
}

if ($Type -eq 'exe' -or $Type -eq 'msi') {
    $arguments += @(
        '--win-menu',
        '--win-shortcut',
        '--win-dir-chooser',
        '--win-per-user-install',
        '--install-dir', $appName
    )
}

& $jpackagePath @arguments

if ($LASTEXITCODE -ne 0) {
    throw "jpackage failed with exit code $LASTEXITCODE"
}

Write-Host "Package created at: $distDir"
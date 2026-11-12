param(
    [Parameter(Mandatory = $true)][string]$AssetsDirectory,
    [string]$JavaExecutable = 'java',
    [string]$DemonicaJar = '',
    [string]$CeleritasJar = '',
    [switch]$SkipBuild
)
# Isolated production client: no MCP classpath or changes to launcher profiles.
$ErrorActionPreference = 'Stop'
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Push-Location $repoRoot
try {
    $assetsRoot = (Resolve-Path -LiteralPath $AssetsDirectory).Path
    $mode = if ($DemonicaJar) { 'demonica' } elseif ($CeleritasJar) { 'celeritas' } else { 'vanilla' }
    $testRoot = [System.IO.Path]::GetFullPath((Join-Path $repoRoot "run/client-smoke-$mode"))
    $version = ((Get-Content gradle.properties | Select-String '^cleanroom_version\s*=\s*(.+)$').Matches.Groups[1].Value).Trim()
    $wrapper = if ($IsWindows) { '.\gradlew.bat' } else { './gradlew' }
    if (-not $SkipBuild) {
        & $wrapper stageProductionSmoke --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Failed to build production test jars' }
    }
    New-Item -ItemType Directory -Force -Path $testRoot | Out-Null
    $installer = Join-Path $testRoot 'installer.jar'
    if (-not (Test-Path -LiteralPath $installer)) {
        Invoke-WebRequest "https://github.com/CleanroomMC/Cleanroom/releases/download/$version/cleanroom-$version-installer.jar" -OutFile $installer
    }
    & $JavaExecutable -jar $installer client --version $version --dir $testRoot --no-provision-java --no-profile --full --no-gui --yes
    if ($LASTEXITCODE -ne 0) { throw 'Client installation failed' }
    $versionRoot = Join-Path $testRoot "versions/Cleanroom-$version"
    $profile = Get-Content (Join-Path $versionRoot "Cleanroom-$version.json") -Raw | ConvertFrom-Json
    $clientJar = Join-Path $versionRoot "Cleanroom-$version.jar"
    if (-not (Test-Path -LiteralPath $clientJar)) {
        Invoke-WebRequest $profile.downloads.client.url -OutFile $clientJar
    }
    $modsRoot = Join-Path $testRoot 'mods'
    New-Item -ItemType Directory -Force -Path $modsRoot | Out-Null
    # Only replace jars in the test instance created above; never touch a user's mods.
    if ((Split-Path -Parent $modsRoot) -ne $testRoot -or (Split-Path -Parent $testRoot) -ne (Join-Path $repoRoot 'run')) {
        throw 'Unexpected test mods directory'
    }
    Get-ChildItem -LiteralPath $modsRoot -Filter '*.jar' | ForEach-Object { Remove-Item -LiteralPath $_.FullName }
    Copy-Item -Path 'build/production-smoke/mods/*.jar' -Destination $modsRoot
    foreach ($optionalMod in @($DemonicaJar, $CeleritasJar)) {
        if ($optionalMod) { Copy-Item -LiteralPath (Resolve-Path -LiteralPath $optionalMod).Path -Destination $modsRoot }
    }
    Copy-Item -Path 'src/test/resources/client/*' -Destination $testRoot -Recurse -Force
    # This fixture contains modern Cleanroom mods, not legacy Forge mods needing Fugue.
    Set-Content -LiteralPath (Join-Path $testRoot 'config/forge_early.cfg') -Value "general {`n B:DISABLE_PATCH_MOD_CHECK=true`n}" -Encoding utf8
    $classpath = (@(Get-ChildItem (Join-Path $testRoot 'libraries') -Recurse -Filter '*.jar' | Select-Object -ExpandProperty FullName) + @($clientJar)) -join [System.IO.Path]::PathSeparator
    $arguments = @('-Xmx3G', '-Dcbmultipart.client.result=client-smoke-result.txt', '-cp', ('"' + $classpath.Replace('\', '/') + '"'),
        'top.outlands.foundation.boot.Foundation', '--username', 'SmokeTester', '--version', "Cleanroom-$version",
        '--gameDir', '.', '--assetsDir', ('"' + $assetsRoot.Replace('\', '/') + '"'), '--assetIndex', '1.12',
        '--uuid', '00000000000000000000000000000000', '--accessToken', '0', '--userType', 'mojang',
        '--tweakClass', 'net.minecraftforge.fml.common.launcher.FMLTweaker', '--versionType', 'Cleanroom')
    [System.IO.File]::WriteAllLines((Join-Path $testRoot 'client-smoke.args'), $arguments, [System.Text.UTF8Encoding]::new($false))
    Push-Location $testRoot
    try {
        Remove-Item -LiteralPath client-smoke-result.txt -ErrorAction SilentlyContinue
        & $JavaExecutable '@client-smoke.args' *> client-smoke.log
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path client-smoke-result.txt) -or
            (Get-Content client-smoke-result.txt -Raw).Trim() -ne 'PASS') {
            throw "Client verification failed. See $testRoot/client-smoke.log"
        }
        Write-Output "Client smoke PASS ($mode): $testRoot/client-smoke.log"
    } finally { Pop-Location }
} finally { Pop-Location }

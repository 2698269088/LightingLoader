# Builds the three-ecosystem demo mods into run/mods (Lighting API bridge chain verification).
# Prerequisites: run .\gradlew.bat build at the project root first (build\classes\java\main).
# Optional: -IncludeVeto also builds the "veto startup" demo jar (cancels CommonSetup at runtime).
param([switch]$IncludeVeto)
$ErrorActionPreference = 'Stop'
$demo = $PSScriptRoot
$proj = Split-Path -Parent (Split-Path -Parent $demo)
$loaderClasses = Join-Path $proj 'build\classes\java\main'
$out = Join-Path $demo 'build'
$modsDir = Join-Path $proj 'run\mods'
$jdkBin = 'C:\Users\26982\.jdks\graalvm-jdk-25\bin'

if (-not (Test-Path $loaderClasses)) { throw 'missing build\classes\java\main; run gradlew build first' }
if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Force -Path $out | Out-Null

# 1) 编译 @Mod 注解桩(仅 Forge 使用;NeoForge 演示改用 libs/neoforge 真实 API 库;不进入 Mod jar)
$stubFiles = Get-ChildItem -Recurse -File (Join-Path $demo 'src\stubs') | ForEach-Object { $_.FullName }
& (Join-Path $jdkBin 'javac.exe') -d (Join-Path $out 'stubs') @stubFiles

# 2) 编译演示 Mod 类(依赖加载器 API 编译产物 + Forge 注解桩 + 真实 NeoForge API 库)
$neoLibs = Join-Path $proj 'libs\neoforge'
if (-not (Test-Path $neoLibs)) { throw 'missing libs\neoforge; assemble the NeoForge ecosystem libraries first' }
$neoCp = (Get-ChildItem -File $neoLibs -Filter *.jar | ForEach-Object { $_.FullName }) -join ';'
$modFiles = Get-ChildItem -Recurse -File (Join-Path $demo 'src\mods') | ForEach-Object { $_.FullName }
& (Join-Path $jdkBin 'javac.exe') -cp "$loaderClasses;$(Join-Path $out 'stubs');$neoCp" -d (Join-Path $out 'classes') @modFiles

# 3) 分别组装三个生态的 Mod jar(只含本生态的类与清单)
function New-ModJar([string]$eco, [string]$jarName) {
    $stage = Join-Path $out "stage-$eco"
    New-Item -ItemType Directory -Force -Path (Join-Path $stage 'top\demo') | Out-Null
    Copy-Item -Recurse (Join-Path $out "classes\top\demo\$eco") (Join-Path $stage 'top\demo')
    if ($eco -eq 'fabric') {
        # 共享能力演示包（typed 持有者/注册表构建器/datagen/gametest/@DistOnly）随 fabric jar 打包
        Copy-Item -Recurse (Join-Path $out 'classes\top\demo\capabilities') (Join-Path $stage 'top\demo')
    }
    Copy-Item -Recurse (Join-Path $demo "res\$eco\*") $stage
    & (Join-Path $jdkBin 'jar.exe') --create --file (Join-Path $modsDir $jarName) -C $stage .
    Write-Host "built $jarName"
}

New-ModJar 'fabric' 'demo-bridge-fabric.jar'
New-ModJar 'forge' 'demo-bridge-forge.jar'
New-ModJar 'neoforge' 'demo-bridge-neoforge.jar'
if ($IncludeVeto) { New-ModJar 'veto' 'demo-veto-fabric.jar' }

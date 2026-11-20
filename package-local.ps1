#!/usr/bin/env pwsh
<#
.SYNOPSIS
    把 CI 下载的全平台原生引擎装进 src/main/resources/engines/，然后本地打包 jar。

.DESCRIPTION
    工作流分工：
      * GitHub Actions 只编原生库（.github/workflows/native_build.yml），产出 engines-unified.zip
      * jar 在本地打（本脚本），因为 jar 里还有 Java 代码和资源

    目录布局（与 EngineExtractor.platform() 对应）：
      src/main/resources/engines/
        windows-x86_64/     chuying_*.dll
        linux-x86_64/       chuying_*.so
        macos-universal/    chuying_*.dylib   (arm64 + x86_64 合一)
        shared/             pikafish.nnue, stockfish/*.nnue, rapfi/*

.PARAMETER UnifiedDir
    解压后的 engines-unified 目录（含 windows-x86_64/ linux-x86_64/ macos-universal/ shared/）

.PARAMETER SkipBuild
    只安装引擎，不执行 gradlew build

.EXAMPLE
    .\package-local.ps1 -UnifiedDir ..\..\_release_tmp\unified
#>
param(
    [Parameter(Mandatory = $true)]
    [string]$UnifiedDir,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$proj = $PSScriptRoot
$dest = Join-Path $proj 'src\main\resources\engines'

if (-not (Test-Path $UnifiedDir)) {
    throw "找不到统一引擎目录: $UnifiedDir"
}

Write-Host "==> 源: $UnifiedDir"
Write-Host "==> 目标: $dest"

# 清空旧的平台目录（避免 2.1 的 windows/linux/macos 残留混进来）
foreach ($old in 'windows', 'linux', 'macos') {
    $p = Join-Path $dest $old
    if (Test-Path $p) {
        Write-Host "    删除旧目录 $old/"
        Remove-Item $p -Recurse -Force
    }
}

# 复制新目录
$copied = 0
foreach ($d in Get-ChildItem $UnifiedDir -Directory) {
    $target = Join-Path $dest $d.Name
    if (Test-Path $target) { Remove-Item $target -Recurse -Force }
    Copy-Item $d.FullName $target -Recurse -Force
    $n = (Get-ChildItem $target -Recurse -File).Count
    $mb = [math]::Round(((Get-ChildItem $target -Recurse -File | Measure-Object Length -Sum).Sum / 1MB), 1)
    Write-Host ("    {0,-20} {1,3} 个文件  {2,6} MB" -f $d.Name, $n, $mb)
    $copied += $n
}

# Rapfi 的 config.toml 模板需要在 shared 里（解压时改写路径）
$rapfiCfg = Join-Path $dest 'shared\rapfi\config.toml'
if (-not (Test-Path $rapfiCfg)) {
    Write-Warning "缺少 shared/rapfi/config.toml —— Rapfi（五子棋）会无法启动，请从仓库历史或本地备份补上"
}

# 校验
Write-Host "`n==> 校验四个引擎 × 三个平台"
$ext = @{ 'windows-x86_64' = '.dll'; 'linux-x86_64' = '.so'; 'macos-universal' = '.dylib' }
$missing = 0
foreach ($plat in $ext.Keys) {
    foreach ($eng in 'stockfish', 'pikafish', 'rapfi', 'gnugo') {
        $f = Join-Path $dest "$plat\chuying_$eng$($ext[$plat])"
        if (-not (Test-Path $f)) {
            Write-Host "    [缺失] $plat/chuying_$eng$($ext[$plat])" -ForegroundColor Red
            $missing++
        }
    }
}
if ($missing -gt 0) {
    throw "有 $missing 个原生库缺失，中止打包"
}
Write-Host "    全部就位" -ForegroundColor Green

if ($SkipBuild) {
    Write-Host "`n==> 已跳过构建（-SkipBuild）"
    exit 0
}

# 打包
Write-Host "`n==> gradlew build"
$env:GRADLE_USER_HOME = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
Push-Location $proj
try {
    & .\gradlew.bat clean build --offline
    if ($LASTEXITCODE -ne 0) { throw "gradle build 失败 (exit $LASTEXITCODE)" }
} finally {
    Pop-Location
}

# 报告产物
Write-Host "`n==> 产物"
Get-ChildItem (Join-Path $proj 'build\libs') -Filter '*.jar' |
    Sort-Object Length -Descending |
    ForEach-Object {
        Write-Host ("    {0,-64} {1,8:N1} MB" -f $_.Name, ($_.Length / 1MB))
    }

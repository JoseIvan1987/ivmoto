# Prepara una actualizacion: compila el APK firmado, calcula su hash y escribe latest.json.
# Uso:  .\scripts\publish-update.ps1 -ApkUrl "https://github.com/USUARIO/ivmoto/releases/download/v1.1/IVMoto-1.1.apk" -Notes "Mejoras de ..."
param(
    [Parameter(Mandatory = $true)][string]$ApkUrl,
    [string]$Notes = ""
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path (Join-Path $root "keystore.properties"))) {
    throw "Falta keystore.properties. Corre primero scripts\make-keystore.ps1 (se hace una sola vez)."
}
if (-not $ApkUrl.StartsWith("https://")) { throw "La direccion del APK debe empezar con https://" }

$gradle = Get-Content (Join-Path $root "app\build.gradle.kts") -Raw
$code = [regex]::Match($gradle, 'versionCode\s*=\s*(\d+)').Groups[1].Value
$name = [regex]::Match($gradle, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
if (-not $code -or -not $name) { throw "No pude leer versionCode/versionName de app\build.gradle.kts" }

$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Push-Location $root
try { & .\gradlew.bat assembleRelease; if ($LASTEXITCODE -ne 0) { throw "Fallo la compilacion" } } finally { Pop-Location }

$built = Get-ChildItem (Join-Path $root "app\build\outputs\apk\release") -Filter *.apk | Select-Object -First 1
if (-not $built) { throw "No encontre el APK de release" }
$dist = Join-Path $root "dist"
New-Item -ItemType Directory -Force -Path $dist | Out-Null
$apk = Join-Path $dist "IVMoto-$name.apk"
Copy-Item $built.FullName $apk -Force

$hash = (Get-FileHash $apk -Algorithm SHA256).Hash.ToLower()
$manifest = [ordered]@{ versionCode = [int]$code; versionName = $name; apkUrl = $ApkUrl; sha256 = $hash; notes = $Notes }
[System.IO.File]::WriteAllText((Join-Path $dist "latest.json"), ($manifest | ConvertTo-Json), (New-Object System.Text.UTF8Encoding($false)))

Write-Host ""
Write-Host "Version $name (codigo $code) lista en: $dist"
Write-Host "  1) Crea la release en GitHub con los DOS archivos:"
Write-Host "     gh release create v$name dist\IVMoto-$name.apk dist\latest.json --title ""IVMoto $name"""
Write-Host "  SHA-256: $hash"

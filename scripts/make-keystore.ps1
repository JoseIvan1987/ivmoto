# Crea la llave de firma de IVMoto. Se hace UNA sola vez.
# La llave y su contrasena quedan fuera de git. HAZ RESPALDO: si las pierdes, nadie podra actualizar la app.
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$keytool = "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"
if (-not (Test-Path $keytool)) { $keytool = "keytool" }

$store = Join-Path $root "ivmoto-release.jks"
if (Test-Path $store) { throw "Ya existe $store. No lo sobrescribo." }

$secure = Read-Host "Elige una contrasena para la llave (guardala en un lugar seguro)" -AsSecureString
$plain = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
if ($plain.Length -lt 8) { throw "Usa al menos 8 caracteres." }

& $keytool -genkeypair -v -keystore $store -alias ivmoto -keyalg RSA -keysize 4096 -validity 10000 `
    -storepass $plain -keypass $plain -dname "CN=IVSoftware, O=IVSoftware, C=MX"

@("storeFile=ivmoto-release.jks", "storePassword=$plain", "keyAlias=ivmoto", "keyPassword=$plain") |
    Set-Content -Path (Join-Path $root "keystore.properties") -Encoding ASCII

Write-Host ""
Write-Host "Listo. Respalda AHORA estos dos archivos fuera de esta carpeta (USB, nube privada):"
Write-Host "  $store"
Write-Host "  $(Join-Path $root 'keystore.properties')"

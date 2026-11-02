# Cómo publicar una actualización de IVMoto

La app busca una vez al día (al abrirse) el archivo `latest.json` de la **última release** de GitHub:
`https://github.com/JoseIvan1987/ivmoto/releases/latest/download/latest.json`.
Si trae una versión más nueva, muestra el aviso "Nueva versión disponible"; el usuario toca, la app
descarga el APK, comprueba su **hash SHA-256** y que esté **firmado con tu misma llave**, y abre el
instalador de Android (un toque para confirmar). Un archivo que no pase esas pruebas se descarta.

## Una sola vez: crear la llave de firma
```powershell
.\scripts\make-keystore.ps1
```
Crea `ivmoto-release.jks` y `keystore.properties` (ambos fuera de git). **Respáldalos fuera de esta
carpeta.** Si se pierden, no podrás publicar actualizaciones que se instalen sobre la app existente.

> Cambiar a esta llave por primera vez obliga a desinstalar la versión de pruebas (firmada con la llave
> de depuración) y volver a instalar. Pasa solo esa vez.

## Cada versión nueva
1. En `app/build.gradle.kts` sube `versionCode` (un número entero **mayor** al anterior) y cambia `versionName`.
2. Prepara los archivos:
   ```powershell
   .\scripts\publish-update.ps1 -ApkUrl "https://github.com/USUARIO/ivmoto/releases/download/v1.1/IVMoto-1.1.apk" -Notes "Qué cambió"
   ```
   Deja en `dist/`: `IVMoto-<versión>.apk` y `latest.json`.
3. Crea la release en GitHub subiendo **los dos archivos** (el APK y `latest.json`):
   ```powershell
   gh release create v1.1 dist\IVMoto-1.1.apk dist\latest.json --title "IVMoto 1.1" --notes "Qué cambió"
   ```
   La dirección fija `releases/latest/download/latest.json` pasa a apuntar a esta release.

## Qué se garantiza y qué no
- Sí: nadie puede instalar a tus usuarios una "actualización" que no esté firmada con tu llave.
- No: Android no deja que una app instalada fuera de Google Play se actualice **totalmente sola**; el
  usuario siempre confirma una vez. Desde Android 12, cuando IVMoto ya instaló una actualización, las
  siguientes pueden pedir menos confirmaciones, según el teléfono.

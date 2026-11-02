# Seguridad en IVMoto

## Reportar una vulnerabilidad
No abras un issue público. Usa "Report a vulnerability" en la pestaña Security del repositorio de GitHub, o escribe al contacto del mantenedor indicado en el perfil del repo.

## Decisiones de diseño
- **Sin telemetría ni analítica.** La app solo se conecta a: la moto (red local), servicios de mapas/rutas/búsqueda que tú uses (OpenStreetMap/OpenFreeMap, OSRM, Valhalla, Overpass, Nominatim, Photon, y OpenRouteService con tu propia llave) y, cuando se configure, GitHub para avisar de actualizaciones.
- **Verificación TLS desactivada solo en Android Auto** (`aa/NoCheckTrustManager.kt`): el protocolo de Android Auto usa certificados autofirmados y la conexión es local entre el teléfono y el tablero. No se usa para tráfico de internet.
- **Permisos sensibles con función real:** `KILL_BACKGROUND_PROCESSES` (solo cierra la app oficial de CFMoto si bloquea los puertos de la moto), `SYSTEM_ALERT_WINDOW` (opcional, para reanudar Android Auto), micrófono (asistente de voz), cámara (QR de emparejamiento), ubicación (Wi‑Fi y bitácora de viajes).
- **`allowBackup=false`:** los viajes y perfiles no se suben al respaldo de Google.

## Firma de releases
El proyecto original firmaba con la llave de depuración de Android (contraseña pública). IVMoto debe firmar con una llave propia:

```bash
keytool -genkeypair -v -keystore ivmoto-release.jks -alias ivmoto -keyalg RSA -keysize 4096 -validity 10000
```

Guarda el `.jks` y sus contraseñas fuera del repositorio. Publica siempre el SHA‑256 de cada APK en la página del release.

## Pendiente por hacer (honesto)
- Auditar a fondo el receptor de Android Auto (`app.ivmoto.aa`) y las dependencias.
- Añadir pruebas automáticas y escaneo de dependencias en CI.
- Configurar `UpdateChecker.REPO` con el repositorio oficial y verificar el hash del APK antes de ofrecer una actualización.

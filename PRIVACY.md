# Privacidad de IVMoto

_Última actualización: 2026-10-31_

IVMoto funciona en local. **No tiene cuentas, no tiene anuncios y no envía telemetría ni reportes de fallos.**

- **Se queda en tu teléfono:** perfiles de moto, viajes con GPS, mapas sin conexión y registros de diagnóstico (con contraseñas y seriales ocultos por defecto, salvo que tú elijas compartirlos).
- **Sale del teléfono solo lo que tú usas:** teselas de mapa, búsquedas de lugares y cálculo de rutas van a servicios públicos de OpenStreetMap y similares (OpenFreeMap, OSRM, Valhalla, Overpass, Nominatim, Photon, o OpenRouteService con tu llave). Esos servicios ven las coordenadas que consultas.
- **Android Auto** lo maneja la app de Google bajo sus propios términos; IVMoto solo retransmite el video al tablero.
- **Respaldo de Android desactivado** para que tus viajes no se suban a la nube.

## Permisos
Cámara (leer el QR de la moto), ubicación (Wi‑Fi y bitácora de viajes), Wi‑Fi cercano, Bluetooth (botones del manubrio), micrófono (asistente de voz de Android Auto), notificaciones (servicio en primer plano), y de forma opcional "mostrar sobre otras apps" (reanudar Android Auto).

## Actualizaciones
Una vez al día, al abrir la app, IVMoto pide a GitHub (github.com/JoseIvan1987/ivmoto) un archivo pequeño que dice cuál es la
última versión. Esa petición no lleva tu nombre ni tu ubicación; el servidor ve, como en cualquier página web,
tu dirección IP y la versión de la app. Para instalar una
actualización la app descarga el APK, comprueba su verificación y su firma, y Android te pide confirmar.

<p align="center"><img src="branding/ivmoto-logo.svg" alt="IVMoto" width="520"></p>

# IVMoto

Proyecta **Android Auto** (Google Maps, Waze, música) al tablero de tu moto **CFMoto** con MotoPlay, por Wi‑Fi, desde tu teléfono Android. Sin cuentas, sin anuncios y **sin telemetría**.

> IVMoto es un proyecto independiente y **no está afiliado a CFMoto**. "CFMoto" es marca de sus dueños y se menciona solo para indicar compatibilidad.

## Qué hace
- Android Auto inalámbrico en el tablero, con control táctil y botones del manubrio (Bluetooth).
- Garage con perfiles por moto, conexión por Wi‑Fi de la moto o Wi‑Fi Direct.
- Computadora de viaje con GPS, bitácora y exportación GPX; mapa OpenStreetMap con modo oscuro y mapas sin conexión.

## Requisitos
Android 10 o superior, Android Auto instalado y una moto CFMoto con MotoPlay.

## Seguridad y privacidad
- No envía datos de uso ni reportes de fallos a nadie. Ver [PRIVACY.md](PRIVACY.md).
- Respaldo en la nube de Android desactivado (tus viajes no salen del teléfono).
- Cómo reportar una vulnerabilidad: [SECURITY.md](SECURITY.md).
- Los APK oficiales se publican con su hash SHA‑256 y se firman con la llave de IVMoto.

## Compilar
Requiere Android Studio (o JDK 17+) y el SDK de Android.

```bash
./gradlew assembleRelease
```

Para firmar el APK de release define tu propia llave (ver SECURITY.md); **nunca** subas llaves al repositorio.

## Licencia
GNU AGPL v3 o posterior. Los avisos de licencia y de los componentes de código abierto incluidos están en [NOTICE](NOTICE) y [LICENSE](LICENSE).

Creado por [IVSoftware](https://ivsoftware.pages.dev/).

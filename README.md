# STD Contador de Inventario APK

Aplicación nativa de Android desarrollada en **Kotlin y Jetpack Compose** para conteo y toma de inventario físico en campo/almacén conectada directamente a **Microsoft SQL Server (2019+)** vía JDBC.

---

## 🚀 Características
- Conexión directa a SQL Server sin necesidad de APIs intermedias.
- Soporte para escáner láser de hardware (emulación teclado HID) y escáner por cámara.
- Conteo en modo continuo (+1 automático) y modo manual con teclado delta (+/-).
- Autenticación y consultas optimizadas contra tablas administrativas estándar (`ssusrs`, `SAOPEI`, `SAITEMOPI`, `SAEXIS`, `SADEPO`, `SAPROD`).

Para más detalles técnicos, consulte [GEMINI.md](GEMINI.md).

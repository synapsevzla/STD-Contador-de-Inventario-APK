# GEMINI.md - STD Contador de Inventario APK (Android Nativo)

## 1. Visión y Alcance
Aplicación móvil nativa Android para conteo y toma de inventario físico en campo/almacén conectada **directamente a Microsoft SQL Server (2019+)** vía JDBC (sin API intermedia).
Objetivo principal: Permitir al operador escanear códigos de barras (lector láser / cámara), validar existencias y registrar conteos físicos de manera fluida, rápida y tolerante a fallos con ergonomía táctil en Android.

Stack tecnológico:
- **Plataforma:** Android Nativo (Kotlin + Jetpack Compose + Material 3).
- **Conectividad a BD:** Conexión directa a Microsoft SQL Server 2019+ mediante driver JDBC (`jtds:1.3.1` o `com.microsoft.sqlserver:mssql-jdbc`) encapsulada en Coroutines / `Dispatchers.IO`.
- **Escaneo de Códigos:** Escaneo vía cámara nativa (CameraX / ZXing Embedded) y soporte de hardware láser (Eventos de teclado físico / emulación HID con tecla `Enter`).
- **Arquitectura:** MVVM / Unidirectional Data Flow, repositorios con manejo seguro de transacciones y estados reactivos (`StateFlow`).
- **Filosofía de desarrollo:** `/ponytail` (soluciones directas, modulares, sin boilerplate excesivo). Límite de modularidad: ningún archivo debe superar las 600 líneas de código.

---

## 2. Flujo Operativo y Modos de Conteo

1. **Configuración de Conexión:**
   - Parámetros de conexión a SQL Server: Servidor / IP, Puerto (por defecto 1433), Instancia (opcional), Base de Datos, Usuario y Clave.
   - Parámetros de sesión: Usuario activo y depósito seleccionado.

2. **Modo Conteo Manual (Búsqueda + Cantidad específica):**
   - Búsqueda en tiempo real por: código de producto, descripción o código de barras alternativo.
   - Teclado numérico táctil optimizado para ingresar delta acumulativo (`+N` o `-N`) o reemplazo directo de cantidad contada.

3. **Modo Conteo Rápido / Escaneo Continuo (+1 Automático):**
   - Diseñado para pistolas láser o cámara en vivo.
   - Cada lectura válida suma automáticamente **+1** a la cantidad contada en la operación activa.
   - Feedback sonoro (Beep) y háptico (Vibrador del teléfono) inmediato.

4. **Consolidación y Cierre:**
   - Listado y resumen de items contados en la operación.
   - Persistencia atómica contra la base de datos SQL Server (`TRANSACTION_READ_COMMITTED` / `COMMIT`).

---

## 3. Consultas SQL del Sistema Administrativo

### 1. Autenticación de Usuarios (`ssusrs`)
```sql
SELECT codusUa, descrip, email,
   RTRIM(LTRIM(SUBSTRING(SDATA3,175,1)+
   SUBSTRING(SDATA1,33,1)+
   SUBSTRING(SDATA2,90,1)+
   SUBSTRING(SDATA3,14,1)+
   SUBSTRING(SDATA1,207,1)+
   SUBSTRING(SDATA3,111,1)+
   SUBSTRING(SDATA3,145,1)+
   SUBSTRING(SDATA2,180,1)+
   SUBSTRING(SDATA2,9,1)+
   SUBSTRING(SDATA3,53,1))) AS Clave,
   RTRIM(LTRIM(SUBSTRING(SDATA2,193,1)+
   SUBSTRING(SDATA1,77,1))) AS Nivel,
   ISNULL(CodVend,'') AS CodVend 
FROM ssusrs
WHERE CodUsua = ? AND Email = ?
```

### 2. Listado de Operaciones de Conteo Abiertas (`SAOPEI`)
```sql
SELECT 
    t1.NumeroD,
    t1.CodUbic,
    t4.Descrip AS Deposito,
    LTRIM(RTRIM(CONCAT(t4.Direc1, ' ', t4.Direc2))) AS Sucursal,
    COUNT(t2.CodItem) AS TotalItems,
    'Conteo en Espera' AS Titulo
FROM SAOPEI t1
INNER JOIN SADEPO t4 ON t4.CodUbic = t1.CodUbic
INNER JOIN SAITEMOPI t2 ON t1.TipoOpI = t2.TipoOpI AND t1.NumeroD = t2.NumeroD
WHERE t1.TipoOpI = 'r'
GROUP BY t1.NumeroD, t1.CodUbic, t4.Descrip, t4.Direc1, t4.Direc2
```

### 3. Detalle de Items para Conteo
```sql
SELECT 
    t1.CodUbic,
    t4.Descrip AS Deposito,
    LTRIM(RTRIM(CONCAT(t4.Direc1, ' ', t4.Direc2))) AS Sucursal,
    'Conteo en Espera' AS Titulo,
    t1.NumeroD,
    t2.CodItem,
    t2.NroLinea,
    LTRIM(RTRIM(CONCAT(t2.Descrip1, ' ', t2.Descrip2, ' ', t2.Descrip3))) AS Descripcion,
    ISNULL(IIF(t2.EsUnid = 0, t5.Unidad, t5.UndEmpaq), 'N/A') AS Unidad,
    t5.ExDecimal,
    t2.Cantidad AS Existencia_Contada,
    ISNULL(t3.Existen, 0) AS Existencia_Actual_En_Deposito,
    (t2.Cantidad - ISNULL(t3.Existen, 0)) AS Diferencia,
    t2.CodUsua_Conteo
FROM SAOPEI t1
INNER JOIN SAITEMOPI t2 ON t1.TipoOpI = t2.TipoOpI AND t1.NumeroD = t2.NumeroD
LEFT JOIN SAEXIS t3 ON t3.CodProd = t2.CodItem AND t3.CodUbic = t1.CodUbic
INNER JOIN SADEPO t4 ON t4.CodUbic = t1.CodUbic
INNER JOIN SAPROD t5 ON t2.CodItem = t5.CodProd
WHERE t1.TipoOpI = 'r' AND t1.NumeroD = ?
ORDER BY t2.NroLinea
```

### 4. Actualización del Conteo
```sql
UPDATE SAITEMOPI
SET Cantidad = ?,
    CodUsua_Conteo = ?
WHERE TipoOpI = 'r' AND NumeroD = ? AND CodItem = ?
```

---

## 4. Estructura del Proyecto Android

```text
/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/std/inventario/
│       │   ├── data/
│       │   │   ├── db/DatabaseManager.kt      # Conexión directa JDBC SQL Server
│       │   │   ├── repository/InventoryRepository.kt # Consultas y mutaciones SQL
│       │   │   ├── preferences/PreferencesManager.kt # Almacenamiento seguro de config
│       │   │   └── model/ (Models de datos)
│       │   ├── ui/
│       │   │   ├── screens/ (Login, Operaciones, Conteo, Configuración)
│       │   │   ├── components/ (Keypad, BarcodeScanner, ItemRow)
│       │   │   └── theme/
│       │   └── MainActivity.kt
│       └── res/ (Layouts, drawables, strings, iconos)
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── GEMINI.md
└── README.md
```

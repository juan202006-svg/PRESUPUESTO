# Mi Presupuesto — Android (v1.0)

Aplicación Android de finanzas personales **100 % local y sin internet**, en español y en pesos colombianos (COP). Desarrollada con **Kotlin, Jetpack Compose, Material 3 y Room**.

## ¿Qué se puede hacer?

- Registrar ingresos y gastos por valor, fecha, categoría y nota.
- Editar o eliminar movimientos (con confirmación de borrado).
- Ver balance del mes, ingresos, gastos y distribución por categoría.
- Cambiar de mes, consultar historiales y buscar por categoría, nota o monto.
- Definir límites de gasto por mes y por categoría y ver advertencias visuales cuando se alcanza el 80 % o se supera el límite.
- Exportar todos los movimientos en **CSV** compatible con hojas de cálculo.
- Guardar una **copia completa en JSON** y restaurarla después. Incluye movimientos y presupuestos de todos los meses.

**Importante:** el balance mensual es ingresos menos gastos. El presupuesto es solamente un límite de gasto, no un ingreso. No hay cuentas bancarias enlazadas ni transacciones automáticas.

## Requisitos

- Android 8.0 (API 26) o superior.
- Android Studio con JDK 17 y Android SDK Platform 36 (se puede instalar desde SDK Manager).
- Conexión a internet **solo en el equipo de compilación**, la primera vez para descargar las dependencias. La aplicación instalada no requiere internet.

## Compilar e instalar desde Android Studio (Windows / Mac / Linux)

1. Extrae el ZIP y abre la carpeta **`MiPresupuestoAndroid`** desde **Android Studio > Open**.
2. Espera a que termine **Gradle Sync**. En **Settings > Build, Execution, Deployment > Build Tools > Gradle**, elige **Gradle JDK 17** si lo pide. Selecciona **Gradle 8.13** para el proyecto. La configuración está en `gradle/wrapper/gradle-wrapper.properties`.
3. Verifica en **Tools > SDK Manager** que está instalada la plataforma **Android API 36**.
4. Pulsa **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. El archivo de instalación se genera en **`app/build/outputs/apk/debug/app-debug.apk`**.
6. Copia el `.apk` a tu celular, ábrelo y permite la instalación de esa fuente cuando Android lo solicite.

> Este ZIP contiene **el código fuente**, no un APK precompilado. El entorno de creación no tenía Gradle ni SDK y por eso tampoco se generaron los archivos ejecutables del Gradle Wrapper (`gradlew`, `gradlew.bat` y `gradle-wrapper.jar`). Si Android Studio no puede sincronizar el proyecto por la ausencia del wrapper, instala Gradle 8.13 y ejecuta `gradle wrapper --gradle-version 8.13` en la carpeta del proyecto; como alternativa **más sencilla**, usa el flujo incluido de GitHub Actions, que instala Gradle y crea el APK automáticamente. La compilación Android completa aún no se ha verificado.

## Alternativa: compilar sin Android Studio usando GitHub Actions

1. Crea un repositorio **privado** de GitHub y sube el contenido descomprimido (los archivos dentro de `MiPresupuestoAndroid`, no el ZIP).
2. Abre la pestaña **Actions > Compilar APK Android**.
3. Elige **Run workflow** para ejecutar la compilación manualmente. También se compila cuando se hace push a `main`.
4. Al finalizar, abre la ejecución y descarga el artefacto **MiPresupuesto-APK**.
5. Descomprime ese artefacto y lleva el `app-debug.apk` a tu celular.

El flujo está definido en `.github/workflows/construir-apk.yml` e instala el SDK y Gradle automáticamente. Si el repositorio usa otra rama como principal, ejecuta manualmente el flujo desde Actions o adapta la rama de la configuración.

## Cómo hacer una copia de seguridad (MUY IMPORTANTE)

Abre el menú ⋮ de la pantalla inicial:

- **Guardar copia de seguridad**: guarda `mi_presupuesto_respaldo.json` en una carpeta de tu elección. Contiene tus datos en texto legible, **sin cifrado**; guárdalo en un lugar privado.
- **Restaurar copia de seguridad**: elige el `.json` y confirma. **Sustituye todos los datos actuales**, no los fusiona. El archivo se valida completo antes de reemplazar la base de datos y la operación de restauración es transaccional.
- **Exportar movimientos (CSV)**: exporta todos los movimientos para analizarlos en Excel; **el CSV no sirve para restaurar la app**. Para restaurar usa el JSON.

La base de datos reside en el almacenamiento privado de Android y **no se envía a ningún servidor**. La app no solicita el permiso `INTERNET`, ni requiere inicio de sesión, ni activa copias automáticas del sistema. **Si desinstalas la app o borras sus datos sin haber guardado un JSON, perderás los movimientos.**

## Arquitectura

- `FinanceData.kt`: entidades Room y DAO.
- `FinanceLogic.kt`: categorías y cálculos por mes, COP y CSV.
- `FinanceViewModel.kt`: estado de la UI y operaciones asíncronas.
- `FinanceBackup.kt`: exportación/validación/importación offline de respaldos JSON con transacción.
- `MainActivity.kt`: interfaz Compose, navegación, modales y selector de archivos del sistema.
- `.github/workflows/construir-apk.yml`: compilación automática y artefacto APK descargable.

## Limitaciones de esta primera versión

- Sin nube, cuentas, conectores bancarios ni notificaciones del sistema.
- Límites visuales del mes seleccionado; no hay recordatorios automáticos.
- No hay presupuestos recurrentes automáticos: cada mes define sus propios límites.
- La persistencia **no está cifrada a nivel de aplicación** (se beneficia del aislamiento y cifrado del dispositivo Android si están activos).
- Las copias JSON son compatibles con esta versión (`version: 1`).

## Nota sobre firmas

`app-debug.apk` es una compilación de desarrollo, firmada automáticamente con la clave de depuración del equipo que compile. Si reinstalas una compilación firmada con una clave diferente puede ser necesario desinstalar la anterior (lo que elimina su base de datos). **Haz una copia JSON antes de cambiar de compilación o dispositivo.** Para distribuir públicamente se debe configurar una firma de publicación estable.

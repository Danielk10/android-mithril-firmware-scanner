# Mithril Firmware Scanner

<p align="center">
  <img src="logo.png" width="160" alt="Mithril Firmware Scanner Logo">
</p>

[![Android](https://img.shields.io/badge/Android-6.0%20(API%2023)%20a%20Android%2017%20(API%2037)-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![ABI](https://img.shields.io/badge/ABI-arm64--v8a-0091EA?logo=arm&logoColor=white)](https://developer.android.com/ndk/guides/abis)
[![NDK](https://img.shields.io/badge/NDK-r30--rc1-4CAF50?logo=android&logoColor=white)](https://developer.android.com/ndk)
[![AGP](https://img.shields.io/badge/AGP-9.2.1-blue?logo=android)](https://developer.android.com/studio/releases/gradle-plugin)
[![16 KB Pages](https://img.shields.io/badge/16%20KB%20Pages-Compatible-success?logo=android)](https://developer.android.com/guide/practices/page-sizes)
[![Mithril](https://img.shields.io/badge/mithril-v__upstream-blueviolet?logo=github)](https://github.com/nmatt0/mithril)
[![libarchive](https://img.shields.io/badge/libarchive-integrado-blue)](https://github.com/libarchive/libarchive)
[![Licencia](https://img.shields.io/badge/Licencia-Apache--2.0-blue)](./LICENSE)

Aplicación Android de alto rendimiento para **análisis estático de seguridad, detección de secretos, inventario SBOM, auditoría de vulnerabilidades CVE e identificación de licencias** en imágenes y sistemas de archivos de firmware e IoT, potenciada por los motores nativos **mithril** y **libarchive** compilados para arquitectura **ARM64 (`arm64-v8a`)**.

> **Nombre visible de la app:** Mithril Firmware Scanner  
> **Identificador de paquete:** `com.diamon.mithril`  
> **Versión actual:** `1.0.0` (Código de versión: `1`)  
> **Rango de soporte Android:** API 23 a API 37 (Android 6.0 a Android 17+)  
> **Arquitectura objetivo:** `arm64-v8a` (con alineación de página a 16 KB para Android 15+)  
> **Autor:** [Danielk10](https://github.com/Danielk10)

---

## 1) Objetivo y Alcance del Proyecto

**Mithril Firmware Scanner** traslada la auditoría estática de seguridad y contenido de firmware directamente a dispositivos móviles Android. Mientras que herramientas de disección física como [Moria](https://github.com/Danielk10/android-moria-firmware-extractor) mapean y desempaquetan los bytes del firmware, **Mithril** interpreta y analiza semánticamente el contenido extraído:

> *"Moria mapea los bytes. Mithril analiza el contenido."*

```text
                      ┌─────────────────────────────────────────┐
                      │    Imagen de Firmware (firmware.bin)    │
                      └────────────────────┬────────────────────┘
                                           │
                                           ▼
                      ┌─────────────────────────────────────────┐
                      │        Moria Firmware Extractor         │
                      │       (Extracción y Carving)            │
                      │     moria -e firmware.bin               │
                      └────────────────────┬────────────────────┘
                                           │ Genera directorio:
                                           │ firmware.bin.extracted/
                                           ▼
                      ┌─────────────────────────────────────────┐
                      │        Mithril Firmware Scanner         │
                      │ (Análisis de Secretos, SBOM, CVE, Lic)  │
                      │   mithril firmware.bin.extracted/       │
                      └─────────────────────────────────────────┘
```

### Los Cuatro Pilares de Mithril

1. 🔑 **Detección de Secretos y Credenciales (`--secrets`)**:
   - Escaneo determinista de claves privadas (PEM, DER, SSH), certificados, tokens de servicios en la nube (AWS, GCP, Azure, GitHub, etc.), JWTs y hashes de contraseñas (`/etc/shadow`, `htpasswd`).
   - Escala determinista de validación: `pattern` (forma), `structural` (análisis sintáctico) y `validated` (comprobación de checksums reales).
2. 📦 **Inventario SBOM Consciente de Firmware (`--sbom`)**:
   - Generación de listas de materiales de software en formatos estándar **CycloneDX** y **SPDX**.
   - Recupera componentes no solo de gestores de paquetes (`dpkg`, `opkg`, `apk`, `rpm`), sino también de banners de versiones ELF, nombres de bibliotecas libc versionadas y banners del kernel de Linux, identificando OpenSSL, BusyBox o uClibc incluso en firmwares despojados (*stripped*) sin gestor de paquetes.
3. 🛡️ **Auditoría de Vulnerabilidades CVE (`--cve`)**:
   - Cruce del SBOM contra un espejo local de vulnerabilidades basado en OSV.dev, NVD 2.0, catálogo CISA KEV y métricas EPSS de FIRST.
   - Lista curada de verificación de CVEs de kernel con filtrado estricto por versión y kconfig.
   - Funciona de forma **100% fuera de línea (offline)** tras sincronizar el espejo con `mithril --fetch-db`.
4. 📜 **Identificación de Licencias Open-Source (`--licenses`, `--license-paths`)**:
   - Detección precisa de licencias SPDX a partir de etiquetas `SPDX-License-Identifier` y textos de licencias (`LICENSE`, `COPYING`, `NOTICE`).
5. 🐧 **Extracción de Configuración del Kernel (`--dump-kconfig`)**:
   - Recuperación de la configuración `.config` del kernel Linux analizado para auditar opciones de seguridad y mitigaciones activas.

---

## 2) Capacidades y Funcionamiento de Comandos

Mithril opera directamente sobre archivos individuales o árboles de directorios completos (sistemas de archivos descomprimidos):

| Acción en UI | Comando CLI | ¿Genera archivos? | Descripción |
| :--- | :---: | :---: | :--- |
| **Escanear Todo** | `mithril -A <target>` | No (salida en pantalla) | Ejecuta todos los pases de análisis (secretos, SBOM, CVEs y licencias). |
| **Secretos** | `mithril --secrets <target>` | No (salida en pantalla) | Escanea exclusivamente credenciales, tokens y claves privadas. |
| **SBOM** | `mithril --sbom <target>` | No (o Sí con `-C`) | Genera el inventario de componentes y versiones de software. |
| **CVE** | `mithril --cve <target>` | No (salida en pantalla) | Compara los componentes contra la base de vulnerabilidades local. |
| **Licencias** | `mithril --licenses <target>` | No (salida en pantalla) | Identifica y totaliza las licencias de código abierto encontradas. |
| **Kernel .cfg** | `mithril --dump-kconfig <target>` | No (salida en pantalla) | Extrae la configuración reconstruida `.config` del kernel detectado. |
| **Descargar DB** | `mithril --fetch-db` | Sí (`mithril_db/`) | Descarga el espejo precompilado de vulnerabilidades para escaneo offline. |
| **Actualizar DB**| `mithril --update-db` | Sí (`mithril_db/`) | Reconstruye el índice de vulnerabilidades desde las fuentes originales. |
| **Modo JSON** | `mithril -j <target>` | No (salida en pantalla) | Devuelve el reporte forense completo en formato JSON estructurado. |

> [!TIP]
> Si deseas volcar los reportes directamente a archivos en disco, puedes usar la terminal integrada ejecutando:
> `mithril --sbom -C out/ ./rootfs/`  
> Esto generará automáticamente `out/sbom.cdx.json`, `out/sbom.spdx.json` y `out/mithril-report.json`.

---

## 3) Arquitectura de la Aplicación y Funcionalidades de la UI

La aplicación combina botones táctiles ergonómicos de un toque con una consola de terminal UNIX interactiva:

```text
┌─────────────────────────────────────────────────────────────┐
│                 Mithril Firmware Scanner                    │
├─────────────────────────────────────────────────────────────┤
│ [ Seleccionar Objetivo ] -> Target: rootfs/ (148 items)     │
├─────────────────────────────────────────────────────────────┤
│ [ Escanear (-A) ]         [ Secretos ]                      │
│ [ SBOM ]                  [ CVE ]                           │
│ [ Licencias ]             [ Kernel .cfg ]                   │
│ [ Descargar DB ]          [ Actualizar DB ]                 │
│ [x] Salida JSON (-j)                                        │
├─────────────────────────────────────────────────────────────┤
│ Consola de Terminal UNIX / Sandbox                          │
│ $ mithril --secrets "rootfs/"                               │
│ 1 secret in 148 files                                       │
│ AWS_KEY: AKIAIOSFODNN7EXAMPLE (structural)                  │
│                                                             │
├─────────────────────────────────────────────────────────────┤
│ [▲] [▼] [◀] [▶] [TAB] [CLEAR] [PASTE] [COPY] [ABORT]        │
│ [ mithril -A "rootfs/"                               ] [RUN]│
└─────────────────────────────────────────────────────────────┘
```

### Características de la Experiencia Móvil:
- **Selector de Objetivos Inteligente**: Permite elegir tanto archivos binarios individuales como carpetas completas del espacio de trabajo (por ejemplo directorios `.extracted/` provenientes de Moria), o importar archivos externos vía Storage Access Framework (SAF).
- **Consola Sandbox UNIX**: Emulador con comandos nativos integrados (`ls`, `cd`, `pwd`, `cat`, `touch`, `mkdir`, `rm`, `cp`, `echo`, `clear`, `help`), soporte de pipes (`|`) y redirecciones (`>`).
- **Barra de Herramientas Rápida**: Atajos para historial (anterior/siguiente), flechas de navegación de cursor, tabulación (4 espacios), portapapeles, limpieza y detención de procesos (`Abort`).
- **Exportación Segura a Descargas**: Exportación recursiva de reportes y archivos a `Downloads/Mithril_Firmware/` mediante `MediaStore.Downloads` (compatible con Android 10 a Android 17 / API 29-37) respetando Scoped Storage.
- **Soporte Bilingüe**: Idioma principal en Inglés y soporte completo en Español.

---

## 4) Requisitos y Entorno de Desarrollo

- **Android Studio:** Ladybug / Meerkat o superior
- **Android Gradle Plugin (AGP):** `9.2.1`
- **Gradle:** `9.6.0`
- **NDK:** `30.0.14904198` (r30-rc1)
- **CMake:** `4.1.2`
- **Compile SDK:** Android 16/17 (API 37) | **Min SDK:** Android 6.0 (API 23)

### Inicialización rápida del SDK

El repositorio incluye un script automatizado para instalar el SDK, NDK y dependencias requeridas:

```bash
./setup-sdk.sh
```

### Compilación del APK

Para compilar la versión de depuración:

```bash
./gradlew assembleDebug
```

El APK resultante se genera en:
`build/outputs/apk/debug/MithrilFirmwareScanner-v1.0.0-debug.apk`

---

## 5) Proyectos Originales y Licencias

| Componente | Autor / Origen | Licencia |
| :--- | :--- | :--- |
| **Mithril CLI** | [nmatt0 (Matt Brown)](https://github.com/nmatt0/mithril) | [Licencia MIT](https://github.com/nmatt0/mithril/blob/main/LICENSE) |
| **libarchive** | [libarchive contributors](https://github.com/libarchive/libarchive) | Licencia BSD 2-Cláusulas |
| **bsdunzip** | libarchive / FreeBSD | Licencia BSD 2-Cláusulas |
| **OpenSSL / libcrypto** | OpenSSL Project | Apache License 2.0 |
| **Zstandard (libzstd)** | Meta Platforms / Yann Collet | Licencia BSD 3-Cláusulas |
| **LZ4 (liblz4)** | Yann Collet | Licencia BSD 2-Cláusulas |
| **XZ / liblzma** | Lasse Collin / Tukaani Project | Dominio Público / LGPL 2.1 |
| **zlib** | Jean-loup Gailly y Mark Adler | Licencia zlib |
| **libxml2** | Daniel Veillard / GNOME | Licencia MIT |
| **ICU** | Unicode Consortium | Licencia Unicode / ICU |

### Licencia del Proyecto Android

Este proyecto y sus adaptaciones para Android están licenciados bajo la **Licencia Apache 2.0**. Consulta el archivo [LICENSE](LICENSE) para más información.

**Autor de la versión Android:** [Danielk10](https://github.com/Danielk10) (Daniel Elias Diamon Vazquez)  
**Contacto:** [danielpdiamon@gmail.com](mailto:danielpdiamon@gmail.com)

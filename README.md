# Android Mithril Firmware Scanner

Scripts de compilación cruzada para construir **Mithril** (escáner estático de firmware) y su dependencia **libarchive** directamente en Android mediante Termux, con aislamiento total de paquete, alineación de páginas de 16 KB y RPATH exclusivo.

## Descripción

Este repositorio contiene los scripts necesarios para compilar desde el código fuente:

- **libarchive**: Biblioteca multiplataforma para lectura y escritura de archivos comprimidos y empaquetados.
- **Mithril**: Herramienta de escaneo estático y análisis de seguridad en firmware embebido.

Ambos binarios se compilan con un prefijo de instalación exclusivo para la aplicación Android `com.diamon.mithril`, garantizando que las bibliotecas y ejecutables no interfieran con otros paquetes del sistema.

## Características

- **Aislamiento por paquete Android**: Cada binario se instala bajo `/data/data/com.diamon.mithril/files/usr`
- **Alineación de 16 KB**: Compatible con los requisitos de memoria de Android 15+
- **RPATH exclusivo**: Los binarios buscan sus bibliotecas únicamente en la ruta del paquete
- **Hardening completo**: Stack protector, FORTIFY_SOURCE, RELRO y BIND_NOW
- **Optimización LTO**: Link-Time Optimization para binarios más pequeños y rápidos
- **Compilación nativa en Termux**: Sin necesidad de NDK externo

## Contenido del Repositorio

| Archivo | Descripción |
|---------|-------------|
| `build_libarchive_mithril_custom.sh` | Compila e instala libarchive en `fake_root` con el prefijo del paquete Mithril |
| `build_mithril_custom.sh` | Compila Mithril enlazando contra la libarchive local aislada |

## Requisitos Previos

- Dispositivo Android con [Termux](https://termux.dev/) instalado
- Conexión a internet para descargar el código fuente
- Paquetes de Termux: `git`, `clang`, `cmake`, `ninja`, `pkg-config`, `binutils` (se instalan automáticamente)

## Uso

### Orden de ejecución

Es obligatorio respetar el orden secuencial. Primero se compila la biblioteca y luego la herramienta:

```bash
chmod 700 build_libarchive_mithril_custom.sh build_mithril_custom.sh

# Paso 1: Compilar libarchive
./build_libarchive_mithril_custom.sh

# Paso 2: Compilar Mithril
./build_mithril_custom.sh
```

### Resultado

Al finalizar, los binarios se encontrarán en:

```
$HOME/fake_root/data/data/com.diamon.mithril/files/usr/
├── bin/
│   └── mithril
├── lib/
│   ├── libarchive.so
│   └── pkgconfig/
└── include/
    └── archive.h
```

## Variable Clave

Si necesitas cambiar el identificador del paquete Android, modifica esta variable en **ambos** scripts:

```bash
export APP_PREFIX=/data/data/com.diamon.mithril/files/usr
```

## Proyectos Originales

| Proyecto | Autor | Repositorio | Licencia |
|----------|-------|-------------|----------|
| **Mithril** | [nmatt0](https://github.com/nmatt0) | [github.com/nmatt0/mithril](https://github.com/nmatt0/mithril) | Consultar repositorio original |
| **libarchive** | [libarchive contributors](https://github.com/libarchive) | [github.com/libarchive/libarchive](https://github.com/libarchive/libarchive) | BSD-2-Clause |

## Autor

**Daniel Elias Diamon Vazquez** — [danielpdiamon@gmail.com](mailto:danielpdiamon@gmail.com)

## Licencia

Este proyecto está licenciado bajo la **Licencia Apache 2.0**. Consulta el archivo [LICENSE](LICENSE) para más detalles.

Los scripts de compilación contenidos en este repositorio son obras originales. Las herramientas y bibliotecas que compilan pertenecen a sus respectivos autores y se distribuyen bajo sus propias licencias.

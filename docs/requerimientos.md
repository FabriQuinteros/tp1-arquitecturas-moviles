# Trabajo Práctico 1 — Desarrollo de Aplicaciones Móviles

**Cátedra:** Arquitecturas Móviles — UTN Facultad Regional San Francisco
**Profesor:** Ing. Juan Pablo Bono
**Aplicación:** Bitácora — registro de notas de campo con foto
**Repositorio:** https://github.com/FabriQuinteros/tp1-arquitecturas-moviles

---

## 1. Problema y contexto

Quien trabaja fuera de un escritorio —una inspección, una visita a un cliente, un
relevamiento— necesita registrar lo que observa en el momento en que lo observa. Hacerlo
después, de memoria, pierde detalle; hacerlo en papel obliga a transcribirlo más tarde.

Bitácora resuelve ese registro en el lugar: el usuario abre la aplicación, escribe una
nota breve, le adjunta una foto y la guarda. Después puede consultarla, editarla o
compartirla por cualquier canal que ya tenga instalado en el teléfono.

El caso justifica una aplicación móvil y no una web: la cámara y el uso a una mano en
condiciones de conectividad incierta son la razón de ser del producto, no un accesorio.

## 2. Alcance

**Incluido en esta entrega**

- Alta, consulta, edición y borrado de notas, almacenadas en el propio dispositivo.
- Una foto por nota, tomada con la cámara del teléfono.
- Compartir una nota hacia otras aplicaciones del dispositivo.
- Funcionamiento completo sin conexión a internet.

**Fuera de esta entrega**

- Sincronización con un servidor y cuentas de usuario.
- Compartir notas entre varios dispositivos.
- Búsqueda de texto, etiquetas y adjuntos que no sean una foto.

Se deja afuera todo lo que exija infraestructura de servidor: el objetivo del trabajo
práctico es el ciclo de vida de la aplicación y la comunicación entre sus componentes,
no el backend.

## 3. Requerimientos funcionales

| ID | Requerimiento |
|----|---------------|
| RF-1 | El usuario puede crear una nota con un título y un texto descriptivo. |
| RF-2 | El usuario puede tomar una foto con la cámara del dispositivo y adjuntarla a la nota que está escribiendo. |
| RF-3 | Cada nota registra automáticamente su fecha y hora de creación. |
| RF-4 | La pantalla principal lista todas las notas guardadas, ordenadas de la más reciente a la más antigua. |
| RF-5 | El usuario puede abrir una nota de la lista para ver su contenido completo y la foto en tamaño grande. |
| RF-6 | El usuario puede editar el título y el texto de una nota ya guardada. |
| RF-7 | El usuario puede borrar una nota, con una confirmación previa. |
| RF-8 | El usuario puede compartir el texto y la foto de una nota hacia otra aplicación del dispositivo. |
| RF-9 | Si el usuario abandona la aplicación con una nota a medio escribir, el borrador se conserva y se restaura al volver. |
| RF-10 | Las notas quedan guardadas entre ejecuciones: cerrar la aplicación no las pierde. |

## 4. Requerimientos no funcionales

| ID | Categoría | Requerimiento |
|----|-----------|---------------|
| RNF-1 | Compatibilidad | Funciona desde Android 8.0 (API 26) en adelante, que cubre la mayoría del parque de dispositivos en uso. |
| RNF-2 | Disponibilidad | Toda la funcionalidad opera sin conexión a internet. |
| RNF-3 | Rendimiento | La pantalla principal muestra la lista en menos de un segundo desde que se abre la aplicación. |
| RNF-4 | Usabilidad | Las acciones frecuentes —crear nota y tomar foto— se alcanzan con una sola mano y a lo sumo dos toques desde la pantalla principal. |
| RNF-5 | Privacidad | Las notas y las fotos no salen del dispositivo salvo que el usuario use explícitamente la acción de compartir. |
| RNF-6 | Permisos | El permiso de cámara se solicita en el momento en que el usuario quiere sacar una foto, nunca al iniciar la aplicación, y la aplicación sigue siendo utilizable si se lo deniega. |
| RNF-7 | Consumo | La aplicación no ejecuta procesos en segundo plano ni mantiene servicios activos mientras no se la está usando. |
| RNF-8 | Almacenamiento | Las fotos se guardan comprimidas para que el uso de espacio crezca de forma razonable con la cantidad de notas. |
| RNF-9 | Robustez | Rotar la pantalla o recibir una llamada en medio de la edición no pierde lo escrito. |
| RNF-10 | Accesibilidad | Los textos respetan el tamaño de fuente configurado en el sistema y los controles tienen descripción para lectores de pantalla. |

## 5. Limitaciones propias de la plataforma móvil

Estas limitaciones condicionan el diseño y son parte de lo que el trabajo práctico busca
identificar:

- **El sistema operativo puede destruir la aplicación en cualquier momento.** Android
  libera memoria cerrando procesos que están en segundo plano. Por eso el estado no puede
  vivir solamente en memoria: RF-9 y RNF-9 existen por esta razón.
- **La pantalla cambia de tamaño y orientación durante la ejecución.** Una rotación
  destruye y vuelve a crear la Activity.
- **Los permisos se conceden y se revocan en cualquier momento.** La aplicación tiene que
  seguir funcionando con el permiso de cámara denegado.
- **Almacenamiento y batería son recursos escasos**, a diferencia de una aplicación de
  escritorio o de servidor.
- **La conectividad es intermitente**, lo que en este caso se resolvió eliminando la
  dependencia de red del alcance.

## 6. Arquitectura de la aplicación

### 6.1 Componentes

| Componente | Tipo | Responsabilidad |
|------------|------|-----------------|
| `MainActivity` | Activity | Lista las notas guardadas y ofrece la acción de crear una nueva. |
| `EditorActivity` | Activity | Alta y edición de una nota; dispara la cámara y recibe la foto. |
| `DetalleActivity` | Activity | Muestra una nota completa y ofrece editar, borrar y compartir. |
| Repositorio de notas | Clase de datos | Único punto de lectura y escritura del almacenamiento local. |

### 6.2 Navegación e Intents

- **Intents explícitos**, entre las pantallas de la propia aplicación:
  `MainActivity → EditorActivity` (nueva nota), `MainActivity → DetalleActivity`
  (abrir una nota, pasando su identificador como extra) y
  `DetalleActivity → EditorActivity` (editar).
- **Intents implícitos**, hacia aplicaciones de terceros: `ACTION_IMAGE_CAPTURE` para
  tomar la foto y `ACTION_SEND` para compartir la nota. En ambos casos la aplicación
  declara la intención y el sistema resuelve qué aplicación la atiende, sin que Bitácora
  sepa cuál está instalada.

### 6.3 Declaraciones del Android Manifest

- Las tres Activities, con `MainActivity` marcada como punto de entrada (`LAUNCHER`).
- Permiso de cámara (`android.permission.CAMERA`), solicitado además en tiempo de
  ejecución según RNF-6.
- Un `FileProvider`, necesario para entregar el archivo de la foto a la aplicación de
  cámara y a la de destino al compartir, sin exponer rutas del sistema de archivos.
- Versión mínima de Android declarada según RNF-1.

### 6.4 Ciclo de vida

El punto crítico es la edición de una nota. `EditorActivity` responde así:

| Evento | Qué hace la aplicación |
|--------|------------------------|
| `onCreate` | Si recibe un estado guardado, restaura el borrador; si recibe el identificador de una nota, la carga. |
| `onSaveInstanceState` | Guarda el borrador en curso, antes de una rotación o de que el sistema destruya el proceso. |
| `onPause` | Persiste el borrador en el almacenamiento local: es el último momento garantizado antes de perder el foco. |
| `onDestroy` | Libera los recursos de la imagen para no retener memoria. |

## 7. Stack tecnológico

| Capa | Elección | Motivo |
|------|----------|--------|
| Lenguaje | Kotlin | Lenguaje oficial de Android; menos código repetido y manejo de nulos en el compilador. |
| Entorno | Android Studio | Requerido por la guía del trabajo práctico; integra emulador, depurador y análisis estático. |
| Interfaz | Vistas XML con `RecyclerView` | Hace explícito el ciclo de vida de la Activity, que es lo que el trabajo práctico busca demostrar. |
| Persistencia | Almacenamiento local del dispositivo | Cubre RF-10 sin agregar infraestructura. |
| Construcción | Gradle | Sistema de construcción estándar del ecosistema. |

## 8. Criterios de aceptación

1. Se crea una nota con foto, se cierra la aplicación por completo y al volver a abrirla
   la nota sigue estando, con su foto.
2. Con una nota a medio escribir, se rota el dispositivo y no se pierde ni una palabra.
3. Con el permiso de cámara denegado, la aplicación permite igualmente crear notas de
   solo texto y explica por qué no puede sacar la foto.
4. La acción de compartir abre el selector del sistema y entrega texto e imagen a la
   aplicación que el usuario elija.
5. La aplicación se instala y opera con el modo avión activado.

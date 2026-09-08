# Trabajo Práctico 1 — Desarrollo de Aplicaciones Móviles

**Cátedra:** Arquitecturas Móviles — UTN Facultad Regional San Francisco
**Profesor:** Ing. Juan Pablo Bono
**Aplicación:** Tarjetazo — gastos de una tarjeta de crédito compartida
**Repositorio:** https://github.com/FabriQuinteros/tp1-arquitecturas-moviles

---

## 1. Problema

Cuando varias personas usan la misma tarjeta de crédito, el titular es el único que ve el
resumen y el único que paga. A fin de mes tiene que reconstruir de memoria cuánto le debe
cada uno.

Tarjetazo registra cada gasto en el momento en que ocurre, con su división entre personas,
y muestra cuánto debe cada una.

## 2. Alcance

Esta entrega es una aplicación Android nativa que funciona contra el almacenamiento del
propio dispositivo, sin servidor. Registrar gastos, dividirlos y ver la deuda resultante.

Queda afuera todo lo que exija backend —cuentas de usuario, invitar colaboradores,
sincronizar entre dispositivos, aprobar gastos de terceros— y también las variantes que no
agregan nada al objetivo del trabajo práctico: gastos en dólares, cuotas, impuesto de
sello y categorías. El objetivo acá es el ciclo de vida de las Activities y la
comunicación entre componentes, no el dominio financiero completo.

## 3. Requerimientos funcionales

| ID | Requerimiento |
|----|---------------|
| RF-1 | El usuario registra un gasto con fecha, comercio e importe. |
| RF-2 | El usuario divide un gasto entre varias personas asignando un porcentaje a cada una; la aplicación no permite guardar una división que no sume cien por ciento. |
| RF-3 | Un gasto sin división explícita se imputa entero al titular. |
| RF-4 | La pantalla principal lista los gastos registrados, con el total. |
| RF-5 | El usuario elimina un gasto, con una confirmación previa. |
| RF-6 | La aplicación muestra cuánto debe cada persona, sumando su parte de cada gasto. |
| RF-7 | El usuario comparte el resumen de deuda hacia otra aplicación del dispositivo. |
| RF-8 | Si el usuario abandona la aplicación con un gasto a medio cargar, el borrador se conserva y se restaura al volver. |
| RF-9 | Los gastos quedan guardados entre ejecuciones: cerrar la aplicación no los pierde. |

## 4. Requerimientos no funcionales

| ID | Categoría | Requerimiento |
|----|-----------|---------------|
| RNF-1 | Compatibilidad | Funciona desde Android 8.0 (API 26) en adelante. |
| RNF-2 | Disponibilidad | Opera sin conexión a internet. |
| RNF-3 | Exactitud | Los importes no se calculan con punto flotante binario: dividir un gasto entre tres personas descuadra los centavos. El resto de la división se asigna de forma explícita a una de las partes. |
| RNF-4 | Robustez | Rotar la pantalla o recibir una llamada en medio de la carga de un gasto no pierde lo cargado. |
| RNF-5 | Privacidad | Los datos no salen del dispositivo salvo que el usuario use explícitamente la acción de compartir. La aplicación no declara ningún permiso. |

## 5. Limitaciones propias de la plataforma móvil

Son las que condicionan el diseño, y lo que la guía del trabajo práctico pide identificar:

- **El sistema operativo puede destruir la aplicación en cualquier momento.** Android
  libera memoria cerrando procesos en segundo plano: el usuario que sale a mirar un
  mensaje puede volver a una aplicación que se reinició. El estado no puede vivir solo en
  memoria, y de ahí salen RF-8 y RNF-4.
- **La pantalla rota durante la ejecución**, lo que destruye y vuelve a crear la Activity.
- **La conectividad es intermitente**: el gasto se carga parado en la caja de un comercio.
  De ahí RNF-2.
- **La entrada de datos es cara**: escribir en un teclado táctil, parado, es lento. Por eso
  el gasto tiene tres campos y la división es opcional (RF-3).

## 6. Arquitectura de la aplicación

### 6.1 Componentes

| Componente | Tipo | Responsabilidad |
|------------|------|-----------------|
| `MainActivity` | Activity | Lista los gastos con el total y ofrece registrar uno nuevo o ver las deudas. |
| `GastoEditorActivity` | Activity | Alta de un gasto y su división por persona. |
| `DeudasActivity` | Activity | Deuda por persona, con la acción de compartirla. |
| Repositorio | Clase de datos | Único punto de lectura y escritura del almacenamiento local. |
| Calculadora de deudas | Clase de dominio | Reparto de importes entre personas. Sin dependencias de Android, para poder probarla sin emulador. |

### 6.2 Navegación e Intents

- **Intents explícitos:** `MainActivity → GastoEditorActivity`, que devuelve el gasto
  cargado como resultado a la Activity que la lanzó, y `MainActivity → DeudasActivity`.
- **Intent implícito:** `ACTION_SEND` para compartir el resumen de deuda. La aplicación
  declara la intención y el sistema resuelve qué aplicación la atiende —mensajería, correo,
  notas— sin que Tarjetazo sepa cuál está instalada.

### 6.3 Declaraciones del Android Manifest

- Las tres Activities, con `MainActivity` marcada como punto de entrada (`LAUNCHER`).
- Versión mínima de Android declarada según RNF-1.
- Ningún permiso: la aplicación no necesita internet, ni ubicación, ni cámara, ni
  contactos. Es verificable en la ficha de la aplicación antes de instalarla.

### 6.4 Ciclo de vida

Es el punto central del trabajo práctico. La carga de un gasto puede quedar a mitad de
camino, y `GastoEditorActivity` responde así:

| Evento | Qué hace la aplicación |
|--------|------------------------|
| `onCreate` | Si recibe un estado guardado, restaura el borrador. |
| `onSaveInstanceState` | Guarda el borrador en curso, antes de una rotación o de que el sistema destruya el proceso. |
| `onPause` | Persiste el borrador en el almacenamiento local: es el último momento garantizado antes de perder el foco. |
| `onResume` | Recupera el borrador persistido si el proceso fue destruido y vuelto a crear. |

`GastoEditorActivity` se lanza esperando un resultado: `MainActivity` sigue viva debajo en
la pila y se detiene (`onStop`) en lugar de destruirse. Es donde se ve la diferencia entre
las dos cosas.

## 7. Stack tecnológico

| Capa | Elección | Motivo |
|------|----------|--------|
| Lenguaje | Kotlin | Lenguaje oficial de Android. |
| Entorno | Android Studio | Requerido por la guía del trabajo práctico. |
| Interfaz | Vistas XML con `RecyclerView` | Hace explícito el ciclo de vida de la Activity, que es lo que hay que demostrar. |
| Importes | Aritmética decimal de precisión arbitraria | Exigido por RNF-3. |
| Persistencia | Almacenamiento local del dispositivo | Cubre RF-9 sin agregar infraestructura. |
| Construcción | Gradle | Estándar del ecosistema. |

## 8. Criterios de aceptación

1. Se carga un gasto de 10.000 pesos dividido entre tres personas en partes iguales y la
   aplicación muestra 3.333,33 por persona, con el centavo restante asignado de forma
   explícita y sin que el total se descuadre.
2. Con un gasto a medio cargar, se rota el dispositivo y no se pierde ningún campo.
3. Se cierra la aplicación por completo y al volver a abrirla están todos los gastos y las
   deudas.
4. La acción de compartir abre el selector del sistema con el resumen de deuda en texto.
5. La aplicación se instala y opera con el modo avión activado, y su ficha en el sistema no
   declara ningún permiso.

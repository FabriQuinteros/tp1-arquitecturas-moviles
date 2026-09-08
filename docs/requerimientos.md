# Trabajo Práctico 1 — Desarrollo de Aplicaciones Móviles

**Cátedra:** Arquitecturas Móviles — UTN Facultad Regional San Francisco
**Profesor:** Ing. Juan Pablo Bono
**Aplicación:** Tarjetazo — gestión de tarjetas de crédito compartidas
**Repositorio:** https://github.com/FabriQuinteros/tp1-arquitecturas-moviles

---

## 1. Problema y contexto

Una tarjeta de crédito rara vez la usa una sola persona. La comparten una pareja, una
familia o un grupo de amigos que se prestan el plástico para una compra puntual. El
titular es el único que ve el resumen, es el único que paga, y es el que tiene que
reconstruir de memoria —o revisando el resumen línea por línea— cuánto le debe cada uno.

El problema se agrava con tres cosas propias del contexto argentino: **las cuotas**, que
convierten un gasto de hoy en una deuda repartida en los próximos doce meses; **los
gastos en dólares**, que se liquidan al tipo de cambio del momento de la compra; y el
**impuesto de sello**, que encarece cada consumo en un porcentaje que casi nadie
contabiliza al dividir la cuenta.

Tarjetazo registra cada gasto en el momento en que ocurre, con quién lo hizo y cómo se
divide, y le dice al titular cuánto le debe cada persona este mes y cuánto le va a deber
en los meses que vienen.

El caso justifica una aplicación móvil: el gasto se carga parado en la caja, en el
momento de pagar. Cargarlo después, en una computadora, es exactamente lo que hoy no
pasa y por lo que el titular termina reconstruyendo todo a fin de mes.

## 2. Visión del producto y alcance de esta entrega

El producto completo contempla:

- **Multi-titular:** cada titular administra sus propias tarjetas e invita colaboradores.
- **Gastos completos:** pesos y dólares con tipo de cambio al momento del gasto, cuotas,
  impuesto de sello y categorías.
- **Divisiones flexibles:** cualquier gasto se reparte en porcentajes configurables por
  persona.
- **Deudas con arrastre:** seguimiento mes a mes con confirmación de pago de las dos
  partes.
- **Tablero:** tendencia de los últimos seis meses y proyección de las cuotas futuras.
- **Flujo de aprobación:** el gasto que carga un invitado entra como pendiente y el
  titular lo aprueba o lo rechaza.

**Entra en el Trabajo Práctico 1** todo lo que puede funcionar contra el almacenamiento
del propio dispositivo: alta y consulta de tarjetas y gastos, moneda y tipo de cambio,
cuotas, impuesto de sello, categorías, divisiones por persona, el cálculo de deuda por
persona y por mes, la proyección de cuotas futuras y el envío del resumen a otra
aplicación.

**Queda fuera del Trabajo Práctico 1** todo lo que exige un servidor: cuentas de usuario,
invitación de colaboradores, sincronización entre dispositivos, confirmación de pago
bidireccional y el flujo de aprobación. No es una simplificación del problema sino un
recorte de la entrega: el objetivo de este trabajo práctico es el ciclo de vida de la
aplicación y la comunicación entre sus componentes, no la infraestructura de backend.
En esta versión el titular es único y local, y los invitados existen como participantes
de una división, no como usuarios que se conectan.

## 3. Requerimientos funcionales

| ID | Requerimiento | En el TP 1 |
|----|---------------|------------|
| RF-1 | El usuario da de alta una tarjeta con nombre, últimos cuatro dígitos y día de cierre del resumen. | Sí |
| RF-2 | El usuario registra un gasto con fecha, comercio, importe, moneda, categoría y tarjeta. | Sí |
| RF-3 | Si el gasto es en dólares, el usuario indica el tipo de cambio del momento y la aplicación guarda las dos cifras: el importe original y el convertido a pesos. | Sí |
| RF-4 | El usuario indica en cuántas cuotas se hizo el gasto, y la aplicación calcula el importe de cada cuota y en qué meses cae. | Sí |
| RF-5 | La aplicación aplica el impuesto de sello como un porcentaje configurable sobre el importe del gasto, y lo muestra desagregado. | Sí |
| RF-6 | El usuario divide un gasto entre varias personas asignando un porcentaje a cada una; la aplicación no permite guardar una división que no sume cien por ciento. | Sí |
| RF-7 | Un gasto sin división explícita se imputa entero al titular. | Sí |
| RF-8 | La pantalla principal lista los gastos del mes en curso, con el total y la posibilidad de moverse a otro mes. | Sí |
| RF-9 | El usuario abre un gasto para ver su detalle completo: importe, impuesto, cuotas, moneda original y división por persona. | Sí |
| RF-10 | El usuario edita o elimina un gasto; eliminarlo pide confirmación y recalcula las deudas afectadas. | Sí |
| RF-11 | La aplicación muestra, por cada persona, cuánto debe en el mes seleccionado, sumando la parte que le toca de cada gasto y de cada cuota vigente. | Sí |
| RF-12 | La aplicación proyecta las cuotas ya comprometidas para los meses siguientes, por mes y por persona. | Sí |
| RF-13 | La aplicación muestra la tendencia de gasto de los últimos seis meses. | Sí |
| RF-14 | El usuario marca como saldada la deuda de una persona en un mes; lo no saldado se arrastra al mes siguiente. | Sí |
| RF-15 | El usuario comparte el resumen de deuda de una persona hacia otra aplicación del dispositivo. | Sí |
| RF-16 | Si el usuario abandona la aplicación con un gasto a medio cargar, el borrador se conserva y se restaura al volver. | Sí |
| RF-17 | Los datos quedan guardados entre ejecuciones: cerrar la aplicación no los pierde. | Sí |
| RF-18 | El titular invita colaboradores a una tarjeta, que cargan gastos desde su propio dispositivo. | No |
| RF-19 | El gasto que carga un invitado entra como pendiente y el titular lo aprueba o lo rechaza. | No |
| RF-20 | El pago de una deuda se confirma por las dos partes: quien paga lo declara y quien cobra lo confirma. | No |

## 4. Requerimientos no funcionales

| ID | Categoría | Requerimiento |
|----|-----------|---------------|
| RNF-1 | Compatibilidad | Funciona desde Android 8.0 (API 26) en adelante, que cubre la mayoría del parque de dispositivos en uso. |
| RNF-2 | Disponibilidad | Toda la funcionalidad de esta entrega opera sin conexión a internet: el gasto se carga en la caja del comercio, donde la señal puede no existir. |
| RNF-3 | Exactitud | Los importes se calculan y almacenan sin aritmética de punto flotante binario, para que el reparto de un gasto entre varias personas no arrastre errores de redondeo. El resto de la división se asigna de forma explícita y determinística a una de las partes. |
| RNF-4 | Rendimiento | La pantalla principal muestra los gastos del mes en menos de un segundo desde que se abre la aplicación. |
| RNF-5 | Usabilidad | Registrar un gasto simple —importe, comercio, tarjeta— se completa en menos de treinta segundos y a lo sumo tres toques desde la pantalla principal. |
| RNF-6 | Privacidad | Los datos financieros no salen del dispositivo salvo que el usuario use explícitamente la acción de compartir. La aplicación no incorpora analítica ni publicidad de terceros. |
| RNF-7 | Confidencialidad | La aplicación no muestra el número completo de ninguna tarjeta: solo los últimos cuatro dígitos, que es lo único que se necesita para distinguirlas. |
| RNF-8 | Robustez | Rotar la pantalla o recibir una llamada en medio de la carga de un gasto no pierde lo cargado. |
| RNF-9 | Consumo | La aplicación no ejecuta procesos en segundo plano ni mantiene servicios activos mientras no se la está usando. |
| RNF-10 | Accesibilidad | Los textos respetan el tamaño de fuente configurado en el sistema y los controles tienen descripción para lectores de pantalla. |
| RNF-11 | Trazabilidad | Todo importe que la aplicación muestre como deuda se puede desandar hasta los gastos que lo componen: el usuario tiene que poder auditar por qué le están cobrando esa cifra. |

## 5. Limitaciones propias de la plataforma móvil

Estas limitaciones condicionan el diseño y son parte de lo que el trabajo práctico busca
identificar:

- **El sistema operativo puede destruir la aplicación en cualquier momento.** Android
  libera memoria cerrando procesos que están en segundo plano; el usuario que sale a
  mirar el mensaje del banco puede volver a una aplicación que se reinició. Por eso el
  estado no puede vivir solamente en memoria: RF-16 y RNF-8 existen por esta razón.
- **La pantalla cambia de tamaño y orientación durante la ejecución.** Una rotación
  destruye y vuelve a crear la Activity.
- **La conectividad es intermitente.** El momento de carga del gasto es justamente el
  peor: dentro de un local, con el teléfono en la mano. De ahí RNF-2.
- **Almacenamiento y batería son recursos escasos**, a diferencia de una aplicación de
  escritorio o de servidor.
- **El dispositivo se pierde, se presta y se roba.** Contiene datos financieros de
  terceros, no solo del dueño: eso empuja RNF-6 y RNF-7.
- **La entrada de datos es cara.** Escribir en un teclado táctil, parado, es lento y
  propenso a errores; el diseño tiene que minimizar los campos obligatorios, de ahí
  RNF-5 y RF-7.

## 6. Arquitectura de la aplicación

### 6.1 Componentes

| Componente | Tipo | Responsabilidad |
|------------|------|-----------------|
| `MainActivity` | Activity | Lista los gastos del mes seleccionado, muestra el total y ofrece registrar uno nuevo. |
| `GastoEditorActivity` | Activity | Alta y edición de un gasto: importe, moneda, cuotas, impuesto, categoría y tarjeta. |
| `DivisionActivity` | Activity | Reparto del gasto en porcentajes por persona; valida que sumen cien. |
| `DetalleGastoActivity` | Activity | Detalle completo de un gasto, con las acciones de editar, eliminar y compartir. |
| `DeudasActivity` | Activity | Deuda por persona en el mes, con el detalle de los gastos que la componen. |
| `TableroActivity` | Activity | Tendencia de los últimos seis meses y proyección de cuotas futuras. |
| Repositorio | Clase de datos | Único punto de lectura y escritura del almacenamiento local. |
| Calculadora de deudas | Clase de dominio | Concentra el cálculo de cuotas, impuesto, conversión de moneda y reparto. Sin dependencias de Android, para poder probarla sin emulador. |

### 6.2 Navegación e Intents

- **Intents explícitos**, entre las pantallas de la propia aplicación:
  `MainActivity → GastoEditorActivity` (nuevo gasto),
  `GastoEditorActivity → DivisionActivity` (que devuelve el reparto como resultado),
  `MainActivity → DetalleGastoActivity` (pasando el identificador del gasto como extra),
  `MainActivity → DeudasActivity` y `MainActivity → TableroActivity`.
- **Intents implícitos**, hacia aplicaciones de terceros: `ACTION_SEND` para mandarle a
  una persona el resumen de lo que debe. La aplicación declara la intención y el sistema
  resuelve qué aplicación la atiende —mensajería, correo, notas—, sin que Tarjetazo sepa
  cuál está instalada.

### 6.3 Declaraciones del Android Manifest

- Las seis Activities, con `MainActivity` marcada como punto de entrada (`LAUNCHER`).
- Versión mínima de Android declarada según RNF-1.
- La aplicación **no declara ningún permiso**: no necesita internet, ni ubicación, ni
  cámara, ni contactos. Es una decisión de diseño alineada con RNF-6, y algo que el
  usuario puede verificar en la ficha de la aplicación antes de instalarla.
- `android:allowBackup` desactivado, para que los datos financieros no salgan del
  dispositivo en una copia de respaldo automática.

### 6.4 Ciclo de vida

El punto crítico es la carga de un gasto, que puede quedar a mitad de camino.
`GastoEditorActivity` responde así:

| Evento | Qué hace la aplicación |
|--------|------------------------|
| `onCreate` | Si recibe un estado guardado, restaura el borrador; si recibe el identificador de un gasto, lo carga para editarlo. |
| `onSaveInstanceState` | Guarda el borrador en curso, antes de una rotación o de que el sistema destruya el proceso. |
| `onPause` | Persiste el borrador en el almacenamiento local: es el último momento garantizado antes de perder el foco. |
| `onResume` | Recupera el borrador persistido si el proceso fue destruido y vuelto a crear. |
| `onDestroy` | Libera las referencias que podrían retener memoria. |

`DivisionActivity` se lanza esperando un resultado: devuelve el reparto a la Activity que
la invocó, que sigue viva debajo en la pila. Es el caso donde se ve la diferencia entre
una Activity que se detiene (`onStop`) y una que se destruye.

## 7. Stack tecnológico

| Capa | Elección | Motivo |
|------|----------|--------|
| Lenguaje | Kotlin | Lenguaje oficial de Android; menos código repetido y manejo de nulos en el compilador. |
| Entorno | Android Studio | Requerido por la guía del trabajo práctico; integra emulador, depurador y análisis estático. |
| Interfaz | Vistas XML con `RecyclerView` | Hace explícito el ciclo de vida de la Activity, que es lo que el trabajo práctico busca demostrar. |
| Importes | Aritmética decimal de precisión arbitraria | Exigido por RNF-3: dividir un importe entre tres personas con punto flotante binario descuadra los centavos. |
| Persistencia | Almacenamiento local del dispositivo | Cubre RF-17 sin agregar infraestructura. |
| Construcción | Gradle | Sistema de construcción estándar del ecosistema. |

## 8. Criterios de aceptación

1. Se carga un gasto de 120.000 pesos en tres cuotas dividido entre tres personas en
   partes iguales, y la aplicación muestra 13.333,33 pesos por persona por mes durante
   tres meses, con el centavo restante asignado de forma explícita y sin que el total
   se descuadre.
2. Se carga un gasto en dólares con un tipo de cambio dado, y el detalle muestra tanto el
   importe original en dólares como el convertido a pesos con ese tipo de cambio, aun
   cuando el tipo de cambio cambie después.
3. Con el impuesto de sello configurado, el detalle del gasto muestra el importe puro y
   el impuesto por separado, y la deuda de cada persona incluye la parte proporcional del
   impuesto.
4. Con un gasto a medio cargar, se rota el dispositivo y no se pierde ningún campo.
5. Se cierra la aplicación por completo y al volver a abrirla están todos los gastos, las
   tarjetas y las deudas.
6. La proyección muestra, para cada uno de los seis meses siguientes, la suma de las
   cuotas ya comprometidas, por persona.
7. La acción de compartir abre el selector del sistema con el resumen de deuda en texto.
8. La aplicación se instala y opera con el modo avión activado, y su ficha en el sistema
   no declara ningún permiso.

# Mockups de GeoLux Android — revisión cerrada

Cerrada el 14 de octubre de 2026 (Andrés). Las 17 pantallas de esta carpeta las
generó Stitch con el prompt de abajo y el sistema de diseño `../DESIGN.md`.

**Son referencia de diseño, no de datos.** La fuente de verdad es el contrato
`contracts/geolux-v1/SPEC.md`, `06_ANDROID_SPEC` y `../DESIGN.md`. Lo que una
imagen muestre y esos documentos no, no se implementa.

## Decisiones

| Tema | Decisión |
|---|---|
| Ganado | Fuera de la Beta. El contrato v1 no trae animales. |
| Terminología | «Finca» solo en la UI; en código, `Project`. Nunca «lote». |
| Misiones | Sin pestaña propia: capa del mapa con ficha (04d) y sección en *Mi finca*. |
| Paleta | Verde de Desktop v0.22 (`../DESIGN.md` §3). Pendiente de Luis. |

## Pantallas y qué ignorar

| Archivo | Tarea | Ignorar al implementar |
|---|---|---|
| `01-mis-fincas.png` | AND-R2 | «KML/GNSS», «v2.4.0-field», «Escala», «34.8 MB» (el ejemplo pesa 48.6 MB), «01» en el título |
| `01b-mis-fincas-vacio.png` | AND-R2 | «Memoria del dispositivo · 0 paquetes», «GEOLUX TERRENO V2.4», «Cartografía real» |
| `02a-vista-previa.png` | AND-R7 | Título «02A Seleccionar Origen» (es «Vista previa»), «Capas y telemetría», «Paquete agronómico» |
| `02b-importando.png` | AND-R7 | Título «02B Seleccionar Archivo» (es «Importando») |
| `02c-ya-importado.png` | AND-R7 | Título «02C Validar Datos», «Local v1.4», foto aérea, «Última lectura» |
| `02d-reemplazar.png` | AND-R7 | Título «02D Progreso Importación», «(.glx)», «Podrías perder delimitaciones o notas» (no hay notas: decir «Verás datos más antiguos») |
| `02e-error.png` | AND-R7 | Título «02E Confirmación», «CRC Erróneo», «ERR_02E», «SCHEMA_V2», «Registros locales 128 KB». Mensajes: SPEC §7 |
| `03-mi-finca.png` | AND-R2 | Mapa de calles en la miniatura (va el ortomosaico o fondo neutro) |
| `04a-mapa.png` | AND-R3 | Mapa de calles de fondo (va el ortomosaico), «GPS RTK ±1.4 cm» |
| `04b-ficha-potrero.png` | AND-R3 | Mapa de calles, «RTK Fijo», «Aforo» (decir «Capacidad»), «Condiciones agronómicas» (es «Descripción»), «Fuente de agua» |
| `04c-ficha-elemento.png` | AND-R3 | Fotos, «Capacidad neta», «Alimentación», «Infraestructura hidráulica», «Ficha técnica», potrero «El Viento» y áreas de las etiquetas, «Copiar» |
| `04d-ficha-mision.png` | AND-R3 | «Mosaico RGB • DJI Mavic 3E», mapa de calles, «Parámetros de telemetría» (es «Datos del plan») |
| `04e-capas.png` | AND-R3 | «Sincronizado», «MAGNA-SIRGAS», «configuración fija de misión activa» |
| `04f-gps.png` | AND-R5 | «Modo Campo Asistido», «Lote 04/06», «Pasto Mombaza», «Aforo programado», «Finca El Recuerdo». Solo valen los 3 estados de GPS |
| `05-potreros.png` | AND-R2 | — |
| `06-detalle-potrero.png` | AND-R2 | «Sensor multiespectral», «2 Puntos de agua», «Ortomosaico 1:500», rótulos de la foto |
| `07-finca-3d.png` | AND-R6 | «Topografía digital» |

## Prompt usado en Stitch

Enviar **BASE + LOTE A** y, al terminar, **BASE + LOTE B**, en un proyecto
de Stitch con `../DESIGN.md` como único sistema de diseño.

```
BASE — GeoLux Android (aplica a todas las pantallas)

App Android de SOLO LECTURA para ganaderos en Colombia. Abre un archivo .geolux
exportado desde GeoLux Desktop y muestra los datos de la finca sin internet.
Usa el sistema de diseño cargado. Textos en español. Números con punto decimal
y 2 decimales, fechas dd/mm/aaaa.

NAVEGACIÓN: barra inferior fija con 4 pestañas: Mi finca, Mapa, Potreros, 3D.
Cabecera: «GEOLUX», nombre de la pantalla y chip «Sin conexión». Las hojas
inferiores (fichas, capas) siempre van ENCIMA de la barra inferior, nunca la
contienen.

PROHIBIDO (no dibujar nunca): avatar o perfil, login, nube o sincronización,
botones de crear, editar, borrar, exportar o compartir, FAB de añadir, ganado
(animales, cabezas, ocupación, sobrecarga, aforo, rotación), consejos o
diagnósticos, estados de instalaciones (operativo, nivel, etc.), RTK, cámara o
dron, fotos, mapas de calles, elevación, pendiente o suelo.
Nunca uses la palabra «lote»: di «potrero».

DATOS DE EJEMPLO (usar exactamente estos, no inventar otros):
Finca: Hacienda La Ceiba · Arauca, Colombia · 245.30 ha · perímetro 6.42 km ·
paquete creado el 07/10/2026 · archivo finca_la_ceiba.geolux (48.6 MB).
Potreros (8): La Ceiba 32.40 ha Activo cap. 40 · El Bajo 28.50 ha En descanso
cap. 40 · El Palmar 41.20 ha Activo cap. 60 · Mata de Caña 19.80 ha Inactivo
cap. 25 · El Mirador 38.10 ha Activo cap. 60 · San José 30.00 ha Activo cap. 45
· La Loma 27.30 ha En descanso cap. 35 · El Estero 28.00 ha Activo cap. 40.
(Capacidad = «Capacidad: 40 animales».)
Elementos (9): Bebedero ×4, Jagüey ×1, Reservorio ×1, Corral ×1,
Manga de manejo ×1, Casa ×1. Cada elemento tiene tipo, nombre y descripción.
Líneas (6): Cerca ×3, Cerca eléctrica ×1, Tubería ×1, Camino interno ×1.
Cada línea tiene categoría, nombre y longitud.
Misiones (2): «Levantamiento norte» · Exportada · 07/10/2026 · altitud 80 m ·
GSD 2.10 cm/px · velocidad 7.20 m/s · solape frontal 80 % · solape lateral
75 % · 412 fotos · 10 waypoints · 6.80 km · 18 min · batería: Suficiente.
«Ortofoto sector este» · Válida · 15/10/2026.
Productos: Ortomosaico 07/10/2026 y Modelo 3D 07/10/2026, ambos de origen
SINTÉTICO: siempre con una etiqueta ámbar «Sintético · datos de prueba».

MAPA: el fondo es SIEMPRE el ortomosaico (foto aérea de potreros en la llanura
de Arauca), nunca un mapa de calles; área de trabajo con línea naranja
discontinua; potreros con borde de color y etiqueta «nombre · área»; elementos
como pines con icono; líneas grises; área de misión azul discontinua y ruta de
waypoints numerados en azul; punto azul de GPS. Botones flotantes: capas, mi
ubicación, ver todo.
```

```
LOTE A — crea estas pantallas

01 Mis fincas: botón principal «Importar archivo .geolux»; tarjeta de Hacienda
La Ceiba con miniatura del mapa del predio, ubicación, 245.30 ha, 8 potreros,
2 misiones, «Creado el 07/10/2026» y botón «Abrir finca».
01b Mis fincas vacío: «Aún no hay fincas en el teléfono» + «Importar archivo .geolux».
02a Vista previa del paquete (hoja inferior): nombre del archivo y tamaño,
nombre de la finca, fecha de creación, conteos (8 potreros, 9 elementos,
6 líneas, 2 misiones), productos (Ortomosaico, Modelo 3D, con etiqueta
Sintético). Botones «Importar» y «Cancelar».
02b Importando: 3 pasos (Abriendo archivo, Comprobando que esté completo,
Preparando el mapa) con progreso; al final «Finca importada» + «Abrir finca».
02c Ya importado: aviso «Este paquete ya está importado» + «Abrir finca».
02d Reemplazar: diálogo «¿Reemplazar Hacienda La Ceiba?» que compara «En el
teléfono: 07/10/2026» y «Archivo nuevo: 15/05/2026» con etiqueta «Más antiguo»
en el nuevo. Botones «Cancelar» y «Reemplazar»; nota «Se pedirá confirmación».
02e Error: tarjeta roja «El archivo está dañado o fue modificado» y variante
«Se creó con una versión más nueva de GeoLux (formato 2); actualiza la
aplicación». Botón «Elegir otro archivo».
03 Mi finca: miniatura del mapa con chip «Ortomosaico · 07/10/2026»;
métricas área total, perímetro y potreros (botón «Ver potreros»);
«Instalaciones»: cantidad por tipo de elemento; «Misiones»: las 2 misiones con
fecha y estado; botón «Explorar en el mapa».
05 Potreros: resumen «8 potreros · 245.30 ha»; filtros Todos (8), Activos (5),
En descanso (2), Inactivos (1); tarjetas con color, nombre, área, estado y
capacidad.
06 Detalle de potrero (La Ceiba): miniatura del ortomosaico con el borde del
potrero; estado; área 32.40 ha, perímetro 2.30 km, capacidad 40 animales;
descripción; «Elementos dentro del potrero» (2 Bebedero, 1 Corral: tipo y
nombre); «Líneas del potrero» (Cerca y Tubería: categoría, nombre, longitud);
botón «Ver en el mapa».
```

```
LOTE B — crea estas pantallas (mismo mapa en todas)

04a Mapa: mapa a pantalla completa con todas las capas, sin hoja abierta.
04b Ficha de potrero: hoja inferior con «La Ceiba» + etiqueta «Activo»; área,
perímetro, capacidad, descripción; botón «Ver detalle del potrero».
04c Ficha de elemento: hoja con «Bebedero norte» + etiqueta «Bebedero»;
descripción; potrero donde está (La Ceiba); coordenadas; botón «Centrar en el
mapa».
04d Ficha de misión: hoja con «Levantamiento norte» + etiqueta «Exportada»;
fecha y todos los datos de la misión en cuadrícula de 2 columnas; botón
«Ver ortomosaico».
04e Capas: hoja «Capas del mapa» con interruptores: Ortomosaico, Área de
trabajo, Potreros, Elementos, Líneas, Misiones.
04f GPS: aviso ámbar sobre el mapa «Activa la ubicación para ver dónde estás»
+ «Activar»; variante «Buscando señal GPS…»; variante con el punto azul y
«Precisión ±5 m».
07 3D: visor del modelo con etiqueta ámbar «Sintético · datos de prueba»;
controles girar, centrar, pantalla completa, + y −; ayuda «Arrastra para
girar · Pellizca para acercar»; ficha con nombre del modelo, fecha
07/10/2026 y origen Sintético. Nada más.
```

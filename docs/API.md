# API REST de PlantBee

La aplicacion de escritorio levanta una API REST propia en `http://127.0.0.1:8080`
usando `com.sun.net.httpserver.HttpServer`, que viene incluido en el JDK (no requiere
dependencias extra ni un servidor aparte).

La API es de solo lectura (`GET`) y lee los datos con Hibernate a traves de los DAOs.

| Metodo | Ruta | Descripcion |
|---|---|---|
| GET | `/api/health` | Estado del servicio |
| GET | `/api/usuarios` | Identificadores y fechas de registro, sin nombres ni datos de contacto |
| GET | `/api/cultivos` | Catalogo de cultivos |
| GET | `/api/colmenas` | Colmenas con identificador del usuario y cantidad de módulos |
| GET | `/api/plantas` | Plantas con su sensor y si necesitan riego |
| GET | `/api/riegos` | Historial de riegos |
| GET | `/api/alertas` | Alertas pendientes |
| GET | `/api/cuidados` | Próximo cuidado calculado para cada planta |
| GET | `/api/cuidados?fecha=2026-11-25` | Riegos registrados y cuidados estimados para esa fecha |
| GET | `/api/cuidados?plantaId=1` | Próximo cuidado de la planta indicada |
| GET | `/api/cuidados?mes=2026-11` | Riegos y cuidados del mes visible en el calendario |

Los filtros `fecha` y `plantaId` se pueden combinar. Si no hay coincidencias,
la respuesta es `[]`. El filtro `mes` se usa por separado. Cada actividad incluye fecha, planta, cultivo, colmena,
módulo, tipo, estado, descripción, motivo, último riego, humedad actual
y mínima, y próxima revisión. Las fechas se devuelven en formato `AAAA-MM-DD`.
Los datos no disponibles se devuelven como `null`. Las revisiones son estimadas
y ninguna consulta registra ni ejecuta un riego.

## Alertas pendientes y lecturas

`GET /api/alertas` conserva `id`, `planta`, `ubicacion`, `tipo`, `mensaje`
y `fechaHora`. También devuelve `plantaId`, `humedadActual` y `humedadMinima`.
`mensaje` conserva el texto guardado al crear el aviso. Para mostrar la condición
vigente, use los dos porcentajes y relacione el aviso con
`GET /api/cuidados?plantaId=...` mediante `plantaId`. La humedad requiere atención
cuando `humedadActual < humedadMinima`. Una lectura ausente se representa como
`null` y da lugar a información insuficiente en cuidados.

Si se marca o elimina un aviso manualmente, la planta todavía aparece como
**Requiere atención** en cuidados mientras la lectura siga por debajo del mínimo.
Una revisión explícita o una nueva lectura puede generar otro aviso. Los GET
leen el estado existente y no concilian alertas, cambian sensores ni registran riegos.

## Ejemplo

```bash
curl http://127.0.0.1:8080/api/plantas
```

```json
[
  {
    "id": 1,
    "nombre": "Albahaca grande",
    "cultivo": "Albahaca",
    "ubicacion": "Balcon - Nivel 2",
    "estado": "Creciendo",
    "siembra": "2026-11-10",
    "sensor": "SH-001",
    "humedad": 35,
    "necesitaRiego": true
  }
]
```

## Respuestas y alcance

- `200` devuelve un objeto para salud o una lista para las rutas de datos.
- `400` indica filtros de cuidados inválidos.
- `404` indica una ruta desconocida dentro de `/api`.
- `405` indica que el método no es GET.
- `500` devuelve un mensaje genérico. Los detalles internos no forman parte del JSON.
- El servidor escucha solo en loopback IPv4. Las rutas de usuarios y colmenas
  conservan identificadores, pero no publican el nombre de la persona propietaria
  ni su correo o teléfono.

## Notas de implementacion

- Las entidades **no** se serializan directamente: tienen relaciones bidireccionales
  (`Colmena -> Modulo -> Colmena`, `Planta -> Sensor -> Planta`) y Gson entraria en
  recursion infinita. Las rutas publican mapas planos u objetos de respuesta sin relaciones.
- Si el puerto 8080 esta ocupado, la API no arranca pero **la aplicacion de escritorio
  sigue funcionando normalmente**.
- La API se detiene automaticamente al cerrar la ventana (`App.stop()`).

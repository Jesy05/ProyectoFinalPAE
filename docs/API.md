# API REST de PlantBee

La aplicacion de escritorio levanta una API REST propia en `http://localhost:8080`
usando `com.sun.net.httpserver.HttpServer`, que viene incluido en el JDK (no requiere
dependencias extra ni un servidor aparte).

La API es de solo lectura (`GET`) y lee los datos con Hibernate a traves de los DAOs.

| Metodo | Ruta | Descripcion |
|---|---|---|
| GET | `/api/health` | Estado del servicio |
| GET | `/api/usuarios` | Usuarios registrados |
| GET | `/api/cultivos` | Catalogo de cultivos |
| GET | `/api/colmenas` | Colmenas con su dueno y cantidad de modulos |
| GET | `/api/plantas` | Plantas con su sensor y si necesitan riego |
| GET | `/api/riegos` | Historial de riegos |
| GET | `/api/alertas` | Alertas pendientes |

## Ejemplo

```bash
curl http://localhost:8080/api/plantas
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

## Notas de implementacion

- Las entidades **no** se serializan directamente: tienen relaciones bidireccionales
  (`Colmena -> Modulo -> Colmena`, `Planta -> Sensor -> Planta`) y Gson entraria en
  recursion infinita. Cada endpoint arma un mapa plano con los datos publicados.
- Si el puerto 8080 esta ocupado, la API no arranca pero **la aplicacion de escritorio
  sigue funcionando normalmente**.
- La API se detiene automaticamente al cerrar la ventana (`App.stop()`).
# PlantBee - Gestion de colmenas de huertos verticales

Aplicacion de escritorio (JavaFX + Hibernate ORM + PostgreSQL) para administrar **PlantBee**, un sistema de
huerto vertical modular de forma hexagonal para espacios urbanos reducidos. Cada colmena esta formada por
modulos apilables; cada planta tiene un sensor de humedad del sustrato y el riego aprovecha agua de lluvia
o agua gris filtrada por un biofiltro.

Proyecto de fin de curso - Programacion de Aplicaciones de Escritorio (UAM).

## Equipo

| Integrante | Usuario GitHub | Responsabilidad |
|---|---|---|
| Gabriela Espinoza | @gabi122007 | Configuracion de Hibernate, `GenericDao` (CRUD base), entidades `Usuario` y `Cultivo`, pantalla de Usuarios |
| Fabiola Sarai Lanuza Paz | @FB1820 | Entidades `Colmena` y `Modulo`, CRUD de colmenas con creacion de modulos en cascada |
| Jose Cristo Carvallo Herrera | @JoseCristoCarvalloHerrera | Entidades `Planta` y `Sensor` (uno a uno), CRUD de plantas y simulacion de sensores |
| Jesy Gonzalez | @Jesy05 | Entidades `Riego` y `Alerta`, historial de riego, alertas, README e integracion final |

## Tecnologias

- Java 21 (proyecto Maven **no modular**, sin `module-info.java`)
- JavaFX 21 (FXML)
- Hibernate ORM 6.6 (JPA / Jakarta Persistence)
- PostgreSQL + driver JDBC 42.7

## Modelo de datos (8 entidades)

```
Usuario 1 ──── N Colmena 1 ──── N Modulo 1 ──── N Planta N ──── 1 Cultivo
                                                   │ 1
                                       ┌───────────┼───────────┐
                                       1           N           N
                                    Sensor       Riego       Alerta
```

| Entidad | Tabla | Relaciones JPA |
|---|---|---|
| `Usuario` | `usuario` | (1:N con Colmena, mapeada desde Colmena) |
| `Cultivo` | `cultivo` | (1:N con Planta, mapeada desde Planta) |
| `Colmena` | `colmena` | `@ManyToOne` Usuario, `@OneToMany(cascade = ALL, orphanRemoval)` Modulo |
| `Modulo` | `modulo` | `@ManyToOne` Colmena, restriccion unica (colmena, nivel) |
| `Planta` | `planta` | `@ManyToOne` Cultivo, `@ManyToOne` Modulo, `@OneToOne(cascade = ALL)` Sensor |
| `Sensor` | `sensor` | `@OneToOne` Planta (llave foranea `planta_id` unica) |
| `Riego` | `riego` | `@ManyToOne` Planta |
| `Alerta` | `alerta` | `@ManyToOne` Planta |

## Decisiones de diseno

### Relaciones y por que cada cascade

| Relacion | Tipo | Dueno (llave foranea) | Cascade | Por que |
|---|---|---|---|---|
| Colmena - Modulo | 1:N **bidireccional** | `Modulo` (`@JoinColumn colmena_id`); `Colmena` usa `mappedBy` | `ALL` + `orphanRemoval` | Un modulo es una pieza fisica de la colmena y no existe sin ella: se guarda y se borra con su colmena. Si se quita de la lista (`quitarModulo`) se borra. |
| Planta - Sensor | 1:1 bidireccional | `Sensor` (`planta_id` **unico**); `Planta` usa `mappedBy` | `ALL` + `orphanRemoval` | El sensor esta instalado en la maceta: se crea en el mismo `persist` de la planta y se elimina con ella. |
| Usuario - Colmena | N:1 | `Colmena` (`usuario_id`) | Ninguno | Un usuario existe por si mismo; borrarlo no debe borrar colmenas en cadena. PostgreSQL impide borrarlo si tiene colmenas. |
| Planta - Cultivo | N:1 | `Planta` (`cultivo_id`) | Ninguno | El cultivo es un catalogo compartido por muchas plantas. |
| Planta - Modulo | N:1 | `Planta` (`modulo_id`) | Ninguno | Una planta se mueve o se borra sin afectar al modulo. |
| Riego / Alerta - Planta | N:1 | `Riego`, `Alerta` (`planta_id`) | Ninguno | Son historial: no se pierden por accidente; la base impide borrar una planta con riegos o alertas. |

- **Metodos de ayuda:** `Colmena.agregarModulo()` / `quitarModulo()` y `Planta.setSensor()` actualizan
  los dos lados de la relacion a la vez. `getModulos()` devuelve una lista de solo lectura para obligar a
  usar esos metodos.
- **`mappedBy` en el lado inverso** evita que Hibernate cree una tabla intermedia innecesaria.

### Otras decisiones

- **`FetchType.LAZY` + `JOIN FETCH`:** ninguna relacion es EAGER. Cada consulta HQL trae con `JOIN FETCH`
  solo lo que la pantalla necesita, y la sesion se cierra en el DAO.
- **`toString` seguro:** `Modulo` y `Planta` revisan `Hibernate.isInitialized(...)` antes de leer una
  relacion LAZY, asi nunca lanzan `LazyInitializationException` y no recorren colecciones (sin bucles).
- **Un solo `SessionFactory`** en `HibernateUtil` y un `GenericDao<T>` con el CRUD y las transacciones
  (commit y rollback) que heredan todos los DAO.
- **Credenciales fuera del repositorio:** `db.properties` esta en `.gitignore`; se sube solo
  `db.properties.example`.
- **La API propia publica mapas planos**, no las entidades, para no exponer de mas ni entrar en ciclos
  (Planta -> Sensor -> Planta) al convertir a JSON.

## Operaciones CRUD y consultas HQL

El CRUD se hace solo con Hibernate (`persist`, `merge`, `find`, `remove`) desde `dao/GenericDao.java`;
no se usa JDBC directo. Consultas HQL sobre entidades relacionadas:

| DAO | Metodo | Que hace |
|---|---|---|
| `ColmenaDao` | `listar()` / `buscarPorUsuario()` | Colmenas con su usuario y modulos (`JOIN FETCH`) |
| `ModuloDao` | `listar()` | Modulos con su colmena |
| `PlantaDao` | `listar()` / `buscarPorColmena()` | Plantas con cultivo, modulo, colmena y sensor |
| `PlantaDao` | `listarQueNecesitanRiego()` | Plantas cuyo sensor esta bajo la humedad minima del cultivo |
| `SensorDao` | `simularSecado()` | `UPDATE` masivo en HQL para simular nuevas lecturas |
| `RiegoDao` | `registrar()` | Guarda el riego, restablece la humedad y atiende alertas en una sola transaccion |
| `RiegoDao` | `litrosPorOrigen()` | `SUM` + `GROUP BY` del agua usada por origen (lluvia, gris filtrada, potable) |
| `AlertaDao` | `listarPendientes()` / `generarAlertasHumedad()` | Alertas con planta y ubicacion; crea alertas con `NOT EXISTS` |

## Probar Reportes desde IntelliJ

Seleccione **PlantBee Demo Reportes** en las configuraciones de ejecución de
IntelliJ. Este perfil usa `database/demo-reportes.properties` y la base
`plantbee_demo_reportes` en el PostgreSQL local aislado del puerto 55432.
`PLANTBEE_DB_CONFIG` selecciona un archivo de configuración. Las variables
`PLANTBEE_DB_URL`, `PLANTBEE_DB_USER` y `PLANTBEE_DB_PASSWORD`, si están presentes
en el proceso de IntelliJ o en la configuración, tienen prioridad sobre el
archivo. Las variables definidas en una terminal abierta después de iniciar
IntelliJ solo afectan a esa terminal y sus procesos hijos.

El perfil requiere que el clúster de demostración ya exista y esté iniciado.
Detener y reiniciar el mismo clúster conserva los registros en su directorio
de datos. El clúster preparado para esta prueba está en una ruta temporal,
por lo que una limpieza de Windows puede eliminarlo. El script
[`datos_demo_reportes.sql`](database/datos_demo_reportes.sql) permite repoblar
una base demo inicializada por Hibernate.

## Reportes (JasperReports)

La pestaña **Reportes** genera cuatro reportes con JasperReports y permite
previsualizarlos y exportarlos a **PDF** o **Excel (.xlsx)**.

| Reporte | Plantilla | Contenido |
|---|---|---|
| Inventario de plantas y sensores | `reportes/plantas.jrxml` | Planta, cultivo, ubicacion, estado, humedad y si necesita riego |
| Historial de riego | `reportes/riegos.jrxml` | Fecha, planta, cantidad y origen del agua, con litros por origen |
| Alertas pendientes | `reportes/alertas.jrxml` | Alertas sin atender con su planta y mensaje |
| Calendario de cuidados | `reportes/calendario.jrxml` | Fecha, planta, actividad, estado y detalle del mes elegido |

Abra **Filtros de reportes** para acotar el inventario por cultivo y colmena.
Los riegos y las alertas comparten el período histórico elegido. Puede usar
el mes, el año, un rango con ambas fechas incluidas o todo el historial.
Las alertas también se pueden seleccionar por estado y tipo. Por defecto se
muestra el mes actual y las alertas pendientes.

El calendario usa el mes y año seleccionados en el panel, incluso si el
historial usa un rango. Sus filas proceden del mismo servicio que alimenta
el calendario de JavaFX. El **Reporte general** reúne las cuatro secciones
en un PDF o cuatro hojas en Excel y conserva el inventario a la fecha de
generación. **Exportar todos (ZIP)** aplica los mismos filtros a los
ocho archivos individuales.

- Los datos se leen con **Hibernate** (los mismos DAO de las pantallas) y se pasan al reporte con
  `JRMapCollectionDataSource`; no se usa JDBC directo.
- `ReporteService` compila cada `.jrxml` una sola vez, llena el reporte y lo exporta.
- La vista previa dibuja cada pagina como imagen (`JasperPrintManager.printPageToImage`) dentro de JavaFX.
- El Excel sale como tabla: encabezado verde con filtros, encabezado fijo al desplazarse, filas
  alternadas y numeros/fechas como celdas reales (no texto).
- La generacion y la exportacion corren en segundo plano (`Task`) para no congelar la ventana.

## Instalacion

1. Instale **JDK 21**, **IntelliJ IDEA** y **PostgreSQL** (con pgAdmin).
2. Clone el repositorio:
   ```bash
   git clone https://github.com/Jesy05/ProyectoFinalPAE.git
   ```
3. En pgAdmin cree la base de datos:
   ```sql
   CREATE DATABASE plantbedb;
   ```
4. Configure PostgreSQL con las variables `PLANTBEE_DB_URL`, `PLANTBEE_DB_USER` y
   `PLANTBEE_DB_PASSWORD`, o cree un `db.properties` local fuera de los recursos
   empaquetados. Use las claves `db.url`, `db.user` y `db.password`. Puede definir
   `PLANTBEE_DB_CONFIG` para señalar ese archivo. Sin esa variable, se busca primero
   `db.properties` en el directorio de trabajo y luego el archivo local existente en
   `src/main/resources/db.properties`. Cada variable de credenciales tiene prioridad
   sobre el archivo. Use únicamente marcadores en la plantilla versionada.
5. Abra el proyecto en IntelliJ y espere a que Maven descargue las dependencias.

## Ejecucion

- Desde IntelliJ: ejecute la clase `ni.edu.uam.proyectofinalpae.Launcher`.
- Desde la terminal:
  ```bash
  ./mvnw clean javafx:run
  ```

La primera vez Hibernate crea las tablas (`hbm2ddl.auto=update`). Despues ejecute en pgAdmin el archivo
`database/datos_prueba.sql` para cargar el catalogo de cultivos.

## Servicios REST

PlantBee consume [Wikipedia](docs/WIKIPEDIA-PLANTAS.md) desde Plantas.
La [API propia](docs/API.md) sirve datos de solo lectura a clientes del mismo equipo
por `127.0.0.1:8080`.

## Flujo de demostracion

1. **Usuarios**: registre un usuario.
2. **Colmenas**: cree una colmena para ese usuario (se generan sus modulos automaticamente).
3. **Plantas**: registre una planta con una lectura inferior al mínimo del cultivo.
   Al guardar, la alerta de humedad queda pendiente. Edite la lectura o pulse
   *Simular lectura de sensores* para comprobar que se muestran los porcentajes actuales.
4. **Riegos y alertas**: pulse *Revisar sensores* para conciliar las lecturas
   existentes. Registre un riego. Si tiene sensor, la humedad sube al 90 %.
   La alerta de humedad se cierra cuando el 90 % alcanza el mínimo del cultivo.
   Abra **Ver calendario** para consultar el riego registrado y la condición
   vigente. El calendario usa la API REST local y muestra el pronóstico de
   Open-Meteo para Managua cuando hay datos para la fecha seleccionada.

La simulación, las ediciones de lecturas y los riegos confirman el cambio y la
conciliación de alertas en la misma transacción. Si faltan datos, se conserva el
aviso anterior. Los reportes de alertas pendientes muestran la lectura vigente.

## Verificación de alertas y cuidados

Ejecute `./mvnw test` para las pruebas unitarias. Para incluir la suite de
integración, cree una base PostgreSQL **desechable** llamada `plantbee_test`
en loopback. Configure `PLANTBEE_DB_URL` como
`jdbc:postgresql://127.0.0.1:PUERTO/plantbee_test` y defina `PLANTBEE_DB_USER`
y `PLANTBEE_DB_PASSWORD` para esa instancia en el entorno de la terminal de
pruebas. La suite crea datos sintéticos y una restricción de prueba para provocar
un rollback. En un equipo con escritorio, defina también
`PLANTBEE_TEST_JAVAFX=true` para incluir la verificación de las tablas en una
ventana invisible.

Para reproducir el flujo desde la aplicación en esa base de pruebas:

1. Use un cultivo con un mínimo de 40 % y una planta con 25 %. Compruebe
   **Requiere atención** en el calendario y un único aviso en la tabla. Cambie
   la lectura a 35 % y pulse **Revisar sensores** dos veces. El mismo aviso
   muestra 35 %. A 40 % se cierra y a 41 % sigue cerrado.
2. Con humedad baja, marque el aviso como atendido o elimínelo manualmente.
   El calendario sigue indicando atención. Revise sensores para comprobar que
   vuelve a aparecer un único aviso. Simule lecturas y verifique el nuevo valor.
3. Registre un riego y compruebe **Riego registrado** y 90 % en Plantas. Con
   un cultivo de prueba cuyo mínimo sea 95 %, también debe seguir apareciendo
   **Requiere atención**. Un aviso de otro tipo permanece pendiente.
4. Consulte `/api/alertas` y `/api/cuidados?plantaId=...` según la
   [documentación REST](docs/API.md). Compare los porcentajes. Exporte PDF y
   Excel desde Reportes y compruebe el texto vigente de la alerta.
5. Sin acceso a Internet, mantenga PostgreSQL y la aplicación en ejecución.
   Abra el calendario para verificar que las actividades locales siguen visibles
   y que el fallo del pronóstico se indica por separado. Cierre y vuelva a abrir
   el calendario después de un cambio y verifique la lectura vigente.

## Pronóstico y reportes JasperReports

El calendario carga por separado `GET /api/cuidados?mes=AAAA-MM` y el
pronóstico diario de Open-Meteo para Managua. **Actualizar clima** vuelve a
consultar el proveedor. PlantBee muestra la temperatura máxima, la probabilidad
máxima de precipitación, la fuente y la hora local de la última consulta exitosa.
La fecha seleccionada determina qué pronóstico se muestra. Los fallos de clima
se indican en su propio bloque y los cuidados locales permanecen consultables.

Al iniciar PlantBee se concilian en segundo plano las alertas de humedad con
las lecturas guardadas. La revisión usa los mismos bloqueos que **Revisar
sensores** y conserva los avisos atendidos en el historial. Si una condición
sigue activa, puede generar otro aviso pendiente.

En **Reportes**, seleccione *Inventario de plantas y sensores* o *Historial de
riego*, pulse **Generar reporte** y elija PDF o Excel. Ambos formatos usan el
mismo conjunto de filas y fecha de generación. Si cambia un dato desde PlantBee,
genere de nuevo la vista previa para exportar los datos vigentes.

| Reporte | Diseño para Jaspersoft Studio | Compilado | PDF de ejemplo |
|---|---|---|---|
| Inventario de plantas y sensores | [JRXML](src/main/resources/reportes/plantas.jrxml) | [JASPER](src/main/resources/reportes/plantas.jasper) | [6 plantas](output/pdf/plantbee-plantas.pdf) |
| Historial de riego | [JRXML](src/main/resources/reportes/riegos.jrxml) | [JASPER](src/main/resources/reportes/riegos.jasper) | [7 riegos](output/pdf/plantbee-riegos.pdf) |
| Alertas pendientes | [JRXML](src/main/resources/reportes/alertas.jrxml) | [JASPER](src/main/resources/reportes/alertas.jasper) | |
| Calendario de cuidados | [JRXML](src/main/resources/reportes/calendario.jrxml) | [JASPER](src/main/resources/reportes/calendario.jasper) | |

Los ejemplos corresponden a los registros de demostración que estaban
guardados en PostgreSQL el 26/11/2026. Las dos exportaciones muestran encabezados
y fecha de generación. Para editar un diseño, abra el JRXML en Jaspersoft
Studio y vuelva a compilarlo con la versión de JasperReports del `pom.xml`.
PlantBee compila el JRXML en su primera generación, de modo que los cambios al
diseño se reflejan en la aplicación.

## Flujo de trabajo con Git

- `main` siempre estable. Cada integrante trabajo en su rama `feature/...`.
- Commits pequenos con prefijos `feat:`, `fix:`, `chore:`, `docs:`.
- Cada rama se integro con un Pull Request revisado y aprobado por otro integrante.

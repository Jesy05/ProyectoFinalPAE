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
4. Copie `src/main/resources/db.properties.example` como `src/main/resources/db.properties`
   y escriba su usuario y clave local de PostgreSQL. **Este archivo esta en `.gitignore` y nunca se sube.**
5. Abra el proyecto en IntelliJ y espere a que Maven descargue las dependencias.

## Ejecucion

- Desde IntelliJ: ejecute la clase `ni.edu.uam.proyectofinalpae.Launcher`.
- Desde la terminal:
  ```bash
  ./mvnw clean javafx:run
  ```

La primera vez Hibernate crea las tablas (`hbm2ddl.auto=update`). Despues ejecute en pgAdmin el archivo
`database/datos_prueba.sql` para cargar el catalogo de cultivos.

## Flujo de demostracion

1. **Usuarios**: registre un usuario.
2. **Colmenas**: cree una colmena para ese usuario (se generan sus modulos automaticamente).
3. **Plantas**: siembre plantas en los modulos con su sensor de humedad. Pulse *Simular lectura de sensores* varias veces.
4. **Riegos y alertas**: pulse *Revisar sensores* para generar alertas; registre un riego con agua de lluvia
   y vea como la alerta queda atendida y se actualiza el total de agua reutilizada.

## Flujo de trabajo con Git

- `main` siempre estable. Cada integrante trabajo en su rama `feature/...`.
- Commits pequenos con prefijos `feat:`, `fix:`, `chore:`, `docs:`.
- Cada rama se integro con un Pull Request revisado y aprobado por otro integrante.

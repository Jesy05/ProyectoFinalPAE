package ni.edu.uam.proyectofinalpae.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import ni.edu.uam.proyectofinalpae.dao.*;
import ni.edu.uam.proyectofinalpae.api.ApiRutas;
import ni.edu.uam.proyectofinalpae.dto.ActividadCuidado;
import ni.edu.uam.proyectofinalpae.model.*;
import ni.edu.uam.proyectofinalpae.repository.CuidadosApiRepository;
import ni.edu.uam.proyectofinalpae.repository.OpenMeteoApiRepository;
import ni.edu.uam.proyectofinalpae.service.ReporteService;
import ni.edu.uam.proyectofinalpae.util.HibernateUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "PLANTBEE_DB_URL",
        matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/plantbee_test")
class SincronizacionCuidadosTest {
    private final PlantaDao plantas = new PlantaDao();
    private final SensorDao sensores = new SensorDao();
    private final AlertaDao alertas = new AlertaDao();
    private final RiegoDao riegos = new RiegoDao();
    private static Modulo modulo;
    private static HttpServer api;
    private static HttpClient http;

    @BeforeAll
    static void iniciar() throws IOException {
        var usuario = new Usuario();
        usuario.setNombre("Prueba de sincronización");
        usuario.setCorreo(UUID.randomUUID() + "@example.test");
        new UsuarioDao().guardar(usuario);

        var colmena = new Colmena();
        colmena.setNombre("Colmena de pruebas");
        colmena.setFechaInstalacion(LocalDate.now());
        colmena.setUsuario(usuario);
        modulo = new Modulo(1, 100);
        colmena.agregarModulo(modulo);
        new ColmenaDao().guardar(colmena);

        api = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        api.createContext("/api", new ApiRutas());
        api.start();
        http = HttpClient.newHttpClient();
    }

    @AfterAll
    static void cerrar() {
        if (http != null) http.close();
        if (api != null) api.stop(0);
        HibernateUtil.cerrar();
    }

    @Test
    void registraEditaSimulaYRevisaSinDuplicar() throws Exception {
        Planta p = crearPlanta(40, 30);
        Alerta original = alertaHumedad(p);
        assertNotNull(original);
        assertEquals(ActividadCuidado.ATENCION, cuidado(p).get("estado").getAsString());

        Planta editada = plantas.buscarPorId(p.getId());
        editada.getSensor().setHumedadActual(35);
        plantas.actualizar(editada);
        Alerta vigente = alertaHumedad(p);
        assertEquals(original.getId(), vigente.getId());
        assertEquals(original.getMensaje(), vigente.getMensaje());
        assertTrue(vigente.getMensajeVigente().contains("35 %"));

        sensores.actualizar(conLectura(p, 40));
        assertNull(alertaHumedad(p));
        assertTrue(alertas.buscarPorId(original.getId()).isAtendida());
        assertFalse(plantas.listarQueNecesitanRiego().stream().anyMatch(item -> item.getId().equals(p.getId())));
        sensores.actualizar(conLectura(p, 50));
        assertNull(alertaHumedad(p));
        assertTrue(sensores.simularSecado() > 0);
        Alerta reabierta = alertaHumedad(p);
        assertNotNull(reabierta);
        alertas.generarAlertasHumedad();
        alertas.generarAlertasHumedad();
        assertEquals(reabierta.getId(), alertaHumedad(p).getId());
        assertEquals(35, cuidado(p).get("humedadActual").getAsInt());

        // Una revisión conserva el aviso más antiguo cuando encuentra duplicados preexistentes.
        var duplicada = new Alerta(p, Alerta.HUMEDAD_BAJA, "Duplicado de prueba");
        alertas.guardar(duplicada);
        alertas.generarAlertasHumedad();
        assertEquals(reabierta.getId(), alertaHumedad(p).getId());
        assertTrue(alertas.buscarPorId(duplicada.getId()).isAtendida());
    }

    @Test
    void sinSensorConservaAvisosYLaGestionManualNoCambiaLaLectura() throws Exception {
        Planta sinSensor = crearPlanta(40, null);
        var previa = new Alerta(sinSensor, Alerta.HUMEDAD_BAJA, "Lectura histórica");
        alertas.guardar(previa);
        alertas.generarAlertasHumedad();
        assertEquals(previa.getId(), alertaHumedad(sinSensor).getId());
        assertEquals(ActividadCuidado.INSUFICIENTE, cuidado(sinSensor).get("estado").getAsString());
        assertTrue(filaAlerta(sinSensor).get("humedadActual").isJsonNull());
        Sensor temporal = new Sensor("Prueba-" + UUID.randomUUID().toString().substring(0, 16), 17);
        temporal.setPlanta(sinSensor);
        sensores.guardar(temporal);
        assertEquals(17, filaAlerta(sinSensor).get("humedadActual").getAsInt());
        sensores.eliminar(temporal.getId());
        assertTrue(filaAlerta(sinSensor).get("humedadActual").isJsonNull());
        assertEquals(previa.getId(), alertaHumedad(sinSensor).getId());

        Planta seca = crearPlanta(40, 20);
        Alerta original = alertaHumedad(seca);
        alertas.marcarAtendida(original.getId());
        assertNull(alertaHumedad(seca));
        assertEquals(20, cuidado(seca).get("humedadActual").getAsInt());
        assertEquals(ActividadCuidado.ATENCION, cuidado(seca).get("estado").getAsString());

        alertas.generarAlertasHumedad();
        Alerta nueva = alertaHumedad(seca);
        assertNotNull(nueva);
        alertas.eliminar(nueva.getId());
        assertNull(alertaHumedad(seca));
        assertEquals(20, plantas.buscarPorId(seca.getId()).getSensor().getHumedadActual());
        sensores.actualizar(conLectura(seca, 21));
        assertNotNull(alertaHumedad(seca));
    }

    @Test
    void registraRiegoYConservaOtrosTiposOUnMinimoMayorA90() throws Exception {
        Planta p = crearPlanta(90, 10);
        Alerta humedad = alertaHumedad(p);
        alertas.guardar(new Alerta(p, "Inspección", "Aviso de otro tipo"));

        riegos.registrar(nuevoRiego(p));
        assertEquals(90, plantas.buscarPorId(p.getId()).getSensor().getHumedadActual());
        assertTrue(alertas.buscarPorId(humedad.getId()).isAtendida());
        assertEquals("Inspección", pendientes(p).getFirst().getTipo());
        assertEquals(ActividadCuidado.REVISION, cuidado(p).get("estado").getAsString());
        assertTrue(json("/api/cuidados?fecha=" + LocalDate.now() + "&plantaId=" + p.getId())
                .asList().stream().map(e -> e.getAsJsonObject().get("estado").getAsString())
                .anyMatch(ActividadCuidado.REGISTRADO::equals));

        Planta exigente = crearPlanta(95, 20);
        Long idAlerta = alertaHumedad(exigente).getId();
        riegos.registrar(nuevoRiego(exigente));
        assertEquals(idAlerta, alertaHumedad(exigente).getId());
        assertEquals(90, filaAlerta(exigente).get("humedadActual").getAsInt());
        assertEquals(ActividadCuidado.ATENCION, cuidado(exigente).get("estado").getAsString());
    }

    @Test
    void revierteRiegoYLecturaSiFallaElCierreDeLaAlerta() {
        Planta p = crearPlanta(50, 10);
        long id = alertaHumedad(p).getId();
        // Restricción exclusiva de esta fila de la base desechable. Fuerza el fallo al hacer flush.
        try (var session = HibernateUtil.getSessionFactory().openSession()) {
            var tx = session.beginTransaction();
            session.createNativeMutationQuery("ALTER TABLE alerta ADD CONSTRAINT prueba_rollback_" + id
                    + " CHECK (id <> " + id + " OR NOT atendida)").executeUpdate();
            tx.commit();
        }

        assertThrows(RuntimeException.class, () -> riegos.registrar(nuevoRiego(p)));
        assertEquals(10, plantas.buscarPorId(p.getId()).getSensor().getHumedadActual());
        assertEquals(id, alertaHumedad(p).getId());
        assertTrue(riegos.listar().stream().noneMatch(r -> r.getPlanta().getId().equals(p.getId())));
    }

    @Test
    void revisionesConcurrentesMantienenUnaAlertaPendiente() throws Exception {
        Planta p = crearPlanta(60, 20);
        alertas.marcarAtendida(alertaHumedad(p).getId());
        var salida = new CountDownLatch(1);
        try (var hilos = Executors.newFixedThreadPool(3)) {
            var tareas = List.of(
                    hilos.submit(() -> { salida.await(); return alertas.generarAlertasHumedad(); }),
                    hilos.submit(() -> { salida.await(); return alertas.generarAlertasHumedad(); }),
                    hilos.submit(() -> { salida.await(); return sensores.simularSecado(); }));
            salida.countDown();
            for (var tarea : tareas) tarea.get(15, TimeUnit.SECONDS);
        }
        assertEquals(1, pendientes(p).size());
        assertEquals(5, filaAlerta(p).get("humedadActual").getAsInt());
    }

    @Test
    void endpointsGetSonSoloLecturaYLosReportesUsanLecturasVigentes(@TempDir Path carpeta) throws Exception {
        Planta p = crearPlanta(40, 17);
        var original = alertaHumedad(p);
        sensores.actualizar(conLectura(p, 23));
        var alerta = filaAlerta(p);
        assertEquals(p.getId(), alerta.get("plantaId").getAsLong());
        assertEquals(23, alerta.get("humedadActual").getAsInt());
        assertEquals(40, alerta.get("humedadMinima").getAsInt());
        assertEquals(original.getMensaje(), alerta.get("mensaje").getAsString());

        Planta sinAviso = crearPlanta(40, 19);
        alertas.marcarAtendida(alertaHumedad(sinAviso).getId());
        String antes = estadoPersistido();
        assertFalse(json("/api/cuidados").isEmpty());
        assertFalse(json("/api/cuidados?mes=" + YearMonth.now()).isEmpty());
        assertFalse(json("/api/cuidados?fecha=" + LocalDate.now()).isEmpty());
        assertEquals(23, cuidado(p).get("humedadActual").getAsInt());
        assertTrue(json("/api/cuidados?plantaId=" + Long.MAX_VALUE).isEmpty());
        assertEquals(400, consultar("GET", "/api/cuidados?fecha=0000-01-01").statusCode());
        assertEquals(400, consultar("GET", "/api/cuidados?mes=" + YearMonth.now() + "&plantaId=1").statusCode());
        assertEquals(404, consultar("GET", "/api/desconocida").statusCode());
        assertEquals(405, consultar("POST", "/api/alertas").statusCode());
        assertNull(alertaHumedad(sinAviso));

        var climaSinInternet = new OpenMeteoApiRepository(uri -> { throw new IOException("Sin red"); });
        var climaInvalido = new OpenMeteoApiRepository(uri -> "{}");
        assertThrows(IOException.class, climaSinInternet::consultarManagua);
        assertThrows(IOException.class, climaInvalido::consultarManagua);
        assertEquals(ActividadCuidado.ATENCION, cuidado(p).get("estado").getAsString());

        var servicio = new ReporteService();
        var tipo = ReporteService.TipoReporte.ALERTAS;
        var reporte = servicio.generar(tipo, false);
        Path pdf = carpeta.resolve("alertas.pdf");
        Path xlsx = carpeta.resolve("alertas.xlsx");
        servicio.exportarPdf(reporte, pdf.toFile());
        servicio.exportarExcel(tipo, xlsx.toFile());
        assertTrue(Files.size(pdf) > 0);
        try (var archivo = new java.util.zip.ZipFile(xlsx.toFile())) {
            var cadenas = archivo.getEntry("xl/sharedStrings.xml");
            assertNotNull(cadenas);
            try (var datos = archivo.getInputStream(cadenas)) {
                String contenido = new String(datos.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(contenido.contains(p.getNombreVisible() + " tiene 23 %"));
                assertFalse(contenido.contains(p.getNombreVisible() + " tiene 17 %"));
            }
        }
        assertEquals(antes, estadoPersistido());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "PLANTBEE_TEST_JAVAFX", matches = "true")
    void tablasYCalendarioSeActualizanAlConfirmarConPronosticoIndisponible() throws Exception {
        Planta p = crearPlanta(95, 20);
        var inicio = new CountDownLatch(1);
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            inicio.countDown();
        });
        assertTrue(inicio.await(10, TimeUnit.SECONDS));

        try {
            var pantallas = enJavaFx(() -> abrirPantallas(p));
            Stage calendario = pantallas.calendario();
            esperarCalendario(calendario, p, ActividadCuidado.ATENCION, "Humedad actual: 29 %");
            enJavaFx(() -> {
                assertTrue(calendario.getScene().getRoot().lookupAll(".label").stream()
                        .map(Label.class::cast).anyMatch(label -> label.getText().contains("Pronóstico no disponible")));
                pulsarCalendario(calendario, "Mes siguiente");
                pulsarCalendario(calendario, "Año anterior");
                pulsarCalendario(calendario, "Ir a la fecha actual");
                CambiosCuidados.ejecutar(new Button(), () -> {
                    riegos.registrar(nuevoRiego(p));
                    return null;
                }, resultado -> {}, "Falló el riego de prueba.");
                return null;
            });
            esperarCalendario(calendario, p, ActividadCuidado.ATENCION, "Humedad actual: 90 %");
            esperarCalendario(calendario, p, ActividadCuidado.REGISTRADO, "250 ml");
            comprobarBuscadoresJavaFx(pantallas.principal(), p);
            comprobarReportesJavaFx(pantallas.principal());
            enJavaFx(() -> {
                calendario.close();
                pantallas.principal().close();
                return null;
            });
        } finally {
            Platform.exit();
        }
    }

    @Test
    void exportaElReporteGeneralYLosOchoArchivosIndividuales(@TempDir Path carpeta) throws Exception {
        Planta planta = crearPlanta(40, 20);
        riegos.registrar(nuevoRiego(planta));
        crearPlanta(45, 20);
        var servicio = new ReporteService();
        var secciones = servicio.prepararSecciones(ReporteService.TipoReporte.GENERAL, YearMonth.now());
        assertEquals(4, secciones.size());
        assertEquals(1, secciones.stream().map(d -> d.parametros().get("FECHA")).distinct().count());

        var impresiones = servicio.generarSecciones(secciones, false);
        Path pdf = carpeta.resolve("general.pdf");
        servicio.exportarPdf(impresiones, pdf.toFile());
        var lector = new PdfReader(pdf.toString());
        try {
            assertEquals(impresiones.stream().mapToInt(i -> i.getPages().size()).sum(), lector.getNumberOfPages());
            assertEquals("Reporte general", lector.getInfo().get("Title"));
        } finally {
            lector.close();
        }

        Path xlsx = carpeta.resolve("general.xlsx");
        servicio.exportarExcel(secciones, xlsx.toFile());
        try (var archivo = new ZipFile(xlsx.toFile());
             var datos = archivo.getInputStream(archivo.getEntry("xl/workbook.xml"))) {
            String hojas = new String(datos.readAllBytes(), StandardCharsets.UTF_8);
            for (String nombre : List.of("Plantas", "Riegos", "Alertas", "Calendario")) {
                assertTrue(hojas.contains("name=\"" + nombre + "\""), hojas);
            }
        }

        Path zip = carpeta.resolve("reportes.zip");
        servicio.exportarTodos(YearMonth.now(), zip.toFile());
        try (var archivo = new ZipFile(zip.toFile())) {
            var esperados = Set.of("plantas.pdf", "plantas.xlsx", "riegos.pdf", "riegos.xlsx",
                    "alertas.pdf", "alertas.xlsx", "calendario.pdf", "calendario.xlsx");
            assertEquals(esperados, archivo.stream().map(e -> e.getName()).collect(java.util.stream.Collectors.toSet()));
            for (String nombre : esperados) {
                try (var datos = archivo.getInputStream(archivo.getEntry(nombre))) {
                    if (nombre.endsWith(".pdf")) {
                        var individual = new PdfReader(datos);
                        try {
                            assertTrue(individual.getNumberOfPages() > 0);
                        } finally {
                            individual.close();
                        }
                    } else {
                        try (var libro = new ZipInputStream(datos)) {
                            assertNotNull(libro.getNextEntry());
                        }
                    }
                }
            }
        }
    }

    @Test
    void reporteMensualCoincideConLaApiDelCalendarioYExportaMesesVacios(@TempDir Path carpeta) throws Exception {
        YearMonth mes = YearMonth.of(2001, 5);
        while (!json("/api/cuidados?mes=" + mes).isEmpty()) mes = mes.plusMonths(2);
        Planta planta = crearPlanta(95, 20);
        planta.setApodo("Albahaca de prueba");
        plantas.actualizar(planta);
        Riego riego = nuevoRiego(planta);
        riego.setFechaHora(mes.atDay(15).atTime(9, 30));
        riegos.registrar(riego);

        var servicio = new ReporteService();
        var tipo = ReporteService.TipoReporte.CALENDARIO;
        var historico = servicio.prepararSecciones(tipo, mes).getFirst();
        assertEquals(1, historico.filas().size());
        assertEquals(ActividadCuidado.REGISTRADO, historico.filas().getFirst().get("estado"));

        YearMonth actual = YearMonth.now();
        var reporteActual = servicio.prepararSecciones(tipo, actual).getFirst();
        var actividades = json("/api/cuidados?mes=" + actual);
        assertEquals(actividades.size(), reporteActual.filas().size());
        for (int i = 0; i < actividades.size(); i++) {
            var esperado = actividades.get(i).getAsJsonObject();
            var fila = reporteActual.filas().get(i);
            assertEquals(esperado.get("fecha").getAsString(), fila.get("fecha").toString());
            assertEquals(esperado.get("planta").getAsString(), fila.get("planta"));
            assertEquals(esperado.get("tipo").getAsString(), fila.get("actividad"));
            assertEquals(ActividadCuidado.nombreEstado(esperado.get("estado").getAsString()), fila.get("estado"));
            assertEquals(esperado.get("descripcion").getAsString(), fila.get("detalle"));
        }
        assertTrue(reporteActual.filas().stream().anyMatch(fila -> planta.getNombreVisible().equals(fila.get("planta"))
                && ActividadCuidado.ATENCION.equals(fila.get("estado"))));

        for (YearMonth periodo : List.of(mes, mes.minusMonths(1))) {
            var datos = servicio.prepararSecciones(tipo, periodo);
            boolean conRiego = periodo.equals(mes);
            assertEquals(conRiego ? 1 : 0, datos.getFirst().filas().size());
            Path pdf = carpeta.resolve(periodo + ".pdf");
            servicio.exportarPdf(servicio.generarSecciones(datos, false), pdf.toFile());
            var lector = new PdfReader(pdf.toString());
            try {
                String texto = new PdfTextExtractor(lector).getTextFromPage(1);
                assertTrue(texto.contains("Calendario de cuidados"));
                assertTrue(texto.contains(periodo.toString()));
                assertTrue(texto.contains(conRiego ? ActividadCuidado.REGISTRADO : "No hay actividades"));
                if (conRiego) assertTrue(texto.contains(planta.getNombreVisible()));
            } finally {
                lector.close();
            }
            Path excel = carpeta.resolve(periodo + ".xlsx");
            servicio.exportarExcel(datos, excel.toFile());
            try (var archivo = new ZipFile(excel.toFile());
                 var xml = archivo.getInputStream(archivo.getEntry("xl/sharedStrings.xml"))) {
                String texto = new String(xml.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(texto.contains(conRiego ? planta.getNombreVisible() : "No hay actividades"));
                assertTrue(texto.contains(periodo.toString()));
            }
        }
    }

    @Test
    void aplicaFiltrosAReportesIndividualesAlGeneralYAlZip(@TempDir Path carpeta) throws Exception {
        var planta = crearPlanta(95, 22);
        planta.setApodo("Filtro confirmado " + UUID.randomUUID().toString().substring(0, 6));
        plantas.actualizar(planta);
        var otraPlanta = crearPlanta(95, 25);
        otraPlanta.setApodo("Fila excluida");
        plantas.actualizar(otraPlanta);

        LocalDate hoy = LocalDate.now();
        for (LocalDate fecha : List.of(hoy.minusDays(1), hoy, hoy.plusDays(1))) {
            Riego riego = nuevoRiego(planta);
            riego.setFechaHora(fecha.atTime(23, 59));
            riego.setCantidadMl(fecha.equals(hoy) ? 123 : 456);
            riegos.registrar(riego);
        }

        var alerta = new Alerta(planta, "Filtro " + UUID.randomUUID().toString().substring(0, 8), "Mensaje histórico de filtro");
        alertas.guardar(alerta);
        alertas.marcarAtendida(alerta.getId());
        alertas.guardar(new Alerta(planta, alerta.getTipo(), "Pendiente excluida"));
        alertas.guardar(new Alerta(planta, "Filtro distinto", "Otro tipo excluido"));

        var servicio = new ReporteService();
        var filtros = new ReporteService.Filtros(planta.getCultivo().getId(), modulo.getColmena().getId(),
                new ReporteService.Periodo(hoy, hoy), ReporteService.EstadoAlerta.ATENDIDAS,
                alerta.getTipo(), YearMonth.now());
        var general = servicio.prepararConFiltros(ReporteService.TipoReporte.GENERAL, filtros);
        for (var seccion : general) {
            assertEquals(seccion.filas(), servicio.prepararConFiltros(seccion.tipo(), filtros).getFirst().filas());
        }
        assertEquals(List.of(planta.getId()), general.get(0).filas().stream().map(f -> f.get("id")).toList());
        assertEquals(List.of(123), general.get(1).filas().stream().filter(f -> planta.getNombreVisible().equals(f.get("planta"))).map(f -> f.get("ml")).toList());
        assertEquals(1, general.get(2).filas().size());
        assertEquals("Atendida", general.get(2).filas().getFirst().get("estado"));
        assertEquals("Mensaje histórico de filtro", general.get(2).filas().getFirst().get("mensaje"));
        assertEquals(YearMonth.now().toString(), general.get(3).parametros().get("MES"));
        assertThrows(IllegalArgumentException.class, () -> new ReporteService.Periodo(hoy, hoy.minusDays(1)));

        Path zip = carpeta.resolve("filtrados.zip");
        servicio.exportarTodosConFiltros(filtros, zip.toFile());
        try (var archivos = new ZipFile(zip.toFile())) {
            for (String nombre : List.of("plantas.pdf", "riegos.pdf", "alertas.pdf")) {
                try (var datos = archivos.getInputStream(archivos.getEntry(nombre))) {
                    var lector = new PdfReader(datos);
                    try {
                        String texto = new PdfTextExtractor(lector).getTextFromPage(1);
                        assertTrue(texto.contains("Filtro confirmado"), texto);
                        assertFalse(texto.contains("Fila excluida"), texto);
                        assertFalse(texto.contains("Pendiente excluida"), texto);
                    } finally {
                        lector.close();
                    }
                }
            }
        }
    }

    private void comprobarBuscadoresJavaFx(Stage ventana, Planta planta) throws Exception {
        var atendida = new Alerta(planta, "Inspección rápida", "Estado consultable");
        alertas.guardar(atendida);
        alertas.marcarAtendida(atendida.getId());
        enJavaFx(() -> {
            var tabs = (TabPane) ventana.getScene().getRoot().lookup("#tabPrincipal");
            tabs.getSelectionModel().select(0);
            var usuarios = tabs.getTabs().get(0).getContent();
            assertTrue(buscar(usuarios, "txtBuscarUsuarios", "tblUsuarios", "SINCRONIZACION").stream()
                    .map(Usuario.class::cast).anyMatch(u -> u.getId().equals(modulo.getColmena().getUsuario().getId())));
            assertEquals(1, buscar(usuarios, "txtBuscarUsuarios", "tblUsuarios", modulo.getColmena().getUsuario().getCorreo()).size());

            tabs.getSelectionModel().select(1);
            var colmenas = tabs.getTabs().get(1).getContent();
            assertTrue(buscar(colmenas, "txtBuscarColmenas", "tblColmenas", "pruebas sincronizacion").stream()
                    .map(Colmena.class::cast).anyMatch(c -> c.getId().equals(modulo.getColmena().getId())));

            tabs.getSelectionModel().select(2);
            var vistaPlantas = tabs.getTabs().get(2).getContent();
            assertEquals(1, buscar(vistaPlantas, "txtBuscarPlantas", "tblPlantas",
                    planta.getCultivo().getNombre() + " colmena").size());
            assertTrue(buscar(vistaPlantas, "txtBuscarPlantas", "tblPlantas", "sin-coincidencias-1234").isEmpty());

            tabs.getSelectionModel().select(3);
            var riego = tabs.getTabs().get(3).getContent();
            assertEquals(1, buscar(riego, "txtBuscarRiegos", "tblRiegos",
                    planta.getNombreVisible() + " " + LocalDate.now() + " lluvia").size());
            assertTrue(buscar(riego, "txtBuscarAlertas", "tblAlertas",
                    planta.getNombreVisible() + " inspeccion atendida").isEmpty());
            var selector = (javafx.scene.control.ComboBox<?>) riego.lookup("#cbEstadoBusquedaAlerta");
            for (int indice = 0; indice < selector.getItems().size(); indice++) {
                if (selector.getItems().get(indice).toString().equals("Atendidas")) {
                    selector.getSelectionModel().select(indice);
                    break;
                }
            }
            var halladas = buscar(riego, "txtBuscarAlertas", "tblAlertas",
                    planta.getNombreVisible() + " inspeccion atendida");
            assertEquals(List.of(atendida.getId()), halladas.stream().map(Alerta.class::cast).map(Alerta::getId).toList());
            return null;
        });
    }

    private static List<?> buscar(javafx.scene.Node vista, String idCampo, String idTabla, String consulta) {
        var campo = (javafx.scene.control.TextField) vista.lookup("#" + idCampo);
        var tabla = (TableView<?>) vista.lookup("#" + idTabla);
        campo.setText(consulta);
        return List.copyOf(tabla.getItems());
    }

    private static void comprobarReportesJavaFx(Stage ventana) throws Exception {
        for (var tipo : List.of(ReporteService.TipoReporte.PLANTAS, ReporteService.TipoReporte.RIEGOS,
                ReporteService.TipoReporte.CALENDARIO, ReporteService.TipoReporte.GENERAL)) {
            var generar = enJavaFx(() -> {
                var raiz = ventana.getScene().getRoot();
                var tabs = (TabPane) raiz.lookup("#tabPrincipal");
                tabs.getSelectionModel().select(4);
                var contenido = tabs.getTabs().get(4).getContent();
                @SuppressWarnings("unchecked")
                var selector = (javafx.scene.control.ComboBox<ReporteService.TipoReporte>) contenido.lookup("#cbReporte");
                selector.setValue(tipo);
                if (tipo == ReporteService.TipoReporte.CALENDARIO) {
                    var panel = (javafx.scene.control.TitledPane) contenido.lookup("#panelFiltros");
                    panel.setExpanded(true);
                    var anio = (javafx.scene.control.TextField) panel.getContent().lookup("#txtAnioCalendario");
                    anio.setText("2000");
                }
                var boton = (Button) contenido.lookup("#btnGenerar");
                boton.fire();
                return boton;
            });
            long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
            while (enJavaFx(generar::isDisabled) && System.nanoTime() < limite) Thread.sleep(50);
            enJavaFx(() -> {
                var raiz = ventana.getScene().getRoot();
                var pdf = (Button) raiz.lookup("#btnPdf");
                var excel = (Button) raiz.lookup("#btnExcel");
                assertFalse(pdf.isDisabled(), tipo.toString());
                assertFalse(excel.isDisabled(), tipo.toString());
                assertFalse(raiz.lookup("#btnExportarTodos").isDisabled());
                CambiosCuidados.notificar();
                assertTrue(pdf.isDisabled());
                assertTrue(excel.isDisabled());
                return null;
            });
        }
    }

    private Pantallas abrirPantallas(Planta p) throws Exception {
        var cargador = new FXMLLoader(getClass().getResource("/view/main-view.fxml"));
        Parent principal = cargador.load();
        var tabs = (TabPane) cargador.getNamespace().get("tabPrincipal");
        assertEquals(5, tabs.getTabs().size());
        var tabla = (TableView<?>) tabs.getTabs().get(3).getContent().lookup("#tblAlertas");
        var tablaPlantas = (TableView<?>) tabs.getTabs().get(2).getContent().lookup("#tblPlantas");
        Stage ventana = new Stage();
        ventana.setOpacity(0);
        ventana.setScene(new Scene(principal));
        ventana.show();
        tabs.getSelectionModel().select(3);
        assertEquals(20, lecturaTabla(tabla, p));
        sensores.actualizar(conLectura(p, 25));
        CambiosCuidados.notificar();
        assertEquals(25, lecturaTabla(tabla, p));

        tabs.getSelectionModel().select(2);
        assertTrue(tablaPlantas.getItems().stream().map(Planta.class::cast)
                .anyMatch(item -> item.getId().equals(p.getId()) && item.getSensor().getHumedadActual() == 25));

        tabs.getSelectionModel().select(3);
        ventana.hide();
        sensores.actualizar(conLectura(p, 29));
        CambiosCuidados.notificar();
        assertEquals(25, lecturaTabla(tabla, p));
        ventana.show();
        assertEquals(29, lecturaTabla(tabla, p));

        Stage calendario = new Stage();
        calendario.setOpacity(0);
        var cuidados = new CuidadosApiRepository(uri -> consultar("GET", uri.getRawPath() + "?" + uri.getRawQuery()).body());
        var clima = new OpenMeteoApiRepository(uri -> { throw new IOException("Sin conexión a Internet"); });
        new CalendarioCuidadosVentana(calendario, ventana, cuidados, clima).mostrar();
        return new Pantallas(ventana, calendario);
    }

    private record Pantallas(Stage principal, Stage calendario) {}

    private static <T> T enJavaFx(Callable<T> operacion) throws Exception {
        var tarea = new FutureTask<>(operacion);
        Platform.runLater(tarea);
        return tarea.get(10, TimeUnit.SECONDS);
    }

    private static void esperarCalendario(Stage ventana, Planta p, String estado, String detalle) throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        do {
            if (enJavaFx(() -> {
                Parent raiz = ventana.getScene().getRoot();
                raiz.applyCss();
                raiz.layout();
                return raiz.lookupAll(".calendario-tarjeta").stream().anyMatch(tarjeta -> {
                    var etiquetas = tarjeta.lookupAll(".label").stream().map(Label.class::cast).toList();
                    return etiquetas.stream().anyMatch(l -> l.getText().contains(p.getNombreVisible()))
                            && etiquetas.stream().anyMatch(l -> estado.equals(l.getAccessibleText()))
                            && etiquetas.stream().anyMatch(l -> l.getText().contains(detalle));
                });
            })) return;
            Thread.sleep(50);
        } while (System.nanoTime() < limite);
        fail("El calendario no mostró " + estado + " con " + detalle);
    }

    private static void pulsarCalendario(Stage calendario, String descripcion) {
        calendario.getScene().getRoot().lookupAll(".button").stream().map(Button.class::cast)
                .filter(b -> descripcion.equals(b.getAccessibleText())).findFirst().orElseThrow().fire();
    }

    private static int lecturaTabla(TableView<?> tabla, Planta p) {
        return tabla.getItems().stream().map(Alerta.class::cast)
                .filter(a -> p.getId().equals(a.getPlanta().getId()))
                .findFirst().orElseThrow().getPlanta().getSensor().getHumedadActual();
    }

    private Planta crearPlanta(int minimo, Integer humedad) {
        var cultivo = new Cultivo();
        cultivo.setNombre("Cultivo " + UUID.randomUUID());
        cultivo.setHumedadMinima(minimo);
        cultivo.setFrecuenciaRiegoDias(3);
        new CultivoDao().guardar(cultivo);

        var p = new Planta();
        p.setApodo("Prueba " + UUID.randomUUID());
        p.setCultivo(cultivo);
        p.setModulo(modulo);
        p.setEstado("Creciendo");
        p.setFechaSiembra(LocalDate.now());
        if (humedad != null) p.setSensor(new Sensor(UUID.randomUUID().toString().substring(0, 25), humedad));
        plantas.guardar(p);
        return p;
    }

    private Sensor conLectura(Planta p, int lectura) {
        Sensor sensor = plantas.buscarPorId(p.getId()).getSensor();
        sensor.setHumedadActual(lectura);
        return sensor;
    }

    private List<Alerta> pendientes(Planta p) {
        return alertas.listarPendientes().stream().filter(a -> a.getPlanta().getId().equals(p.getId())).toList();
    }

    private Alerta alertaHumedad(Planta p) {
        var humedad = pendientes(p).stream().filter(a -> Alerta.HUMEDAD_BAJA.equals(a.getTipo())).toList();
        assertTrue(humedad.size() <= 1);
        return humedad.isEmpty() ? null : humedad.getFirst();
    }

    private static Riego nuevoRiego(Planta p) {
        var riego = new Riego();
        riego.setPlanta(p);
        riego.setModo("Manual");
        riego.setOrigenAgua("Agua de lluvia");
        riego.setCantidadMl(250);
        return riego;
    }

    private static JsonObject cuidado(Planta p) throws Exception {
        var filas = json("/api/cuidados?plantaId=" + p.getId());
        assertEquals(1, filas.size());
        var fila = filas.get(0).getAsJsonObject();
        assertEquals(p.getId(), fila.get("plantaId").getAsLong());
        return fila;
    }

    private static JsonObject filaAlerta(Planta p) throws Exception {
        return json("/api/alertas").asList().stream().map(e -> e.getAsJsonObject())
                .filter(a -> a.get("plantaId").getAsLong() == p.getId()
                        && Alerta.HUMEDAD_BAJA.equals(a.get("tipo").getAsString()))
                .findFirst().orElseThrow();
    }

    private static JsonArray json(String ruta) throws Exception {
        var respuesta = consultar("GET", ruta);
        assertEquals(200, respuesta.statusCode(), respuesta.body());
        assertTrue(respuesta.headers().firstValue("Content-Type").orElseThrow().contains("application/json"));
        return JsonParser.parseString(respuesta.body()).getAsJsonArray();
    }

    private static HttpResponse<String> consultar(String metodo, String ruta) throws IOException, InterruptedException {
        var uri = URI.create("http://127.0.0.1:" + api.getAddress().getPort() + ruta);
        return http.send(HttpRequest.newBuilder(uri).method(metodo, HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static String estadoPersistido() {
        try (var session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createNativeQuery("""
                    SELECT concat(
                        (SELECT jsonb_agg(to_jsonb(s) ORDER BY s.id) FROM sensor s),
                        (SELECT jsonb_agg(to_jsonb(a) ORDER BY a.id) FROM alerta a),
                        (SELECT jsonb_agg(to_jsonb(r) ORDER BY r.id) FROM riego r))
                    """, String.class).getSingleResult();
        }
    }
}

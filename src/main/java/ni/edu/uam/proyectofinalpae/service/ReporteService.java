package ni.edu.uam.proyectofinalpae.service;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimplePdfExporterConfiguration;
import net.sf.jasperreports.export.SimpleXlsxReportConfiguration;
import ni.edu.uam.proyectofinalpae.dao.AlertaDao;
import ni.edu.uam.proyectofinalpae.dao.PlantaDao;
import ni.edu.uam.proyectofinalpae.dao.RiegoDao;
import ni.edu.uam.proyectofinalpae.dto.ActividadCuidado;
import ni.edu.uam.proyectofinalpae.model.Alerta;
import ni.edu.uam.proyectofinalpae.model.Planta;
import ni.edu.uam.proyectofinalpae.model.Riego;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Genera los reportes de PlantBee con JasperReports.
 * Los datos se leen con Hibernate (los mismos DAO de las pantallas) y se le pasan al reporte
 * como una lista de filas; el diseno de cada reporte esta en /reportes/*.jrxml.
 */
public class ReporteService {

    /** Reportes disponibles: titulo para la pantalla, archivo .jrxml y nombre de la hoja de Excel. */
    public enum TipoReporte {
        PLANTAS("Inventario de plantas y sensores", "plantas", "Plantas"),
        RIEGOS("Historial de riego", "riegos", "Riegos"),
        ALERTAS("Alertas", "alertas", "Alertas"),
        CALENDARIO("Calendario de cuidados", "calendario", "Calendario"),
        GENERAL("Reporte general", "general", "General");

        private final String titulo;
        private final String archivo;
        private final String hoja;

        TipoReporte(String titulo, String archivo, String hoja) {
            this.titulo = titulo;
            this.archivo = archivo;
            this.hoja = hoja;
        }

        public String getArchivo() {
            return archivo;
        }

        @Override
        public String toString() {
            return titulo;
        }
    }

    private static final List<TipoReporte> INDIVIDUALES =
            List.of(TipoReporte.PLANTAS, TipoReporte.RIEGOS, TipoReporte.ALERTAS, TipoReporte.CALENDARIO);
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** Compilar un .jrxml es lento: se compila una sola vez y se guarda aqui. */
    private final Map<TipoReporte, JasperReport> compilados = new EnumMap<>(TipoReporte.class);

    private final PlantaDao plantaDao = new PlantaDao();
    private final RiegoDao riegoDao = new RiegoDao();
    private final AlertaDao alertaDao = new AlertaDao();
    private final CalendarioCuidadosService calendario = new CalendarioCuidadosService();

    /**
     * Llena el reporte con los datos actuales de la base.
     *
     * @param paraExcel true = una sola pagina larga (sin cortes), que es lo que necesita el Excel
     */
    public JasperPrint generar(TipoReporte tipo, boolean paraExcel) throws JRException {
        return generar(preparar(tipo), paraExcel);
    }

    public DatosReporte preparar(TipoReporte tipo) {
        return preparar(tipo, Filtros.sinPeriodo(YearMonth.now()), LocalDateTime.now().format(FORMATO_FECHA));
    }

    public List<DatosReporte> prepararSecciones(TipoReporte tipo, YearMonth mes) {
        return prepararConFiltros(tipo, Filtros.sinPeriodo(mes));
    }

    public List<DatosReporte> prepararConFiltros(TipoReporte tipo, Filtros filtros) {
        String fecha = LocalDateTime.now().format(FORMATO_FECHA);
        var tipos = tipo == TipoReporte.GENERAL ? INDIVIDUALES : List.of(tipo);
        return tipos.stream().map(seccion -> preparar(seccion, filtros, fecha)).toList();
    }

    private DatosReporte preparar(TipoReporte tipo, Filtros filtros, String fecha) {
        Map<String, Object> parametros = new HashMap<>();
        parametros.put("FECHA", fecha);
        List<Map<String, ?>> filas = switch (tipo) {
            case PLANTAS -> filasPlantas(filtros);
            case RIEGOS -> {
                var periodo = filtros.historico();
                var riegos = periodo == null ? riegoDao.listar()
                        : riegoDao.listarEntre(periodo.inicio(), periodo.finExclusivo());
                parametros.put("RESUMEN_AGUA", resumenAgua(riegos));
                yield filasRiegos(riegos);
            }
            case ALERTAS -> filasAlertas(filtros);
            case CALENDARIO -> {
                parametros.put("MES", filtros.mesCalendario().toString());
                yield filasCalendario(filtros.mesCalendario());
            }
            case GENERAL -> throw new IllegalArgumentException("El reporte general se prepara por secciones.");
        };
        parametros.put("ALCANCE", switch (tipo) {
            case PLANTAS -> "Inventario actual al generar el documento";
            case RIEGOS -> "Período de riegos: " + descripcionPeriodo(filtros.historico());
            case ALERTAS -> "Período de alertas: " + descripcionPeriodo(filtros.historico())
                    + " · Estado: " + filtros.estadoAlerta()
                    + (filtros.tipoAlerta() == null ? "" : " · Tipo: " + filtros.tipoAlerta());
            case CALENDARIO -> "Mes de cuidados: " + filtros.mesCalendario();
            case GENERAL -> throw new IllegalArgumentException("El reporte general se prepara por secciones.");
        });
        parametros.put("TOTAL_REGISTROS", filas.size());
        return new DatosReporte(tipo, Map.copyOf(parametros), List.copyOf(filas));
    }

    private static String descripcionPeriodo(Periodo periodo) {
        if (periodo == null) return "Todo el historial";
        var formato = DateTimeFormatter.ofPattern("dd/MM/uuuu");
        return periodo.desde().format(formato) + " a " + periodo.hasta().format(formato);
    }

    public JasperPrint generar(DatosReporte datos, boolean paraExcel) throws JRException {
        var parametros = new HashMap<>(datos.parametros());
        if (paraExcel) {
            parametros.put(JRParameter.IS_IGNORE_PAGINATION, Boolean.TRUE);
        }
        try (InputStream imagen = logo()) {
            parametros.put("LOGO", imagen);
            return JasperFillManager.fillReport(compilar(datos.tipo()), parametros, new JRMapCollectionDataSource(datos.filas()));
        } catch (IOException e) {
            throw new JRException("No se pudo cerrar el recurso del logo.", e);
        }
    }

    public List<JasperPrint> generarSecciones(List<DatosReporte> secciones, boolean paraExcel) throws JRException {
        List<JasperPrint> reportes = new ArrayList<>();
        for (DatosReporte datos : secciones) {
            reportes.add(generar(datos, paraExcel));
        }
        return List.copyOf(reportes);
    }

    public enum EstadoAlerta {
        PENDIENTES("Pendientes", false), ATENDIDAS("Atendidas", true), TODAS("Todas", null);

        private final String etiqueta;
        private final Boolean atendida;

        EstadoAlerta(String etiqueta, Boolean atendida) {
            this.etiqueta = etiqueta;
            this.atendida = atendida;
        }

        @Override
        public String toString() {
            return etiqueta;
        }
    }

    public record Periodo(LocalDate desde, LocalDate hasta) {
        public Periodo {
            Objects.requireNonNull(desde);
            Objects.requireNonNull(hasta);
            if (desde.isAfter(hasta) || desde.getYear() < 1 || hasta.getYear() > 9999) {
                throw new IllegalArgumentException("El rango de fechas no es válido.");
            }
        }

        LocalDateTime inicio() {
            return desde.atStartOfDay();
        }

        LocalDateTime finExclusivo() {
            return hasta.plusDays(1).atStartOfDay();
        }
    }

    public record Filtros(Long cultivoId, Long colmenaId, Periodo historico,
                          EstadoAlerta estadoAlerta, String tipoAlerta, YearMonth mesCalendario) {
        public Filtros {
            Objects.requireNonNull(estadoAlerta);
            Objects.requireNonNull(mesCalendario);
        }

        private static Filtros sinPeriodo(YearMonth mes) {
            return new Filtros(null, null, null, EstadoAlerta.PENDIENTES, null, mes);
        }
    }

    public record DatosReporte(TipoReporte tipo, Map<String, Object> parametros, List<Map<String, ?>> filas) {}

    /** Guarda el reporte ya generado como PDF. */
    public void exportarPdf(JasperPrint reporte, File destino) throws JRException {
        JasperExportManager.exportReportToPdfFile(reporte, destino.getAbsolutePath());
    }

    public void exportarPdf(List<JasperPrint> reportes, File destino) throws JRException {
        if (reportes.size() == 1) {
            exportarPdf(reportes.getFirst(), destino);
            return;
        }
        var exportador = new JRPdfExporter();
        var configuracion = new SimplePdfExporterConfiguration();
        configuracion.setCreatingBatchModeBookmarks(true);
        configuracion.setMetadataTitle(TipoReporte.GENERAL.toString());
        exportador.setConfiguration(configuracion);
        exportador.setExporterInput(SimpleExporterInput.getInstance(reportes));
        exportador.setExporterOutput(new SimpleOutputStreamExporterOutput(destino));
        exportador.exportReport();
    }

    /**
     * Guarda el reporte como Excel (.xlsx): una hoja con encabezado verde, filas alternadas,
     * filtros en el encabezado, encabezado fijo al desplazarse y numeros/fechas como celdas reales.
     */
    public void exportarExcel(TipoReporte tipo, File destino) throws JRException {
        exportarExcel(prepararSecciones(tipo, YearMonth.now()), destino);
    }

    public void exportarExcel(DatosReporte datos, File destino) throws JRException {
        exportarExcel(List.of(datos), destino);
    }

    public void exportarExcel(List<DatosReporte> secciones, File destino) throws JRException {
        exportarExcel(secciones, new SimpleOutputStreamExporterOutput(destino));
    }

    private void exportarExcel(List<DatosReporte> secciones, SimpleOutputStreamExporterOutput salida) throws JRException {
        List<JasperPrint> reportes = generarSecciones(secciones, true);
        SimpleXlsxReportConfiguration configuracion = new SimpleXlsxReportConfiguration();
        configuracion.setOnePagePerSheet(secciones.size() > 1);
        configuracion.setSheetNames(secciones.stream().map(datos -> datos.tipo().hoja).toArray(String[]::new));
        configuracion.setDetectCellType(true);
        configuracion.setRemoveEmptySpaceBetweenRows(true);
        configuracion.setRemoveEmptySpaceBetweenColumns(true);
        configuracion.setWhitePageBackground(false);
        configuracion.setCollapseRowSpan(true);

        JRXlsxExporter exportador = new JRXlsxExporter();
        exportador.setExporterInput(SimpleExporterInput.getInstance(reportes));
        exportador.setExporterOutput(salida);
        exportador.setConfiguration(configuracion);
        exportador.exportReport();
    }

    /** Exporta los reportes individuales usando la misma fecha de generación. */
    public void exportarTodos(YearMonth mes, File destino) throws IOException, JRException {
        exportarTodosConFiltros(Filtros.sinPeriodo(mes), destino);
    }

    public void exportarTodosConFiltros(Filtros filtros, File destino) throws IOException, JRException {
        exportarTodos(prepararConFiltros(TipoReporte.GENERAL, filtros), destino);
    }

    public void exportarTodos(List<DatosReporte> secciones, File destino) throws IOException, JRException {
        Path archivo = destino.toPath().toAbsolutePath();
        Path temporal = Files.createTempFile(archivo.getParent(), ".plantbee-reportes-", ".zip");
        try {
            try (var zip = new ZipOutputStream(Files.newOutputStream(temporal))) {
                for (DatosReporte datos : secciones) {
                    String nombre = datos.tipo().getArchivo();
                    agregarAlZip(zip, nombre + ".pdf",
                            salida -> JasperExportManager.exportReportToPdfStream(generar(datos, false), salida));
                    agregarAlZip(zip, nombre + ".xlsx",
                            salida -> exportarExcel(List.of(datos), new SimpleOutputStreamExporterOutput(salida)));
                }
            }
            try {
                Files.move(temporal, archivo, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporal, archivo, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporal);
        }
    }

    private static void agregarAlZip(ZipOutputStream zip, String nombre, Exportacion exportar)
            throws IOException, JRException {
        var contenido = new ByteArrayOutputStream();
        exportar.escribir(contenido);
        zip.putNextEntry(new ZipEntry(nombre));
        contenido.writeTo(zip);
        zip.closeEntry();
    }

    @FunctionalInterface
    private interface Exportacion {
        void escribir(OutputStream salida) throws JRException;
    }

    private synchronized JasperReport compilar(TipoReporte tipo) throws JRException {
        JasperReport reporte = compilados.get(tipo);
        if (reporte == null) {
            String ruta = "/reportes/" + tipo.getArchivo() + ".jrxml";
            try (InputStream plantilla = ReporteService.class.getResourceAsStream(ruta)) {
                if (plantilla == null) throw new JRException("No se encontró la plantilla " + ruta);
                reporte = JasperCompileManager.compileReport(plantilla);
                compilados.put(tipo, reporte);
            } catch (IOException e) {
                throw new JRException("No se pudo cerrar la plantilla " + ruta, e);
            }
        }
        return reporte;
    }

    private InputStream logo() {
        return ReporteService.class.getResourceAsStream("/img/logo-plantbee.png");
    }

    // ----- Filas de cada reporte (cada fila es un mapa: nombre del campo -> valor) -----

    private List<Map<String, ?>> filasPlantas(Filtros filtros) {
        List<Map<String, ?>> filas = new ArrayList<>();
        var plantas = filtros.colmenaId() == null ? plantaDao.listar() : plantaDao.buscarPorColmena(filtros.colmenaId());
        for (Planta p : plantas) {
            if (filtros.cultivoId() != null && !filtros.cultivoId().equals(p.getCultivo().getId())) continue;
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("id", p.getId());
            fila.put("planta", p.getNombreVisible());
            fila.put("cultivo", p.getCultivo().getNombre());
            fila.put("ubicacion", p.getModulo().toString());
            fila.put("estado", p.getEstado());
            fila.put("humedad", p.getSensor() == null ? null : p.getSensor().getHumedadActual());
            fila.put("minima", p.getCultivo().getHumedadMinima());
            fila.put("regar", p.getSensor() == null || p.getSensor().getHumedadActual() == null
                    ? "Sin lectura" : p.getSensor().necesitaRiego() ? "Sí" : "No");
            filas.add(fila);
        }
        return filas;
    }

    private List<Map<String, ?>> filasRiegos(List<Riego> riegos) {
        List<Map<String, ?>> filas = new ArrayList<>();
        for (Riego r : riegos) {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("fecha", Timestamp.valueOf(r.getFechaHora()));
            fila.put("planta", r.getPlanta().getNombreVisible());
            fila.put("ubicacion", r.getPlanta().getModulo().toString());
            fila.put("ml", r.getCantidadMl());
            fila.put("origen", r.getOrigenAgua());
            fila.put("modo", r.getModo());
            filas.add(fila);
        }
        return filas;
    }

    private List<Map<String, ?>> filasAlertas(Filtros filtros) {
        List<Map<String, ?>> filas = new ArrayList<>();
        var periodo = filtros.historico();
        var alertas = alertaDao.listarFiltradas(filtros.estadoAlerta().atendida, filtros.tipoAlerta(),
                periodo == null ? null : periodo.inicio(), periodo == null ? null : periodo.finExclusivo());
        for (Alerta a : alertas) {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("fecha", Timestamp.valueOf(a.getFechaHora()));
            fila.put("planta", a.getPlanta().getNombreVisible());
            fila.put("ubicacion", a.getPlanta().getModulo().toString());
            fila.put("tipo", a.getTipo());
            fila.put("estado", a.isAtendida() ? "Atendida" : "Pendiente");
            fila.put("mensaje", a.isAtendida() ? a.getMensaje() : a.getMensajeVigente());
            filas.add(fila);
        }
        return filas;
    }

    private List<Map<String, ?>> filasCalendario(YearMonth mes) {
        List<Map<String, ?>> filas = new ArrayList<>();
        for (ActividadCuidado actividad : calendario.paraMes(mes)) {
            filas.add(Map.of(
                    "fecha", Date.valueOf(LocalDate.parse(actividad.fecha())),
                    "planta", actividad.planta(),
                    "actividad", actividad.tipo(),
                    "estado", ActividadCuidado.nombreEstado(actividad.estado()),
                    "detalle", actividad.descripcion()));
        }
        return filas;
    }

    /** Usa las mismas filas del reporte para que el total corresponda al detalle. */
    private String resumenAgua(List<Riego> riegos) {
        if (riegos.isEmpty()) return "Sin riegos registrados";
        var porOrigen = new java.util.TreeMap<String, Long>();
        for (Riego riego : riegos) {
            porOrigen.merge(riego.getOrigenAgua(), (long) riego.getCantidadMl(), Long::sum);
        }
        return porOrigen.entrySet().stream()
                .map(fila -> fila.getKey() + ": " + String.format("%.2f L", fila.getValue() / 1000.0))
                .collect(java.util.stream.Collectors.joining("   ·   "));
    }
}

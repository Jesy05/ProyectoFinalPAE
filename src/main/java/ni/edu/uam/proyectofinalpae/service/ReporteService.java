package ni.edu.uam.proyectofinalpae.service;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimpleXlsxReportConfiguration;
import ni.edu.uam.proyectofinalpae.dao.AlertaDao;
import ni.edu.uam.proyectofinalpae.dao.PlantaDao;
import ni.edu.uam.proyectofinalpae.dao.RiegoDao;
import ni.edu.uam.proyectofinalpae.model.Alerta;
import ni.edu.uam.proyectofinalpae.model.Planta;
import ni.edu.uam.proyectofinalpae.model.Riego;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        ALERTAS("Alertas pendientes", "alertas", "Alertas");

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

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** Compilar un .jrxml es lento: se compila una sola vez y se guarda aqui. */
    private final Map<TipoReporte, JasperReport> compilados = new EnumMap<>(TipoReporte.class);

    private final PlantaDao plantaDao = new PlantaDao();
    private final RiegoDao riegoDao = new RiegoDao();
    private final AlertaDao alertaDao = new AlertaDao();

    /**
     * Llena el reporte con los datos actuales de la base.
     *
     * @param paraExcel true = una sola pagina larga (sin cortes), que es lo que necesita el Excel
     */
    public JasperPrint generar(TipoReporte tipo, boolean paraExcel) throws JRException {
        return generar(preparar(tipo), paraExcel);
    }

    public DatosReporte preparar(TipoReporte tipo) {
        String fecha = LocalDateTime.now().format(FORMATO_FECHA);
        Map<String, Object> parametros = new HashMap<>();
        parametros.put("FECHA", fecha);
        List<Map<String, ?>> filas = switch (tipo) {
            case PLANTAS -> filasPlantas();
            case RIEGOS -> {
                var riegos = riegoDao.listar();
                parametros.put("RESUMEN_AGUA", resumenAgua(riegos));
                yield filasRiegos(riegos);
            }
            case ALERTAS -> filasAlertas();
        };
        return new DatosReporte(tipo, Map.copyOf(parametros), List.copyOf(filas));
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

    public record DatosReporte(TipoReporte tipo, Map<String, Object> parametros, List<Map<String, ?>> filas) {}

    /** Guarda el reporte ya generado como PDF. */
    public void exportarPdf(JasperPrint reporte, File destino) throws JRException {
        JasperExportManager.exportReportToPdfFile(reporte, destino.getAbsolutePath());
    }

    /**
     * Guarda el reporte como Excel (.xlsx): una hoja con encabezado verde, filas alternadas,
     * filtros en el encabezado, encabezado fijo al desplazarse y numeros/fechas como celdas reales.
     */
    public void exportarExcel(TipoReporte tipo, File destino) throws JRException {
        exportarExcel(preparar(tipo), destino);
    }

    public void exportarExcel(DatosReporte datos, File destino) throws JRException {
        JasperPrint reporte = generar(datos, true);

        SimpleXlsxReportConfiguration configuracion = new SimpleXlsxReportConfiguration();
        configuracion.setOnePagePerSheet(false);
        configuracion.setSheetNames(new String[]{datos.tipo().hoja});
        configuracion.setDetectCellType(true);
        configuracion.setRemoveEmptySpaceBetweenRows(true);
        configuracion.setRemoveEmptySpaceBetweenColumns(true);
        configuracion.setWhitePageBackground(false);
        configuracion.setCollapseRowSpan(true);

        JRXlsxExporter exportador = new JRXlsxExporter();
        exportador.setExporterInput(new SimpleExporterInput(reporte));
        exportador.setExporterOutput(new SimpleOutputStreamExporterOutput(destino));
        exportador.setConfiguration(configuracion);
        exportador.exportReport();
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

    private List<Map<String, ?>> filasPlantas() {
        List<Map<String, ?>> filas = new ArrayList<>();
        for (Planta p : plantaDao.listar()) {
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

    private List<Map<String, ?>> filasAlertas() {
        List<Map<String, ?>> filas = new ArrayList<>();
        for (Alerta a : alertaDao.listarPendientes()) {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("fecha", Timestamp.valueOf(a.getFechaHora()));
            fila.put("planta", a.getPlanta().getNombreVisible());
            fila.put("ubicacion", a.getPlanta().getModulo().toString());
            fila.put("tipo", a.getTipo());
            fila.put("mensaje", a.getMensajeVigente());
            filas.add(fila);
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

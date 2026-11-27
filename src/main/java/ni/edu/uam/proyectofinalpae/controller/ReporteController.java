package ni.edu.uam.proyectofinalpae.controller;

import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TitledPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperPrintManager;
import ni.edu.uam.proyectofinalpae.dao.AlertaDao;
import ni.edu.uam.proyectofinalpae.dao.ColmenaDao;
import ni.edu.uam.proyectofinalpae.dao.CultivoDao;
import ni.edu.uam.proyectofinalpae.model.Colmena;
import ni.edu.uam.proyectofinalpae.model.Cultivo;
import ni.edu.uam.proyectofinalpae.service.ReporteService;
import ni.edu.uam.proyectofinalpae.service.ReporteService.DatosReporte;
import ni.edu.uam.proyectofinalpae.service.ReporteService.EstadoAlerta;
import ni.edu.uam.proyectofinalpae.service.ReporteService.Filtros;
import ni.edu.uam.proyectofinalpae.service.ReporteService.Periodo;
import ni.edu.uam.proyectofinalpae.service.ReporteService.TipoReporte;
import ni.edu.uam.proyectofinalpae.util.Mensajes;

import java.awt.image.BufferedImage;
import java.io.File;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pestana Reportes: genera un reporte de JasperReports, lo muestra dentro de la app
 * (vista previa pagina por pagina) y lo exporta a PDF o a Excel.
 */
public class ReporteController {

    /** Escala con la que se dibujan las paginas: 1.5 = nitido sin ser pesado. */
    private static final float ZOOM = 1.5f;

    @FXML private ComboBox<TipoReporte> cbReporte;
    @FXML private ComboBox<Month> cbMesCalendario;
    @FXML private TextField txtAnioCalendario;
    @FXML private TitledPane panelFiltros;
    @FXML private VBox paginaReportes;
    @FXML private ScrollPane scrollFiltros;
    @FXML private ComboBox<Cultivo> cbCultivo;
    @FXML private ComboBox<Colmena> cbColmena;
    @FXML private ComboBox<ModoPeriodo> cbPeriodo;
    @FXML private FlowPane filaRango;
    @FXML private VBox grupoInventario;
    @FXML private VBox grupoHistorico;
    @FXML private VBox grupoMes;
    @FXML private VBox grupoAlertas;
    @FXML private Label lblPeriodoHistorico;
    @FXML private Label lblMes;
    @FXML private DatePicker dpDesde;
    @FXML private DatePicker dpHasta;
    @FXML private ComboBox<EstadoAlerta> cbEstadoAlerta;
    @FXML private ComboBox<String> cbTipoAlerta;
    @FXML private Label lblCatalogos;
    @FXML private Button btnGenerar;
    @FXML private Button btnPdf;
    @FXML private Button btnExcel;
    @FXML private Button btnExportarTodos;
    @FXML private Label lblEstado;
    @FXML private ScrollPane scrollVista;
    @FXML private VBox contenedorPaginas;

    private final ReporteService reporteService = new ReporteService();

    /** Reporte que se esta viendo; el PDF se exporta exactamente igual a la vista previa. */
    private List<JasperPrint> reporteActual = List.of();
    private List<DatosReporte> datosActuales = List.of();
    private TipoReporte tipoActual;
    private YearMonth mesActual;
    private long versionReporte;
    private boolean cargandoCatalogos;
    private final Cultivo todosCultivos = new Cultivo();
    private final Colmena todasColmenas = new Colmena();
    private static final String TODOS_TIPOS = "Todos los tipos";

    @FXML
    public void initialize() {
        cbReporte.setItems(FXCollections.observableArrayList(TipoReporte.values()));
        cbReporte.getSelectionModel().selectFirst();
        configurarPeriodo();
        configurarFiltros();
        btnPdf.setDisable(true);
        btnExcel.setDisable(true);
        mostrarMensaje("Elija un reporte y presione Generar reporte para ver la vista previa.");
        CambiosCuidados.observar(contenedorPaginas, this::comprobarVigencia);
    }

    private void configurarPeriodo() {
        YearMonth hoy = YearMonth.now();
        cbMesCalendario.setItems(FXCollections.observableArrayList(Month.values()));
        cbMesCalendario.setConverter(new StringConverter<>() {
            @Override
            public String toString(Month mes) {
                return mes == null ? "" : mes.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es-NI"));
            }

            @Override
            public Month fromString(String texto) {
                return cbMesCalendario.getValue();
            }
        });
        cbMesCalendario.setValue(hoy.getMonth());
        txtAnioCalendario.setTextFormatter(new TextFormatter<>(cambio ->
                cambio.getControlNewText().matches("[0-9]{0,4}") ? cambio : null));
        txtAnioCalendario.setText(Integer.toString(hoy.getYear()));
    }

    private void configurarFiltros() {
        todosCultivos.setNombre("Todos los cultivos");
        todasColmenas.setNombre("Todas las colmenas");
        cbCultivo.getItems().setAll(todosCultivos);
        cbColmena.getItems().setAll(todasColmenas);
        cbTipoAlerta.getItems().setAll(TODOS_TIPOS);
        cbCultivo.setValue(todosCultivos);
        cbColmena.setValue(todasColmenas);
        cbTipoAlerta.setValue(TODOS_TIPOS);
        cbPeriodo.setItems(FXCollections.observableArrayList(ModoPeriodo.values()));
        cbEstadoAlerta.setItems(FXCollections.observableArrayList(EstadoAlerta.values()));
        cbPeriodo.setValue(ModoPeriodo.MES);
        cbEstadoAlerta.setValue(EstadoAlerta.PENDIENTES);
        scrollFiltros.prefHeightProperty().bind(Bindings.createDoubleBinding(() -> {
            double ancho = Math.max(scrollFiltros.getWidth() - 16, 1);
            double altoContenido = ((javafx.scene.layout.Region) scrollFiltros.getContent()).prefHeight(ancho);
            return Math.min(altoContenido + 3, Math.max(90, paginaReportes.getHeight() - 285));
        }, scrollFiltros.widthProperty(), paginaReportes.heightProperty(), cbReporte.valueProperty(),
                grupoInventario.managedProperty(), grupoHistorico.managedProperty(), grupoMes.managedProperty(),
                grupoAlertas.managedProperty(), filaRango.managedProperty()));
        scrollFiltros.minHeightProperty().bind(scrollFiltros.prefHeightProperty());
        lblCatalogos.visibleProperty().bind(lblCatalogos.textProperty().isNotEmpty());
        lblCatalogos.managedProperty().bind(lblCatalogos.visibleProperty());
        filaRango.visibleProperty().bind(cbPeriodo.valueProperty().isEqualTo(ModoPeriodo.RANGO));
        filaRango.managedProperty().bind(filaRango.visibleProperty());
        panelFiltros.expandedProperty().addListener((observable, anterior, abierto) -> {
            if (abierto) cargarCatalogos();
        });
        cbReporte.valueProperty().addListener((obs, anterior, actual) -> actualizarFiltrosVisibles());
        cbPeriodo.valueProperty().addListener((obs, anterior, actual) -> actualizarFiltrosVisibles());
        actualizarFiltrosVisibles();
        for (Observable propiedad : List.<Observable>of(cbReporte.valueProperty(), cbCultivo.valueProperty(), cbColmena.valueProperty(),
                cbPeriodo.valueProperty(), dpDesde.valueProperty(), dpHasta.valueProperty(), cbMesCalendario.valueProperty(),
                txtAnioCalendario.textProperty(), cbEstadoAlerta.valueProperty(), cbTipoAlerta.valueProperty())) {
            propiedad.addListener(observable -> invalidarVista("Genere el reporte para aplicar la selección actual."));
        }
    }

    private void actualizarFiltrosVisibles() {
        TipoReporte tipo = cbReporte.getValue();
        boolean general = tipo == TipoReporte.GENERAL;
        boolean historico = general || tipo == TipoReporte.RIEGOS || tipo == TipoReporte.ALERTAS;
        ModoPeriodo modo = cbPeriodo.getValue();
        boolean mesVisible = incluyeCalendario(tipo) || historico && modo == ModoPeriodo.MES;
        mostrar(grupoInventario, general || tipo == TipoReporte.PLANTAS);
        mostrar(grupoHistorico, historico);
        mostrar(grupoAlertas, general || tipo == TipoReporte.ALERTAS);
        mostrar(grupoMes, mesVisible || historico && modo == ModoPeriodo.ANIO);
        mostrar(cbMesCalendario, mesVisible);
        lblMes.setText(incluyeCalendario(tipo)
                ? (historico && modo == ModoPeriodo.MES ? "Mes del calendario y del historial" : "Mes del calendario")
                : (mesVisible ? "Mes del historial" : "Año del historial"));
        lblPeriodoHistorico.setText(general ? "Período de riegos y alertas"
                : tipo == TipoReporte.RIEGOS ? "Período de riegos" : "Período de alertas");
    }

    private static void mostrar(javafx.scene.Node grupo, boolean visible) {
        grupo.setVisible(visible);
        grupo.setManaged(visible);
    }

    private void cargarCatalogos() {
        if (cargandoCatalogos) return;
        cargandoCatalogos = true;
        Task<Catalogos> tarea = new Task<>() {
            @Override
            protected Catalogos call() {
                return new Catalogos(new CultivoDao().listar(), new ColmenaDao().listar(), new AlertaDao().listarTipos());
            }
        };
        tarea.setOnSucceeded(evento -> {
            cargandoCatalogos = false;
            Catalogos catalogos = tarea.getValue();
            Long cultivo = cbCultivo.getValue() == null ? null : cbCultivo.getValue().getId();
            Long colmena = cbColmena.getValue() == null ? null : cbColmena.getValue().getId();
            String tipo = cbTipoAlerta.getValue();
            cbCultivo.getItems().setAll(todosCultivos);
            cbCultivo.getItems().addAll(catalogos.cultivos());
            cbColmena.getItems().setAll(todasColmenas);
            cbColmena.getItems().addAll(catalogos.colmenas());
            cbTipoAlerta.getItems().setAll(TODOS_TIPOS);
            cbTipoAlerta.getItems().addAll(catalogos.tiposAlerta());
            cbCultivo.setValue(catalogos.cultivos().stream().filter(c -> c.getId().equals(cultivo))
                    .findFirst().orElse(todosCultivos));
            cbColmena.setValue(catalogos.colmenas().stream().filter(c -> c.getId().equals(colmena))
                    .findFirst().orElse(todasColmenas));
            cbTipoAlerta.setValue(catalogos.tiposAlerta().contains(tipo) ? tipo : TODOS_TIPOS);
            lblCatalogos.setText("");
        });
        tarea.setOnFailed(evento -> {
            cargandoCatalogos = false;
            lblCatalogos.setText("No se pudieron cargar los filtros. Cierre y vuelva a abrir el panel para reintentar.");
        });
        iniciar(tarea, "plantbee-filtros-reportes");
    }

    @FXML
    private void restablecerFiltros() {
        cbCultivo.setValue(todosCultivos);
        cbColmena.setValue(todasColmenas);
        cbTipoAlerta.setValue(TODOS_TIPOS);
        cbEstadoAlerta.setValue(EstadoAlerta.PENDIENTES);
        cbPeriodo.setValue(ModoPeriodo.MES);
        dpDesde.setValue(null);
        dpHasta.setValue(null);
        YearMonth hoy = YearMonth.now();
        cbMesCalendario.setValue(hoy.getMonth());
        txtAnioCalendario.setText(Integer.toString(hoy.getYear()));
    }

    private Filtros leerFiltros(TipoReporte tipo) {
        try {
            boolean historico = tipo == TipoReporte.RIEGOS || tipo == TipoReporte.ALERTAS || tipo == TipoReporte.GENERAL;
            ModoPeriodo modo = cbPeriodo.getValue();
            YearMonth mes = incluyeCalendario(tipo) || historico && (modo == ModoPeriodo.MES || modo == ModoPeriodo.ANIO)
                    ? leerMes() : YearMonth.now();
            Periodo periodo = historico ? switch (modo) {
                case MES -> new Periodo(mes.atDay(1), mes.atEndOfMonth());
                case ANIO -> new Periodo(LocalDate.of(mes.getYear(), 1, 1), LocalDate.of(mes.getYear(), 12, 31));
                case RANGO -> leerRango();
                case TODO -> null;
            } : null;
            Cultivo cultivo = cbCultivo.getValue();
            Colmena colmena = cbColmena.getValue();
            return new Filtros(cultivo == null ? null : cultivo.getId(), colmena == null ? null : colmena.getId(),
                    periodo, cbEstadoAlerta.getValue(),
                    TODOS_TIPOS.equals(cbTipoAlerta.getValue()) ? null : cbTipoAlerta.getValue(), mes);
        } catch (DateTimeException | IllegalArgumentException e) {
            Mensajes.advertencia(e.getMessage());
            return null;
        }
    }

    private Periodo leerRango() {
        dpDesde.commitValue();
        dpHasta.commitValue();
        if (dpDesde.getValue() == null || dpHasta.getValue() == null) {
            throw new IllegalArgumentException("Indique las fechas Desde y Hasta.");
        }
        return new Periodo(dpDesde.getValue(), dpHasta.getValue());
    }

    private YearMonth leerMes() {
        String anio = txtAnioCalendario.getText();
        if (!anio.matches("[0-9]{4}") || Integer.parseInt(anio) == 0) {
            throw new IllegalArgumentException("Indique un año de cuatro dígitos entre 0001 y 9999.");
        }
        return YearMonth.of(Integer.parseInt(anio), cbMesCalendario.getValue());
    }

    private static boolean incluyeCalendario(TipoReporte tipo) {
        return tipo == TipoReporte.CALENDARIO || tipo == TipoReporte.GENERAL;
    }

    /** Genera el reporte en segundo plano (no congela la ventana) y muestra sus paginas. */
    @FXML
    private void generar() {
        TipoReporte tipo = cbReporte.getValue();
        if (tipo == null) {
            Mensajes.advertencia("Seleccione un reporte.");
            return;
        }
        Filtros filtros = leerFiltros(tipo);
        if (filtros == null) return;
        YearMonth mes = filtros.mesCalendario();
        long version = CambiosCuidados.version().get();
        ocupado(true, "Generando " + tipo + "...");

        Task<VistaPrevia> tarea = new Task<>() {
            @Override
            protected VistaPrevia call() throws Exception {
                var datos = reporteService.prepararConFiltros(tipo, filtros);
                var reportes = reporteService.generarSecciones(datos, false);
                List<Image> paginas = new ArrayList<>();
                for (JasperPrint reporte : reportes) {
                    for (int i = 0; i < reporte.getPages().size(); i++) {
                        BufferedImage imagen = (BufferedImage) JasperPrintManager.printPageToImage(reporte, i, ZOOM);
                        paginas.add(SwingFXUtils.toFXImage(imagen, null));
                    }
                }
                return new VistaPrevia(datos, reportes, paginas);
            }
        };

        tarea.setOnSucceeded(e -> {
            VistaPrevia vista = tarea.getValue();
            reporteActual = vista.reportes();
            datosActuales = vista.datos();
            tipoActual = tipo;
            mesActual = mes;
            versionReporte = version;
            if (comprobarVigencia()) {
                ocupado(false, lblEstado.getText());
                return;
            }
            mostrarPaginas(vista.paginas());
            panelFiltros.setExpanded(false);
            int total = vista.paginas().size();
            String periodo = incluyeCalendario(tipo) ? " · " + mes : "";
            ocupado(false, tipo + periodo + " · " + total + (total == 1 ? " página" : " páginas"));
        });
        tarea.setOnFailed(e -> {
            ocupado(false, "No se pudo generar el reporte.");
            Mensajes.error("No se pudo generar el reporte.", comoException(tarea.getException()));
        });
        iniciar(tarea, "plantbee-reporte");
    }

    private boolean comprobarVigencia() {
        if (reporteActual.isEmpty() || versionReporte == CambiosCuidados.version().get()) return false;
        invalidarVista("Los datos cambiaron. Genere de nuevo el reporte para actualizarlo.");
        return true;
    }

    private void invalidarVista(String estado) {
        if (reporteActual.isEmpty()) return;
        reporteActual = List.of();
        datosActuales = List.of();
        tipoActual = null;
        mostrarMensaje(estado);
        lblEstado.setText(estado);
        btnPdf.setDisable(true);
        btnExcel.setDisable(true);
    }

    @FXML
    private void exportarPdf() {
        if (reporteActual.isEmpty() || comprobarVigencia()) return;
        File destino = elegirArchivo(nombreReporteActual(), "PDF", ".pdf");
        if (destino == null || reporteActual.isEmpty() || comprobarVigencia()) return;

        var reporte = reporteActual;
        ocupado(true, "Exportando PDF...");
        Task<Void> tarea = new Task<>() {
            @Override
            protected Void call() throws Exception {
                reporteService.exportarPdf(reporte, destino);
                return null;
            }
        };
        tarea.setOnSucceeded(e -> {
            ocupado(false, "PDF guardado en " + destino.getAbsolutePath());
            Mensajes.info("PDF guardado en:\n" + destino.getAbsolutePath());
        });
        tarea.setOnFailed(e -> {
            ocupado(false, "No se pudo exportar el PDF.");
            Mensajes.error("No se pudo exportar el PDF. Revise que el archivo no este abierto.",
                    comoException(tarea.getException()));
        });
        iniciar(tarea, "plantbee-pdf");
    }

    @FXML
    private void exportarExcel() {
        if (datosActuales.isEmpty() || comprobarVigencia()) return;
        File destino = elegirArchivo(nombreReporteActual(), "Excel", ".xlsx");
        if (destino == null || datosActuales.isEmpty() || comprobarVigencia()) return;

        var datos = datosActuales;
        ocupado(true, "Exportando Excel...");
        Task<Void> tarea = new Task<>() {
            @Override
            protected Void call() throws Exception {
                reporteService.exportarExcel(datos, destino);
                return null;
            }
        };
        tarea.setOnSucceeded(e -> {
            ocupado(false, "Excel guardado en " + destino.getAbsolutePath());
            Mensajes.info("Excel guardado en:\n" + destino.getAbsolutePath());
        });
        tarea.setOnFailed(e -> {
            ocupado(false, "No se pudo exportar el Excel.");
            Mensajes.error("No se pudo exportar el Excel. Revise que el archivo no este abierto en Excel.",
                    comoException(tarea.getException()));
        });
        iniciar(tarea, "plantbee-excel");
    }

    @FXML
    private void exportarTodos() {
        Filtros filtros = leerFiltros(TipoReporte.GENERAL);
        if (filtros == null) return;
        File destino = elegirArchivo("reportes-" + filtros.mesCalendario(), "ZIP", ".zip");
        if (destino == null) return;
        List<DatosReporte> secciones = tipoActual == TipoReporte.GENERAL && !comprobarVigencia()
                ? datosActuales : List.of();
        ocupado(true, "Exportando todos los reportes...");
        Task<Void> tarea = new Task<>() {
            @Override
            protected Void call() throws Exception {
                if (secciones.isEmpty()) reporteService.exportarTodosConFiltros(filtros, destino);
                else reporteService.exportarTodos(secciones, destino);
                return null;
            }
        };
        tarea.setOnSucceeded(e -> ocupado(false, "ZIP guardado en " + destino.getAbsolutePath()));
        tarea.setOnFailed(e -> {
            ocupado(false, "No se pudieron exportar los reportes.");
            Mensajes.error("No se pudo generar el ZIP de reportes.", comoException(tarea.getException()));
        });
        iniciar(tarea, "plantbee-reportes-zip");
    }

    // ----- Ayudantes -----

    private void mostrarPaginas(List<Image> paginas) {
        contenedorPaginas.getChildren().clear();
        for (Image pagina : paginas) {
            ImageView vista = new ImageView(pagina);
            vista.setPreserveRatio(true);
            vista.setSmooth(true);
            // La hoja se ajusta al ancho disponible, sin pasar de su tamano real
            vista.fitWidthProperty().bind(scrollVista.widthProperty().subtract(60)
                    .map(ancho -> Math.min(ancho.doubleValue(), pagina.getWidth())));
            StackPane hoja = new StackPane(vista);
            hoja.getStyleClass().add("reporte-hoja");
            contenedorPaginas.getChildren().add(hoja);
        }
        scrollVista.setVvalue(0);
    }

    private void mostrarMensaje(String texto) {
        Label mensaje = new Label(texto);
        mensaje.getStyleClass().add("reporte-vacio");
        contenedorPaginas.getChildren().setAll(mensaje);
    }

    private String nombreReporteActual() {
        return tipoActual.getArchivo() + (incluyeCalendario(tipoActual) ? "-" + mesActual : "");
    }

    private File elegirArchivo(String nombreReporte, String tipoArchivo, String extension) {
        FileChooser selector = new FileChooser();
        selector.setTitle("Guardar reporte como " + tipoArchivo);
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter(tipoArchivo, "*" + extension));
        selector.setInitialFileName("plantbee-" + nombreReporte + "-" + LocalDate.now() + extension);
        File archivo = selector.showSaveDialog(btnGenerar.getScene().getWindow());
        if (archivo != null && !archivo.getName().toLowerCase().endsWith(extension)) {
            archivo = new File(archivo.getParentFile(), archivo.getName() + extension);
        }
        return archivo;
    }

    private void ocupado(boolean ocupado, String estado) {
        btnGenerar.setDisable(ocupado);
        cbReporte.setDisable(ocupado);
        panelFiltros.setDisable(ocupado);
        btnPdf.setDisable(ocupado || reporteActual.isEmpty());
        btnExcel.setDisable(ocupado || datosActuales.isEmpty());
        btnExportarTodos.setDisable(ocupado);
        lblEstado.setText(estado);
    }

    private static void iniciar(Task<?> tarea, String nombreHilo) {
        Thread hilo = new Thread(tarea, nombreHilo);
        hilo.setDaemon(true);
        hilo.start();
    }

    private static Exception comoException(Throwable causa) {
        return causa instanceof Exception e ? e : new RuntimeException(causa);
    }

    private enum ModoPeriodo {
        MES("Mes seleccionado"), ANIO("Año seleccionado"), RANGO("Rango de fechas"), TODO("Todo el historial");

        private final String etiqueta;

        ModoPeriodo(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        @Override
        public String toString() {
            return etiqueta;
        }
    }

    private record Catalogos(List<Cultivo> cultivos, List<Colmena> colmenas, List<String> tiposAlerta) {}

    /** Resultado de generar: el reporte lleno y sus paginas ya dibujadas como imagenes. */
    private record VistaPrevia(List<DatosReporte> datos, List<JasperPrint> reportes, List<Image> paginas) {
    }
}

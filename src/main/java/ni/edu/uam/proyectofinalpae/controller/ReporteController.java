package ni.edu.uam.proyectofinalpae.controller;

import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperPrintManager;
import ni.edu.uam.proyectofinalpae.service.ReporteService;
import ni.edu.uam.proyectofinalpae.service.ReporteService.TipoReporte;
import ni.edu.uam.proyectofinalpae.util.Mensajes;

import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Pestana Reportes: genera un reporte de JasperReports, lo muestra dentro de la app
 * (vista previa pagina por pagina) y lo exporta a PDF o a Excel.
 */
public class ReporteController {

    /** Escala con la que se dibujan las paginas: 1.5 = nitido sin ser pesado. */
    private static final float ZOOM = 1.5f;

    @FXML private ComboBox<TipoReporte> cbReporte;
    @FXML private Button btnGenerar;
    @FXML private Button btnPdf;
    @FXML private Button btnExcel;
    @FXML private Label lblEstado;
    @FXML private ScrollPane scrollVista;
    @FXML private VBox contenedorPaginas;

    private final ReporteService reporteService = new ReporteService();

    /** Reporte que se esta viendo; el PDF se exporta exactamente igual a la vista previa. */
    private JasperPrint reporteActual;
    private TipoReporte tipoActual;

    @FXML
    public void initialize() {
        cbReporte.setItems(FXCollections.observableArrayList(TipoReporte.values()));
        cbReporte.getSelectionModel().selectFirst();
        btnPdf.setDisable(true);
        btnExcel.setDisable(true);
        mostrarMensaje("Elija un reporte y presione Generar reporte para ver la vista previa.");
    }

    /** Genera el reporte en segundo plano (no congela la ventana) y muestra sus paginas. */
    @FXML
    private void generar() {
        TipoReporte tipo = cbReporte.getValue();
        if (tipo == null) {
            Mensajes.advertencia("Seleccione un reporte.");
            return;
        }
        ocupado(true, "Generando " + tipo + "...");

        Task<VistaPrevia> tarea = new Task<>() {
            @Override
            protected VistaPrevia call() throws Exception {
                JasperPrint reporte = reporteService.generar(tipo, false);
                List<Image> paginas = new ArrayList<>();
                for (int i = 0; i < reporte.getPages().size(); i++) {
                    BufferedImage imagen = (BufferedImage) JasperPrintManager.printPageToImage(reporte, i, ZOOM);
                    paginas.add(SwingFXUtils.toFXImage(imagen, null));
                }
                return new VistaPrevia(reporte, paginas);
            }
        };

        tarea.setOnSucceeded(e -> {
            VistaPrevia vista = tarea.getValue();
            reporteActual = vista.reporte();
            tipoActual = tipo;
            mostrarPaginas(vista.paginas());
            int total = vista.paginas().size();
            ocupado(false, tipo + " · " + total + (total == 1 ? " página" : " páginas"));
        });
        tarea.setOnFailed(e -> {
            ocupado(false, "No se pudo generar el reporte.");
            Mensajes.error("No se pudo generar el reporte.", comoException(tarea.getException()));
        });
        iniciar(tarea, "plantbee-reporte");
    }

    @FXML
    private void exportarPdf() {
        if (reporteActual == null) return;
        File destino = elegirArchivo("PDF", "*.pdf", ".pdf");
        if (destino == null) return;

        JasperPrint reporte = reporteActual;
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
        if (tipoActual == null) return;
        File destino = elegirArchivo("Excel", "*.xlsx", ".xlsx");
        if (destino == null) return;

        TipoReporte tipo = tipoActual;
        ocupado(true, "Exportando Excel...");
        Task<Void> tarea = new Task<>() {
            @Override
            protected Void call() throws Exception {
                reporteService.exportarExcel(tipo, destino);
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

    private File elegirArchivo(String tipoArchivo, String patron, String extension) {
        FileChooser selector = new FileChooser();
        selector.setTitle("Guardar reporte como " + tipoArchivo);
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter(tipoArchivo, patron));
        selector.setInitialFileName("plantbee-" + tipoActual.getArchivo() + "-" + LocalDate.now() + extension);
        File archivo = selector.showSaveDialog(btnGenerar.getScene().getWindow());
        if (archivo != null && !archivo.getName().toLowerCase().endsWith(extension)) {
            archivo = new File(archivo.getParentFile(), archivo.getName() + extension);
        }
        return archivo;
    }

    private void ocupado(boolean ocupado, String estado) {
        btnGenerar.setDisable(ocupado);
        cbReporte.setDisable(ocupado);
        btnPdf.setDisable(ocupado || reporteActual == null);
        btnExcel.setDisable(ocupado || tipoActual == null);
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

    /** Resultado de generar: el reporte lleno y sus paginas ya dibujadas como imagenes. */
    private record VistaPrevia(JasperPrint reporte, List<Image> paginas) {
    }
}

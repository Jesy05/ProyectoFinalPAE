package ni.edu.uam.proyectofinalpae.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import ni.edu.uam.proyectofinalpae.api.HttpStatusException;
import ni.edu.uam.proyectofinalpae.dao.ColmenaDao;
import ni.edu.uam.proyectofinalpae.dao.CultivoDao;
import ni.edu.uam.proyectofinalpae.dao.ModuloDao;
import ni.edu.uam.proyectofinalpae.dao.PlantaDao;
import ni.edu.uam.proyectofinalpae.dao.SensorDao;
import ni.edu.uam.proyectofinalpae.dto.WikipediaSummary;
import ni.edu.uam.proyectofinalpae.model.*;
import ni.edu.uam.proyectofinalpae.service.WikipediaService;
import ni.edu.uam.proyectofinalpae.util.Mensajes;

import java.net.http.HttpTimeoutException;
import java.time.LocalDate;
import java.util.Objects;

/** CRUD de plantas con su sensor de humedad (relacion uno a uno). */
public class PlantaController {

    @FXML private TextField txtApodo;
    @FXML private ComboBox<Cultivo> cbCultivo;
    @FXML private ComboBox<Modulo> cbModulo;
    @FXML private DatePicker dpSiembra;
    @FXML private ComboBox<String> cbEstado;
    @FXML private TextField txtCodigoSensor;
    @FXML private TextField txtHumedad;
    @FXML private ComboBox<Colmena> cbFiltroColmena;
    @FXML private TableView<Planta> tblPlantas;
    @FXML private TextField txtBuscarPlantas;
    private ObservableList<Planta> filasBusqueda;
    @FXML private TableColumn<Planta, Long> colId;
    @FXML private TableColumn<Planta, String> colPlanta;
    @FXML private TableColumn<Planta, String> colCultivo;
    @FXML private TableColumn<Planta, String> colUbicacion;
    @FXML private TableColumn<Planta, String> colEstado;
    @FXML private TableColumn<Planta, String> colHumedad;
    @FXML private TableColumn<Planta, LocalDate> colSiembra;
    @FXML private TextField txtConsultaWikipedia;
    @FXML private Label lblWikipediaTitulo;
    @FXML private Label lblWikipediaDescripcion;
    @FXML private Label lblWikipediaExtracto;
    @FXML private Hyperlink lnkWikipedia;
    @FXML private Button btnConsultarWikipedia;

    private final PlantaDao plantaDao = new PlantaDao();
    private final SensorDao sensorDao = new SensorDao();
    private final CultivoDao cultivoDao = new CultivoDao();
    private final ModuloDao moduloDao = new ModuloDao();
    private final ColmenaDao colmenaDao = new ColmenaDao();
    private final WikipediaService wikipediaService = new WikipediaService();
    private Planta seleccionada;
    private boolean actualizandoLecturas;
    private Task<WikipediaSummary> consultaWikipediaActiva;

    @FXML
    public void initialize() {
        filasBusqueda = BusquedaTabla.instalar(tblPlantas, txtBuscarPlantas, p -> BusquedaTabla.campos(
                p.getNombreVisible(), p.getCultivo().getNombre(), p.getModulo().getColmena().getNombre()));
        cbEstado.setItems(FXCollections.observableArrayList(
                "Germinando", "Creciendo", "Lista para cosechar", "Cosechada"));
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colPlanta.setCellValueFactory(new PropertyValueFactory<>("nombreVisible"));
        colCultivo.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCultivo().getNombre()));
        colUbicacion.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getModulo().toString()));
        colEstado.setCellValueFactory(new PropertyValueFactory<>("estado"));
        colSiembra.setCellValueFactory(new PropertyValueFactory<>("fechaSiembra"));
        colHumedad.setCellValueFactory(d -> new SimpleStringProperty(textoHumedad(d.getValue())));
        tblPlantas.getSelectionModel().selectedItemProperty()
                .addListener((obs, anterior, planta) -> {
                    if (!actualizandoLecturas) cargarFormulario(planta);
                });
        txtConsultaWikipedia.textProperty().addListener((obs, anterior, actual) -> limpiarWikipedia());
        nuevo();
        recargar();
        CambiosCuidados.observar(tblPlantas, this::actualizarLecturas);
    }

    private void actualizarLecturas() {
        Colmena filtro = cbFiltroColmena.getValue();
        try {
            Planta anterior = seleccionada;
            var plantas = filtro == null ? plantaDao.listar() : plantaDao.buscarPorColmena(filtro.getId());
            actualizandoLecturas = true;
            try {
                filasBusqueda.setAll(plantas);
                if (anterior != null) {
                    seleccionada = tblPlantas.getItems().stream().filter(p -> p.getId().equals(anterior.getId()))
                            .findFirst().orElse(null);
                    if (seleccionada == null) {
                        nuevo();
                    } else {
                        tblPlantas.getSelectionModel().select(seleccionada);
                        if (txtHumedad.getText().equals(lecturaFormulario(anterior))) {
                            txtHumedad.setText(lecturaFormulario(seleccionada));
                        }
                    }
                }
            } finally {
                actualizandoLecturas = false;
            }
        } catch (Exception e) {
            Mensajes.error("No se pudieron actualizar las lecturas de las plantas.", e);
        }
    }

    @FXML
    private void recargar() {
        try {
            cbCultivo.setItems(FXCollections.observableArrayList(cultivoDao.listar()));
            cbModulo.setItems(FXCollections.observableArrayList(moduloDao.listar()));
            cbFiltroColmena.setItems(FXCollections.observableArrayList(colmenaDao.listar()));
            cbFiltroColmena.setValue(null);
            filasBusqueda.setAll(plantaDao.listar());
        } catch (Exception e) {
            Mensajes.error("No se pudieron cargar las plantas.", e);
        }
    }

    /** Consulta HQL filtrada por colmena. */
    @FXML
    private void filtrar() {
        Colmena colmena = cbFiltroColmena.getValue();
        if (colmena == null) return;
        try {
            filasBusqueda.setAll(plantaDao.buscarPorColmena(colmena.getId()));
        } catch (Exception e) {
            Mensajes.error("No se pudo filtrar.", e);
        }
    }

    @FXML
    private void nuevo() {
        tblPlantas.getSelectionModel().clearSelection();
        seleccionada = null;
        txtApodo.clear();
        cbCultivo.setValue(null);
        cbModulo.setValue(null);
        dpSiembra.setValue(LocalDate.now());
        cbEstado.setValue("Germinando");
        txtCodigoSensor.clear();
        txtHumedad.setText("60");
        txtConsultaWikipedia.clear();
        limpiarWikipedia();
    }

    private void limpiarWikipedia() {
        if (consultaWikipediaActiva != null) {
            consultaWikipediaActiva.cancel();
            consultaWikipediaActiva = null;
        }
        btnConsultarWikipedia.setDisable(false);
        lnkWikipedia.setDisable(true);
        lblWikipediaTitulo.setText("Sin consulta");
        lblWikipediaDescripcion.setText("");
        lblWikipediaExtracto.setText("Seleccione una planta para consultar información.");
        lnkWikipedia.setText("Wikipedia");
        lnkWikipedia.setUserData("");
    }

    @FXML
    private void consultarWikipedia() {
        String consulta = txtConsultaWikipedia.getText() == null
                ? "" : txtConsultaWikipedia.getText().trim();
        if (consulta.isEmpty() && seleccionada != null && seleccionada.getCultivo() != null) {
            String cientifico = seleccionada.getCultivo().getNombreCientifico();
            consulta = cientifico == null || cientifico.isBlank()
                    ? seleccionada.getCultivo().getNombre() : cientifico;
            txtConsultaWikipedia.setText(consulta);
        }
        if (consulta.isEmpty()) {
            Mensajes.advertencia("Escriba un cultivo o seleccione una planta.");
            return;
        }

        limpiarWikipedia();
        String consultaFinal = consulta;
        btnConsultarWikipedia.setDisable(true);
        lblWikipediaTitulo.setText("Consultando...");
        lblWikipediaExtracto.setText("Consultando Wikipedia...");

        Task<WikipediaSummary> task = new Task<>() {
            @Override
            protected WikipediaSummary call() throws Exception {
                return wikipediaService.consultar(consultaFinal);
            }
        };

        consultaWikipediaActiva = task;
        task.setOnSucceeded(event -> {
            if (consultaWikipediaActiva != task) return;
            consultaWikipediaActiva = null;
            WikipediaSummary result = task.getValue();
            lblWikipediaTitulo.setText(result.title());
            lblWikipediaDescripcion.setText(result.description());
            lblWikipediaExtracto.setText(result.extract());
            lnkWikipedia.setText(result.pageUrl().isBlank() ? "Wikipedia" : result.pageUrl());
            lnkWikipedia.setUserData(result.pageUrl());
            lnkWikipedia.setDisable(result.pageUrl().isBlank());
            btnConsultarWikipedia.setDisable(false);
        });

        task.setOnFailed(event -> {
            if (consultaWikipediaActiva != task) return;
            consultaWikipediaActiva = null;
            btnConsultarWikipedia.setDisable(false);
            Throwable cause = task.getException();
            String recomendacion = "Tal vez el nombre de la planta no está bien escrito. "
                    + "Revise la ortografía o pruebe con su nombre científico "
                    + "(por ejemplo, Albahaca u Ocimum basilicum).";

            // Wikipedia responde 404 cuando no existe una pagina con ese nombre
            if (cause instanceof HttpStatusException http && http.statusCode() == 404) {
                lblWikipediaTitulo.setText("Cultivo no encontrado");
                lblWikipediaExtracto.setText("No encontramos \"" + consultaFinal + "\" en Wikipedia. "
                        + recomendacion);
                Mensajes.advertencia("No encontramos \"" + consultaFinal + "\" en Wikipedia.\n\n"
                        + recomendacion);
                return;
            }

            lblWikipediaTitulo.setText("Consulta no disponible");
            String mensaje = switch (cause) {
                case HttpTimeoutException error -> "Wikipedia no respondió a tiempo. Intente de nuevo.";
                case HttpStatusException error when error.statusCode() == 429 ->
                        "Wikipedia alcanzó el límite de consultas. Intente más tarde.";
                case HttpStatusException error -> "Wikipedia devolvió HTTP " + error.statusCode() + ".";
                case null, default -> "No se obtuvo un resumen válido. Revise la conexión e intente de nuevo.";
            };
            lblWikipediaExtracto.setText(mensaje);
        });

        Thread worker = new Thread(task, "plantbee-wikipedia-query");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void abrirWikipedia() {
        Object value = lnkWikipedia.getUserData();
        if (!(value instanceof String url) || url.isBlank()) {
            return;
        }
        try {
            if (!java.awt.Desktop.isDesktopSupported()
                    || !java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                throw new IllegalStateException("El sistema no permite abrir el navegador.");
            }
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
        } catch (Exception ex) {
            Mensajes.error("No se pudo abrir el enlace de Wikipedia.", ex);
        }
    }

    @FXML
    private void guardar() {
        try {
            String apodo = txtApodo.getText().trim();
            Cultivo cultivo = cbCultivo.getValue();
            Modulo modulo = cbModulo.getValue();
            LocalDate siembra = dpSiembra.getValue();
            String codigo = txtCodigoSensor.getText().trim().toUpperCase();

            // Validaciones basicas
            if (cultivo == null) throw new IllegalArgumentException("Seleccione el cultivo.");
            if (modulo == null) throw new IllegalArgumentException("Seleccione el modulo donde esta la planta.");
            if (siembra == null || siembra.isAfter(LocalDate.now()))
                throw new IllegalArgumentException("La fecha de siembra no puede estar vacia ni ser futura.");
            if (codigo.isEmpty()) throw new IllegalArgumentException("Escriba el codigo del sensor, ej. SH-001.");
            int humedad = leerPorcentaje(txtHumedad.getText());

            Long idActual = seleccionada == null ? null : seleccionada.getId();
            if (plantaDao.contarEnModulo(modulo.getId(), idActual) >= modulo.getCapacidad())
                throw new IllegalArgumentException("Ese modulo ya esta lleno (" + modulo.getCapacidad() + " macetas).");
            Long sensorActual = (seleccionada == null || seleccionada.getSensor() == null)
                    ? null : seleccionada.getSensor().getId();
            if (sensorDao.codigoEnUso(codigo, sensorActual))
                throw new IllegalArgumentException("Ya existe otro sensor con el codigo " + codigo + ".");

            Planta planta = seleccionada == null ? new Planta() : seleccionada;
            planta.setApodo(apodo.isEmpty() ? null : apodo);
            planta.setCultivo(cultivo);
            planta.setModulo(modulo);
            planta.setFechaSiembra(siembra);
            planta.setEstado(cbEstado.getValue());
            if (planta.getSensor() == null) {
                planta.setSensor(new Sensor(codigo, humedad));   // se guarda en cascada
            } else {
                planta.getSensor().setCodigo(codigo);
                planta.getSensor().setHumedadActual(humedad);
            }

            if (planta.getId() == null) {
                plantaDao.guardar(planta);
            } else {
                plantaDao.actualizar(planta);
            }
            nuevo();
            CambiosCuidados.notificar();
            Mensajes.info(idActual == null ? "Planta registrada con su sensor " + codigo + "."
                    : "Planta actualizada.");
        } catch (IllegalArgumentException e) {
            Mensajes.advertencia(e.getMessage());
        } catch (Exception e) {
            Mensajes.error("No se pudo guardar la planta.", e);
            recargar();
        }
    }

    @FXML
    private void eliminar() {
        if (seleccionada == null) {
            Mensajes.advertencia("Seleccione una planta de la tabla.");
            return;
        }
        if (!Mensajes.confirmar("Eliminar " + seleccionada.getNombreVisible() + " y su sensor?")) return;
        try {
            plantaDao.eliminar(seleccionada.getId());
            nuevo();
            CambiosCuidados.notificar();
        } catch (Exception e) {
            Mensajes.error("No se pudo eliminar. Si la planta tiene riegos o alertas, eliminelos primero.", e);
        }
    }

    @FXML
    private void simularSecado(ActionEvent evento) {
        CambiosCuidados.ejecutar((Button) evento.getSource(), sensorDao::simularSecado,
                afectados -> Mensajes.info("Se simularon nuevas lecturas en " + afectados
                        + " sensores (-15 puntos de humedad)."),
                "No se pudo simular la lectura de sensores.");
    }

    private int leerPorcentaje(String texto) {
        try {
            int valor = Integer.parseInt(texto.trim());
            if (valor < 0 || valor > 100) throw new NumberFormatException();
            return valor;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("La humedad debe ser un numero entero entre 0 y 100.");
        }
    }

    private static String lecturaFormulario(Planta planta) {
        return planta.getSensor() == null ? "" : Objects.toString(planta.getSensor().getHumedadActual(), "");
    }

    private String textoHumedad(Planta p) {
        if (p.getSensor() == null) return "Sin sensor";
        if (p.getSensor().getHumedadActual() == null) return "Lectura no disponible";
        String texto = p.getSensor().getHumedadActual() + " % (min " + p.getCultivo().getHumedadMinima() + ")";
        return p.getSensor().necesitaRiego() ? texto + "  REGAR" : texto;
    }

    private void cargarFormulario(Planta planta) {
        limpiarWikipedia();
        seleccionada = planta;
        if (planta == null) {
            txtConsultaWikipedia.clear();
            return;
        }
        if (planta.getCultivo() != null) {
            String cientifico = planta.getCultivo().getNombreCientifico();
            txtConsultaWikipedia.setText(cientifico == null || cientifico.isBlank()
                    ? planta.getCultivo().getNombre() : cientifico);
        }
        txtApodo.setText(planta.getApodo() == null ? "" : planta.getApodo());
        cbCultivo.getItems().stream().filter(c -> c.getId().equals(planta.getCultivo().getId()))
                .findFirst().ifPresent(cbCultivo::setValue);
        cbModulo.getItems().stream().filter(m -> m.getId().equals(planta.getModulo().getId()))
                .findFirst().ifPresent(cbModulo::setValue);
        dpSiembra.setValue(planta.getFechaSiembra());
        cbEstado.setValue(planta.getEstado());
        if (planta.getSensor() != null) {
            txtCodigoSensor.setText(planta.getSensor().getCodigo());
        } else {
            txtCodigoSensor.clear();
        }
        txtHumedad.setText(lecturaFormulario(planta));
    }
}

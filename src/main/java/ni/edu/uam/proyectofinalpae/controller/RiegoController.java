package ni.edu.uam.proyectofinalpae.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import ni.edu.uam.proyectofinalpae.dao.AlertaDao;
import ni.edu.uam.proyectofinalpae.dao.PlantaDao;
import ni.edu.uam.proyectofinalpae.dao.RiegoDao;
import ni.edu.uam.proyectofinalpae.model.Alerta;
import ni.edu.uam.proyectofinalpae.model.Planta;
import ni.edu.uam.proyectofinalpae.model.Riego;
import ni.edu.uam.proyectofinalpae.util.Mensajes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Registro del historial de riego y gestion de alertas generadas por los sensores. */
public class RiegoController {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML private ComboBox<Planta> cbPlanta;
    @FXML private TextField txtCantidad;
    @FXML private ComboBox<String> cbOrigen;
    @FXML private ComboBox<String> cbModo;
    @FXML private Label lblAgua;
    @FXML private TableView<Riego> tblRiegos;
    @FXML private TextField txtBuscarRiegos;
    private ObservableList<Riego> filasRiegos;
    @FXML private TableColumn<Riego, String> colFecha;
    @FXML private TableColumn<Riego, String> colPlanta;
    @FXML private TableColumn<Riego, Integer> colCantidad;
    @FXML private TableColumn<Riego, String> colOrigen;
    @FXML private TableColumn<Riego, String> colModo;
    @FXML private TableView<Alerta> tblAlertas;
    @FXML private TextField txtBuscarAlertas;
    @FXML private ComboBox<EstadoAlerta> cbEstadoBusquedaAlerta;
    @FXML private TableColumn<Alerta, String> colAEstado;
    private ObservableList<Alerta> filasAlertas;
    @FXML private TableColumn<Alerta, String> colAFecha;
    @FXML private TableColumn<Alerta, String> colAPlanta;
    @FXML private TableColumn<Alerta, String> colATipo;
    @FXML private TableColumn<Alerta, String> colAMensaje;

    private final RiegoDao riegoDao = new RiegoDao();
    private final AlertaDao alertaDao = new AlertaDao();
    private final PlantaDao plantaDao = new PlantaDao();
    private CalendarioCuidadosVentana calendario;

    @FXML
    public void initialize() {
        filasRiegos = BusquedaTabla.instalar(tblRiegos, txtBuscarRiegos, r -> BusquedaTabla.campos(
                r.getPlanta().getNombreVisible(), r.getFechaHora().format(FORMATO),
                r.getFechaHora().toLocalDate().toString(), r.getOrigenAgua()));
        filasAlertas = BusquedaTabla.instalar(tblAlertas, txtBuscarAlertas, a -> BusquedaTabla.campos(
                a.getPlanta().getNombreVisible(), a.getTipo(), textoEstado(a)));
        cbEstadoBusquedaAlerta.setItems(FXCollections.observableArrayList(EstadoAlerta.values()));
        cbEstadoBusquedaAlerta.setValue(EstadoAlerta.PENDIENTES);
        cbEstadoBusquedaAlerta.setOnAction(evento -> {
            try {
                recargarAlertas();
            } catch (RuntimeException e) {
                Mensajes.error("No se pudieron consultar las alertas.", e);
            }
        });
        cbOrigen.setItems(FXCollections.observableArrayList(
                "Agua de lluvia", "Agua gris filtrada (biofiltro)", "Agua potable"));
        cbModo.setItems(FXCollections.observableArrayList("Automatico", "Manual"));
        colFecha.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getFechaHora().format(FORMATO)));
        colPlanta.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPlanta().toString()));
        colCantidad.setCellValueFactory(new PropertyValueFactory<>("cantidadMl"));
        colOrigen.setCellValueFactory(new PropertyValueFactory<>("origenAgua"));
        colModo.setCellValueFactory(new PropertyValueFactory<>("modo"));
        colAFecha.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getFechaHora().format(FORMATO)));
        colAPlanta.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPlanta().toString()));
        colATipo.setCellValueFactory(new PropertyValueFactory<>("tipo"));
        colAMensaje.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().isAtendida()
                ? d.getValue().getMensaje() : d.getValue().getMensajeVigente()));
        colAEstado.setCellValueFactory(d -> new SimpleStringProperty(textoEstado(d.getValue())));
        limpiar();
        CambiosCuidados.observar(tblAlertas, this::recargar);
    }

    @FXML
    private void verCalendario() {
        if (calendario == null) {
            calendario = new CalendarioCuidadosVentana(cbModo.getScene().getWindow());
        }
        calendario.mostrar();
    }

    @FXML
    private void recargar() {
        try {
            Planta seleccionada = cbPlanta.getValue();
            cbPlanta.setItems(FXCollections.observableArrayList(plantaDao.listar()));
            if (seleccionada != null) {
                cbPlanta.getItems().stream().filter(p -> p.getId().equals(seleccionada.getId()))
                        .findFirst().ifPresent(cbPlanta::setValue);
            }
            filasRiegos.setAll(riegoDao.listar());
            recargarAlertas();
            actualizarResumenAgua();
        } catch (Exception e) {
            Mensajes.error("No se pudieron cargar riegos y alertas.", e);
        }
    }

    private void recargarAlertas() {
        EstadoAlerta estado = cbEstadoBusquedaAlerta.getValue();
        filasAlertas.setAll(alertaDao.listarFiltradas(estado.atendida, null, null, null));
    }

    private static String textoEstado(Alerta alerta) {
        return alerta.isAtendida() ? "Atendida" : "Pendiente";
    }

    private enum EstadoAlerta {
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

    @FXML
    private void registrarRiego(ActionEvent evento) {
        try {
            Planta planta = cbPlanta.getValue();
            if (planta == null) throw new IllegalArgumentException("Seleccione la planta que se rego.");
            if (cbOrigen.getValue() == null) throw new IllegalArgumentException("Seleccione el origen del agua.");
            int cantidad;
            try {
                cantidad = Integer.parseInt(txtCantidad.getText().trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("La cantidad debe ser un numero entero de mililitros.");
            }
            if (cantidad <= 0 || cantidad > 5000)
                throw new IllegalArgumentException("La cantidad debe estar entre 1 y 5000 ml.");

            Riego riego = new Riego();
            riego.setPlanta(planta);
            riego.setCantidadMl(cantidad);
            riego.setOrigenAgua(cbOrigen.getValue());
            riego.setModo(cbModo.getValue());
            riego.setFechaHora(LocalDateTime.now());
            CambiosCuidados.ejecutar((Button) evento.getSource(), () -> {
                riegoDao.registrar(riego);
                return planta.getNombreVisible();
            }, nombre -> {
                limpiar();
                Mensajes.info("Riego registrado para " + nombre + ".");
            }, "No se pudo registrar el riego.");
        } catch (IllegalArgumentException e) {
            Mensajes.advertencia(e.getMessage());
        } catch (Exception e) {
            Mensajes.error("No se pudo registrar el riego.", e);
        }
    }

    @FXML
    private void eliminarRiego() {
        Riego riego = tblRiegos.getSelectionModel().getSelectedItem();
        if (riego == null) {
            Mensajes.advertencia("Seleccione un riego del historial.");
            return;
        }
        if (!Mensajes.confirmar("Eliminar este registro de riego?")) return;
        try {
            riegoDao.eliminar(riego.getId());
            CambiosCuidados.notificar();
        } catch (Exception e) {
            Mensajes.error("No se pudo eliminar el riego.", e);
        }
    }

    /** Concilia las alertas con las lecturas actuales. */
    @FXML
    private void revisarSensores(ActionEvent evento) {
        CambiosCuidados.ejecutar((Button) evento.getSource(), alertaDao::generarAlertasHumedad,
                nuevas -> Mensajes.info(nuevas == 0
                        ? "Revisión completada. No se generaron alertas nuevas."
                        : "Se generaron " + nuevas + " alertas nuevas de humedad baja."),
                "No se pudieron revisar los sensores.");
    }

    @FXML
    private void marcarAtendida() {
        Alerta alerta = tblAlertas.getSelectionModel().getSelectedItem();
        if (alerta == null) {
            Mensajes.advertencia("Seleccione una alerta.");
            return;
        }
        try {
            alertaDao.marcarAtendida(alerta.getId());
            CambiosCuidados.notificar();
        } catch (Exception e) {
            Mensajes.error("No se pudo actualizar la alerta.", e);
        }
    }

    @FXML
    private void eliminarAlerta() {
        Alerta alerta = tblAlertas.getSelectionModel().getSelectedItem();
        if (alerta == null) {
            Mensajes.advertencia("Seleccione una alerta.");
            return;
        }
        if (!Mensajes.confirmar("Eliminar esta alerta?")) return;
        try {
            alertaDao.eliminar(alerta.getId());
            CambiosCuidados.notificar();
        } catch (Exception e) {
            Mensajes.error("No se pudo eliminar la alerta.", e);
        }
    }

    private void actualizarResumenAgua() {
        StringBuilder texto = new StringBuilder("Agua usada por origen: ");
        var filas = riegoDao.litrosPorOrigen();
        if (filas.isEmpty()) texto.append("sin riegos todavia");
        for (Object[] fila : filas) {
            texto.append(fila[0]).append(" = ")
                    .append(String.format("%.2f L", ((Number) fila[1]).doubleValue())).append("   ");
        }
        lblAgua.setText(texto.toString());
    }

    private void limpiar() {
        cbPlanta.setValue(null);
        txtCantidad.setText("250");
        cbOrigen.setValue("Agua de lluvia");
        cbModo.setValue("Manual");
    }
}

package ni.edu.uam.proyectofinalpae.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
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
    @FXML private TableColumn<Riego, String> colFecha;
    @FXML private TableColumn<Riego, String> colPlanta;
    @FXML private TableColumn<Riego, Integer> colCantidad;
    @FXML private TableColumn<Riego, String> colOrigen;
    @FXML private TableColumn<Riego, String> colModo;
    @FXML private TableView<Alerta> tblAlertas;
    @FXML private TableColumn<Alerta, String> colAFecha;
    @FXML private TableColumn<Alerta, String> colAPlanta;
    @FXML private TableColumn<Alerta, String> colATipo;
    @FXML private TableColumn<Alerta, String> colAMensaje;

    private final RiegoDao riegoDao = new RiegoDao();
    private final AlertaDao alertaDao = new AlertaDao();
    private final PlantaDao plantaDao = new PlantaDao();

    @FXML
    public void initialize() {
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
        colAMensaje.setCellValueFactory(new PropertyValueFactory<>("mensaje"));
        limpiar();
        recargar();
    }

    @FXML
    private void recargar() {
        try {
            cbPlanta.setItems(FXCollections.observableArrayList(plantaDao.listar()));
            tblRiegos.setItems(FXCollections.observableArrayList(riegoDao.listar()));
            tblAlertas.setItems(FXCollections.observableArrayList(alertaDao.listarPendientes()));
            actualizarResumenAgua();
        } catch (Exception e) {
            Mensajes.error("No se pudieron cargar riegos y alertas.", e);
        }
    }

    @FXML
    private void registrarRiego() {
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
            riegoDao.registrar(riego);

            Mensajes.info("Riego registrado. La humedad de " + planta.getNombreVisible()
                    + " se restablecio y sus alertas quedaron atendidas.");
            limpiar();
            recargar();
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
            recargar();
        } catch (Exception e) {
            Mensajes.error("No se pudo eliminar el riego.", e);
        }
    }

    /** Revisa los sensores (HQL) y crea alertas para las plantas con humedad baja. */
    @FXML
    private void revisarSensores() {
        try {
            int nuevas = alertaDao.generarAlertasHumedad();
            recargar();
            Mensajes.info(nuevas == 0
                    ? "Todas las plantas tienen buena humedad (o ya tienen alerta pendiente)."
                    : "Se generaron " + nuevas + " alertas nuevas de humedad baja.");
        } catch (Exception e) {
            Mensajes.error("No se pudieron revisar los sensores.", e);
        }
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
            recargar();
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
            recargar();
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

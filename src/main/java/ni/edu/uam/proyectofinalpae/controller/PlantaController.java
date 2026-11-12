package ni.edu.uam.proyectofinalpae.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import ni.edu.uam.proyectofinalpae.dao.ColmenaDao;
import ni.edu.uam.proyectofinalpae.dao.CultivoDao;
import ni.edu.uam.proyectofinalpae.dao.ModuloDao;
import ni.edu.uam.proyectofinalpae.dao.PlantaDao;
import ni.edu.uam.proyectofinalpae.dao.SensorDao;
import ni.edu.uam.proyectofinalpae.model.*;
import ni.edu.uam.proyectofinalpae.util.Mensajes;

import java.time.LocalDate;

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
    @FXML private TableColumn<Planta, Long> colId;
    @FXML private TableColumn<Planta, String> colPlanta;
    @FXML private TableColumn<Planta, String> colCultivo;
    @FXML private TableColumn<Planta, String> colUbicacion;
    @FXML private TableColumn<Planta, String> colEstado;
    @FXML private TableColumn<Planta, String> colHumedad;
    @FXML private TableColumn<Planta, LocalDate> colSiembra;

    private final PlantaDao plantaDao = new PlantaDao();
    private final SensorDao sensorDao = new SensorDao();
    private final CultivoDao cultivoDao = new CultivoDao();
    private final ModuloDao moduloDao = new ModuloDao();
    private final ColmenaDao colmenaDao = new ColmenaDao();
    private Planta seleccionada;

    @FXML
    public void initialize() {
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
                .addListener((obs, anterior, planta) -> cargarFormulario(planta));
        nuevo();
        recargar();
    }

    @FXML
    private void recargar() {
        try {
            cbCultivo.setItems(FXCollections.observableArrayList(cultivoDao.listar()));
            cbModulo.setItems(FXCollections.observableArrayList(moduloDao.listar()));
            cbFiltroColmena.setItems(FXCollections.observableArrayList(colmenaDao.listar()));
            cbFiltroColmena.setValue(null);
            tblPlantas.setItems(FXCollections.observableArrayList(plantaDao.listar()));
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
            tblPlantas.setItems(FXCollections.observableArrayList(plantaDao.buscarPorColmena(colmena.getId())));
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
                Mensajes.info("Planta registrada con su sensor " + codigo + ".");
            } else {
                plantaDao.actualizar(planta);
                Mensajes.info("Planta actualizada.");
            }
            nuevo();
            recargar();
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
            recargar();
        } catch (Exception e) {
            Mensajes.error("No se pudo eliminar. Si la planta tiene riegos o alertas, eliminelos primero.", e);
        }
    }

    /** Demo: baja la humedad de todos los sensores para poder generar alertas. */
    @FXML
    private void simularSecado() {
        try {
            int afectados = sensorDao.simularSecado();
            recargar();
            Mensajes.info("Se simularon nuevas lecturas en " + afectados + " sensores (-15 % de humedad).");
        } catch (Exception e) {
            Mensajes.error("No se pudo simular la lectura de sensores.", e);
        }
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

    private String textoHumedad(Planta p) {
        if (p.getSensor() == null) return "Sin sensor";
        String texto = p.getSensor().getHumedadActual() + " % (min " + p.getCultivo().getHumedadMinima() + ")";
        return p.getSensor().necesitaRiego() ? texto + "  REGAR" : texto;
    }

    private void cargarFormulario(Planta planta) {
        seleccionada = planta;
        if (planta == null) return;
        txtApodo.setText(planta.getApodo() == null ? "" : planta.getApodo());
        cbCultivo.getItems().stream().filter(c -> c.getId().equals(planta.getCultivo().getId()))
                .findFirst().ifPresent(cbCultivo::setValue);
        cbModulo.getItems().stream().filter(m -> m.getId().equals(planta.getModulo().getId()))
                .findFirst().ifPresent(cbModulo::setValue);
        dpSiembra.setValue(planta.getFechaSiembra());
        cbEstado.setValue(planta.getEstado());
        if (planta.getSensor() != null) {
            txtCodigoSensor.setText(planta.getSensor().getCodigo());
            txtHumedad.setText(String.valueOf(planta.getSensor().getHumedadActual()));
        }
    }
}

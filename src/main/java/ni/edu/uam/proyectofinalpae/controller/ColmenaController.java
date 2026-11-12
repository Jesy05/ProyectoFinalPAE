package ni.edu.uam.proyectofinalpae.controller;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import ni.edu.uam.proyectofinalpae.dao.ColmenaDao;
import ni.edu.uam.proyectofinalpae.dao.UsuarioDao;
import ni.edu.uam.proyectofinalpae.model.Colmena;
import ni.edu.uam.proyectofinalpae.model.Modulo;
import ni.edu.uam.proyectofinalpae.model.Usuario;
import ni.edu.uam.proyectofinalpae.util.Mensajes;

import java.time.LocalDate;
public class ColmenaController {

    @FXML private TextField txtNombre;
    @FXML private TextField txtUbicacion;
    @FXML private DatePicker dpFecha;
    @FXML private ComboBox<Usuario> cbUsuario;
    @FXML private Spinner<Integer> spNiveles;
    @FXML private Spinner<Integer> spCapacidad;
    @FXML private ComboBox<Usuario> cbFiltroUsuario;
    @FXML private TableView<Colmena> tblColmenas;
    @FXML private TableColumn<Colmena, Long> colId;
    @FXML private TableColumn<Colmena, String> colNombre;
    @FXML private TableColumn<Colmena, String> colUbicacion;
    @FXML private TableColumn<Colmena, String> colUsuario;
    @FXML private TableColumn<Colmena, Integer> colNiveles;
    @FXML private TableColumn<Colmena, LocalDate> colFecha;

    private final ColmenaDao colmenaDao = new ColmenaDao();
    private final UsuarioDao usuarioDao = new UsuarioDao();
    private Colmena seleccionado;

    @FXML
    public void initialize() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colNombre.setCellValueFactory(new PropertyValueFactory<>("nombre"));
        colUbicacion.setCellValueFactory(new PropertyValueFactory<>("ubicacion"));
        colFecha.setCellValueFactory(new PropertyValueFactory<>("fechaInstalacion"));
        colUsuario.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getUsuario().getNombre()));
        colNiveles.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getModulos().size()).asObject());
        tblColmenas.getSelectionModel().selectedItemProperty()
                .addListener((obs, anterior, colmena) -> cargarFormulario(colmena));
        dpFecha.setValue(LocalDate.now());
        recargar();
    }

    /** Vuelve a leer usuarios y colmenas de la base de datos. */
    @FXML
    private void recargar() {
        try {
            var usuarios = FXCollections.observableArrayList(usuarioDao.listar());
            cbUsuario.setItems(usuarios);
            cbFiltroUsuario.setItems(usuarios);
            cbFiltroUsuario.setValue(null);
            tblColmenas.setItems(FXCollections.observableArrayList(colmenaDao.listar()));
        } catch (Exception e) {
            Mensajes.error("No se pudieron cargar las colmenas.", e);
        }
    }

    /** Consulta HQL filtrada por el usuario elegido. */
    @FXML
    private void filtrar() {
        Usuario usuario = cbFiltroUsuario.getValue();
        if (usuario == null) return;
        try {
            tblColmenas.setItems(FXCollections.observableArrayList(colmenaDao.buscarPorUsuario(usuario.getId())));
        } catch (Exception e) {
            Mensajes.error("No se pudo filtrar.", e);
        }
    }

    @FXML
    private void nuevo() {
        tblColmenas.getSelectionModel().clearSelection();
        seleccionado = null;
        txtNombre.clear();
        txtUbicacion.clear();
        dpFecha.setValue(LocalDate.now());
        cbUsuario.setValue(null);
        spNiveles.setDisable(false);
        spCapacidad.setDisable(false);
        txtNombre.requestFocus();
    }

    @FXML
    private void guardar() {
        try {
            String nombre = txtNombre.getText().trim();
            String ubicacion = txtUbicacion.getText().trim();
            LocalDate fecha = dpFecha.getValue();
            Usuario usuario = cbUsuario.getValue();

            if (nombre.isEmpty()) throw new IllegalArgumentException("El nombre de la colmena es obligatorio.");
            if (usuario == null) throw new IllegalArgumentException("Seleccione el usuario dueno de la colmena.");
            if (fecha == null || fecha.isAfter(LocalDate.now()))
                throw new IllegalArgumentException("La fecha de instalacion no puede estar vacia ni ser futura.");

            if (seleccionado == null) {
                Colmena colmena = new Colmena();
                llenar(colmena, nombre, ubicacion, fecha, usuario);
                // Se crean los niveles; se guardan solos gracias a cascade = ALL
                for (int nivel = 1; nivel <= spNiveles.getValue(); nivel++) {
                    colmena.agregarModulo(new Modulo(nivel, spCapacidad.getValue()));
                }
                colmenaDao.guardar(colmena);
                Mensajes.info("Colmena registrada con " + colmena.getModulos().size() + " modulos.");
            } else {
                llenar(seleccionado, nombre, ubicacion, fecha, usuario);
                colmenaDao.actualizar(seleccionado);
                Mensajes.info("Colmena actualizada.");
            }
            nuevo();
            recargar();
        } catch (IllegalArgumentException e) {
            Mensajes.advertencia(e.getMessage());
        } catch (Exception e) {
            Mensajes.error("No se pudo guardar la colmena.", e);
            recargar();
        }
    }

    @FXML
    private void eliminar() {
        if (seleccionado == null) {
            Mensajes.advertencia("Seleccione una colmena de la tabla.");
            return;
        }
        if (!Mensajes.confirmar("Eliminar la colmena " + seleccionado.getNombre() + " y todos sus modulos?")) return;
        try {
            colmenaDao.eliminar(seleccionado.getId());
            nuevo();
            recargar();
        } catch (Exception e) {
            Mensajes.error("No se pudo eliminar. Si la colmena tiene plantas, eliminelas primero.", e);
        }
    }

    private void llenar(Colmena h, String nombre, String ubicacion, LocalDate fecha, Usuario usuario) {
        h.setNombre(nombre);
        h.setUbicacion(ubicacion.isEmpty() ? null : ubicacion);
        h.setFechaInstalacion(fecha);
        h.setUsuario(usuario);
    }

    private void cargarFormulario(Colmena colmena) {
        seleccionado = colmena;
        if (colmena == null) return;
        txtNombre.setText(colmena.getNombre());
        txtUbicacion.setText(colmena.getUbicacion() == null ? "" : colmena.getUbicacion());
        dpFecha.setValue(colmena.getFechaInstalacion());
        // Selecciona en el combo el usuario con el mismo id
        cbUsuario.getItems().stream()
                .filter(u -> u.getId().equals(colmena.getUsuario().getId()))
                .findFirst()
                .ifPresent(cbUsuario::setValue);
        // Los niveles solo se definen al crear la colmena
        spNiveles.setDisable(true);
        spCapacidad.setDisable(true);
    }
}


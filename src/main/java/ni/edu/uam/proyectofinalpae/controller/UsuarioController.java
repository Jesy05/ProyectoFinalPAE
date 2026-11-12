package ni.edu.uam.proyectofinalpae.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import ni.edu.uam.proyectofinalpae.dao.UsuarioDao;
import ni.edu.uam.proyectofinalpae.model.Usuario;
import ni.edu.uam.proyectofinalpae.util.Mensajes;

import java.time.LocalDate;

/** CRUD de usuarios: crear, consultar, editar y eliminar. */
public class UsuarioController {

    @FXML private TextField txtNombre;
    @FXML private TextField txtCorreo;
    @FXML private TextField txtTelefono;
    @FXML private TableView<Usuario> tblUsuarios;
    @FXML private TableColumn<Usuario, Long> colId;
    @FXML private TableColumn<Usuario, String> colNombre;
    @FXML private TableColumn<Usuario, String> colCorreo;
    @FXML private TableColumn<Usuario, String> colTelefono;
    @FXML private TableColumn<Usuario, LocalDate> colFecha;

    private final UsuarioDao usuarioDao = new UsuarioDao();
    private Usuario seleccionado;

    @FXML
    public void initialize() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colNombre.setCellValueFactory(new PropertyValueFactory<>("nombre"));
        colCorreo.setCellValueFactory(new PropertyValueFactory<>("correo"));
        colTelefono.setCellValueFactory(new PropertyValueFactory<>("telefono"));
        colFecha.setCellValueFactory(new PropertyValueFactory<>("fechaRegistro"));
        tblUsuarios.getSelectionModel().selectedItemProperty()
                .addListener((obs, anterior, usuario) -> cargarFormulario(usuario));
        recargar();
    }

    @FXML
    private void recargar() {
        try {
            tblUsuarios.setItems(FXCollections.observableArrayList(usuarioDao.listar()));
        } catch (Exception e) {
            Mensajes.error("No se pudieron cargar los usuarios.", e);
        }
    }

    @FXML
    private void nuevo() {
        tblUsuarios.getSelectionModel().clearSelection();
        seleccionado = null;
        txtNombre.clear();
        txtCorreo.clear();
        txtTelefono.clear();
        txtNombre.requestFocus();
    }

    @FXML
    private void guardar() {
        try {
            String nombre = txtNombre.getText().trim();
            String correo = txtCorreo.getText().trim();
            String telefono = txtTelefono.getText().trim();

            // Validaciones basicas
            if (nombre.isEmpty()) throw new IllegalArgumentException("El nombre es obligatorio.");
            if (!correo.matches("^[\\w.+-]+@[\\w-]+\\.[\\w.]+$"))
                throw new IllegalArgumentException("Escriba un correo valido, por ejemplo ana@correo.com");
            if (!telefono.isEmpty() && !telefono.matches("\\d{8}"))
                throw new IllegalArgumentException("El telefono debe tener 8 digitos (o dejelo vacio).");

            Long idActual = seleccionado == null ? null : seleccionado.getId();
            if (usuarioDao.correoEnUso(correo, idActual))
                throw new IllegalArgumentException("Ya existe otro usuario con ese correo.");

            Usuario usuario = seleccionado == null ? new Usuario() : seleccionado;
            usuario.setNombre(nombre);
            usuario.setCorreo(correo);
            usuario.setTelefono(telefono.isEmpty() ? null : telefono);

            if (usuario.getId() == null) {
                usuarioDao.guardar(usuario);
                Mensajes.info("Usuario registrado.");
            } else {
                usuarioDao.actualizar(usuario);
                Mensajes.info("Usuario actualizado.");
            }
            nuevo();
            recargar();
        } catch (IllegalArgumentException e) {
            Mensajes.advertencia(e.getMessage());
        } catch (Exception e) {
            Mensajes.error("No se pudo guardar el usuario.", e);
            recargar();
        }
    }

    @FXML
    private void eliminar() {
        if (seleccionado == null) {
            Mensajes.advertencia("Seleccione un usuario de la tabla.");
            return;
        }
        if (!Mensajes.confirmar("Eliminar a " + seleccionado.getNombre() + "?")) return;
        try {
            usuarioDao.eliminar(seleccionado.getId());
            nuevo();
            recargar();
        } catch (Exception e) {
            Mensajes.error("No se pudo eliminar. Si el usuario tiene colmenas, eliminelas primero.", e);
        }
    }

    private void cargarFormulario(Usuario usuario) {
        seleccionado = usuario;
        if (usuario == null) return;
        txtNombre.setText(usuario.getNombre());
        txtCorreo.setText(usuario.getCorreo());
        txtTelefono.setText(usuario.getTelefono() == null ? "" : usuario.getTelefono());
    }
}

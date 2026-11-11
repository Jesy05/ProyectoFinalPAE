package ni.edu.uam.proyectofinalpae.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.hibernate.exception.ConstraintViolationException;

public class Mensajes {
    private Mensajes() {
    }

    public static void info(String mensaje) {
        mostrar(Alert.AlertType.INFORMATION, "PlantBee", mensaje);
    }

    public static void advertencia(String mensaje) {
        mostrar(Alert.AlertType.WARNING, "Revise los datos", mensaje);
    }

    public static void error(String contexto, Exception e) {
        mostrar(Alert.AlertType.ERROR, "Error", contexto + "\n\nDetalle: " + describir(e));
    }

    public static boolean confirmar(String pregunta) {
        Alert alerta = new Alert(Alert.AlertType.CONFIRMATION, pregunta, ButtonType.OK, ButtonType.CANCEL);
        alerta.setTitle("Confirmar");
        alerta.setHeaderText(null);
        return alerta.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }

    /** Busca la causa real del error para mostrar algo entendible. */
    public static String describir(Throwable e) {
        Throwable actual = e;
        while (actual != null) {
            if (actual instanceof ConstraintViolationException) {
                return "La operacion viola una regla de la base de datos: el dato ya existe "
                        + "o el registro esta siendo usado por otros registros.";
            }
            actual = actual.getCause();
        }
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    private static void mostrar(Alert.AlertType tipo, String titulo, String mensaje) {
        Alert alerta = new Alert(tipo, mensaje);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        alerta.showAndWait();
    }
}

package ni.edu.uam.proyectofinalpae;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ni.edu.uam.proyectofinalpae.util.HibernateUtil;
import ni.edu.uam.proyectofinalpae.util.Mensajes;

public class App extends Application {

    @Override
    public void start(Stage stage) {
        try {
            // Se abre la conexion antes de mostrar la ventana para detectar errores de BD al inicio
            HibernateUtil.getSessionFactory();
            Parent root = FXMLLoader.load(App.class.getResource("/view/main-view.fxml"));
            stage.setScene(new Scene(root, 1050, 680));
            stage.setTitle("PlantBee - Huertos verticales modulares");
            stage.show();
        } catch (Exception e) {
            Mensajes.error("No se pudo iniciar PlantBee. Revise que PostgreSQL este encendido "
                    + "y que exista src/main/resources/db.properties con su clave.", e);
            Platform.exit();
        }
    }

    @Override
    public void stop() {
        HibernateUtil.cerrar();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

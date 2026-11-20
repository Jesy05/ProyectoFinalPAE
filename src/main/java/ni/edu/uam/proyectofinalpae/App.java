package ni.edu.uam.proyectofinalpae;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ni.edu.uam.proyectofinalpae.api.ApiServidor;
import ni.edu.uam.proyectofinalpae.util.HibernateUtil;
import ni.edu.uam.proyectofinalpae.util.LogoPlantBee;
import ni.edu.uam.proyectofinalpae.util.Mensajes;
import ni.edu.uam.proyectofinalpae.util.Navegador;

import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

public class App extends Application {

    /** Hojas de estilo de toda la aplicacion. */
    private static final String[] ESTILOS = {
            "plantbee.css",
            "formularios.css",
            "tablas.css",
            "colmenas.css",
            "plantas.css",
            "riegos.css",
            "navegacion.css",
            "tipografia.css",
            "bienvenida.css"
    };

    /** Se guarda la referencia para que Java no descarte la configuracion del log. */
    private static final Logger LOG_HIBERNATE = Logger.getLogger("org.hibernate");

    private final ApiServidor api = new ApiServidor();

    @Override
    public void start(Stage stage) {
        // La consola solo muestra advertencias reales de Hibernate, no sus mensajes informativos
        LOG_HIBERNATE.setLevel(Level.WARNING);
        Logger.getLogger("org.hibernate.orm.connections.pooling").setLevel(Level.SEVERE);

        try {
            // 1. Inicializar Hibernate (si la BD falla, se avisa antes de mostrar la ventana)
            HibernateUtil.getSessionFactory();
            api.iniciar();

            // 2. Primera pantalla: bienvenida
            Parent bienvenida = Navegador.cargar("bienvenida-view.fxml");
            Scene scene = new Scene(bienvenida, 1200, 750);

            // 3. Hojas de estilo
            for (String archivo : ESTILOS) {
                URL recurso = App.class.getResource("/css/" + archivo);
                if (recurso == null) {
                    throw new IllegalStateException("No se encontro el CSS: " + archivo);
                }
                scene.getStylesheets().add(recurso.toExternalForm());
            }
            Navegador.configurar(scene);

            // 4. Ventana
            stage.setScene(scene);
            stage.setMinWidth(1050);
            stage.setMinHeight(680);
            stage.setTitle("PlantBee - Huertos verticales modulares");
            stage.getIcons().add(LogoPlantBee.icono(64));
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
            Mensajes.error("No se pudo iniciar PlantBee. Revise que PostgreSQL este encendido "
                    + "y que exista src/main/resources/db.properties con su clave.", e);
            Platform.exit();
        }
    }

    @Override
    public void stop() {
        api.detener();
        HibernateUtil.cerrar();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

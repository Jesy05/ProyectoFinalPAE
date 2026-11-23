package ni.edu.uam.proyectofinalpae;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
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
            "bienvenida.css",
            "reportes.css"
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

            // Tamano segun la pantalla: con escalado de Windows (125 %, 150 %) el espacio real es menor
            Rectangle2D pantalla = Screen.getPrimary().getVisualBounds();
            double ancho = Math.min(1200, pantalla.getWidth() * 0.92);
            double alto = Math.min(750, pantalla.getHeight() * 0.90);
            Scene scene = new Scene(bienvenida, ancho, alto);

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
            // El minimo nunca puede ser mayor que la pantalla, si no la ventana no se deja achicar
            stage.setMinWidth(Math.min(900, pantalla.getWidth()));
            stage.setMinHeight(Math.min(560, pantalla.getHeight()));
            stage.setTitle("PlantBee - Huertos verticales modulares");
            stage.getIcons().add(LogoPlantBee.icono(64));
            stage.show();
            stage.centerOnScreen();

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

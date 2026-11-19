
package ni.edu.uam.proyectofinalpae;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

import ni.edu.uam.proyectofinalpae.util.HibernateUtil;
import ni.edu.uam.proyectofinalpae.util.Mensajes;

import java.net.URL;

public class App extends Application {

    @Override
    public void start(Stage stage) {

        try {

            // 1. Inicializar Hibernate
            HibernateUtil.getSessionFactory();

            // 2. Cargar interfaz principal
            URL vista = App.class.getResource(
                    "/view/main-view.fxml"
            );

            if (vista == null) {
                throw new IllegalStateException(
                        "No se encontro main-view.fxml"
                );
            }

            Parent root = FXMLLoader.load(vista);

            // 3. Crear escena
            Scene scene = new Scene(root, 1200, 750);

            // 4. Cargar hojas de estilos
            String[] estilos = {
                    "plantbee.css",
                    "formularios.css",
                    "tablas.css",
                    "colmenas.css",
                    "plantas.css",
                    "riegos.css",
                    "navegacion.css",
                    "tipografia.css"
            };

            for (String archivo : estilos) {

                URL recurso = App.class.getResource(
                        "/css/" + archivo
                );

                if (recurso == null) {
                    throw new IllegalStateException(
                            "No se encontro el CSS: " + archivo
                    );
                }

                scene.getStylesheets().add(
                        recurso.toExternalForm()
                );

                System.out.println(
                        "CSS cargado: " + archivo
                );
            }

            // 5. Configurar ventana
            stage.setScene(scene);

            stage.setMinWidth(1050);
            stage.setMinHeight(680);

            stage.setTitle(
                    "PlantBee - Huertos verticales modulares"
            );

            // 6. Mostrar aplicacion
            stage.show();

            // 7. Verificar interfaz
            root.applyCss();
            root.layout();

            verificarInterfaz(scene);

        } catch (Exception e) {

            System.err.println(
                    "========== ERROR PLANTBEE =========="
            );

            e.printStackTrace();

            System.err.println(
                    "===================================="
            );

            Mensajes.error(
                    "No se pudo iniciar PlantBee.\n"
                            + "Error: "
                            + e.getClass().getSimpleName()
                            + "\nDetalle: "
                            + e.getMessage(),
                    e
            );

            Platform.exit();
        }
    }

    private void verificarInterfaz(Scene scene) {

        Node navegacion = scene.lookup(".plantbee-tabs");

        if (navegacion instanceof TabPane tabs) {

            System.out.println(
                    "TabPane encontrado: "
                            + tabs.getStyleClass()
            );

            System.out.println(
                    "Pestanas: " + tabs.getTabs().size()
            );

            Node pestana = tabs.lookup(".tab");

            if (pestana != null) {

                System.out.println(
                        "Clases de pestana: "
                                + pestana.getStyleClass()
                );

                if (pestana instanceof Region region) {

                    System.out.println(
                            "Fondo efectivo de pestana: "
                                    + region.getBackground()
                    );
                }
            }
        }

        Node nodoTitulo = scene.lookup(".brand-title");

        if (nodoTitulo instanceof Label titulo) {

            System.out.println(
                    "Fuente real del titulo: "
                            + titulo.getFont().getName()
            );

            System.out.println(
                    "Familia real: "
                            + titulo.getFont().getFamily()
            );
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

package ni.edu.uam.proyectofinalpae.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;

import java.io.IOException;
import java.net.URL;

/**
 * Cambia la pantalla que se muestra en la ventana: bienvenida -> onboarding -> panel de control.
 * Se reutiliza la misma Scene para conservar las hojas de estilo.
 */
public final class Navegador {

    private static Scene escena;

    private Navegador() {
    }

    public static void configurar(Scene scene) {
        escena = scene;
    }

    /** Carga un FXML de /view, le agrega los iconos y devuelve su nodo raiz. */
    public static Parent cargar(String vista) throws IOException {
        URL recurso = Navegador.class.getResource("/view/" + vista);
        if (recurso == null) {
            throw new IllegalStateException("No se encontro la vista " + vista);
        }
        Parent raiz = FXMLLoader.load(recurso);
        Iconos.decorar(raiz);
        return raiz;
    }

    public static void irBienvenida() {
        mostrar("bienvenida-view.fxml");
    }

    public static void irOnboarding() {
        mostrar("onboarding-view.fxml");
    }

    /** Panel de control con las pestanas de Usuarios, Colmenas, Plantas y Riegos. */
    public static void irPanel() {
        Parent panel = mostrar("main-view.fxml");
        if (panel != null) {
            Node inicio = panel.lookup("#btnInicio");
            if (inicio instanceof Button boton) {
                boton.setOnAction(e -> irBienvenida());
            }
        }
    }

    private static Parent mostrar(String vista) {
        try {
            Parent raiz = cargar(vista);
            escena.setRoot(raiz);
            return raiz;
        } catch (Exception e) {
            Mensajes.error("No se pudo abrir la pantalla " + vista, e);
            return null;
        }
    }
}

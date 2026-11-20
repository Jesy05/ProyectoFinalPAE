package ni.edu.uam.proyectofinalpae.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

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
            // En pantallas pequenas cada pestana se desplaza en vez de aplastar las tablas
            for (Node nodo : panel.lookupAll(".tab-pane")) {
                TabPane pestanas = (TabPane) nodo;
                for (Tab tab : pestanas.getTabs()) {
                    if (tab.getContent() != null && !(tab.getContent() instanceof ScrollPane)) {
                        // Se guarda la pantalla original para poder buscar su boton Recargar
                        tab.setUserData(tab.getContent());
                        ScrollPane scroll = new ScrollPane(tab.getContent());
                        scroll.setFitToWidth(true);
                        scroll.setFitToHeight(true);
                        scroll.getStyleClass().add("panel-scroll");
                        tab.setContent(scroll);
                    }
                }
                // Cada pestana se actualiza al abrirla: asi una usuaria registrada en Usuarios
                // aparece de inmediato en el combo de Dueno de Colmenas
                pestanas.getSelectionModel().selectedItemProperty()
                        .addListener((obs, anterior, actual) -> recargar(actual));
            }
            Node inicio = panel.lookup("#btnInicio");
            if (inicio instanceof Button boton) {
                boton.setOnAction(e -> irBienvenida());
            }
        }
    }

    /** Presiona el boton Recargar de la pestana: vuelve a leer sus combos y su tabla desde la base. */
    private static void recargar(Tab tab) {
        if (tab != null && tab.getUserData() instanceof Parent pantalla) {
            for (Node nodo : pantalla.lookupAll(".button")) {
                if (nodo instanceof Button boton && "Recargar".equals(boton.getText())) {
                    boton.fire();
                    return;
                }
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

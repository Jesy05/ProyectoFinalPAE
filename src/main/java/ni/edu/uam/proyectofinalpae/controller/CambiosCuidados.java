package ni.edu.uam.proyectofinalpae.controller;

import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableLongValue;
import javafx.concurrent.Task;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Window;
import ni.edu.uam.proyectofinalpae.util.Mensajes;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** Avisa a las ventanas abiertas después de un cambio guardado desde JavaFX. */
public final class CambiosCuidados {
    private static final ReadOnlyLongWrapper VERSION = new ReadOnlyLongWrapper();

    private CambiosCuidados() {
    }

    static ObservableLongValue version() {
        return VERSION.getReadOnlyProperty();
    }

    static void observar(Node vista, Runnable actualizar) {
        ChangeListener<Number> cambio = (obs, anterior, actual) -> actualizar.run();
        vista.sceneProperty().flatMap(Scene::windowProperty).flatMap(Window::showingProperty)
                .addListener((obs, anterior, visible) -> {
                    if (Boolean.TRUE.equals(visible)) {
                        VERSION.addListener(cambio);
                        actualizar.run();
                    } else {
                        VERSION.removeListener(cambio);
                    }
                });
    }

    static <T> void ejecutar(Button boton, Callable<T> operacion, Consumer<T> alGuardar, String error) {
        boton.setDisable(true);
        Task<T> tarea = new Task<>() {
            @Override
            protected T call() throws Exception {
                return operacion.call();
            }
        };
        tarea.setOnSucceeded(evento -> {
            boton.setDisable(false);
            notificar();
            if (boton.getScene() != null) alGuardar.accept(tarea.getValue());
        });
        tarea.setOnFailed(evento -> {
            boton.setDisable(false);
            if (boton.getScene() != null) {
                Throwable causa = tarea.getException();
                Mensajes.error(error, causa instanceof Exception ex ? ex : new RuntimeException(causa));
            }
        });
        Thread hilo = new Thread(tarea, "plantbee-cuidados-escritura");
        hilo.setDaemon(true);
        hilo.start();
    }

    public static void notificar() {
        VERSION.set(VERSION.get() + 1);
    }
}

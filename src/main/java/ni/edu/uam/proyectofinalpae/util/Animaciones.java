package ni.edu.uam.proyectofinalpae.util;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;

import java.util.List;

/**
 * Animaciones reutilizables de PlantBee. JavaFX no anima desde el CSS (no tiene @keyframes),
 * asi que el estilo va en los .css y el movimiento se hace con las transiciones de JavaFX.
 */
public final class Animaciones {

    private static final String CON_HOVER = "plantbee-hover";

    private Animaciones() {
    }

    /** El nodo aparece subiendo un poco mientras se hace visible. */
    public static void entrada(Node nodo, double retrasoMs) {
        nodo.setOpacity(0);
        nodo.setTranslateY(18);
        FadeTransition aparecer = new FadeTransition(Duration.millis(420), nodo);
        aparecer.setToValue(1);
        TranslateTransition subir = new TranslateTransition(Duration.millis(420), nodo);
        subir.setToY(0);
        subir.setInterpolator(Interpolator.EASE_OUT);
        ParallelTransition juntas = new ParallelTransition(aparecer, subir);
        juntas.setDelay(Duration.millis(retrasoMs));
        juntas.play();
    }

    /** Varios nodos aparecen uno detras de otro (efecto cascada). */
    public static void escalonar(List<? extends Node> nodos, double retrasoInicialMs, double pasoMs) {
        for (int i = 0; i < nodos.size(); i++) {
            entrada(nodos.get(i), retrasoInicialMs + i * pasoMs);
        }
    }

    /** Transicion suave al cambiar de pantalla o de pestana. */
    public static void fundido(Node nodo) {
        nodo.setOpacity(0);
        nodo.setScaleX(0.985);
        nodo.setScaleY(0.985);
        FadeTransition aparecer = new FadeTransition(Duration.millis(320), nodo);
        aparecer.setToValue(1);
        ScaleTransition crecer = new ScaleTransition(Duration.millis(320), nodo);
        crecer.setToX(1);
        crecer.setToY(1);
        crecer.setInterpolator(Interpolator.EASE_OUT);
        new ParallelTransition(aparecer, crecer).play();
    }

    /** Aparece con un pequeno rebote (crece un poco de mas y se acomoda). */
    public static void rebote(Node nodo) {
        Timeline t = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(nodo.scaleXProperty(), 0.55), new KeyValue(nodo.scaleYProperty(), 0.55),
                        new KeyValue(nodo.opacityProperty(), 0)),
                new KeyFrame(Duration.millis(260),
                        new KeyValue(nodo.scaleXProperty(), 1.08, Interpolator.EASE_OUT),
                        new KeyValue(nodo.scaleYProperty(), 1.08, Interpolator.EASE_OUT),
                        new KeyValue(nodo.opacityProperty(), 1)),
                new KeyFrame(Duration.millis(420),
                        new KeyValue(nodo.scaleXProperty(), 1, Interpolator.EASE_BOTH),
                        new KeyValue(nodo.scaleYProperty(), 1, Interpolator.EASE_BOTH)));
        t.play();
    }

    /** Sube y baja suavemente sin parar (por ejemplo, la ilustracion del huerto). */
    public static Animation flotar(Node nodo, double distancia, double segundos) {
        TranslateTransition t = new TranslateTransition(Duration.seconds(segundos), nodo);
        t.setFromY(-distancia / 2);
        t.setToY(distancia / 2);
        t.setAutoReverse(true);
        t.setCycleCount(Animation.INDEFINITE);
        t.setInterpolator(Interpolator.EASE_BOTH);
        t.play();
        return t;
    }

    /** Late (crece y se encoge) sin parar; sirve para indicadores "en vivo" y avisos. */
    public static Animation latir(Node nodo, double escala, double segundos) {
        ScaleTransition t = new ScaleTransition(Duration.seconds(segundos), nodo);
        t.setFromX(1);
        t.setFromY(1);
        t.setToX(escala);
        t.setToY(escala);
        t.setAutoReverse(true);
        t.setCycleCount(Animation.INDEFINITE);
        t.setInterpolator(Interpolator.EASE_BOTH);
        t.play();
        return t;
    }

    /** Al pasar el mouse el nodo crece un poco; al salir vuelve a su tamano. */
    public static void hover(Node nodo, double escala) {
        if (nodo.getProperties().containsKey(CON_HOVER)) return;
        nodo.getProperties().put(CON_HOVER, Boolean.TRUE);
        ScaleTransition t = new ScaleTransition(Duration.millis(140), nodo);
        nodo.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> {
            if (nodo.isDisabled()) return;
            t.stop();
            t.setToX(escala);
            t.setToY(escala);
            t.play();
        });
        nodo.addEventHandler(MouseEvent.MOUSE_EXITED, e -> {
            t.stop();
            t.setToX(1);
            t.setToY(1);
            t.play();
        });
    }

    /** Le pone el efecto hover a todos los botones de una pantalla. */
    public static void decorarBotones(Parent raiz) {
        for (Node nodo : raiz.lookupAll(".button")) {
            if (nodo instanceof Button) {
                hover(nodo, 1.04);
            }
        }
    }
}

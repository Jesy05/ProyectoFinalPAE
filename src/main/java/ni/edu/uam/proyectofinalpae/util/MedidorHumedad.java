package ni.edu.uam.proyectofinalpae.util;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Line;
import javafx.scene.shape.StrokeLineCap;
import javafx.util.Duration;

/**
 * Medidor circular de humedad: un anillo que se llena del 0 al 100 % con animacion,
 * con una marca en la humedad minima del cultivo. Se vuelve rojo cuando hay que regar.
 */
public class MedidorHumedad extends StackPane {

    private static final double RADIO = 46;

    private final DoubleProperty valor = new SimpleDoubleProperty(0);
    private final Arc relleno = new Arc(0, 0, RADIO, RADIO, 90, 0);
    private final Line marcaMinima = new Line(0, -RADIO - 7, 0, -RADIO + 7);
    private final Label porcentaje = new Label("--");
    private int minima;
    private Timeline animacion;

    public MedidorHumedad() {
        getStyleClass().add("medidor");

        Arc pista = new Arc(0, 0, RADIO, RADIO, 90, -360);
        pista.setType(ArcType.OPEN);
        pista.getStyleClass().add("medidor-pista");

        relleno.setType(ArcType.OPEN);
        relleno.setStrokeLineCap(StrokeLineCap.ROUND);
        relleno.getStyleClass().add("medidor-relleno");
        // El anillo avanza en sentido horario desde arriba
        relleno.lengthProperty().bind(valor.multiply(-3.6));
        valor.addListener((obs, a, v) -> porcentaje.setText(Math.round(v.doubleValue()) + "%"));

        marcaMinima.getStyleClass().add("medidor-minimo");

        porcentaje.getStyleClass().add("medidor-valor");
        Label etiqueta = new Label("humedad");
        etiqueta.getStyleClass().add("medidor-etiqueta");
        VBox centro = new VBox(-2, porcentaje, etiqueta);
        centro.setAlignment(javafx.geometry.Pos.CENTER);

        javafx.scene.Group anillo = new javafx.scene.Group(pista, relleno, marcaMinima);
        getChildren().addAll(anillo, centro);
        setMinSize(120, 120);
        setPrefSize(120, 120);
    }

    /** Mueve el anillo hasta la nueva humedad con una animacion suave. */
    public void mostrar(Integer humedad, int humedadMinima) {
        this.minima = humedadMinima;
        // La marca de la humedad minima se coloca girando la linea alrededor del centro
        marcaMinima.getTransforms().setAll(new javafx.scene.transform.Rotate(humedadMinima * 3.6, 0, 0));

        if (humedad == null) {
            porcentaje.setText("--");
            getStyleClass().removeAll("bajo", "ok");
            return;
        }
        boolean bajo = humedad < humedadMinima;
        getStyleClass().removeAll("bajo", "ok");
        getStyleClass().add(bajo ? "bajo" : "ok");

        if (animacion != null) animacion.stop();
        animacion = new Timeline(new KeyFrame(Duration.millis(900),
                new KeyValue(valor, humedad, Interpolator.EASE_OUT)));
        animacion.play();
    }

    public int getMinima() {
        return minima;
    }
}

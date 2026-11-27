package ni.edu.uam.proyectofinalpae.util;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.PathTransition;
import javafx.animation.ScaleTransition;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * Abejita de PlantBee dibujada con formas vectoriales. Aletea sin parar y puede volar
 * por una ruta de curvas, mirando siempre hacia donde va.
 */
public class Abeja extends Group {

    private static final Color AMARILLO = Color.web("#F2C230");
    private static final Color OSCURO = Color.web("#254E06");

    private final Group cuerpo = new Group();

    public Abeja(double escala) {
        // Alas (detras del cuerpo), semitransparentes
        Ellipse alaAtras = new Ellipse(-3, -9, 6, 8.5);
        Ellipse alaAdelante = new Ellipse(3, -10, 6.5, 9);
        for (Ellipse ala : new Ellipse[]{alaAtras, alaAdelante}) {
            ala.setFill(Color.web("#FFFFFF", 0.78));
            ala.setStroke(Color.web("#9DBD60", 0.9));
            ala.setStrokeWidth(0.8);
        }
        Group alas = new Group(alaAtras, alaAdelante);

        // Cuerpo amarillo con dos rayas verdes oscuras (recortadas a la forma del cuerpo)
        Ellipse abdomen = new Ellipse(0, 0, 11, 7.5);
        abdomen.setFill(AMARILLO);
        abdomen.setStroke(OSCURO);
        abdomen.setStrokeWidth(1.2);
        Rectangle raya1 = new Rectangle(-5, -8, 3.2, 16);
        Rectangle raya2 = new Rectangle(1.5, -8, 3.2, 16);
        Group rayas = new Group(raya1, raya2);
        rayas.getChildren().forEach(r -> ((Rectangle) r).setFill(OSCURO));
        rayas.setClip(new Ellipse(0, 0, 11, 7.5));

        Circle cabeza = new Circle(12, -1, 5);
        cabeza.setFill(OSCURO);
        Circle ojo = new Circle(13.8, -2.3, 1.3, Color.WHITE);
        Polygon aguijon = new Polygon(-11, -1.5, -15.5, 0, -11, 1.5);
        aguijon.setFill(OSCURO);

        cuerpo.getChildren().addAll(alas, aguijon, abdomen, rayas, cabeza, ojo);
        getChildren().add(cuerpo);
        setScaleX(escala);
        setScaleY(escala);
        setMouseTransparent(true);

        // Aleteo rapido: las alas se aplastan y se abren
        ScaleTransition aleteo = new ScaleTransition(Duration.millis(70), alas);
        aleteo.setFromY(1);
        aleteo.setToY(0.35);
        aleteo.setAutoReverse(true);
        aleteo.setCycleCount(Animation.INDEFINITE);
        aleteo.play();
    }

    /**
     * Vuela sin parar por una ruta cerrada que pasa por los puntos dados (x, y, x, y...).
     * Entre punto y punto hace una curva suave.
     */
    public Animation volar(double segundos, double... puntos) {
        Path ruta = new Path(new MoveTo(puntos[0], puntos[1]));
        int n = puntos.length / 2;
        for (int i = 1; i <= n; i++) {
            double x0 = puntos[(i - 1) * 2], y0 = puntos[(i - 1) * 2 + 1];
            double x1 = puntos[(i % n) * 2], y1 = puntos[(i % n) * 2 + 1];
            // Puntos de control desplazados para que la curva ondule
            double dx = x1 - x0, dy = y1 - y0;
            ruta.getElements().add(new CubicCurveTo(
                    x0 + dx * 0.3 - dy * 0.35, y0 + dy * 0.3 + dx * 0.35,
                    x0 + dx * 0.7 + dy * 0.35, y0 + dy * 0.7 - dx * 0.35,
                    x1, y1));
        }
        PathTransition vuelo = new PathTransition(Duration.seconds(segundos), ruta, this);
        vuelo.setCycleCount(Animation.INDEFINITE);
        vuelo.setInterpolator(Interpolator.LINEAR);

        // Mira hacia donde vuela: si va a la izquierda, se voltea
        translateXProperty().addListener((obs, antes, ahora) -> {
            double cambio = ahora.doubleValue() - antes.doubleValue();
            if (Math.abs(cambio) > 0.05) {
                cuerpo.setScaleX(cambio < 0 ? -1 : 1);
            }
        });
        vuelo.play();
        return vuelo;
    }
}

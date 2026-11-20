package ni.edu.uam.proyectofinalpae.util;

import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/**
 * Logo de PlantBee dibujado con formas vectoriales: tres modulos hexagonales con un brote,
 * igual que la colmena real. Se puede usar desde FXML:
 *
 * <pre>&lt;LogoPlantBee alto="48" conTexto="false" color="#F5EAB9"/&gt;</pre>
 */
public class LogoPlantBee extends HBox {

    /** Verde oscuro de la marca. */
    public static final Color VERDE = Color.web("#004F2E");
    /** Crema de la marca. */
    public static final Color CREMA = Color.web("#F5EAB9");

    // Medidas del dibujo original (luego se escala al alto pedido)
    private static final double RADIO = 52;
    private static final double ALTO_DIBUJO = 296;
    private static final double[][] CENTROS = {{110, 58}, {58, 148}, {110, 238}};

    private double alto = 120;
    private boolean conTexto = true;
    private Color color = VERDE;

    public LogoPlantBee() {
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("logo-plantbee");
        dibujar();
    }

    public LogoPlantBee(double alto, boolean conTexto, Color color) {
        this.alto = alto;
        this.conTexto = conTexto;
        this.color = color;
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("logo-plantbee");
        dibujar();
    }

    private void dibujar() {
        getChildren().clear();
        setSpacing(alto * 0.12);

        Group simbolo = new Group();
        for (double[] c : CENTROS) {
            simbolo.getChildren().addAll(hexagono(c[0], c[1]), tallo(c[0], c[1]),
                    hoja(c[0], c[1], false), hoja(c[0], c[1], true));
        }
        double escala = alto / ALTO_DIBUJO;
        simbolo.setScaleX(escala);
        simbolo.setScaleY(escala);
        getChildren().add(new Group(simbolo));

        if (conTexto) {
            VBox texto = new VBox(-alto * 0.06, palabra("PLANT"), palabra("BEE"));
            texto.setAlignment(Pos.CENTER_LEFT);
            getChildren().add(texto);
        }
    }

    private Polygon hexagono(double cx, double cy) {
        Polygon hex = new Polygon();
        for (int i = 0; i < 6; i++) {
            double angulo = Math.toRadians(60 * i);
            hex.getPoints().addAll(cx + RADIO * Math.cos(angulo), cy + RADIO * Math.sin(angulo));
        }
        hex.setFill(Color.TRANSPARENT);
        hex.setStroke(color);
        hex.setStrokeWidth(9);
        hex.setStrokeLineJoin(StrokeLineJoin.ROUND);
        return hex;
    }

    private Line tallo(double cx, double cy) {
        Line linea = new Line(cx, cy + 26, cx, cy - 4);
        linea.setStroke(color);
        linea.setStrokeWidth(8);
        linea.setStrokeLineCap(StrokeLineCap.ROUND);
        return linea;
    }

    /** Hoja en forma de gota que sale de la punta del tallo, hacia la izquierda o la derecha. */
    private SVGPath hoja(double cx, double cy, boolean derecha) {
        double s = derecha ? 1 : -1;      // direccion horizontal de la hoja
        double x = cx;
        double y = cy - 4;                // punta del tallo
        SVGPath hoja = new SVGPath();
        hoja.setContent(String.format(java.util.Locale.US,
                "M%.1f,%.1f C%.1f,%.1f %.1f,%.1f %.1f,%.1f C%.1f,%.1f %.1f,%.1f %.1f,%.1f Z",
                x, y,
                x + s * 2, y - 16, x + s * 14, y - 24, x + s * 29, y - 23,
                x + s * 29, y - 9, x + s * 16, y + 1, x, y));
        hoja.setFill(color);
        return hoja;
    }

    private Text palabra(String valor) {
        Text t = new Text(valor);
        t.setFont(Font.font("Segoe UI", FontWeight.EXTRA_BOLD, alto * 0.30));
        t.setFill(color);
        return t;
    }

    /** Imagen del simbolo (sin texto) para usarla como icono de la ventana. */
    public static Image icono(int tamano) {
        LogoPlantBee logo = new LogoPlantBee(tamano, false, VERDE);
        SnapshotParameters parametros = new SnapshotParameters();
        parametros.setFill(CREMA);
        return logo.snapshot(parametros, null);
    }

    public double getAlto() {
        return alto;
    }

    public void setAlto(double alto) {
        this.alto = alto;
        dibujar();
    }

    public boolean isConTexto() {
        return conTexto;
    }

    public void setConTexto(boolean conTexto) {
        this.conTexto = conTexto;
        dibujar();
    }

    public String getColor() {
        return color.toString();
    }

    /** Acepta colores web, por ejemplo "#F5EAB9". */
    public void setColor(String color) {
        this.color = Color.web(color);
        dibujar();
    }
}

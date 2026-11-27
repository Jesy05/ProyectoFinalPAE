package ni.edu.uam.proyectofinalpae.controller;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import ni.edu.uam.proyectofinalpae.util.Abeja;
import ni.edu.uam.proyectofinalpae.util.Animaciones;
import ni.edu.uam.proyectofinalpae.util.LogoPlantBee;
import ni.edu.uam.proyectofinalpae.util.Navegador;

/** Pantalla de entrada (landing) de PlantBee. */
public class BienvenidaController {

    @FXML private VBox info;
    @FXML private LogoPlantBee logoArte;
    @FXML private ImageView huerto;
    @FXML private Pane capaAbejas;

    @FXML
    public void initialize() {
        // El logo, el titulo, las tarjetas y los botones entran uno tras otro
        Animaciones.escalonar(info.getChildren(), 120, 110);
        // Las tarjetas de beneficios crecen un poco al pasar el mouse
        for (Node tarjeta : info.lookupAll(".bienvenida-beneficio")) {
            Animaciones.hover(tarjeta, 1.02);
        }
        // La colmena y la torre del huerto aparecen y flotan suavemente, desfasadas entre si
        // (fundido usa opacidad y escala; el flotado usa el eje vertical, asi no se pelean)
        Animaciones.fundido(logoArte);
        Animaciones.fundido(huerto);
        Animaciones.flotar(logoArte, 12, 3.6);
        Animaciones.flotar(huerto, 16, 2.8);

        // Las abejitas salen cuando la columna ya tiene su tamano final
        capaAbejas.widthProperty().addListener((obs, antes, ancho) -> soltarAbejas());
        capaAbejas.heightProperty().addListener((obs, antes, alto) -> soltarAbejas());
    }

    /** Tres abejitas que vuelan por los espacios libres de la columna crema (sin tapar el texto). */
    private void soltarAbejas() {
        double w = capaAbejas.getWidth();
        double h = capaAbejas.getHeight();
        if (w < 200 || h < 200 || !capaAbejas.getChildren().isEmpty()) return;

        Abeja arriba = new Abeja(1.5);
        Abeja lateral = new Abeja(1.2);
        Abeja abajo = new Abeja(1.35);
        capaAbejas.getChildren().addAll(arriba, lateral, abajo);

        // Arriba a la derecha del logo
        arriba.volar(11, w * 0.45, 66, w * 0.70, 48, w * 0.88, 96, w * 0.62, 128);
        // Por el borde derecho de la columna
        lateral.volar(14, w - 62, 170, w - 48, h * 0.52, w - 80, h * 0.78, w - 55, h * 0.36);
        // Abajo, junto a los botones
        abajo.volar(9, w * 0.64, h - 72, w * 0.82, h - 44, w - 60, h - 96, w * 0.72, h - 112);
        // Aparecen poco a poco (solo opacidad: la escala de cada abeja se respeta)
        for (Abeja abeja : new Abeja[]{arriba, lateral, abajo}) {
            abeja.setOpacity(0);
            javafx.animation.FadeTransition aparecer =
                    new javafx.animation.FadeTransition(javafx.util.Duration.millis(900), abeja);
            aparecer.setToValue(1);
            aparecer.play();
        }
    }

    @FXML
    private void comenzar() {
        Navegador.irOnboarding();
    }

    @FXML
    private void irAlPanel() {
        Navegador.irPanel();
    }
}

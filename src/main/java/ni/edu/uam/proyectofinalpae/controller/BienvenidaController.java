package ni.edu.uam.proyectofinalpae.controller;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import ni.edu.uam.proyectofinalpae.util.Animaciones;
import ni.edu.uam.proyectofinalpae.util.LogoPlantBee;
import ni.edu.uam.proyectofinalpae.util.Navegador;

/** Pantalla de entrada (landing) de PlantBee. */
public class BienvenidaController {

    @FXML private VBox info;
    @FXML private LogoPlantBee logoArte;
    @FXML private ImageView huerto;

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

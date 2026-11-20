package ni.edu.uam.proyectofinalpae.controller;

import javafx.fxml.FXML;
import ni.edu.uam.proyectofinalpae.util.Navegador;

/** Pantalla de entrada (landing) de PlantBee. */
public class BienvenidaController {

    @FXML
    private void comenzar() {
        Navegador.irOnboarding();
    }

    @FXML
    private void irAlPanel() {
        Navegador.irPanel();
    }
}

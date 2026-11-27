package ni.edu.uam.proyectofinalpae.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import ni.edu.uam.proyectofinalpae.util.Animaciones;
import ni.edu.uam.proyectofinalpae.util.Iconos;
import ni.edu.uam.proyectofinalpae.util.Navegador;
import org.kordamp.ikonli.javafx.FontIcon;

/** Recorrido corto que explica que es PlantBee antes de entrar al panel de control. */
public class OnboardingController {

    /** Cada paso: icono, titulo y explicacion. */
    private static final String[][] PASOS = {
            {"mdi2h-hexagon-multiple", "Una colmena para tu casa",
                    "PlantBee es un huerto vertical hecho con módulos hexagonales apilables de plástico "
                            + "reciclado. Se instala en un balcón o apartamento y aprovecha el espacio hacia arriba."},
            {"mdi2w-water-percent", "Sensores que cuidan tus plantas",
                    "Cada maceta tiene un sensor de humedad. Cuando la humedad baja del mínimo que necesita "
                            + "el cultivo, PlantBee genera una alerta para que sepás cuándo regar."},
            {"mdi2r-recycle", "Agua que se reutiliza",
                    "El riego aprovecha agua de lluvia o agua gris filtrada por un biofiltro natural. "
                            + "La aplicación lleva la cuenta de cuántos litros de agua recuperada usás."},
            {"mdi2v-view-dashboard", "Tu panel de control",
                    "Registrá usuarios, creá colmenas con sus módulos, sembrá plantas con su sensor y "
                            + "llevá el historial de riego y alertas. Todo se guarda en PostgreSQL con Hibernate."}
    };

    @FXML private StackPane contenedorIcono;
    @FXML private Label lblPaso;
    @FXML private Label lblTitulo;
    @FXML private Label lblTexto;
    @FXML private HBox puntos;
    @FXML private Button btnAtras;
    @FXML private Button btnSiguiente;

    private int paso = 0;

    @FXML
    public void initialize() {
        for (int i = 0; i < PASOS.length; i++) {
            Region punto = new Region();
            punto.getStyleClass().add("onboarding-punto");
            puntos.getChildren().add(punto);
        }
        mostrarPaso();
    }

    @FXML
    private void siguiente() {
        if (paso == PASOS.length - 1) {
            Navegador.irPanel();
            return;
        }
        paso++;
        mostrarPaso();
    }

    @FXML
    private void atras() {
        if (paso > 0) {
            paso--;
            mostrarPaso();
        }
    }

    @FXML
    private void saltar() {
        Navegador.irPanel();
    }

    @FXML
    private void volverInicio() {
        Navegador.irBienvenida();
    }

    private void mostrarPaso() {
        String[] datos = PASOS[paso];
        FontIcon icono = Iconos.crear(datos[0], 64);
        icono.getStyleClass().add("onboarding-icono");
        contenedorIcono.getChildren().setAll(icono);

        lblPaso.setText("Paso " + (paso + 1) + " de " + PASOS.length);
        lblTitulo.setText(datos[1]);
        lblTexto.setText(datos[2]);

        // Cada paso: el icono aparece con un rebote y el texto entra deslizandose
        Animaciones.rebote(contenedorIcono);
        Animaciones.entrada(lblTitulo, 80);
        Animaciones.entrada(lblTexto, 160);

        for (int i = 0; i < puntos.getChildren().size(); i++) {
            puntos.getChildren().get(i).getStyleClass().remove("activo");
            if (i == paso) {
                puntos.getChildren().get(i).getStyleClass().add("activo");
            }
        }

        btnAtras.setDisable(paso == 0);
        boolean ultimo = paso == PASOS.length - 1;
        btnSiguiente.setText(ultimo ? "Entrar al panel" : "Siguiente");
        btnSiguiente.setGraphic(Iconos.crear(ultimo ? "mdi2v-view-dashboard" : "mdi2a-arrow-right", 18));
        btnSiguiente.setContentDisplay(ultimo ? javafx.scene.control.ContentDisplay.LEFT
                : javafx.scene.control.ContentDisplay.RIGHT);
    }
}

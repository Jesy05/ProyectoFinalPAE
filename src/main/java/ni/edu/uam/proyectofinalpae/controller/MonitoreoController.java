package ni.edu.uam.proyectofinalpae.controller;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import ni.edu.uam.proyectofinalpae.service.SimuladorIoTService;
import ni.edu.uam.proyectofinalpae.service.SimuladorIoTService.Lectura;
import ni.edu.uam.proyectofinalpae.util.Animaciones;
import ni.edu.uam.proyectofinalpae.util.Iconos;
import ni.edu.uam.proyectofinalpae.util.MedidorHumedad;
import ni.edu.uam.proyectofinalpae.util.Mensajes;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pestana Monitoreo: tablero "en vivo" de la colmena. Muestra cada sensor capacitivo con un medidor
 * animado y, en modo automatico, abre la valvula de 12 V y registra el riego en la base de datos.
 */
public class MonitoreoController {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final double SEGUNDOS_CICLO = 4;
    private static final double ALTO_TANQUE = 92;
    private static final double ANCHO_BATERIA = 170;

    @FXML private VBox raiz;
    @FXML private Circle puntoVivo;
    @FXML private Label lblUltimaLectura;
    @FXML private StackPane tanque;
    @FXML private Label lblTanque;
    @FXML private FontIcon iconoSol;
    @FXML private Label lblPotencia;
    @FXML private StackPane barraBateria;
    @FXML private Label lblBateria;
    @FXML private ToggleButton tglAutomatico;
    @FXML private Label lblRiegosAuto;
    @FXML private Label lblResumen;
    @FXML private Button btnLeer;
    @FXML private Button btnSimular;
    @FXML private FlowPane tarjetasPlantas;
    @FXML private HBox filaEstado;

    private final SimuladorIoTService simulador = new SimuladorIoTService();
    private final Map<Long, TarjetaPlanta> tarjetas = new LinkedHashMap<>();
    private final DoubleProperty nivelAgua = new SimpleDoubleProperty(0);
    private final DoubleProperty cargaBateria = new SimpleDoubleProperty(0);
    private Timeline ciclo;
    private boolean cicloEnCurso;
    private boolean accionManual;
    private boolean primeraCarga = true;

    @FXML
    public void initialize() {
        armarTanque();
        armarBateria();

        // Animaciones permanentes: el punto "en vivo" late y el sol gira despacio
        Animaciones.latir(puntoVivo, 1.6, 0.8);
        RotateTransition girar = new RotateTransition(Duration.seconds(18), iconoSol);
        girar.setByAngle(360);
        girar.setCycleCount(Animation.INDEFINITE);
        girar.setInterpolator(Interpolator.LINEAR);
        girar.play();

        tglAutomatico.selectedProperty().addListener((obs, antes, activo) -> {
            tglAutomatico.setText(activo ? "Activado" : "Desactivado");
            if (activo) ejecutarCiclo(true, false);
        });

        Animaciones.escalonar(filaEstado.getChildren(), 80, 90);

        // El monitoreo lee los sensores cada pocos segundos
        ciclo = new Timeline(new KeyFrame(Duration.seconds(SEGUNDOS_CICLO), e -> ejecutarCiclo(tglAutomatico.isSelected(), false)));
        ciclo.setCycleCount(Animation.INDEFINITE);
        ciclo.play();
        // Al salir del panel (otra pantalla) se detiene el ciclo
        raiz.sceneProperty().addListener((obs, antes, escena) -> {
            if (escena == null) {
                ciclo.stop();
            } else if (ciclo.getStatus() != Animation.Status.RUNNING) {
                ciclo.play();
            }
        });

        actualizarEnergia();
        leerSensores();
    }

    @FXML
    private void leerSensores() {
        if (accionManual) return;
        accionManual(true);
        ejecutarCiclo(tglAutomatico.isSelected(), true);
    }

    /** Simula que pasan unas horas: la tierra se seca y aparecen plantas que necesitan agua. */
    @FXML
    private void simularPasoDelTiempo() {
        if (accionManual) return;
        accionManual(true);
        Task<List<Lectura>> tarea = new Task<>() {
            @Override
            protected List<Lectura> call() {
                simulador.simularPasoDelTiempo();
                return simulador.leerSensores();
            }
        };
        tarea.setOnSucceeded(e -> {
            accionManual(false);
            mostrarLecturas(tarea.getValue());
        });
        tarea.setOnFailed(e -> {
            accionManual(false);
            Mensajes.error("No se pudo simular la lectura de los sensores.", comoException(tarea.getException()));
        });
        iniciar(tarea);
    }

    @FXML
    private void llenarTanque() {
        simulador.llenarTanqueConLluvia();
        actualizarTanque();
    }

    /**
     * Un ciclo del microcontrolador: (si esta en automatico) riega y luego lee todos los sensores.
     * El ciclo periodico no bloquea los botones; si ya hay uno en curso, se salta este turno.
     */
    private void ejecutarCiclo(boolean regar, boolean manual) {
        if (!manual) {
            if (cicloEnCurso || accionManual) return;
            cicloEnCurso = true;
        }
        Task<ResultadoCiclo> tarea = new Task<>() {
            @Override
            protected ResultadoCiclo call() {
                List<Long> regadas = regar ? simulador.cicloAutomatico() : List.of();
                return new ResultadoCiclo(regadas, simulador.leerSensores());
            }
        };
        tarea.setOnSucceeded(e -> {
            terminarCiclo(manual);
            ResultadoCiclo r = tarea.getValue();
            simulador.cargarBateria(LocalTime.now());
            actualizarEnergia();
            actualizarTanque();
            lblRiegosAuto.setText(simulador.getRiegosAutomaticos() + " riegos automáticos en esta sesión");
            if (r.regadas().isEmpty()) {
                mostrarLecturas(r.lecturas());
            } else {
                animarRiegos(r.regadas(), r.lecturas());
            }
        });
        tarea.setOnFailed(e -> {
            terminarCiclo(manual);
            tglAutomatico.setSelected(false);
            Mensajes.error("No se pudieron leer los sensores.", comoException(tarea.getException()));
        });
        iniciar(tarea);
    }

    // ---------------------------------------------------------------- tarjetas de plantas

    private void mostrarLecturas(List<Lectura> lecturas) {
        Set<Long> vigentes = new HashSet<>();
        List<Node> nuevas = new ArrayList<>();
        for (Lectura lectura : lecturas) {
            vigentes.add(lectura.plantaId());
            TarjetaPlanta tarjeta = tarjetas.get(lectura.plantaId());
            if (tarjeta == null) {
                tarjeta = new TarjetaPlanta();
                tarjetas.put(lectura.plantaId(), tarjeta);
                tarjetasPlantas.getChildren().add(tarjeta.raiz);
                nuevas.add(tarjeta.raiz);
            }
            tarjeta.mostrar(lectura);
        }
        // Plantas que ya no existen
        tarjetas.entrySet().removeIf(e -> {
            if (vigentes.contains(e.getKey())) return false;
            tarjetasPlantas.getChildren().remove(e.getValue().raiz);
            return true;
        });
        if (!nuevas.isEmpty()) {
            Animaciones.escalonar(nuevas, primeraCarga ? 200 : 0, 60);
        }
        primeraCarga = false;

        long sinAgua = lecturas.stream().filter(Lectura::necesitaRiego).count();
        lblResumen.setText(lecturas.isEmpty()
                ? "No hay plantas registradas todavía."
                : lecturas.size() + " plantas · " + sinAgua + (sinAgua == 1 ? " necesita riego" : " necesitan riego"));
        lblUltimaLectura.setText("Última lectura " + LocalTime.now().format(HORA));
    }

    /** Abre la valvula de cada planta regada (gotas cayendo) y luego muestra la humedad nueva. */
    private void animarRiegos(List<Long> regadas, List<Lectura> lecturas) {
        for (Long id : regadas) {
            TarjetaPlanta tarjeta = tarjetas.get(id);
            if (tarjeta != null) tarjeta.abrirValvula();
        }
        PauseTransition espera = new PauseTransition(Duration.millis(1700));
        espera.setOnFinished(e -> mostrarLecturas(lecturas));
        espera.play();
    }

    /** Tarjeta de una planta: medidor animado, datos del sensor y estado de la valvula. */
    private final class TarjetaPlanta {
        final VBox raiz = new VBox(10);
        final Label nombre = new Label();
        final Label detalle = new Label();
        final Label chip = new Label();
        final MedidorHumedad medidor = new MedidorHumedad();
        final Label sensor = new Label();
        final Label voltaje = new Label();
        final Label minima = new Label();
        final Label valvula = new Label();
        final Pane gotas = new Pane();
        Animation latido;

        TarjetaPlanta() {
            raiz.getStyleClass().add("planta-card");
            nombre.getStyleClass().add("planta-card-nombre");
            detalle.getStyleClass().add("planta-card-detalle");
            chip.getStyleClass().add("chip");
            detalle.setWrapText(true);

            Region espacio = new Region();
            HBox.setHgrow(espacio, Priority.ALWAYS);
            HBox cabecera = new HBox(8, nombre, espacio, chip);
            cabecera.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            for (Label l : List.of(sensor, voltaje, minima, valvula)) {
                l.getStyleClass().add("planta-card-dato");
                l.setGraphicTextGap(7);
            }
            sensor.setGraphic(Iconos.crear("mdi2a-access-point", 15));
            voltaje.setGraphic(Iconos.crear("mdi2l-lightning-bolt", 15));
            minima.setGraphic(Iconos.crear("mdi2g-gauge", 15));
            VBox datos = new VBox(6, sensor, voltaje, minima, valvula);
            datos.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            gotas.setMouseTransparent(true);
            gotas.setPrefSize(120, 120);
            StackPane zonaMedidor = new StackPane(medidor, gotas);

            HBox cuerpo = new HBox(14, zonaMedidor, datos);
            cuerpo.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            raiz.getChildren().addAll(cabecera, detalle, cuerpo);
            Animaciones.hover(raiz, 1.02);
            // Tres tarjetas por fila que ocupan todo el ancho (minimo 300 px)
            raiz.prefWidthProperty().bind(javafx.beans.binding.Bindings.max(300,
                    tarjetasPlantas.widthProperty().subtract(2 * 14 + 2).divide(3)));
        }

        void mostrar(Lectura l) {
            nombre.setText(l.planta());
            detalle.setText(l.cultivo() + " · " + l.ubicacion());
            medidor.mostrar(l.humedad(), l.humedadMinima());
            minima.setText("Mínimo " + l.humedadMinima() + " %");
            if (!l.tieneSensor()) {
                sensor.setText("Sin sensor instalado");
                voltaje.setText("—");
                ponerChip("Sin sensor", "chip-gris");
            } else {
                sensor.setText(l.codigoSensor() + " · capacitivo");
                voltaje.setText(String.format(Locale.US, "Salida %.2f V", l.voltaje()));
                if (l.necesitaRiego()) {
                    ponerChip("Necesita riego", "chip-regar");
                } else {
                    ponerChip("Humedad OK", "chip-ok");
                }
            }
            valvula(false);
        }

        void abrirValvula() {
            ponerChip("Regando…", "chip-regando");
            valvula(true);
            animarGotas();
        }

        private void valvula(boolean abierta) {
            valvula.setText(abierta
                    ? "Válvula abierta · " + SimuladorIoTService.DOSIS_RIEGO_ML + " ml"
                    : "Válvula cerrada");
            valvula.setGraphic(Iconos.crear(abierta ? "mdi2v-valve-open" : "mdi2v-valve-closed", 15));
            valvula.getStyleClass().removeAll("valvula-abierta");
            if (abierta) valvula.getStyleClass().add("valvula-abierta");
        }

        private void ponerChip(String texto, String clase) {
            chip.setText(texto);
            chip.getStyleClass().removeAll("chip-ok", "chip-regar", "chip-regando", "chip-gris");
            chip.getStyleClass().add(clase);
            if (latido != null) {
                latido.stop();
                chip.setScaleX(1);
                chip.setScaleY(1);
                latido = null;
            }
            if ("chip-regar".equals(clase)) {
                latido = Animaciones.latir(chip, 1.08, 0.55);
            }
        }

        /** Gotas de agua cayendo sobre el medidor mientras la valvula esta abierta. */
        private void animarGotas() {
            gotas.getChildren().clear();
            List<Animation> caidas = new ArrayList<>();
            double[] posiciones = {38, 60, 82};
            for (int i = 0; i < posiciones.length; i++) {
                Circle gota = new Circle(4.5);
                gota.getStyleClass().add("gota");
                gota.setLayoutX(posiciones[i]);
                gota.setLayoutY(6);
                gota.setOpacity(0);
                gotas.getChildren().add(gota);

                TranslateTransition caer = new TranslateTransition(Duration.millis(520), gota);
                caer.setFromY(0);
                caer.setToY(70);
                caer.setInterpolator(Interpolator.EASE_IN);
                FadeTransition aparecer = new FadeTransition(Duration.millis(520), gota);
                aparecer.setFromValue(1);
                aparecer.setToValue(0);
                ParallelTransition gotaCae = new ParallelTransition(caer, aparecer);
                gotaCae.setCycleCount(3);
                SequentialTransition conRetraso = new SequentialTransition(
                        new PauseTransition(Duration.millis(i * 150)), gotaCae);
                caidas.add(conRetraso);
            }
            caidas.forEach(Animation::play);
        }
    }

    // ---------------------------------------------------------------- tanque y energia

    private void armarTanque() {
        Rectangle agua = new Rectangle(58, 0);
        agua.getStyleClass().add("tanque-agua");
        agua.heightProperty().bind(nivelAgua.multiply(ALTO_TANQUE));
        StackPane.setAlignment(agua, javafx.geometry.Pos.BOTTOM_CENTER);
        Rectangle vidrio = new Rectangle(64, ALTO_TANQUE + 6);
        vidrio.getStyleClass().add("tanque-vidrio");
        tanque.getChildren().addAll(agua, vidrio);
        tanque.setMinSize(64, ALTO_TANQUE + 6);
        tanque.setMaxSize(64, ALTO_TANQUE + 6);
        // Brillo que sube y baja sobre el agua para que se vea "viva"
        FadeTransition brillo = new FadeTransition(Duration.seconds(1.6), agua);
        brillo.setFromValue(0.82);
        brillo.setToValue(1);
        brillo.setAutoReverse(true);
        brillo.setCycleCount(Animation.INDEFINITE);
        brillo.play();
    }

    private void armarBateria() {
        Rectangle fondo = new Rectangle(ANCHO_BATERIA, 12);
        fondo.getStyleClass().add("bateria-fondo");
        Rectangle carga = new Rectangle(0, 12);
        carga.getStyleClass().add("bateria-carga");
        carga.widthProperty().bind(cargaBateria.multiply(ANCHO_BATERIA));
        StackPane.setAlignment(carga, javafx.geometry.Pos.CENTER_LEFT);
        barraBateria.getChildren().addAll(fondo, carga);
        barraBateria.setMaxWidth(ANCHO_BATERIA);
    }

    private void actualizarTanque() {
        double fraccion = simulador.getNivelTanqueMl() / (double) SimuladorIoTService.CAPACIDAD_TANQUE_ML;
        new Timeline(new KeyFrame(Duration.millis(800), new KeyValue(nivelAgua, fraccion, Interpolator.EASE_BOTH))).play();
        lblTanque.setText(String.format(Locale.US, "%.1f / %d L",
                simulador.getNivelTanqueMl() / 1000.0, SimuladorIoTService.CAPACIDAD_TANQUE_ML / 1000));
    }

    private void actualizarEnergia() {
        double potencia = simulador.potenciaSolar(LocalTime.now());
        lblPotencia.setText(potencia > 0
                ? String.format(Locale.US, "%.1f W", potencia)
                : "0 W · de noche");
        double bateria = simulador.getBateriaPorcentaje();
        new Timeline(new KeyFrame(Duration.millis(800), new KeyValue(cargaBateria, bateria / 100, Interpolator.EASE_BOTH))).play();
        lblBateria.setText(String.format(Locale.US, "Batería %.0f %%", bateria));
        actualizarTanque();
    }

    // ---------------------------------------------------------------- ayudantes

    /** Solo las acciones que pide la persona desactivan los botones (el ciclo de fondo no). */
    private void accionManual(boolean valor) {
        accionManual = valor;
        btnLeer.setDisable(valor);
        btnSimular.setDisable(valor);
    }

    private void terminarCiclo(boolean manual) {
        if (manual) {
            accionManual(false);
        } else {
            cicloEnCurso = false;
        }
    }

    private static void iniciar(Task<?> tarea) {
        Thread hilo = new Thread(tarea, "plantbee-monitoreo");
        hilo.setDaemon(true);
        hilo.start();
    }

    private static Exception comoException(Throwable causa) {
        return causa instanceof Exception e ? e : new RuntimeException(causa);
    }

    private record ResultadoCiclo(List<Long> regadas, List<Lectura> lecturas) {
    }
}

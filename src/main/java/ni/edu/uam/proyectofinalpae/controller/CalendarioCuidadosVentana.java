package ni.edu.uam.proyectofinalpae.controller;

import javafx.beans.value.ChangeListener;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import ni.edu.uam.proyectofinalpae.api.HttpStatusException;
import ni.edu.uam.proyectofinalpae.dto.ActividadCuidado;
import ni.edu.uam.proyectofinalpae.repository.CuidadosApiRepository;
import ni.edu.uam.proyectofinalpae.repository.OpenMeteoApiRepository;
import ni.edu.uam.proyectofinalpae.repository.OpenMeteoApiRepository.Pronostico;
import ni.edu.uam.proyectofinalpae.repository.OpenMeteoApiRepository.PronosticoConsultado;
import ni.edu.uam.proyectofinalpae.util.Iconos;
import ni.edu.uam.proyectofinalpae.util.LogoPlantBee;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static ni.edu.uam.proyectofinalpae.dto.ActividadCuidado.*;

final class CalendarioCuidadosVentana {
    private static final Locale ESPANOL = Locale.forLanguageTag("es-NI");
    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MMMM 'de' uuuu", ESPANOL);
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ESPANOL);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    private static final DateTimeFormatter CONSULTA = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss");

    private final Stage ventana;
    private final CuidadosApiRepository cuidados;
    private final OpenMeteoApiRepository clima;
    private final Label tituloMes = new Label();
    private final Label tituloFecha = new Label();
    private final Label lblPronostico = new Label();
    private final Label lblSugerencia = new Label();
    private final GridPane cuadricula = new GridPane();
    private final VBox detalle = new VBox(8);
    private final ScrollPane desplazamiento = new ScrollPane(detalle);
    private final ChangeListener<Number> alCambiar = (obs, anterior, actual) -> recargar();
    private LocalDate seleccion;
    private YearMonth mes;
    private Map<String, List<ActividadCuidado>> actividades = Map.of();
    private Task<List<ActividadCuidado>> carga;
    private Task<PronosticoConsultado> cargaPronostico;
    private PronosticoConsultado pronosticoConsultado;
    private String mensajePronostico;
    private String mensaje;

    CalendarioCuidadosVentana(Window propietario) {
        this(new Stage(), propietario, new CuidadosApiRepository(), new OpenMeteoApiRepository());
    }

    CalendarioCuidadosVentana(Stage ventana, Window propietario, CuidadosApiRepository cuidados,
                              OpenMeteoApiRepository clima) {
        this.ventana = ventana;
        this.cuidados = cuidados;
        this.clima = clima;
        ventana.initOwner(propietario);
        ventana.setTitle("Calendario de cuidados");
        ventana.getIcons().setAll(LogoPlantBee.icono(64));

        var icono = Iconos.crear("mdi2c-calendar", 22);
        icono.getStyleClass().add("calendario-icono");
        Label titulo = new Label("Calendario de cuidados", icono);
        titulo.setMaxWidth(Double.MAX_VALUE);
        titulo.getStyleClass().add("calendario-titulo");

        tituloMes.getStyleClass().add("calendario-mes");
        tituloMes.setMinWidth(160);
        tituloMes.setAlignment(Pos.CENTER);
        Button hoy = boton("Hoy", "Ir a la fecha actual", this::irAHoy);
        hoy.getStyleClass().add("calendario-boton-hoy");
        HBox navegacion = new HBox(6,
                boton("«", "Año anterior", () -> cambiarMes(mes.minusYears(1))),
                boton("‹", "Mes anterior", () -> cambiarMes(mes.minusMonths(1))),
                tituloMes,
                boton("›", "Mes siguiente", () -> cambiarMes(mes.plusMonths(1))),
                boton("»", "Año siguiente", () -> cambiarMes(mes.plusYears(1))), hoy);
        navegacion.setAlignment(Pos.CENTER);
        cuadricula.setHgap(5);
        cuadricula.setVgap(4);
        for (int i = 0; i < 7; i++) {
            ColumnConstraints columna = new ColumnConstraints();
            columna.setPercentWidth(100.0 / 7);
            cuadricula.getColumnConstraints().add(columna);
        }

        FlowPane leyenda = new FlowPane(6, 4,
                etiqueta(ATENCION), etiqueta(REVISION), etiqueta(REGISTRADO), etiqueta(INSUFICIENTE));
        VBox panelMes = new VBox(8, navegacion, cuadricula, leyenda);
        panelMes.getStyleClass().add("calendario-panel");

        tituloFecha.getStyleClass().add("calendario-fecha");
        lblPronostico.getStyleClass().add("calendario-pronostico");
        lblPronostico.setWrapText(true);
        lblSugerencia.getStyleClass().add("calendario-sugerencia");
        lblSugerencia.setWrapText(true);
        lblSugerencia.managedProperty().bind(lblSugerencia.visibleProperty());
        Button actualizarPronostico = boton("Actualizar clima", "Consultar Open-Meteo", this::cargarPronostico);
        HBox cabeceraPronostico = new HBox(8, lblPronostico, actualizarPronostico);
        HBox.setHgrow(lblPronostico, Priority.ALWAYS);
        VBox bloquePronostico = new VBox(3, cabeceraPronostico, lblSugerencia);
        desplazamiento.setFitToWidth(true);
        desplazamiento.setMinViewportHeight(108);
        VBox.setVgrow(desplazamiento, Priority.ALWAYS);
        VBox panelFecha = new VBox(6, tituloFecha, bloquePronostico, desplazamiento);
        panelFecha.getStyleClass().add("calendario-panel");
        VBox.setVgrow(panelFecha, Priority.ALWAYS);
        Button cerrar = boton("Cerrar", "Cerrar calendario", ventana::close);
        cerrar.getStyleClass().add("calendario-cerrar");
        cerrar.setCancelButton(true);
        HBox pie = new HBox(cerrar);
        pie.setAlignment(Pos.CENTER_RIGHT);

        VBox raiz = new VBox(10, titulo, panelMes, panelFecha, pie);
        raiz.getStyleClass().add("calendario-cuidados");
        Scene escena = new Scene(raiz, 700, 670);
        escena.getStylesheets().add(getClass().getResource("/css/calendario-cuidados.css").toExternalForm());
        ventana.setMinWidth(575);
        ventana.setMinHeight(575);
        ventana.setScene(escena);
        ventana.setOnShown(evento -> CambiosCuidados.version().addListener(alCambiar));
        ventana.setOnHidden(evento -> {
            CambiosCuidados.version().removeListener(alCambiar);
            cancelarCarga();
            if (cargaPronostico != null) {
                cargaPronostico.cancel();
                cargaPronostico = null;
            }
        });
    }

    void mostrar() {
        if (ventana.isShowing()) {
            ventana.toFront();
            return;
        }
        irAHoy();
        cargarPronostico();
        ventana.show();
    }

    private void irAHoy() {
        seleccion = LocalDate.now();
        mes = YearMonth.from(seleccion);
        recargar();
    }

    private void cambiarMes(YearMonth nuevo) {
        if (nuevo.getYear() < 1 || nuevo.getYear() > 9999) {
            return;
        }
        mes = nuevo;
        seleccion = mes.atDay(Math.min(seleccion.getDayOfMonth(), mes.lengthOfMonth()));
        recargar();
    }

    private void recargar() {
        cancelarCarga();
        YearMonth solicitado = mes;
        actividades = Map.of();
        mensaje = "Cargando cuidados…";
        dibujar();
        carga = new Task<>() {
            @Override
            protected List<ActividadCuidado> call() throws Exception {
                return cuidados.paraMes(solicitado);
            }
        };
        Task<List<ActividadCuidado>> tarea = carga;
        tarea.setOnSucceeded(evento -> {
            if (carga == tarea) {
                actividades = tarea.getValue().stream().collect(Collectors.groupingBy(ActividadCuidado::fecha));
                mensaje = null;
                dibujar();
            }
        });
        tarea.setOnFailed(evento -> {
            if (carga == tarea) {
                mensaje = "API de cuidados: " + detalleError(tarea.getException());
                dibujar();
            }
        });
        Thread hilo = new Thread(tarea, "plantbee-calendario");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void cargarPronostico() {
        if (cargaPronostico != null) cargaPronostico.cancel();
        pronosticoConsultado = null;
        mensajePronostico = "Consultando Open-Meteo…";
        mostrarPronostico();
        Task<PronosticoConsultado> tarea = new Task<>() {
            @Override
            protected PronosticoConsultado call() throws Exception {
                return clima.consultarManagua();
            }
        };
        cargaPronostico = tarea;
        tarea.setOnSucceeded(evento -> {
            if (cargaPronostico == tarea) {
                pronosticoConsultado = tarea.getValue();
                mensajePronostico = null;
                mostrarPronostico();
            }
        });
        tarea.setOnFailed(evento -> {
            if (cargaPronostico == tarea) {
                mensajePronostico = "Pronóstico no disponible en Open-Meteo. " + detalleError(tarea.getException());
                mostrarPronostico();
            }
        });
        Thread hilo = new Thread(tarea, "plantbee-pronostico");
        hilo.setDaemon(true);
        hilo.start();
    }

    private static String detalleError(Throwable error) {
        if (error instanceof HttpTimeoutException) return "Se agotó el tiempo de espera.";
        if (error instanceof HttpStatusException) return error.getMessage();
        if (error instanceof ConnectException || error instanceof UnknownHostException) {
            return "No se pudo conectar.";
        }
        return error.getMessage() == null ? "Error de comunicación." : error.getMessage();
    }

    private void mostrarPronostico() {
        String prefijo = "Managua · ";
        Pronostico pronostico = pronosticoConsultado == null ? null : pronosticoConsultado.dias().get(seleccion);
        String actualizacion = pronosticoConsultado == null ? ""
                : "\nActualizado en PlantBee: " + pronosticoConsultado.consultadoEn().format(CONSULTA)
                + " (hora de Managua)";
        String sugerencia = mensajePronostico == null && !seleccion.isBefore(LocalDate.now())
                ? sugerenciaMeteorologica(pronostico) : "";
        lblSugerencia.setText(sugerencia);
        lblSugerencia.setVisible(!sugerencia.isEmpty());
        if (mensajePronostico != null) {
            lblPronostico.setText(prefijo + mensajePronostico);
        } else if (pronostico == null) {
            lblPronostico.setText(prefijo + "Sin pronóstico de Open-Meteo para esta fecha." + actualizacion);
        } else {
            String temperatura = pronostico.temperaturaMaxima() == null ? "sin datos"
                    : String.format(ESPANOL, "%.1f °C", pronostico.temperaturaMaxima());
            String lluvia = pronostico.probabilidadPrecipitacion() == null ? "sin datos"
                    : pronostico.probabilidadPrecipitacion() + " %";
            lblPronostico.setText(prefijo + "Máx. " + temperatura + " · Prob. máx. de lluvia " + lluvia
                    + " · Open-Meteo" + actualizacion);
        }
    }

    private static String sugerenciaMeteorologica(Pronostico pronostico) {
        if (pronostico == null) {
            return "";
        }
        Integer lluvia = pronostico.probabilidadPrecipitacion();
        if (lluvia != null && lluvia >= 60) {
            return "Puede llover. Si tu planta está al aire libre, revisa la tierra antes de regar.";
        }
        Double temperatura = pronostico.temperaturaMaxima();
        if (temperatura != null && temperatura >= 34) {
            return "Se espera calor. Revisa la humedad temprano; si la tierra sigue húmeda, espera.";
        }
        return "";
    }

    private void cancelarCarga() {
        if (carga != null) {
            carga.cancel();
            carga = null;
        }
    }

    private void dibujar() {
        tituloMes.setText(mes.format(MES));
        cuadricula.getChildren().clear();
        String[] semana = {"Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"};
        for (int i = 0; i < semana.length; i++) {
            Label encabezado = new Label(semana[i]);
            encabezado.getStyleClass().add("calendario-semana");
            encabezado.setMaxWidth(Double.MAX_VALUE);
            encabezado.setAlignment(Pos.CENTER);
            cuadricula.add(encabezado, i, 0);
        }
        int inicio = mes.atDay(1).getDayOfWeek().getValue() - 1;
        LocalDate hoy = LocalDate.now();
        for (int dia = 1; dia <= mes.lengthOfMonth(); dia++) {
            LocalDate fecha = mes.atDay(dia);
            List<String> estados = actividades.getOrDefault(fecha.toString(), List.of()).stream()
                    .map(ActividadCuidado::estado).distinct().toList();
            String texto = fecha.format(DIA) + (fecha.equals(hoy) ? " · Hoy" : "")
                    + (estados.isEmpty() ? "" : " · " + estados.stream()
                    .map(ActividadCuidado::nombreEstado).collect(Collectors.joining(", ")));
            Button boton = boton(Integer.toString(dia), texto, () -> {
                seleccion = fecha;
                dibujar();
                cuadricula.getChildren().stream()
                        .filter(nodo -> fecha.equals(nodo.getUserData())).findFirst()
                        .ifPresent(javafx.scene.Node::requestFocus);
            });
            boton.setUserData(fecha);
            boton.getStyleClass().add("calendario-dia");
            boton.setMaxWidth(Double.MAX_VALUE);
            if (fecha.equals(hoy)) {
                boton.getStyleClass().add("calendario-hoy");
            }
            if (fecha.equals(seleccion)) {
                boton.getStyleClass().add("calendario-seleccion");
            }
            if (!estados.isEmpty()) {
                HBox marcas = new HBox(3);
                for (String estado : estados) {
                    Label marca = new Label(simboloEstado(estado));
                    marca.getStyleClass().addAll("calendario-marca", clase(estado));
                    marcas.getChildren().add(marca);
                }
                boton.setGraphic(marcas);
                boton.setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
            }
            int posicion = inicio + dia - 1;
            cuadricula.add(boton, posicion % 7, posicion / 7 + 1);
        }
        mostrarDetalle();
    }

    private void mostrarDetalle() {
        tituloFecha.setText(seleccion.format(DIA));
        mostrarPronostico();
        detalle.getChildren().clear();
        desplazamiento.setVvalue(0);
        if (mensaje != null) {
            detalle.getChildren().add(mensajeDetalle(mensaje));
            if (carga != null && carga.getState() == javafx.concurrent.Worker.State.FAILED) {
                detalle.getChildren().add(boton("Reintentar", "Volver a cargar los cuidados", this::recargar));
            }
            return;
        }
        List<ActividadCuidado> delDia = actividades.getOrDefault(seleccion.toString(), List.of());
        if (delDia.isEmpty()) {
            detalle.getChildren().add(mensajeDetalle("No hay cuidados ni riegos registrados para esta fecha."));
        }
        for (ActividadCuidado actividad : delDia) {
            detalle.getChildren().add(tarjeta(actividad));
        }
    }

    private static Label mensajeDetalle(String texto) {
        Label etiqueta = new Label(texto);
        etiqueta.getStyleClass().add("calendario-mensaje");
        etiqueta.setWrapText(true);
        return etiqueta;
    }

    private static VBox tarjeta(ActividadCuidado actividad) {
        String nombre = actividad.planta();
        String cultivo = actividad.cultivo();
        Label planta = new Label(nombre.strip().equalsIgnoreCase(cultivo.strip())
                ? nombre : nombre + " · " + cultivo);
        planta.getStyleClass().add("calendario-mes");
        String humedad = actividad.humedadActual() == null
                ? "Lectura no disponible" : "Humedad actual: " + actividad.humedadActual() + " %";
        Label informacion = new Label(actividad.colmena() + " · Módulo " + actividad.modulo()
                + "\nEstado de la planta: " + actividad.estadoPlanta()
                + "\n" + actividad.descripcion()
                + "\n" + actividad.motivo()
                + "\nÚltimo riego: " + fechaVisible(actividad.ultimoRiego())
                + "   ·   Próxima revisión: " + fechaVisible(actividad.proximaRevision())
                + "\n" + humedad + "   ·   Mínimo del cultivo: " + actividad.humedadMinima() + " %");
        informacion.setWrapText(true);
        planta.setWrapText(true);
        VBox tarjeta = new VBox(5, planta, etiqueta(actividad.estado()), informacion);
        tarjeta.getStyleClass().addAll("calendario-tarjeta", clase(actividad.estado()));
        return tarjeta;
    }

    private static String fechaVisible(String iso) {
        return iso == null ? "Sin datos" : LocalDate.parse(iso).format(FECHA);
    }

    private static Label etiqueta(String estado) {
        Label etiqueta = new Label(simboloEstado(estado) + "  " + nombreEstado(estado));
        etiqueta.setAccessibleText(nombreEstado(estado));
        etiqueta.getStyleClass().addAll("calendario-etiqueta", clase(estado));
        return etiqueta;
    }

    private static String simboloEstado(String estado) {
        return switch (estado) {
            case REGISTRADO -> "✓";
            case REVISION -> "◷";
            default -> "●";
        };
    }

    private static String clase(String estado) {
        return switch (estado) {
            case ATENCION -> "atencion";
            case REVISION -> "revision";
            case REGISTRADO -> "registrado";
            default -> "insuficiente";
        };
    }

    private static Button boton(String texto, String descripcion, Runnable accion) {
        Button boton = new Button(texto);
        boton.setAccessibleText(descripcion);
        boton.setTooltip(new Tooltip(descripcion));
        boton.setOnAction(evento -> accion.run());
        return boton;
    }
}

package ni.edu.uam.proyectofinalpae.util;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.Region;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Map;

/**
 * Agrega iconos vectoriales (Material Design, libreria Ikonli) a las pestanas y botones.
 * Se hace en un solo lugar para que todas las pantallas usen los mismos iconos.
 */
public final class Iconos {

    /** Texto del boton o pestana -> codigo del icono. */
    private static final Map<String, String> ICONOS = Map.ofEntries(
            // Pestanas
            Map.entry("Usuarios", "mdi2a-account-group"),
            Map.entry("Colmenas", "mdi2h-hexagon-multiple"),
            Map.entry("Plantas", "mdi2s-sprout"),
            Map.entry("Riegos y alertas", "mdi2w-water"),
            Map.entry("Reportes", "mdi2f-file-chart"),
            Map.entry("Inicio", "mdi2h-home"),
            // Botones CRUD
            Map.entry("Nuevo", "mdi2p-plus"),
            Map.entry("Guardar", "mdi2c-content-save"),
            Map.entry("Eliminar", "mdi2d-delete"),
            Map.entry("Eliminar riego", "mdi2d-delete"),
            Map.entry("Eliminar alerta", "mdi2d-delete"),
            Map.entry("Recargar", "mdi2r-refresh"),
            Map.entry("Ver todos", "mdi2f-filter-variant"),
            Map.entry("Ver todas", "mdi2f-filter-variant"),
            // Acciones de PlantBee
            Map.entry("Simular lectura de sensores", "mdi2w-water-percent"),
            Map.entry("Registrar riego", "mdi2w-weather-rainy"),
            Map.entry("Revisar sensores", "mdi2b-bell-ring"),
            Map.entry("Marcar como atendida", "mdi2c-check-circle"),
            Map.entry("Consultar Wikipedia", "mdi2w-wikipedia"),
            // Reportes
            Map.entry("Generar reporte", "mdi2f-file-eye"),
            Map.entry("Exportar PDF", "mdi2f-file-pdf-box"),
            Map.entry("Exportar Excel", "mdi2m-microsoft-excel")
    );

    private Iconos() {
    }

    /** Crea un icono con el tamano indicado. */
    public static FontIcon crear(String codigo, int tamano) {
        FontIcon icono = new FontIcon(codigo);
        icono.setIconSize(tamano);
        return icono;
    }

    /** Recorre la pantalla y le pone icono a cada pestana y boton conocido. */
    public static void decorar(Parent raiz) {
        for (Node nodo : raiz.lookupAll(".tab-pane")) {
            for (Tab tab : ((TabPane) nodo).getTabs()) {
                String codigo = ICONOS.get(tab.getText());
                if (codigo != null && tab.getGraphic() == null) {
                    FontIcon icono = crear(codigo, 18);
                    icono.getStyleClass().add("tab-icon");
                    tab.setGraphic(icono);
                }
                // El contenido de las pestanas no visibles todavia no es hijo del TabPane
                if (tab.getContent() instanceof Parent contenido) {
                    decorar(contenido);
                }
            }
        }
        for (Node nodo : raiz.lookupAll(".table-view")) {
            TableView<?> tabla = (TableView<?>) nodo;
            if (tabla.getPlaceholder() == null) {
                Label vacio = new Label("Todavía no hay registros", crear("mdi2s-sprout-outline", 22));
                vacio.getStyleClass().add("tabla-vacia");
                tabla.setPlaceholder(vacio);
            }
        }
        for (Node nodo : raiz.lookupAll(".button")) {
            Button boton = (Button) nodo;
            String codigo = ICONOS.get(boton.getText());
            if (codigo != null && boton.getGraphic() == null) {
                FontIcon icono = crear(codigo, 17);
                // El icono toma el mismo color que el texto de cada boton
                icono.iconColorProperty().bind(boton.textFillProperty());
                boton.setGraphic(icono);
                boton.setGraphicTextGap(8);
                // Con icono el boton necesita mas ancho: evita que el texto se corte con "..."
                boton.setMinWidth(Region.USE_PREF_SIZE);
            }
        }
    }
}

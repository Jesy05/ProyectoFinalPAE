package ni.edu.uam.proyectofinalpae.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Filtra los datos de una tabla sin alterar su origen ni el orden elegido por el usuario. */
final class BusquedaTabla {
    private static final Pattern MARCAS = Pattern.compile("\\p{M}+");

    private BusquedaTabla() {}

    static <T> ObservableList<T> instalar(TableView<T> tabla, TextField entrada, Function<T, String> campos) {
        ObservableList<T> origen = FXCollections.observableArrayList();
        var filtrados = new FilteredList<>(origen);
        var ordenados = new SortedList<>(filtrados);
        ordenados.comparatorProperty().bind(tabla.comparatorProperty());
        tabla.setItems(ordenados);
        entrada.textProperty().addListener((obs, anterior, actual) -> {
            String termino = normalizar(actual);
            if (termino.isBlank()) {
                filtrados.setPredicate(null);
                return;
            }
            String[] palabras = termino.split("\\s+");
            filtrados.setPredicate(fila -> {
                String texto = normalizar(campos.apply(fila));
                for (String palabra : palabras) {
                    if (!texto.contains(palabra)) return false;
                }
                return true;
            });
        });
        return origen;
    }

    static String campos(String... valores) {
        var texto = new StringBuilder();
        for (String valor : valores) {
            if (valor != null) texto.append(valor).append(' ');
        }
        return texto.toString();
    }

    private static String normalizar(String valor) {
        if (valor == null) return "";
        return MARCAS.matcher(Normalizer.normalize(valor.strip(), Normalizer.Form.NFD))
                .replaceAll("").toLowerCase(Locale.ROOT);
    }
}

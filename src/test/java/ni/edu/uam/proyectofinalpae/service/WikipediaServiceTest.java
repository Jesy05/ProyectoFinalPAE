package ni.edu.uam.proyectofinalpae.service;

import ni.edu.uam.proyectofinalpae.dto.WikipediaSummary;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WikipediaServiceTest {
    @Test
    void recortaElNombreAntesDeConsultar() throws Exception {
        AtomicReference<String> consultado = new AtomicReference<>();
        WikipediaService service = new WikipediaService(nombre -> {
            consultado.set(nombre);
            return new WikipediaSummary(nombre, "Descripcion", "Resumen", "https://example.test");
        });

        WikipediaSummary result = service.consultar("  Tomate  ");

        assertEquals("Tomate", consultado.get());
        assertEquals("Tomate", result.title());
    }

    @Test
    void rechazaNombreVacio() {
        WikipediaService service = new WikipediaService(nombre -> null);
        assertThrows(IllegalArgumentException.class, () -> service.consultar(""));
    }
}

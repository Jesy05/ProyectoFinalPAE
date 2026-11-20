package ni.edu.uam.proyectofinalpae.repository;

import ni.edu.uam.proyectofinalpae.dto.WikipediaSummary;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WikipediaApiRepositoryTest {
    @Test
    void parseaResumenCompletoSinInternet() throws Exception {
        WikipediaApiRepository repository = new WikipediaApiRepository(uri -> {
            assertTrue(uri.toString().contains("wikipedia.org"));
            return """
                    {"title":"Albahaca","description":"Planta aromatica",
                     "extract":"La albahaca es una planta.",
                     "content_urls":{"desktop":{"page":"https://es.wikipedia.org/wiki/Albahaca"}}}
                    """;
        });

        WikipediaSummary result = repository.buscarResumen("Albahaca");

        assertEquals("Albahaca", result.title());
        assertEquals("Planta aromatica", result.description());
        assertEquals("La albahaca es una planta.", result.extract());
        assertEquals("https://es.wikipedia.org/wiki/Albahaca", result.pageUrl());
    }

    @Test
    void codificaEspaciosEnLaConsulta() throws Exception {
        WikipediaApiRepository repository = new WikipediaApiRepository(uri -> {
            assertTrue(uri.toString().contains("Albahaca%20morada"));
            return "{\"title\":\"Albahaca morada\",\"extract\":\"Resumen\"}";
        });

        assertEquals("Albahaca morada", repository.buscarResumen("Albahaca morada").title());
    }

    @Test
    void rechazaConsultaVacia() {
        WikipediaApiRepository repository = new WikipediaApiRepository(uri -> "{}");
        assertThrows(IllegalArgumentException.class, () -> repository.buscarResumen(" "));
    }
}

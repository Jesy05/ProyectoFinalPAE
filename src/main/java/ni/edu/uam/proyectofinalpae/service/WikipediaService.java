package ni.edu.uam.proyectofinalpae.service;

import ni.edu.uam.proyectofinalpae.dto.WikipediaSummary;
import ni.edu.uam.proyectofinalpae.repository.WikipediaApiRepository;
import ni.edu.uam.proyectofinalpae.repository.WikipediaRepository;

import java.io.IOException;

public class WikipediaService {
    private final WikipediaRepository repository;

    public WikipediaService() {
        this(new WikipediaApiRepository());
    }

    public WikipediaService(WikipediaRepository repository) {
        this.repository = repository;
    }

    public WikipediaSummary consultar(String nombreCultivo)
            throws IOException, InterruptedException {
        if (nombreCultivo == null || nombreCultivo.isBlank()) {
            throw new IllegalArgumentException("Escriba un cultivo para consultar.");
        }
        return repository.buscarResumen(nombreCultivo.trim());
    }
}

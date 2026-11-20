package ni.edu.uam.proyectofinalpae.repository;

import ni.edu.uam.proyectofinalpae.dto.WikipediaSummary;

import java.io.IOException;

public interface WikipediaRepository {
    WikipediaSummary buscarResumen(String consulta)
            throws IOException, InterruptedException;
}

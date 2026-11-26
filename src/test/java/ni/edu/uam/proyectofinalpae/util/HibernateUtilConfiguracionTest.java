package ni.edu.uam.proyectofinalpae.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HibernateUtilConfiguracionTest {
    @TempDir Path directorio;

    @Test
    void permiteConfiguracionCompletaSinArchivoNiConexion() {
        var entorno = Map.of("PLANTBEE_DB_URL", "jdbc:postgresql://127.0.0.1/prueba",
                "PLANTBEE_DB_USER", "usuario_ficticio", "PLANTBEE_DB_PASSWORD", "valor_ficticio",
                "PLANTBEE_DB_CONFIG", "inexistente.properties");
        assertEquals("usuario_ficticio", HibernateUtil.cargarCredenciales(entorno, directorio).getProperty("db.user"));
    }

    @Test
    void aplicaVariablesSobreArchivoExternoYRechazaClavesFaltantes() throws Exception {
        Files.writeString(directorio.resolve("config.properties"), """
                db.url=jdbc:postgresql://127.0.0.1/prueba
                db.user=archivo
                db.password=valor_ficticio
                """);
        var entorno = Map.of("PLANTBEE_DB_CONFIG", "config.properties", "PLANTBEE_DB_USER", "entorno");
        assertEquals("entorno", HibernateUtil.cargarCredenciales(entorno, directorio).getProperty("db.user"));
        assertThrows(IllegalStateException.class, () -> HibernateUtil.cargarCredenciales(Map.of(), directorio));
    }
}

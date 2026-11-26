package ni.edu.uam.proyectofinalpae.util;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

public class HibernateUtil {
    private static SessionFactory factory;

    private HibernateUtil() {
    }

    public static synchronized SessionFactory getSessionFactory() {
        if (factory == null) {
            factory = construir();
        }
        return factory;
    }

    private static SessionFactory construir() {
        Properties db = cargarCredenciales();
        Configuration cfg = new Configuration().configure("hibernate.cfg.xml");
        cfg.setProperty("hibernate.connection.url", db.getProperty("db.url"));
        cfg.setProperty("hibernate.connection.username", db.getProperty("db.user"));
        cfg.setProperty("hibernate.connection.password", db.getProperty("db.password"));
        return cfg.buildSessionFactory();
    }

    private static Properties cargarCredenciales() {
        return cargarCredenciales(System.getenv(), Path.of("."));
    }

    static Properties cargarCredenciales(Map<String, String> entorno, Path directorio) {
        Properties props = new Properties();
        if (!(entorno.containsKey("PLANTBEE_DB_URL") && entorno.containsKey("PLANTBEE_DB_USER")
                && entorno.containsKey("PLANTBEE_DB_PASSWORD"))) {
            String archivo = entorno.get("PLANTBEE_DB_CONFIG");
            Path ruta = archivo == null || archivo.isBlank()
                    ? directorio.resolve("db.properties") : directorio.resolve(archivo);
            if ((archivo == null || archivo.isBlank()) && Files.notExists(ruta)) {
                ruta = directorio.resolve("src/main/resources/db.properties");
            }
            try (InputStream in = Files.newInputStream(ruta)) {
                props.load(in);
            } catch (IOException e) {
                throw new IllegalStateException("No se pudo leer la configuración externa de PostgreSQL.");
            }
        }
        aplicar(entorno, props, "PLANTBEE_DB_URL", "db.url");
        aplicar(entorno, props, "PLANTBEE_DB_USER", "db.user");
        aplicar(entorno, props, "PLANTBEE_DB_PASSWORD", "db.password");
        return props;
    }

    private static void aplicar(Map<String, String> entorno, Properties props, String variable, String clave) {
        String valor = entorno.getOrDefault(variable, props.getProperty(clave));
        if (valor == null || (!"db.password".equals(clave) && valor.isBlank())) {
            throw new IllegalStateException("Falta la configuración " + clave + ".");
        }
        props.setProperty(clave, valor);
    }

    public static synchronized void cerrar() {
        if (factory != null) {
            factory.close();
            factory = null;
        }
    }
}

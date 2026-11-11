package ni.edu.uam.proyectofinalpae.util;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

import java.io.IOException;
import java.io.InputStream;
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
        Properties props = new Properties();
        try (InputStream in = HibernateUtil.class.getResourceAsStream("/db.properties")) {
            if (in == null) {
                throw new IllegalStateException("Falta src/main/resources/db.properties. "
                        + "Copie db.properties.example con ese nombre y escriba su clave de PostgreSQL.");
            }
            props.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer db.properties", e);
        }
        return props;
    }

    public static synchronized void cerrar() {
        if (factory != null) {
            factory.close();
            factory = null;
        }
    }
}

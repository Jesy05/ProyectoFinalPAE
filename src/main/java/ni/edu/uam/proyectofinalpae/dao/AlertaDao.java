package ni.edu.uam.proyectofinalpae.dao;

import ni.edu.uam.proyectofinalpae.model.Alerta;
import ni.edu.uam.proyectofinalpae.model.Planta;

import java.util.List;

public class AlertaDao extends GenericDao<Alerta> {

    public AlertaDao() {
        super(Alerta.class);
    }

    /** HQL sobre entidades relacionadas: alertas sin atender con su planta y ubicacion. */
    public List<Alerta> listarPendientes() {
        return enSesion(s -> s.createQuery(
                        "SELECT a FROM Alerta a "
                                + "JOIN FETCH a.planta p "
                                + "JOIN FETCH p.cultivo "
                                + "JOIN FETCH p.modulo m "
                                + "JOIN FETCH m.colmena "
                                + "WHERE a.atendida = false "
                                + "ORDER BY a.fechaHora DESC", Alerta.class)
                .getResultList());
    }

    /**
     * Revisa los sensores y crea una alerta por cada planta con humedad baja
     * que todavia no tenga una alerta pendiente. Devuelve cuantas alertas nuevas creo.
     */
    public int generarAlertasHumedad() {
        return enTransaccion(s -> {
            List<Planta> secas = s.createQuery(
                            "SELECT p FROM Planta p "
                                    + "JOIN FETCH p.cultivo cu "
                                    + "JOIN FETCH p.sensor se "
                                    + "WHERE se.humedadActual < cu.humedadMinima "
                                    + "AND NOT EXISTS (SELECT a.id FROM Alerta a "
                                    + "                WHERE a.planta = p AND a.atendida = false)", Planta.class)
                    .getResultList();
            for (Planta p : secas) {
                String mensaje = p.getNombreVisible() + " tiene " + p.getSensor().getHumedadActual()
                        + " % de humedad (minimo " + p.getCultivo().getHumedadMinima() + " %). Necesita riego.";
                s.persist(new Alerta(p, "Humedad baja", mensaje));
            }
            return secas.size();
        });
    }

    public void marcarAtendida(Long alertaId) {
        enTransaccion(s -> {
            Alerta alerta = s.find(Alerta.class, alertaId);
            if (alerta != null) {
                alerta.setAtendida(true);   // Hibernate detecta el cambio y hace UPDATE al hacer commit
            }
            return null;
        });
    }
}

package ni.edu.uam.proyectofinalpae.dao;

import ni.edu.uam.proyectofinalpae.model.Alerta;
import ni.edu.uam.proyectofinalpae.model.Planta;

import org.hibernate.Session;

import java.util.List;
import java.util.function.BiConsumer;

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
                                + "LEFT JOIN FETCH p.sensor "
                                + "JOIN FETCH p.modulo m "
                                + "JOIN FETCH m.colmena "
                                + "WHERE a.atendida = false "
                                + "ORDER BY a.fechaHora DESC", Alerta.class)
                .getResultList());
    }

    /** Concilia las lecturas y devuelve cuántas alertas nuevas se crearon. */
    public int generarAlertasHumedad() {
        return enTransaccion(s -> {
            int nuevas = 0;
            for (Long id : bloquearPlantas(s)) {
                nuevas += conciliarHumedad(s, s.find(Planta.class, id));
            }
            return nuevas;
        });
    }

    // Solo se bloquea la fila de planta, antes de leer sensores o alertas.
    static boolean bloquearPlanta(Session s, Long id) {
        return !s.createNativeQuery("SELECT id FROM planta WHERE id = :id FOR UPDATE", Long.class)
                .setParameter("id", id)
                .getResultList().isEmpty();
    }

    static List<Long> bloquearPlantas(Session s) {
        return s.createNativeQuery("SELECT id FROM planta ORDER BY id FOR UPDATE", Long.class)
                .getResultList();
    }

    /** Requiere el bloqueo de la planta existente o una planta creada en esta transacción. */
    static int conciliarHumedad(Session s, Planta planta) {
        var sensor = planta.getSensor();
        if (sensor == null || sensor.getHumedadActual() == null) return 0;

        var pendientes = s.createQuery("""
                        FROM Alerta a WHERE a.planta = :planta AND a.atendida = false AND a.tipo = :tipo
                        ORDER BY a.fechaHora, a.id
                        """, Alerta.class)
                .setParameter("planta", planta)
                .setParameter("tipo", Alerta.HUMEDAD_BAJA)
                .getResultList();
        boolean baja = sensor.necesitaRiego();
        if (baja && pendientes.isEmpty()) {
            var alerta = new Alerta(planta, Alerta.HUMEDAD_BAJA, null);
            alerta.setMensaje(alerta.getMensajeVigente());
            s.persist(alerta);
            return 1;
        }
        // Conserva el primer aviso si había duplicados históricos pendientes.
        pendientes.stream().skip(baja ? 1 : 0).forEach(a -> a.setAtendida(true));
        return 0;
    }

    public void marcarAtendida(Long alertaId) {
        modificarAlerta(alertaId, (s, alerta) -> alerta.setAtendida(true));
    }

    @Override
    public void eliminar(Long alertaId) {
        modificarAlerta(alertaId, Session::remove);
    }

    private void modificarAlerta(Long alertaId, BiConsumer<Session, Alerta> cambio) {
        enTransaccion(s -> {
            Long plantaId = s.createQuery("SELECT a.planta.id FROM Alerta a WHERE a.id = :id", Long.class)
                    .setParameter("id", alertaId).uniqueResult();
            if (plantaId != null && bloquearPlanta(s, plantaId)) {
                Alerta alerta = s.find(Alerta.class, alertaId);
                if (alerta != null) cambio.accept(s, alerta);
            }
            return null;
        });
    }
}

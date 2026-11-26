package ni.edu.uam.proyectofinalpae.dao;

import ni.edu.uam.proyectofinalpae.model.Planta;
import ni.edu.uam.proyectofinalpae.model.Riego;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RiegoDao extends GenericDao<Riego> {

    private static final String HISTORIAL = """
            SELECT r FROM Riego r
            JOIN FETCH r.planta p
            JOIN FETCH p.cultivo
            JOIN FETCH p.modulo m
            JOIN FETCH m.colmena
            """;

    public RiegoDao() {
        super(Riego.class);
    }

    /** Guarda el riego, actualiza la lectura a 90 % y concilia en una sola transacción. */
    public void registrar(Riego riego) {
        enTransaccion(s -> {
            Long plantaId = riego.getPlanta().getId();
            if (!AlertaDao.bloquearPlanta(s, plantaId)) {
                throw new IllegalArgumentException("La planta ya no existe.");
            }
            Planta planta = s.find(Planta.class, plantaId);
            s.persist(riego);
            if (planta.getSensor() != null) {
                planta.getSensor().setHumedadActual(90);
                AlertaDao.conciliarHumedad(s, planta);
            }
            return null;
        });
    }

    /** HQL sobre entidades relacionadas: historial con planta, cultivo, modulo y colmena. */
    @Override
    public List<Riego> listar() {
        return enSesion(s -> s.createQuery(HISTORIAL + "ORDER BY r.fechaHora DESC", Riego.class)
                .getResultList());
    }

    public List<Riego> listarEntre(LocalDateTime inicio, LocalDateTime fin) {
        return enSesion(s -> s.createQuery(HISTORIAL
                        + "WHERE r.fechaHora >= :inicio AND r.fechaHora < :fin ORDER BY r.fechaHora DESC",
                        Riego.class)
                .setParameter("inicio", inicio)
                .setParameter("fin", fin)
                .getResultList());
    }

    public Map<Long, LocalDateTime> ultimosHasta(LocalDateTime corte) {
        return enSesion(s -> {
            var filas = s.createQuery("""
                            SELECT r.planta.id, MAX(r.fechaHora)
                            FROM Riego r WHERE r.fechaHora <= :corte
                            GROUP BY r.planta.id
                            """, Object[].class)
                    .setParameter("corte", corte)
                    .getResultList();
            Map<Long, LocalDateTime> ultimos = new HashMap<>();
            for (Object[] fila : filas) {
                ultimos.put((Long) fila[0], (LocalDateTime) fila[1]);
            }
            return ultimos;
        });
    }

    /** HQL con agregacion: litros usados por cada origen de agua (lluvia, gris filtrada, potable). */
    public List<Object[]> litrosPorOrigen() {
        return enSesion(s -> s.createQuery(
                        "SELECT r.origenAgua, SUM(r.cantidadMl) / 1000.0 FROM Riego r "
                                + "GROUP BY r.origenAgua ORDER BY r.origenAgua", Object[].class)
                .getResultList());
    }
}

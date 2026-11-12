package ni.edu.uam.proyectofinalpae.dao;

import ni.edu.uam.proyectofinalpae.model.Riego;

import java.util.List;

public class RiegoDao extends GenericDao<Riego> {

    public RiegoDao() {
        super(Riego.class);
    }

    /**
     * Registra el riego y, en la MISMA transaccion, deja el sensor de la planta en 90 % de humedad
     * y marca como atendidas sus alertas pendientes. Si algo falla, se revierte todo.
     */
    public void registrar(Riego riego) {
        enTransaccion(s -> {
            s.persist(riego);
            Long plantaId = riego.getPlanta().getId();
            s.createMutationQuery("UPDATE Sensor se SET se.humedadActual = 90 WHERE se.planta.id = :plantaId")
                    .setParameter("plantaId", plantaId)
                    .executeUpdate();
            s.createMutationQuery("UPDATE Alerta a SET a.atendida = true "
                            + "WHERE a.planta.id = :plantaId AND a.atendida = false")
                    .setParameter("plantaId", plantaId)
                    .executeUpdate();
            return null;
        });
    }

    /** HQL sobre entidades relacionadas: historial con planta, cultivo, modulo y colmena. */
    @Override
    public List<Riego> listar() {
        return enSesion(s -> s.createQuery(
                        "SELECT r FROM Riego r "
                                + "JOIN FETCH r.planta p "
                                + "JOIN FETCH p.cultivo "
                                + "JOIN FETCH p.modulo m "
                                + "JOIN FETCH m.colmena "
                                + "ORDER BY r.fechaHora DESC", Riego.class)
                .getResultList());
    }

    /** HQL con agregacion: litros usados por cada origen de agua (lluvia, gris filtrada, potable). */
    public List<Object[]> litrosPorOrigen() {
        return enSesion(s -> s.createQuery(
                        "SELECT r.origenAgua, SUM(r.cantidadMl) / 1000.0 FROM Riego r "
                                + "GROUP BY r.origenAgua ORDER BY r.origenAgua", Object[].class)
                .getResultList());
    }
}

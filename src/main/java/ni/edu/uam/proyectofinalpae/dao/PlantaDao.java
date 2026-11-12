package ni.edu.uam.proyectofinalpae.dao;

import ni.edu.uam.proyectofinalpae.model.Planta;

import java.util.List;

public class PlantaDao extends GenericDao<Planta> {

    /** Parte comun de las consultas: trae la planta con todo lo que se muestra en pantalla. */
    private static final String SELECT_COMPLETO =
            "SELECT p FROM Planta p "
                    + "JOIN FETCH p.cultivo cu "
                    + "JOIN FETCH p.modulo m "
                    + "JOIN FETCH m.colmena c "
                    + "LEFT JOIN FETCH p.sensor se ";

    public PlantaDao() {
        super(Planta.class);
    }

    /** HQL sobre 5 entidades relacionadas: Planta, Cultivo, Modulo, Colmena y Sensor. */
    @Override
    public List<Planta> listar() {
        return enSesion(s -> s.createQuery(
                        SELECT_COMPLETO + "ORDER BY c.nombre, m.numeroNivel", Planta.class)
                .getResultList());
    }

    /** HQL: plantas de una colmena especifica. */
    public List<Planta> buscarPorColmena(Long colmenaId) {
        return enSesion(s -> s.createQuery(
                        SELECT_COMPLETO + "WHERE c.id = :colmenaId ORDER BY m.numeroNivel", Planta.class)
                .setParameter("colmenaId", colmenaId)
                .getResultList());
    }

    /** HQL: plantas cuyo sensor marca menos humedad que el minimo de su cultivo. */
    public List<Planta> listarQueNecesitanRiego() {
        return enSesion(s -> s.createQuery(
                        SELECT_COMPLETO + "WHERE se.humedadActual < cu.humedadMinima "
                                + "ORDER BY se.humedadActual", Planta.class)
                .getResultList());
    }

    /** HQL: cuantas plantas hay en un modulo (sin contar la planta que se esta editando). */
    public long contarEnModulo(Long moduloId, Long plantaExcluida) {
        return enSesion(s -> s.createQuery(
                        "SELECT COUNT(p) FROM Planta p WHERE p.modulo.id = :moduloId AND p.id <> :excluida",
                        Long.class)
                .setParameter("moduloId", moduloId)
                .setParameter("excluida", plantaExcluida == null ? -1L : plantaExcluida)
                .getSingleResult());
    }
}

package ni.edu.uam.proyectofinalpae.dao;
import ni.edu.uam.proyectofinalpae.model.Colmena;

import java.util.List;
public class ColmenaDao extends GenericDao<Colmena>{

    public ColmenaDao() {
        super(Colmena.class);
    }

    /**
     * HQL sobre entidades relacionadas: trae cada colmena con su usuario y sus modulos.
     * JOIN FETCH carga las relaciones LAZY antes de cerrar la sesion.
     */
    @Override
    public List<Colmena> listar() {
        return enSesion(s -> s.createQuery(
                        "SELECT DISTINCT c FROM Colmena c "
                                + "JOIN FETCH c.usuario "
                                + "LEFT JOIN FETCH c.modulos "
                                + "ORDER BY c.nombre", Colmena.class)
                .getResultList());
    }

    /** HQL: colmenas que pertenecen a un usuario especifico. */
    public List<Colmena> buscarPorUsuario(Long usuarioId) {
        return enSesion(s -> s.createQuery(
                        "SELECT DISTINCT c FROM Colmena c "
                                + "JOIN FETCH c.usuario u "
                                + "LEFT JOIN FETCH c.modulos "
                                + "WHERE u.id = :usuarioId "
                                + "ORDER BY c.nombre", Colmena.class)
                .setParameter("usuarioId", usuarioId)
                .getResultList());
    }
}


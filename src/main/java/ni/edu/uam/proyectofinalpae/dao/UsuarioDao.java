package ni.edu.uam.proyectofinalpae.dao;

import ni.edu.uam.proyectofinalpae.model.Usuario;

import java.util.List;

public class UsuarioDao extends GenericDao<Usuario>{
    public UsuarioDao() {
        super(Usuario.class);
    }

    @Override
    public List<Usuario> listar() {
        return enSesion(s -> s.createQuery("FROM Usuario u ORDER BY u.nombre", Usuario.class)
                .getResultList());
    }

    /** HQL: verifica si el correo ya pertenece a OTRO usuario (para no duplicarlo). */
    public boolean correoEnUso(String correo, Long idActual) {
        List<Usuario> encontrados = enSesion(s -> s.createQuery(
                        "FROM Usuario u WHERE lower(u.correo) = lower(:correo)", Usuario.class)
                .setParameter("correo", correo)
                .getResultList());
        return encontrados.stream().anyMatch(u -> !u.getId().equals(idActual));
    }
}
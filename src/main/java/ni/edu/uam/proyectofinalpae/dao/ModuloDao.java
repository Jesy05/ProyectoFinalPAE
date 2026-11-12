package ni.edu.uam.proyectofinalpae.dao;
import ni.edu.uam.proyectofinalpae.model.Modulo;

import java.util.List;
public class ModuloDao extends GenericDao<Modulo> {

    public ModuloDao() {
        super(Modulo.class);
    }


    @Override
    public List<Modulo> listar() {
        return enSesion(s -> s.createQuery(
                        "SELECT m FROM Modulo m JOIN FETCH m.colmena c "
                                + "ORDER BY c.nombre, m.numeroNivel", Modulo.class)
                .getResultList());
    }
}



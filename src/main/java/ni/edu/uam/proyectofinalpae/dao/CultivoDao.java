package ni.edu.uam.proyectofinalpae.dao;

import ni.edu.uam.proyectofinalpae.model.Cultivo;

import java.util.List;

public class CultivoDao extends GenericDao<Cultivo> {

    public CultivoDao() {
        super(Cultivo.class);
    }

    @Override
    public List<Cultivo> listar() {
        return enSesion(s -> s.createQuery("FROM Cultivo c ORDER BY c.nombre", Cultivo.class)
                .getResultList());
    }
}

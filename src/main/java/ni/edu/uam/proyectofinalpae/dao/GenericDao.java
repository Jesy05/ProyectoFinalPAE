package ni.edu.uam.proyectofinalpae.dao;
import ni.edu.uam.proyectofinalpae.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.util.List;
import java.util.function.Function;
public class GenericDao<T>  {
    private final Class<T> clase;

    protected GenericDao(Class<T> clase) {
        this.clase = clase;
    }

    /** CREATE */
    public void guardar(T entidad) {
        enTransaccion(s -> {
            s.persist(entidad);
            return null;
        });
    }

    /** UPDATE */
    public T actualizar(T entidad) {
        return enTransaccion(s -> s.merge(entidad));
    }

    /** DELETE */
    public void eliminar(Long id) {
        enTransaccion(s -> {
            T entidad = s.find(clase, id);
            if (entidad != null) {
                s.remove(entidad);
            }
            return null;
        });
    }

    /** READ por llave primaria */
    public T buscarPorId(Long id) {
        return enSesion(s -> s.find(clase, id));
    }

    /** READ de todos los registros */
    public List<T> listar() {
        return enSesion(s -> s.createQuery("FROM " + clase.getSimpleName(), clase).getResultList());
    }

    /** Abre una sesion, ejecuta la operacion y la cierra. */
    protected <R> R enSesion(Function<Session, R> operacion) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            return operacion.apply(s);
        }
    }

    /** Igual que enSesion pero dentro de una transaccion: commit si todo sale bien, rollback si falla. */
    protected <R> R enTransaccion(Function<Session, R> operacion) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            try {
                R resultado = operacion.apply(s);
                tx.commit();
                return resultado;
            } catch (RuntimeException ex) {
                if (tx.isActive()) {
                    tx.rollback();
                }
                throw ex;
            }
        }
    }
}

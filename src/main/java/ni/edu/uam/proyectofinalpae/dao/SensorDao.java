package ni.edu.uam.proyectofinalpae.dao;

import ni.edu.uam.proyectofinalpae.model.Planta;
import ni.edu.uam.proyectofinalpae.model.Sensor;
import org.hibernate.Session;

public class SensorDao extends GenericDao<Sensor> {

    public SensorDao() {
        super(Sensor.class);
    }

    /** HQL: verifica si el codigo ya lo usa OTRO sensor. */
    public boolean codigoEnUso(String codigo, Long sensorActual) {
        Long cantidad = enSesion(s -> s.createQuery(
                        "SELECT COUNT(se) FROM Sensor se WHERE upper(se.codigo) = upper(:codigo) AND se.id <> :actual",
                        Long.class)
                .setParameter("codigo", codigo)
                .setParameter("actual", sensorActual == null ? -1L : sensorActual)
                .getSingleResult());
        return cantidad > 0;
    }

    @Override
    public void guardar(Sensor sensor) {
        enTransaccion(s -> {
            Long plantaId = sensor.getPlanta().getId();
            if (!AlertaDao.bloquearPlanta(s, plantaId)) {
                throw new IllegalArgumentException("La planta ya no existe.");
            }
            Planta planta = s.find(Planta.class, plantaId);
            if (planta.getSensor() != null) {
                throw new IllegalArgumentException("La planta ya tiene un sensor.");
            }
            planta.setSensor(sensor);
            s.persist(sensor);
            AlertaDao.conciliarHumedad(s, planta);
            return null;
        });
    }

    @Override
    public Sensor actualizar(Sensor sensor) {
        return enTransaccion(s -> {
            Sensor actual = bloquearSensor(s, sensor.getId());
            if (actual == null) throw new IllegalArgumentException("El sensor ya no existe.");
            actual.setCodigo(sensor.getCodigo());
            actual.setHumedadActual(sensor.getHumedadActual());
            AlertaDao.conciliarHumedad(s, actual.getPlanta());
            return actual;
        });
    }

    @Override
    public void eliminar(Long id) {
        enTransaccion(s -> {
            Sensor sensor = bloquearSensor(s, id);
            if (sensor != null) sensor.getPlanta().setSensor(null);
            return null;
        });
    }

    private static Sensor bloquearSensor(Session s, Long id) {
        Long plantaId = s.createQuery("SELECT se.planta.id FROM Sensor se WHERE se.id = :id", Long.class)
                .setParameter("id", id).uniqueResult();
        if (plantaId == null || !AlertaDao.bloquearPlanta(s, plantaId)) return null;
        return s.find(Sensor.class, id);
    }

    /** Simula la pérdida de 15 puntos y concilia todas las lecturas disponibles. */
    public int simularSecado() {
        return enTransaccion(s -> {
            int afectados = 0;
            for (Long id : AlertaDao.bloquearPlantas(s)) {
                Planta planta = s.find(Planta.class, id);
                Sensor sensor = planta.getSensor();
                if (sensor == null || sensor.getHumedadActual() == null) continue;
                sensor.setHumedadActual(Math.max(0, sensor.getHumedadActual() - 15));
                AlertaDao.conciliarHumedad(s, planta);
                afectados++;
            }
            return afectados;
        });
    }
}

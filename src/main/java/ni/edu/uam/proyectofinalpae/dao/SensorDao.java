package ni.edu.uam.proyectofinalpae.dao;

import ni.edu.uam.proyectofinalpae.model.Sensor;

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

    /**
     * Simula el paso del tiempo: todos los sensores pierden 15 puntos de humedad (minimo 0).
     * Es una actualizacion masiva en HQL (UPDATE) dentro de una transaccion.
     */
    public int simularSecado() {
        return enTransaccion(s -> s.createMutationQuery(
                        "UPDATE Sensor se SET se.humedadActual = "
                                + "CASE WHEN se.humedadActual >= 15 THEN se.humedadActual - 15 ELSE 0 END")
                .executeUpdate());
    }
}

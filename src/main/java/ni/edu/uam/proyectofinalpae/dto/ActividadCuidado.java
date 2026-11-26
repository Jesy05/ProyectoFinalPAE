package ni.edu.uam.proyectofinalpae.dto;

public record ActividadCuidado(
        String fecha, Long plantaId, String planta, String estadoPlanta, String cultivo, String colmena, int modulo,
        String tipo, String estado, String descripcion, String motivo, String ultimoRiego,
        Integer humedadActual, int humedadMinima, String proximaRevision) {

    public static final String ATENCION = "Requiere atención";
    public static final String REVISION = "Revisar humedad";
    public static final String REGISTRADO = "Riego registrado";
    public static final String INSUFICIENTE = "Información insuficiente";
}

package ni.edu.uam.proyectofinalpae.service;

import ni.edu.uam.proyectofinalpae.dao.PlantaDao;
import ni.edu.uam.proyectofinalpae.dao.RiegoDao;
import ni.edu.uam.proyectofinalpae.dao.SensorDao;
import ni.edu.uam.proyectofinalpae.model.Planta;
import ni.edu.uam.proyectofinalpae.model.Riego;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Simula la parte fisica de una colmena PlantBee mientras el hardware esta en fase de prototipo:
 * sensores capacitivos de humedad, microcontrolador, rele con valvula de 12 V, tanque de agua
 * recuperada y panel solar con bateria.
 *
 * Las lecturas de humedad y los riegos son REALES en la base de datos (se usan los DAO de Hibernate);
 * el nivel del tanque y la energia solar se simulan en memoria.
 */
public class SimuladorIoTService {

    /** Tanque de agua de lluvia y agua gris filtrada por el biofiltro. */
    public static final int CAPACIDAD_TANQUE_ML = 20_000;
    /** Cantidad que deja pasar la valvula en cada riego automatico. */
    public static final int DOSIS_RIEGO_ML = 250;
    /** Panel solar previsto en el prototipo: 20 W / 12 V. */
    public static final double POTENCIA_PANEL_W = 20.0;

    // Calibracion tipica de un sensor capacitivo: seco ~2.8 V, en agua ~1.2 V
    private static final double VOLTAJE_SECO = 2.80;
    private static final double VOLTAJE_MOJADO = 1.20;

    private final PlantaDao plantaDao = new PlantaDao();
    private final SensorDao sensorDao = new SensorDao();
    private final RiegoDao riegoDao = new RiegoDao();

    private int nivelTanqueMl = 14_000;
    private double bateriaPorcentaje = 78;
    private int riegosAutomaticos;

    /** Una lectura de sensor lista para mostrar en pantalla. */
    public record Lectura(Long plantaId, String planta, String cultivo, String ubicacion,
                          String codigoSensor, Integer humedad, int humedadMinima, double voltaje) {

        public boolean tieneSensor() {
            return codigoSensor != null && humedad != null;
        }

        public boolean necesitaRiego() {
            return tieneSensor() && humedad < humedadMinima;
        }
    }

    /** Lee todos los sensores (consulta HQL con JOIN FETCH de planta, cultivo, modulo, colmena y sensor). */
    public List<Lectura> leerSensores() {
        List<Lectura> lecturas = new ArrayList<>();
        for (Planta p : plantaDao.listar()) {
            String codigo = p.getSensor() == null ? null : p.getSensor().getCodigo();
            Integer humedad = p.getSensor() == null ? null : p.getSensor().getHumedadActual();
            lecturas.add(new Lectura(p.getId(), p.getNombreVisible(), p.getCultivo().getNombre(),
                    p.getModulo().toString(), codigo, humedad, p.getCultivo().getHumedadMinima(),
                    humedad == null ? 0 : voltajeDeSalida(humedad)));
        }
        return lecturas;
    }

    /**
     * Voltaje que entregaria un sensor capacitivo: a mas agua, mayor constante dielectrica,
     * mayor capacitancia y MENOR voltaje de salida.
     */
    public static double voltajeDeSalida(int humedad) {
        int h = Math.max(0, Math.min(100, humedad));
        return VOLTAJE_SECO - (VOLTAJE_SECO - VOLTAJE_MOJADO) * h / 100.0;
    }

    /** Simula que pasan unas horas: cada sensor pierde humedad y se concilian las alertas. */
    public int simularPasoDelTiempo() {
        return sensorDao.simularSecado();
    }

    /**
     * Lo que haria el microcontrolador en cada ciclo: por cada planta bajo su humedad minima,
     * activa el rele, abre la valvula de 12 V y deja pasar una dosis de agua del tanque.
     * Cada riego se guarda en la base (RiegoDao.registrar sube la humedad y atiende la alerta).
     *
     * @return las plantas que se regaron en este ciclo
     */
    public List<Long> cicloAutomatico() {
        List<Long> regadas = new ArrayList<>();
        for (Lectura lectura : leerSensores()) {
            if (!lectura.necesitaRiego()) continue;
            if (nivelTanqueMl < DOSIS_RIEGO_ML || bateriaPorcentaje < 5) break;

            Planta planta = plantaDao.buscarPorId(lectura.plantaId());
            if (planta == null) continue; // la borraron desde otra pantalla
            Riego riego = new Riego();
            riego.setPlanta(planta);
            riego.setCantidadMl(DOSIS_RIEGO_ML);
            riego.setOrigenAgua("Agua de lluvia");
            riego.setModo("Automatico");
            riego.setFechaHora(LocalDateTime.now());
            riegoDao.registrar(riego);

            nivelTanqueMl -= DOSIS_RIEGO_ML;
            bateriaPorcentaje = Math.max(0, bateriaPorcentaje - 0.6); // la valvula de 12 V consume bateria
            riegosAutomaticos++;
            regadas.add(lectura.plantaId());
        }
        return regadas;
    }

    /** Simula una lluvia que vuelve a llenar el tanque. */
    public void llenarTanqueConLluvia() {
        nivelTanqueMl = CAPACIDAD_TANQUE_ML;
    }

    /** Potencia que entrega el panel segun la hora: maxima al mediodia y 0 de noche. */
    public double potenciaSolar(LocalTime hora) {
        double h = hora.getHour() + hora.getMinute() / 60.0;
        if (h < 6 || h > 18) return 0;
        return POTENCIA_PANEL_W * Math.sin(Math.PI * (h - 6) / 12.0);
    }

    /** El panel carga la bateria (llamado en cada ciclo del monitoreo). */
    public void cargarBateria(LocalTime hora) {
        bateriaPorcentaje = Math.min(100, bateriaPorcentaje + potenciaSolar(hora) / POTENCIA_PANEL_W * 0.4);
    }

    public int getNivelTanqueMl() {
        return nivelTanqueMl;
    }

    public double getBateriaPorcentaje() {
        return bateriaPorcentaje;
    }

    public int getRiegosAutomaticos() {
        return riegosAutomaticos;
    }
}

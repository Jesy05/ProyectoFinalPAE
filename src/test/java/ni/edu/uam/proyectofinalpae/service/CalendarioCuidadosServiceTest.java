package ni.edu.uam.proyectofinalpae.service;

import ni.edu.uam.proyectofinalpae.dto.ActividadCuidado;
import ni.edu.uam.proyectofinalpae.model.Colmena;
import ni.edu.uam.proyectofinalpae.model.Cultivo;
import ni.edu.uam.proyectofinalpae.model.Modulo;
import ni.edu.uam.proyectofinalpae.model.Planta;
import ni.edu.uam.proyectofinalpae.model.Sensor;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CalendarioCuidadosServiceTest {
    private static final LocalDate HOY = LocalDate.of(2026, 11, 25);

    @Test
    void priorizaHumedadBajaAunqueLaRevisionSeaFutura() {
        var actividad = CalendarioCuidadosService.estimar(planta(39), HOY, HOY);

        assertEquals(ActividadCuidado.ATENCION, actividad.estado());
        assertEquals(HOY.toString(), actividad.fecha());
    }

    @Test
    void humedadEnElMinimoSoloRequiereRevisionCuandoVenceLaFecha() {
        var actividad = CalendarioCuidadosService.estimar(planta(40), HOY.minusDays(5), HOY);

        assertEquals(ActividadCuidado.REVISION, actividad.estado());
        assertEquals(HOY.toString(), actividad.fecha());
    }

    @Test
    void calculaLaRevisionDelSiguienteMesDesdeElUltimoRiego() {
        var actividad = CalendarioCuidadosService.estimar(planta(90), HOY.plusDays(4), HOY.plusDays(4));

        assertEquals("2026-12-02", actividad.proximaRevision());
        assertEquals(actividad.proximaRevision(), actividad.fecha());
    }

    @Test
    void identificaLosDatosFaltantesSinInventarLecturasNiRiegos() {
        var actividad = CalendarioCuidadosService.estimar(planta(null), null, HOY);

        assertEquals(ActividadCuidado.INSUFICIENTE, actividad.estado());
        assertNull(actividad.humedadActual());
        assertNull(actividad.ultimoRiego());
        assertNull(actividad.proximaRevision());
    }

    @Test
    void sensorSinLecturaNoIndicaHumedadBaja() {
        Planta planta = planta(null);
        planta.setSensor(new Sensor("S-sin-lectura", null));
        var actividad = CalendarioCuidadosService.estimar(planta, HOY, HOY);

        assertEquals(ActividadCuidado.INSUFICIENTE, actividad.estado());
        assertNull(actividad.humedadActual());
    }

    private static Planta planta(Integer humedad) {
        Cultivo cultivo = new Cultivo();
        cultivo.setNombre("Albahaca");
        cultivo.setFrecuenciaRiegoDias(3);
        cultivo.setHumedadMinima(40);
        Colmena colmena = new Colmena();
        colmena.setNombre("Balcón");
        Modulo modulo = new Modulo(2, 4);
        colmena.agregarModulo(modulo);
        Planta planta = new Planta();
        planta.setCultivo(cultivo);
        planta.setModulo(modulo);
        if (humedad != null) {
            planta.setSensor(new Sensor("S-1", humedad));
        }
        return planta;
    }
}

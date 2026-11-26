package ni.edu.uam.proyectofinalpae.repository;

import ni.edu.uam.proyectofinalpae.api.HttpStatusException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

class CalendarioRestTest {
    private static final YearMonth MES = YearMonth.of(2026, 11);
    private static final String ACTIVIDADES = """
            [{"fecha":"2026-11-26","plantaId":1,"planta":"Albahaca","estadoPlanta":"Creciendo",
              "cultivo":"Albahaca","colmena":"Huerto","modulo":2,"tipo":"Atención",
              "estado":"Requiere atención","descripcion":"Comprueba el sustrato","motivo":"Humedad baja",
              "ultimoRiego":null,"humedadActual":20,"humedadMinima":40,"proximaRevision":"2026-11-26"}]
            """;
    private static final String CLIMA = """
            {"daily":{"time":["2026-11-26","2026-11-27"],"temperature_2m_max":[32.5,null],
                      "precipitation_probability_max":[60,20]}}
            """;

    @Test
    void conservaLosCamposOpcionalesYElMesDeLaConsulta() throws Exception {
        var repositorio = new CuidadosApiRepository(uri -> {
            assertEquals("/api/cuidados", uri.getPath());
            assertEquals("mes=2026-11", uri.getQuery());
            return ACTIVIDADES;
        });
        var actividad = repositorio.paraMes(MES).getFirst();
        assertEquals(20, actividad.humedadActual());
        assertNull(actividad.ultimoRiego());
        assertTrue(new CuidadosApiRepository(uri -> "[]").paraMes(MES).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> repositorio.paraMes(YearMonth.of(0, 1)));
    }

    @Test
    void rechazaCuidadosConTiposIncorrectosOCamposFaltantes() {
        for (String cuerpo : new String[]{"", "null", "[null]", "[{}]", "[] texto",
                ACTIVIDADES.replace("\"humedadActual\":20,", ""),
                ACTIVIDADES.replace("\"humedadActual\":20", "\"humedadActual\":\"20\""),
                ACTIVIDADES.replace("\"plantaId\":1", "\"plantaId\":1.5"),
                ACTIVIDADES.replace("2026-11-26", "2026-12-26")}) {
            assertThrows(IOException.class, () -> new CuidadosApiRepository(uri -> cuerpo).paraMes(MES), cuerpo);
        }
    }

    @Test
    void decodificaPronosticosYRegistraHoraDeManagua() throws Exception {
        var clima = new OpenMeteoApiRepository(uri -> {
            assertEquals("api.open-meteo.com", uri.getHost());
            assertTrue(uri.getQuery().contains("timezone=America/Managua"));
            return CLIMA;
        }).consultarManagua();
        assertEquals(32.5, clima.dias().get(LocalDate.of(2026, 11, 26)).temperaturaMaxima());
        assertEquals(60, clima.dias().get(LocalDate.of(2026, 11, 26)).probabilidadPrecipitacion());
        assertNull(clima.dias().get(LocalDate.of(2026, 11, 27)).temperaturaMaxima());
        assertEquals("America/Managua", clima.consultadoEn().getZone().getId());
    }

    @Test
    void rechazaSeriesVaciasTiposIncorrectosYFechasDuplicadas() {
        for (String cuerpo : new String[]{"", "{}", "{\"daily\":[]}",
                CLIMA.replace("32.5,null", "32.5"),
                CLIMA.replace("32.5", "\"32.5\""),
                CLIMA.replace("[60,20]", "[101,20]"),
                CLIMA.replace("[60,20]", "[60.5,20]"),
                CLIMA.replace("2026-11-27", "2026-11-26")}) {
            assertThrows(IOException.class, () -> new OpenMeteoApiRepository(uri -> cuerpo).consultarManagua(), cuerpo);
        }
    }

    @Test
    void conservaElCodigoHttpYElTimeoutParaLaInterfaz() {
        var cuidados = new CuidadosApiRepository(uri -> { throw new HttpStatusException(503); });
        assertEquals(503, assertThrows(HttpStatusException.class, () -> cuidados.paraMes(MES)).statusCode());
        var meteo = new OpenMeteoApiRepository(uri -> { throw new HttpTimeoutException("timeout"); });
        assertThrows(HttpTimeoutException.class, meteo::consultarManagua);
    }
}

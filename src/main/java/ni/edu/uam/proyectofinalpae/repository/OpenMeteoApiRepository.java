package ni.edu.uam.proyectofinalpae.repository;

import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import ni.edu.uam.proyectofinalpae.api.HttpClientGateway;
import ni.edu.uam.proyectofinalpae.api.HttpGateway;

import java.io.IOException;
import java.net.URI;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ni.edu.uam.proyectofinalpae.repository.JsonRespuesta.*;

public class OpenMeteoApiRepository {
    private static final ZoneId MANAGUA = ZoneId.of("America/Managua");
    private static final URI PRONOSTICO_MANAGUA = URI.create(
            "https://api.open-meteo.com/v1/forecast?latitude=12.13&longitude=-86.25"
            + "&daily=temperature_2m_max,precipitation_probability_max"
            + "&temperature_unit=celsius&timezone=America%2FManagua&forecast_days=7");
    private final HttpGateway http;

    public OpenMeteoApiRepository() {
        this(new HttpClientGateway());
    }

    public OpenMeteoApiRepository(HttpGateway http) {
        this.http = http;
    }

    public PronosticoConsultado consultarManagua() throws IOException, InterruptedException {
        String cuerpo = http.get(PRONOSTICO_MANAGUA);
        try {
            var diario = campo(leer(cuerpo).getAsJsonObject(), "daily", false).getAsJsonObject();
            var fechas = campo(diario, "time", false).getAsJsonArray();
            var temperaturas = campo(diario, "temperature_2m_max", false).getAsJsonArray();
            var probabilidades = campo(diario, "precipitation_probability_max", false).getAsJsonArray();
            if (fechas.isEmpty() || fechas.size() != temperaturas.size() || fechas.size() != probabilidades.size()) {
                throw new IllegalArgumentException("Series diarias vacías o de distinta longitud");
            }

            for (int i = 0; i < fechas.size(); i++) {
                texto(fechas.get(i));
                var temperatura = temperaturas.get(i);
                if (!temperatura.isJsonNull() && !Double.isFinite(numero(temperatura).doubleValue())) {
                    throw new IllegalArgumentException("Temperatura fuera de rango");
                }
                var probabilidad = probabilidades.get(i);
                if (!probabilidad.isJsonNull()) porcentaje(probabilidad);
            }

            Diario datos = GSON.fromJson(diario, Diario.class);
            Map<LocalDate, Pronostico> dias = new HashMap<>();
            for (int i = 0; i < fechas.size(); i++) {
                LocalDate fecha = LocalDate.parse(datos.time().get(i));
                var pronostico = new Pronostico(datos.temperatura().get(i), datos.precipitacion().get(i));
                if (dias.putIfAbsent(fecha, pronostico) != null) {
                    throw new IllegalArgumentException("Fecha repetida: " + fecha);
                }
            }
            if (dias.values().stream().allMatch(p -> p.temperaturaMaxima() == null
                    && p.probabilidadPrecipitacion() == null)) {
                throw new IllegalArgumentException("Pronóstico sin valores meteorológicos");
            }
            return new PronosticoConsultado(Map.copyOf(dias), ZonedDateTime.now(MANAGUA));
        } catch (JsonParseException | DateTimeException | IllegalArgumentException | IllegalStateException
                 | ArithmeticException e) {
            throw new IOException("Open-Meteo devolvió datos inválidos o incompletos.", e);
        }
    }

    public record PronosticoConsultado(Map<LocalDate, Pronostico> dias, ZonedDateTime consultadoEn) {}

    public record Pronostico(Double temperaturaMaxima, Integer probabilidadPrecipitacion) {}

    private record Diario(List<String> time,
                          @SerializedName("temperature_2m_max") List<Double> temperatura,
                          @SerializedName("precipitation_probability_max") List<Integer> precipitacion) {}
}

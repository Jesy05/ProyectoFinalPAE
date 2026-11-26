package ni.edu.uam.proyectofinalpae.repository;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import ni.edu.uam.proyectofinalpae.api.ApiServidor;
import ni.edu.uam.proyectofinalpae.api.HttpClientGateway;
import ni.edu.uam.proyectofinalpae.api.HttpGateway;
import ni.edu.uam.proyectofinalpae.dto.ActividadCuidado;

import java.io.IOException;
import java.net.URI;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static ni.edu.uam.proyectofinalpae.dto.ActividadCuidado.*;
import static ni.edu.uam.proyectofinalpae.repository.JsonRespuesta.*;

public class CuidadosApiRepository {
    private static final Set<String> ESTADOS = Set.of(ATENCION, REVISION, REGISTRADO, INSUFICIENTE);
    private final HttpGateway http;

    public CuidadosApiRepository() {
        this(new HttpClientGateway());
    }

    public CuidadosApiRepository(HttpGateway http) {
        this.http = http;
    }

    public List<ActividadCuidado> paraMes(YearMonth mes) throws IOException, InterruptedException {
        if (mes == null || mes.getYear() < 1 || mes.getYear() > 9999) {
            throw new IllegalArgumentException("El mes debe estar entre 0001-01 y 9999-12");
        }
        URI uri = URI.create("http://127.0.0.1:" + ApiServidor.PUERTO + "/api/cuidados?mes=" + mes);
        String cuerpo = http.get(uri);
        try {
            List<ActividadCuidado> actividades = new ArrayList<>();
            for (JsonElement elemento : leer(cuerpo).getAsJsonArray()) {
                var objeto = elemento.getAsJsonObject();
                for (String nombre : List.of("planta", "estadoPlanta", "cultivo", "colmena", "tipo",
                        "estado", "descripcion", "motivo")) {
                    texto(campo(objeto, nombre, false));
                }
                LocalDate fecha = LocalDate.parse(texto(campo(objeto, "fecha", false)));
                if (!YearMonth.from(fecha).equals(mes)
                        || !ESTADOS.contains(objeto.get("estado").getAsString())
                        || numero(campo(objeto, "plantaId", false)).longValueExact() <= 0
                        || numero(campo(objeto, "modulo", false)).intValueExact() <= 0) {
                    throw new IllegalArgumentException("Actividad fuera del mes o con identificadores o estado inválidos");
                }
                porcentaje(campo(objeto, "humedadMinima", false));
                JsonElement humedad = campo(objeto, "humedadActual", true);
                if (!humedad.isJsonNull()) porcentaje(humedad);
                for (String nombre : List.of("ultimoRiego", "proximaRevision")) {
                    JsonElement valor = campo(objeto, nombre, true);
                    if (!valor.isJsonNull()) LocalDate.parse(texto(valor));
                }
                actividades.add(GSON.fromJson(objeto, ActividadCuidado.class));
            }
            return List.copyOf(actividades);
        } catch (JsonParseException | DateTimeException | IllegalArgumentException | IllegalStateException
                 | ArithmeticException e) {
            throw new IOException("La API de cuidados devolvió datos inválidos.", e);
        }
    }
}

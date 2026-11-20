package ni.edu.uam.proyectofinalpae.repository;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import ni.edu.uam.proyectofinalpae.api.HttpClientGateway;
import ni.edu.uam.proyectofinalpae.api.HttpGateway;
import ni.edu.uam.proyectofinalpae.dto.WikipediaSummary;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class WikipediaApiRepository implements WikipediaRepository {
    private static final String BASE_URL =
            "https://es.wikipedia.org/api/rest_v1/page/summary/";

    private final HttpGateway http;

    public WikipediaApiRepository() {
        this(new HttpClientGateway());
    }

    public WikipediaApiRepository(HttpGateway http) {
        this.http = http;
    }

    @Override
    public WikipediaSummary buscarResumen(String consulta)
            throws IOException, InterruptedException {
        if (consulta == null || consulta.isBlank()) {
            throw new IllegalArgumentException("El nombre del cultivo es obligatorio.");
        }
        String texto = consulta.trim();
        String encoded = URLEncoder.encode(texto, StandardCharsets.UTF_8)
                .replace("+", "%20");
        String body = http.get(URI.create(BASE_URL + encoded));

        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            String title = texto(json, "title", texto);
            String description = texto(json, "description", "Sin descripcion disponible.");
            String extract = texto(json, "extract", "No hay resumen disponible.");
            String pageUrl = "";

            if (json.has("content_urls") && json.get("content_urls").isJsonObject()) {
                JsonObject urls = json.getAsJsonObject("content_urls");
                if (urls.has("desktop") && urls.get("desktop").isJsonObject()) {
                    pageUrl = texto(urls.getAsJsonObject("desktop"), "page", "");
                }
            }
            return new WikipediaSummary(title, description, extract, pageUrl);
        } catch (JsonParseException | IllegalStateException | NullPointerException ex) {
            throw new IOException("La respuesta de Wikipedia no tiene un formato valido.", ex);
        }
    }

    private static String texto(JsonObject object, String key, String fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        return object.get(key).getAsString();
    }
}

package ni.edu.uam.proyectofinalpae.repository;

import com.google.gson.JsonElement;
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
            String extract = texto(json, "extract", "");
            if (extract.isBlank()) {
                throw new IOException("Wikipedia no devolvió un resumen utilizable.");
            }
            String pageUrl = "";

            if (json.has("content_urls") && json.get("content_urls").isJsonObject()) {
                JsonObject urls = json.getAsJsonObject("content_urls");
                if (urls.has("desktop") && urls.get("desktop").isJsonObject()) {
                    pageUrl = texto(urls.getAsJsonObject("desktop"), "page", "");
                }
            }
            return new WikipediaSummary(title, description, extract, enlaceSeguro(pageUrl));
        } catch (JsonParseException | IllegalStateException | NullPointerException ex) {
            throw new IOException("La respuesta de Wikipedia no tiene un formato valido.", ex);
        }
    }

    private static String texto(JsonObject object, String key, String fallback) {
        JsonElement valor = object.get(key);
        if (valor == null || !valor.isJsonPrimitive() || !valor.getAsJsonPrimitive().isString()
                || valor.getAsString().isBlank()) {
            return fallback;
        }
        return valor.getAsString();
    }

    private static String enlaceSeguro(String url) {
        try {
            URI uri = URI.create(url);
            if ("https".equalsIgnoreCase(uri.getScheme()) && "es.wikipedia.org".equalsIgnoreCase(uri.getHost())
                    && uri.getRawUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443)) {
                return url;
            }
        } catch (IllegalArgumentException ignorado) {
            // Una ficha puede mostrarse sin enlace.
        }
        return "";
    }
}

package ni.edu.uam.proyectofinalpae.api;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class HttpClientGateway implements HttpGateway {
    private static final String USER_AGENT =
            "PlantBee/1.0 (https://github.com/Jesy05/ProyectoFinalPAE)";

    private final HttpClient client;
    private final Duration timeout;

    public HttpClientGateway() {
        this(Duration.ofSeconds(10));
    }

    public HttpClientGateway(Duration timeout) {
        this.timeout = timeout;
        this.client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String get(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        HttpResponse<String> response = client.send(
                request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new HttpStatusException(response.statusCode());
        }
        return response.body();
    }
}

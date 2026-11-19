package ni.edu.uam.proyectofinalpae.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class ApiServidor {
    public static final int PUERTO = 8080;

    private HttpServer servidor;

    /** Arranca la API. Si el puerto esta ocupado la app de escritorio sigue funcionando. */
    public void iniciar() {
        if (servidor != null) {
            return;
        }
        try {
            servidor = HttpServer.create(new InetSocketAddress(PUERTO), 0);
            servidor.createContext("/api", new ApiRutas());
            servidor.setExecutor(Executors.newFixedThreadPool(4));
            servidor.start();
            System.out.println("API PlantBee en http://localhost:" + PUERTO + "/api/health");
        } catch (IOException e) {
            servidor = null;
            System.out.println("No se pudo iniciar la API en el puerto " + PUERTO
                    + ". La aplicacion sigue funcionando sin API. Detalle: " + e.getMessage());
        }
    }

    public void detener() {
        if (servidor != null) {
            servidor.stop(0);
            servidor = null;
        }
    }

    public boolean estaActiva() {
        return servidor != null;
    }

    /** Escribe una respuesta JSON con el codigo HTTP indicado. */
    public static void responder(HttpExchange intercambio, int codigo, String json) throws IOException {
        byte[] cuerpo = json.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        intercambio.sendResponseHeaders(codigo, cuerpo.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(cuerpo);
        }
    }
}

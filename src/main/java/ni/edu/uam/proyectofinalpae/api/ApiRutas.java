package ni.edu.uam.proyectofinalpae.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.Map;

public class ApiRutas implements HttpHandler {
    @Override
    public void handle(HttpExchange intercambio) throws IOException {
        String ruta = intercambio.getRequestURI().getPath();
        try {
            if (!"GET".equalsIgnoreCase(intercambio.getRequestMethod())) {
                ApiServidor.responder(intercambio, 405,
                        JsonUtil.aJson(Map.of("error", "Solo se admite el metodo GET")));
                return;
            }
            switch (ruta) {
                case "/api/health" -> ApiServidor.responder(intercambio, 200,
                        JsonUtil.aJson(Map.of("estado", "ok", "servicio", "PlantBee")));
                default -> ApiServidor.responder(intercambio, 404,
                        JsonUtil.aJson(Map.of("error", "Ruta no encontrada: " + ruta)));
            }
        } catch (RuntimeException e) {
            ApiServidor.responder(intercambio, 500, JsonUtil.aJson(
                    Map.of("error", "Error interno", "detalle", String.valueOf(e.getMessage()))));
        }
    }
}

package ni.edu.uam.proyectofinalpae.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ni.edu.uam.proyectofinalpae.dao.CultivoDao;
import ni.edu.uam.proyectofinalpae.dao.UsuarioDao;
import ni.edu.uam.proyectofinalpae.model.Cultivo;
import ni.edu.uam.proyectofinalpae.model.Usuario;

public class ApiRutas implements HttpHandler {
    private final UsuarioDao usuarioDao = new UsuarioDao();
    private final CultivoDao cultivoDao = new CultivoDao();

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
                case "/api/usuarios" -> ApiServidor.responder(intercambio, 200,
                        JsonUtil.aJson(usuarios()));
                case "/api/cultivos" -> ApiServidor.responder(intercambio, 200,
                        JsonUtil.aJson(cultivos()));
                default -> ApiServidor.responder(intercambio, 404,
                        JsonUtil.aJson(Map.of("error", "Ruta no encontrada: " + ruta)));
            }
        } catch (RuntimeException e) {
            ApiServidor.responder(intercambio, 500, JsonUtil.aJson(
                    Map.of("error", "Error interno", "detalle", String.valueOf(e.getMessage()))));
        }
    }

    private List<Map<String, Object>> usuarios() {
        List<Map<String, Object>> filas = new ArrayList<>();
        for (Usuario u : usuarioDao.listar()) {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("id", u.getId());
            fila.put("nombre", u.getNombre());
            fila.put("correo", u.getCorreo());
            fila.put("telefono", u.getTelefono());
            fila.put("fechaRegistro", String.valueOf(u.getFechaRegistro()));
            filas.add(fila);
        }
        return filas;
    }

    private List<Map<String, Object>> cultivos() {
        List<Map<String, Object>> filas = new ArrayList<>();
        for (Cultivo c : cultivoDao.listar()) {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("id", c.getId());
            fila.put("nombre", c.getNombre());
            fila.put("nombreCientifico", c.getNombreCientifico());
            fila.put("frecuenciaRiegoDias", c.getFrecuenciaRiegoDias());
            fila.put("humedadMinima", c.getHumedadMinima());
            fila.put("luzRequerida", c.getLuzRequerida());
            filas.add(fila);
        }
        return filas;
    }
}

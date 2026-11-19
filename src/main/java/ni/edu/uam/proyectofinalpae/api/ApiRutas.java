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
import ni.edu.uam.proyectofinalpae.dao.AlertaDao;
import ni.edu.uam.proyectofinalpae.dao.ColmenaDao;
import ni.edu.uam.proyectofinalpae.dao.PlantaDao;
import ni.edu.uam.proyectofinalpae.dao.RiegoDao;
import ni.edu.uam.proyectofinalpae.model.Alerta;
import ni.edu.uam.proyectofinalpae.model.Colmena;
import ni.edu.uam.proyectofinalpae.model.Planta;
import ni.edu.uam.proyectofinalpae.model.Riego;

public class ApiRutas implements HttpHandler {
    private final UsuarioDao usuarioDao = new UsuarioDao();
    private final CultivoDao cultivoDao = new CultivoDao();
    private final ColmenaDao colmenaDao = new ColmenaDao();
    private final PlantaDao plantaDao = new PlantaDao();
    private final RiegoDao riegoDao = new RiegoDao();
    private final AlertaDao alertaDao = new AlertaDao();

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
                case "/api/colmenas" -> ApiServidor.responder(intercambio, 200,
                        JsonUtil.aJson(colmenas()));
                case "/api/plantas" -> ApiServidor.responder(intercambio, 200,
                        JsonUtil.aJson(plantas()));
                case "/api/riegos" -> ApiServidor.responder(intercambio, 200,
                        JsonUtil.aJson(riegos()));
                case "/api/alertas" -> ApiServidor.responder(intercambio, 200,
                        JsonUtil.aJson(alertas()));
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

    private List<Map<String, Object>> colmenas() {
        List<Map<String, Object>> filas = new ArrayList<>();
        for (Colmena c : colmenaDao.listar()) {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("id", c.getId());
            fila.put("nombre", c.getNombre());
            fila.put("ubicacion", c.getUbicacion());
            fila.put("dueno", c.getUsuario().getNombre());
            fila.put("fechaInstalacion", String.valueOf(c.getFechaInstalacion()));
            fila.put("modulos", c.getModulos().size());
            filas.add(fila);
        }
        return filas;
    }

    private List<Map<String, Object>> plantas() {
        List<Map<String, Object>> filas = new ArrayList<>();
        for (Planta p : plantaDao.listar()) {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("id", p.getId());
            fila.put("nombre", p.getNombreVisible());
            fila.put("cultivo", p.getCultivo().getNombre());
            fila.put("ubicacion", p.getModulo().toString());
            fila.put("estado", p.getEstado());
            fila.put("siembra", String.valueOf(p.getFechaSiembra()));
            if (p.getSensor() == null) {
                fila.put("sensor", null);
                fila.put("humedad", null);
                fila.put("necesitaRiego", false);
            } else {
                fila.put("sensor", p.getSensor().getCodigo());
                fila.put("humedad", p.getSensor().getHumedadActual());
                fila.put("necesitaRiego", p.getSensor().necesitaRiego());
            }
            filas.add(fila);
        }
        return filas;
    }

    private List<Map<String, Object>> riegos() {
        List<Map<String, Object>> filas = new ArrayList<>();
        for (Riego r : riegoDao.listar()) {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("id", r.getId());
            fila.put("planta", r.getPlanta().getNombreVisible());
            fila.put("mililitros", r.getCantidadMl());
            fila.put("origenAgua", r.getOrigenAgua());
            fila.put("modo", r.getModo());
            fila.put("fechaHora", String.valueOf(r.getFechaHora()));
            filas.add(fila);
        }
        return filas;
    }

    private List<Map<String, Object>> alertas() {
        List<Map<String, Object>> filas = new ArrayList<>();
        for (Alerta a : alertaDao.listarPendientes()) {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("id", a.getId());
            fila.put("planta", a.getPlanta().getNombreVisible());
            fila.put("ubicacion", a.getPlanta().getModulo().toString());
            fila.put("tipo", a.getTipo());
            fila.put("mensaje", a.getMensaje());
            fila.put("fechaHora", String.valueOf(a.getFechaHora()));
            filas.add(fila);
        }
        return filas;
    }
}

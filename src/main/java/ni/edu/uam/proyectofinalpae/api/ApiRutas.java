package ni.edu.uam.proyectofinalpae.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
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
import ni.edu.uam.proyectofinalpae.service.CalendarioCuidadosService;

public class ApiRutas implements HttpHandler {
    private static final System.Logger LOG = System.getLogger(ApiRutas.class.getName());
    private final UsuarioDao usuarioDao = new UsuarioDao();
    private final CultivoDao cultivoDao = new CultivoDao();
    private final ColmenaDao colmenaDao = new ColmenaDao();
    private final PlantaDao plantaDao = new PlantaDao();
    private final RiegoDao riegoDao = new RiegoDao();
    private final AlertaDao alertaDao = new AlertaDao();
    private final CalendarioCuidadosService cuidados = new CalendarioCuidadosService();

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
                case "/api/cuidados" -> responderCuidados(intercambio);
                default -> ApiServidor.responder(intercambio, 404,
                        JsonUtil.aJson(Map.of("error", "Ruta no encontrada: " + ruta)));
            }
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.ERROR, "Error al procesar una ruta de PlantBee: {0}",
                    e.getClass().getSimpleName());
            ApiServidor.responder(intercambio, 500, JsonUtil.aJson(
                    Map.of("error", "Error interno")));
        }
    }

    private void responderCuidados(HttpExchange intercambio) throws IOException {
        LocalDate fecha = null;
        YearMonth mes = null;
        Long plantaId = null;
        String consulta = intercambio.getRequestURI().getRawQuery();
        try {
            if (consulta != null && !consulta.isEmpty()) {
                for (String parametro : consulta.split("&", -1)) {
                    String[] partes = parametro.split("=", 2);
                    if (partes.length != 2) {
                        throw new IllegalArgumentException();
                    }
                    String clave = URLDecoder.decode(partes[0], StandardCharsets.UTF_8);
                    String valor = URLDecoder.decode(partes[1], StandardCharsets.UTF_8);
                    if ("fecha".equals(clave) && fecha == null && valor.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                        fecha = LocalDate.parse(valor);
                        if (fecha.getYear() == 0) {
                            throw new IllegalArgumentException();
                        }
                    } else if ("mes".equals(clave) && mes == null && valor.matches("[0-9]{4}-[0-9]{2}")) {
                        mes = YearMonth.parse(valor);
                        if (mes.getYear() == 0) {
                            throw new IllegalArgumentException();
                        }
                    } else if ("plantaId".equals(clave) && plantaId == null && valor.matches("[0-9]+")) {
                        plantaId = Long.valueOf(valor);
                        if (plantaId <= 0) {
                            throw new IllegalArgumentException();
                        }
                    } else {
                        throw new IllegalArgumentException();
                    }
                }
            }
            if (mes != null && (fecha != null || plantaId != null)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException | DateTimeParseException e) {
            ApiServidor.responder(intercambio, 400,
                    JsonUtil.aJson(Map.of("error",
                            "Use mes en formato AAAA-MM sin otros filtros, o fecha AAAA-MM-DD y plantaId positivo.")));
            return;
        }
        var actividades = mes != null ? cuidados.paraMes(mes)
                : fecha == null ? cuidados.proximos(plantaId) : cuidados.paraFecha(fecha, plantaId);
        ApiServidor.responder(intercambio, 200, JsonUtil.aJson(actividades));
    }

    private List<Map<String, Object>> usuarios() {
        List<Map<String, Object>> filas = new ArrayList<>();
        for (Usuario u : usuarioDao.listar()) {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("id", u.getId());



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
            fila.put("usuarioId", c.getUsuario().getId());
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
            fila.put("plantaId", a.getPlanta().getId());
            var sensor = a.getPlanta().getSensor();
            fila.put("humedadActual", sensor == null ? null : sensor.getHumedadActual());
            fila.put("humedadMinima", a.getPlanta().getCultivo().getHumedadMinima());
            fila.put("mensaje", a.getMensaje());
            fila.put("fechaHora", String.valueOf(a.getFechaHora()));
            filas.add(fila);
        }
        return filas;
    }
}

package ni.edu.uam.proyectofinalpae.service;

import ni.edu.uam.proyectofinalpae.dao.PlantaDao;
import ni.edu.uam.proyectofinalpae.dao.RiegoDao;
import ni.edu.uam.proyectofinalpae.dto.ActividadCuidado;
import ni.edu.uam.proyectofinalpae.model.Planta;
import ni.edu.uam.proyectofinalpae.model.Riego;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static ni.edu.uam.proyectofinalpae.dto.ActividadCuidado.*;

/** Calcula revisiones a partir del estado actual y consulta riegos ya registrados. */
public class CalendarioCuidadosService {
    private final PlantaDao plantas = new PlantaDao();
    private final RiegoDao riegos = new RiegoDao();

    public List<ActividadCuidado> proximos(Long plantaId) {
        return estimar(LocalDateTime.now(), plantaId);
    }

    public List<ActividadCuidado> paraFecha(LocalDate fecha, Long plantaId) {
        return consultar(fecha, fecha.plusDays(1), plantaId);
    }

    public List<ActividadCuidado> paraMes(YearMonth mes) {
        return consultar(mes.atDay(1), mes.plusMonths(1).atDay(1), null);
    }

    private List<ActividadCuidado> consultar(LocalDate inicio, LocalDate fin, Long plantaId) {
        LocalDateTime ahora = LocalDateTime.now();
        List<ActividadCuidado> actividades = new ArrayList<>();
        if (!inicio.isAfter(ahora.toLocalDate())) {
            for (Riego riego : riegos.listarEntre(inicio.atStartOfDay(), fin.atStartOfDay())) {
                if (!riego.getFechaHora().isAfter(ahora) && coincide(riego.getPlanta(), plantaId)) {
                    actividades.add(registrado(riego));
                }
            }
        }
        if (fin.isAfter(ahora.toLocalDate())) {
            for (ActividadCuidado actividad : estimar(ahora, plantaId)) {
                LocalDate fecha = LocalDate.parse(actividad.fecha());
                if (!fecha.isBefore(inicio) && fecha.isBefore(fin)) {
                    actividades.add(actividad);
                }
            }
        }
        return ordenar(actividades);
    }

    private List<ActividadCuidado> estimar(LocalDateTime ahora, Long plantaId) {
        var ultimos = riegos.ultimosHasta(ahora);
        List<ActividadCuidado> actividades = new ArrayList<>();
        for (Planta planta : plantas.listar()) {
            if (coincide(planta, plantaId)) {
                LocalDateTime ultimo = ultimos.get(planta.getId());
                actividades.add(estimar(planta, ultimo == null ? null : ultimo.toLocalDate(), ahora.toLocalDate()));
            }
        }
        return ordenar(actividades);
    }

    static ActividadCuidado estimar(Planta planta, LocalDate ultimo, LocalDate hoy) {
        Integer humedad = planta.getSensor() == null ? null : planta.getSensor().getHumedadActual();
        LocalDate revision = proximaRevision(planta, ultimo);
        if (humedad != null && planta.getSensor().necesitaRiego()) {
            return actividad(planta, hoy, "Atención", ATENCION,
                    "Revisa el sustrato. Si sigue seco, considera regar.",
                    "La última lectura simulada está por debajo del mínimo del cultivo.",
                    ultimo, humedad, hoy);
        }
        if (humedad == null || revision == null) {
            String motivo = humedad == null ? "No hay una lectura de humedad disponible."
                    : ultimo == null ? "La planta no tiene un riego registrado."
                    : "El cultivo no tiene una frecuencia válida.";
            return actividad(planta, hoy, "Revisión", INSUFICIENTE,
                    "Comprueba la humedad de la tierra.", motivo, ultimo, humedad, revision);
        }
        LocalDate fecha = revision.isBefore(hoy) ? hoy : revision;
        String motivo = revision.isBefore(hoy)
                ? "La revisión estimada estaba prevista para el " + revision + "."
                : "La fecha se estima con el último riego y la frecuencia del cultivo.";
        return actividad(planta, fecha, "Revisión", REVISION,
                "La lectura actual indica humedad suficiente. Comprueba la humedad en la fecha indicada.",
                motivo, ultimo, humedad, fecha);
    }

    private static ActividadCuidado registrado(Riego riego) {
        Planta planta = riego.getPlanta();
        LocalDate fecha = riego.getFechaHora().toLocalDate();
        return actividad(planta, fecha, "Riego", REGISTRADO,
                "Se registraron " + riego.getCantidadMl() + " ml de agua.",
                "Origen: " + riego.getOrigenAgua() + " · Modo: " + riego.getModo(),
                fecha, null, proximaRevision(planta, fecha));
    }

    private static LocalDate proximaRevision(Planta planta, LocalDate ultimo) {
        int frecuencia = planta.getCultivo().getFrecuenciaRiegoDias();
        return ultimo != null && frecuencia > 0 ? ultimo.plusDays(frecuencia) : null;
    }

    private static ActividadCuidado actividad(Planta planta, LocalDate fecha, String tipo, String estado,
                                              String descripcion, String motivo, LocalDate ultimo,
                                              Integer humedad, LocalDate revision) {
        return new ActividadCuidado(fecha.toString(), planta.getId(), planta.getNombreVisible(),
                planta.getEstado(), planta.getCultivo().getNombre(), planta.getModulo().getColmena().getNombre(),
                planta.getModulo().getNumeroNivel(), tipo, estado, descripcion, motivo,
                Objects.toString(ultimo, null), humedad, planta.getCultivo().getHumedadMinima(),
                Objects.toString(revision, null));
    }

    private static boolean coincide(Planta planta, Long plantaId) {
        return plantaId == null || plantaId.equals(planta.getId());
    }

    private static List<ActividadCuidado> ordenar(List<ActividadCuidado> actividades) {
        return actividades.stream()
                .sorted(Comparator.comparing(ActividadCuidado::fecha).thenComparing(ActividadCuidado::planta))
                .toList();
    }
}

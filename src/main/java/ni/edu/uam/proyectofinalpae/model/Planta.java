package ni.edu.uam.proyectofinalpae.model;

import jakarta.persistence.*;
import org.hibernate.Hibernate;

import java.time.LocalDate;

/**
 * Planta sembrada en una maceta de un modulo hexagonal.
 * Relaciones:
 *  - Planta (N) -> Cultivo (1)  con @ManyToOne
 *  - Planta (N) -> Modulo (1)   con @ManyToOne
 *  - Planta (1) -> Sensor (1)   con @OneToOne (cascade: el sensor se guarda/elimina junto con la planta)
 */
@Entity
@Table(name = "planta")
public class Planta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 60)
    private String apodo;

    @Column(name = "fecha_siembra", nullable = false)
    private LocalDate fechaSiembra;

    @Column(nullable = false, length = 20)
    private String estado;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cultivo_id", nullable = false)
    private Cultivo cultivo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "modulo_id", nullable = false)
    private Modulo modulo;

    /*
     * Relacion uno a uno: Sensor es el dueno (tiene planta_id UNIQUE) y aqui va mappedBy.
     * cascade = ALL + orphanRemoval: el sensor esta instalado en la maceta y no existe sin su planta,
     * asi que se guarda en el mismo persist de la planta y se borra junto con ella.
     */
    @OneToOne(mappedBy = "planta", cascade = CascadeType.ALL, orphanRemoval = true)
    private Sensor sensor;

    public Planta() {
    }

    /** Nombre para mostrar: el apodo o, si no tiene, el nombre del cultivo. */
    public String getNombreVisible() {
        if (apodo != null && !apodo.isBlank()) {
            return apodo;
        }
        // El cultivo es LAZY: solo se lee si ya esta cargado (evita LazyInitializationException)
        return (cultivo != null && Hibernate.isInitialized(cultivo)) ? cultivo.getNombre() : "Planta " + id;
    }

    public Long getId() {
        return id;
    }

    public String getApodo() {
        return apodo;
    }

    public void setApodo(String apodo) {
        this.apodo = apodo;
    }

    public LocalDate getFechaSiembra() {
        return fechaSiembra;
    }

    public void setFechaSiembra(LocalDate fechaSiembra) {
        this.fechaSiembra = fechaSiembra;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public Cultivo getCultivo() {
        return cultivo;
    }

    public void setCultivo(Cultivo cultivo) {
        this.cultivo = cultivo;
    }

    public Modulo getModulo() {
        return modulo;
    }

    public void setModulo(Modulo modulo) {
        this.modulo = modulo;
    }

    public Sensor getSensor() {
        return sensor;
    }

    /** Mantiene sincronizados los dos lados de la relacion uno a uno. */
    public void setSensor(Sensor sensor) {
        this.sensor = sensor;
        if (sensor != null) {
            sensor.setPlanta(this);
        }
    }

    /** Ej: "Albahaca (Balcon - Nivel 2)". Requiere cultivo, modulo y colmena cargados. */
    @Override
    public String toString() {
        // El modulo tambien es LAZY: llamar toString sobre un proxy sin cargar lo intentaria cargar
        String ubicacion = (modulo != null && Hibernate.isInitialized(modulo)) ? modulo.toString() : "Modulo";
        return getNombreVisible() + " (" + ubicacion + ")";
    }
}

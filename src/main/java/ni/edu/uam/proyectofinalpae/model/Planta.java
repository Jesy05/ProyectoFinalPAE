package ni.edu.uam.proyectofinalpae.model;

import jakarta.persistence.*;

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

    @OneToOne(mappedBy = "planta", cascade = CascadeType.ALL, orphanRemoval = true)
    private Sensor sensor;

    public Planta() {
    }

    /** Nombre para mostrar: el apodo o, si no tiene, el nombre del cultivo. */
    public String getNombreVisible() {
        return (apodo == null || apodo.isBlank()) ? cultivo.getNombre() : apodo;
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
        return getNombreVisible() + " (" + modulo + ")";
    }
}

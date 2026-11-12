package ni.edu.uam.proyectofinalpae.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Historial de riego de una planta. Registra de donde salio el agua, porque PlantBee
 * reutiliza agua de lluvia y agua gris filtrada por el biofiltro.
 * Relacion: Riego (N) -> Planta (1) con @ManyToOne.
 */
@Entity
@Table(name = "riego")
public class Riego {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora = LocalDateTime.now();

    @Column(name = "cantidad_ml", nullable = false)
    private int cantidadMl;

    @Column(name = "origen_agua", nullable = false, length = 40)
    private String origenAgua;

    @Column(nullable = false, length = 20)
    private String modo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "planta_id", nullable = false)
    private Planta planta;

    public Riego() {
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(LocalDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }

    public int getCantidadMl() {
        return cantidadMl;
    }

    public void setCantidadMl(int cantidadMl) {
        this.cantidadMl = cantidadMl;
    }

    public String getOrigenAgua() {
        return origenAgua;
    }

    public void setOrigenAgua(String origenAgua) {
        this.origenAgua = origenAgua;
    }

    public String getModo() {
        return modo;
    }

    public void setModo(String modo) {
        this.modo = modo;
    }

    public Planta getPlanta() {
        return planta;
    }

    public void setPlanta(Planta planta) {
        this.planta = planta;
    }
}

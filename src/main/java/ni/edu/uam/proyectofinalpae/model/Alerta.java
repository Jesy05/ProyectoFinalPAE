package ni.edu.uam.proyectofinalpae.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Aviso que el sistema genera cuando una planta necesita atencion (por ejemplo, humedad baja).
 * Relacion: Alerta (N) -> Planta (1) con @ManyToOne.
 */
@Entity
@Table(name = "alerta")
public class Alerta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora = LocalDateTime.now();

    @Column(nullable = false, length = 30)
    private String tipo;

    @Column(nullable = false, length = 255)
    private String mensaje;

    @Column(nullable = false)
    private boolean atendida = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "planta_id", nullable = false)
    private Planta planta;

    public Alerta() {
    }

    public Alerta(Planta planta, String tipo, String mensaje) {
        this.planta = planta;
        this.tipo = tipo;
        this.mensaje = mensaje;
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public boolean isAtendida() {
        return atendida;
    }

    public void setAtendida(boolean atendida) {
        this.atendida = atendida;
    }

    public Planta getPlanta() {
        return planta;
    }

    public void setPlanta(Planta planta) {
        this.planta = planta;
    }
}

package ni.edu.uam.proyectofinalpae.model;

import jakarta.persistence.*;

/**
 * Sensor de humedad del sustrato instalado en la maceta de una planta.
 * Relacion: Sensor (1) -> Planta (1) con @OneToOne. Esta clase es la duena de la relacion
 * (tiene la llave foranea planta_id, que ademas es UNIQUE).
 */
@Entity
@Table(name = "sensor")
public class Sensor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String codigo;

    /** Lectura actual de humedad del sustrato, de 0 a 100 %. */
    @Column(name = "humedad_actual", nullable = false)
    private int humedadActual;

    @OneToOne(optional = false)
    @JoinColumn(name = "planta_id", nullable = false, unique = true)
    private Planta planta;

    public Sensor() {
    }

    public Sensor(String codigo, int humedadActual) {
        this.codigo = codigo;
        this.humedadActual = humedadActual;
    }

    /** True si la humedad esta por debajo del minimo que pide el cultivo. */
    public boolean necesitaRiego() {
        return humedadActual < planta.getCultivo().getHumedadMinima();
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public int getHumedadActual() {
        return humedadActual;
    }

    public void setHumedadActual(int humedadActual) {
        this.humedadActual = humedadActual;
    }

    public Planta getPlanta() {
        return planta;
    }

    public void setPlanta(Planta planta) {
        this.planta = planta;
    }
}

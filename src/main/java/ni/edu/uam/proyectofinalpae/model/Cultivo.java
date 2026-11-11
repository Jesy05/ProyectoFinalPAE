package ni.edu.uam.proyectofinalpae.model;

import jakarta.persistence.*;

@Entity
@Table(name = "cultivo")
public class Cultivo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String nombre;

    @Column(name = "nombre_cientifico", length = 120)
    private String nombreCientifico;

    @Column(name = "frecuencia_riego_dias", nullable = false)
    private int frecuenciaRiegoDias;

    /** Porcentaje de humedad del sustrato por debajo del cual la planta necesita agua. */
    @Column(name = "humedad_minima", nullable = false)
    private int humedadMinima;

    @Column(name = "luz_requerida", length = 30)
    private String luzRequerida;

    public Cultivo() {
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getNombreCientifico() {
        return nombreCientifico;
    }

    public void setNombreCientifico(String nombreCientifico) {
        this.nombreCientifico = nombreCientifico;
    }

    public int getFrecuenciaRiegoDias() {
        return frecuenciaRiegoDias;
    }

    public void setFrecuenciaRiegoDias(int frecuenciaRiegoDias) {
        this.frecuenciaRiegoDias = frecuenciaRiegoDias;
    }

    public int getHumedadMinima() {
        return humedadMinima;
    }

    public void setHumedadMinima(int humedadMinima) {
        this.humedadMinima = humedadMinima;
    }

    public String getLuzRequerida() {
        return luzRequerida;
    }

    public void setLuzRequerida(String luzRequerida) {
        this.luzRequerida = luzRequerida;
    }

    @Override
    public String toString() {
        return nombre;
    }
}

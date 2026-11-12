package ni.edu.uam.proyectofinalpae.model;
import jakarta.persistence.*;
@Entity
@Table(name = "modulo",
        uniqueConstraints = @UniqueConstraint(columnNames = {"colmena_id", "numero_nivel"}))
public class Modulo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_nivel", nullable = false)
    private int numeroNivel;

    @Column(nullable = false)
    private int capacidad;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "colmena_id", nullable = false)
    private Colmena colmena;

    public Modulo() {
    }

    public Modulo(int numeroNivel, int capacidad) {
        this.numeroNivel = numeroNivel;
        this.capacidad = capacidad;
    }

    public Long getId() {
        return id;
    }

    public int getNumeroNivel() {
        return numeroNivel;
    }

    public void setNumeroNivel(int numeroNivel) {
        this.numeroNivel = numeroNivel;
    }

    public int getCapacidad() {
        return capacidad;
    }

    public void setCapacidad(int capacidad) {
        this.capacidad = capacidad;
    }

    public Colmena getColmena() {
        return colmena;
    }

    public void setColmena(Colmena colmena) {
        this.colmena = colmena;
    }

    /** Ej: "Balcon - Nivel 2". Requiere que la colmena venga cargado (JOIN FETCH). */
    @Override
    public String toString() {
        return colmena.getNombre() + " - Nivel " + numeroNivel;
    }
}



package ni.edu.uam.proyectofinalpae.model;

import jakarta.persistence.*;
import org.hibernate.Hibernate;

/**
 * Modulo hexagonal apilable de una colmena. Cada modulo es un nivel con espacio para varias macetas.
 * Relaciones:
 *  - Modulo (N) -> Colmena (1) con @ManyToOne + @JoinColumn: este lado es el DUENO de la relacion.
 *  - Modulo (1) -> Planta (N), mapeada desde Planta.
 * Una colmena no puede tener dos modulos con el mismo nivel (@UniqueConstraint).
 */
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

    /**
     * Ej: "Colmena del balcon - Nivel 2".
     * La colmena es LAZY: si no vino cargada con JOIN FETCH no se toca, asi el toString nunca lanza
     * LazyInitializationException. Tampoco recorre la lista de modulos de la colmena (evita bucles).
     */
    @Override
    public String toString() {
        String nombreColmena = (colmena != null && Hibernate.isInitialized(colmena))
                ? colmena.getNombre() : "Colmena";
        return nombreColmena + " - Nivel " + numeroNivel;
    }
}

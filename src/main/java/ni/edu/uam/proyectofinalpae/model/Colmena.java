package ni.edu.uam.proyectofinalpae.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Colmena PlantBee: estructura vertical de modulos hexagonales instalada en la casa de un usuario.
 * Relaciones:
 *  - Colmena (N) -> Usuario (1) con @ManyToOne (llave foranea usuario_id).
 *  - Colmena (1) -> Modulo (N) con @OneToMany BIDIRECCIONAL: Modulo es el dueno de la relacion
 *    (tiene @JoinColumn colmena_id) y aqui solo va mappedBy, para que no se cree una tabla intermedia.
 */
@Entity
@Table(name = "colmena")
public class Colmena {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(length = 150)
    private String ubicacion;

    @Column(name = "fecha_instalacion", nullable = false)
    private LocalDate fechaInstalacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /*
     * Por que cascade = ALL y orphanRemoval = true:
     * un modulo es una pieza fisica de la colmena y no existe sin ella (composicion).
     *  - ALL: al guardar una colmena nueva se guardan sus modulos en el mismo persist, y al
     *    eliminarla se eliminan sus modulos. Nunca quedan modulos sueltos.
     *  - orphanRemoval: si un modulo se quita de la lista (quitarModulo), se borra de la base.
     * No se usa cascade en Usuario -> Colmena ni en Planta -> Cultivo: un usuario o un cultivo del
     * catalogo existen por si mismos y no deben borrarse en cadena.
     */
    @OneToMany(mappedBy = "colmena", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("numeroNivel")
    private List<Modulo> modulos = new ArrayList<>();

    public Colmena() {
    }

    /** Metodo de ayuda: agrega el modulo y le asigna esta colmena (sincroniza los dos lados). */
    public void agregarModulo(Modulo modulo) {
        modulos.add(modulo);
        modulo.setColmena(this);
    }

    /**
     * Metodo de ayuda: quita el modulo de la lista y rompe el enlace del otro lado.
     * Por orphanRemoval, Hibernate lo borra de la base al guardar la colmena.
     */
    public void quitarModulo(Modulo modulo) {
        modulos.remove(modulo);
        modulo.setColmena(null);
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

    public String getUbicacion() {
        return ubicacion;
    }

    public void setUbicacion(String ubicacion) {
        this.ubicacion = ubicacion;
    }

    public LocalDate getFechaInstalacion() {
        return fechaInstalacion;
    }

    public void setFechaInstalacion(LocalDate fechaInstalacion) {
        this.fechaInstalacion = fechaInstalacion;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    /** Lista de solo lectura: para agregar o quitar se usan los metodos de ayuda. */
    public List<Modulo> getModulos() {
        return Collections.unmodifiableList(modulos);
    }

    @Override
    public String toString() {
        return nombre;
    }
}
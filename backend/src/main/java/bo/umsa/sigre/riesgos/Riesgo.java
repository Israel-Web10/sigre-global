package bo.umsa.sigre.riesgos;

import java.time.LocalDate;

import bo.umsa.sigre.comun.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Riesgo (clase central del diagrama de clases D02). */
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "riesgo")
public class Riesgo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_riesgo")
    private Integer id;
    @Column(name = "id_filial")
    private Integer idFilial;
    @Column(name = "id_responsable")
    private Integer idResponsable;
    @Column(name = "id_registrador")
    private Integer idRegistrador;
    private String codigo;              // lo genera el trigger con el patrón RSK-CAT-####
    private String titulo;
    private String descripcion;
    @Enumerated(EnumType.STRING)
    private CategoriaRiesgo categoria;
    private String proceso;
    private String causa;
    private String consecuencia;
    @Column(name = "fecha_registro")
    private LocalDate fechaRegistro = LocalDate.now();
    @Enumerated(EnumType.STRING)
    private EstadoRiesgo estado = EstadoRiesgo.IDENTIFICADO;
    private String origen = "MANUAL";

    protected Riesgo() {
    }

    public Riesgo(Integer idFilial, Integer idResponsable, Integer idRegistrador, String titulo, String descripcion,
                  CategoriaRiesgo categoria, String proceso, String causa, String consecuencia, String origen) {
        this.idFilial = idFilial;
        this.idResponsable = idResponsable;
        this.idRegistrador = idRegistrador;
        this.titulo = titulo == null ? null : titulo.trim();
        this.descripcion = descripcion;
        this.categoria = categoria;
        this.proceso = proceso;
        this.causa = causa;
        this.consecuencia = consecuencia;
        this.origen = origen == null ? "MANUAL" : origen;
    }

    /** Cambia el estado respetando la máquina de estados D08. */
    public void cambiarEstado(EstadoRiesgo nuevo) {
        if (!estado.puedePasarA(nuevo)) {
            throw new ReglaNegocioException("D08: Transición de estado no permitida para el riesgo " + codigo + ": " + estado + " → " + nuevo);
        }
        this.estado = nuevo;
    }

    public void actualizar(String titulo, String descripcion, String proceso, String causa, String consecuencia, Integer idResponsable) {
        if (estado == EstadoRiesgo.CERRADO) throw new ReglaNegocioException("D08: Un riesgo CERRADO no se modifica");
        this.titulo = titulo.trim();
        this.descripcion = descripcion;
        this.proceso = proceso;
        this.causa = causa;
        this.consecuencia = consecuencia;
        this.idResponsable = idResponsable;
    }

    public Integer getId() { return id; }
    public Integer getIdFilial() { return idFilial; }
    public Integer getIdResponsable() { return idResponsable; }
    public Integer getIdRegistrador() { return idRegistrador; }
    public String getCodigo() { return codigo; }
    public String getTitulo() { return titulo; }
    public CategoriaRiesgo getCategoria() { return categoria; }
    public EstadoRiesgo getEstado() { return estado; }
}

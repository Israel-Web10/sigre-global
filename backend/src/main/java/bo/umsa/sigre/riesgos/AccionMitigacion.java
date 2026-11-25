package bo.umsa.sigre.riesgos;

import java.math.BigDecimal;
import java.time.LocalDate;

import bo.umsa.sigre.comun.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Acción de un plan de mitigación (CU05). */
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "accion_mitigacion")
public class AccionMitigacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_accion")
    private Integer id;
    @Column(name = "id_plan")
    private Integer idPlan;
    @Column(name = "id_responsable")
    private Integer idResponsable;
    private String descripcion;
    @Column(name = "fecha_limite")
    private LocalDate fechaLimite;
    private BigDecimal costo = BigDecimal.ZERO;
    private Integer avance = 0;
    private String estado = "PENDIENTE";
    private String comentario;

    protected AccionMitigacion() {
    }

    public AccionMitigacion(Integer idPlan, Integer idResponsable, String descripcion, LocalDate fechaLimite, BigDecimal costo) {
        this.idPlan = idPlan;
        this.idResponsable = idResponsable;
        this.descripcion = descripcion;
        this.fechaLimite = fechaLimite;
        this.costo = costo == null ? BigDecimal.ZERO : costo;
    }

    /** CU05: el avance no disminuye; al 100 % la acción se cierra (el trigger exige evidencia). */
    public void registrarAvance(int porcentaje, String comentario) {
        if ("CERRADA".equals(estado)) throw new ReglaNegocioException("CU05: La acción ya está cerrada");
        if (porcentaje < 0 || porcentaje > 100) throw new ReglaNegocioException("CU05: El avance debe estar entre 0 y 100 %");
        if (porcentaje < avance) {
            throw new ReglaNegocioException("CU05: El avance no puede disminuir (de " + avance + " % a " + porcentaje + " %)");
        }
        this.avance = porcentaje;
        this.comentario = comentario;
    }

    public boolean estaVencida() {
        return !"CERRADA".equals(estado) && fechaLimite.isBefore(LocalDate.now());
    }

    public Integer getId() { return id; }
    public Integer getIdPlan() { return idPlan; }
    public Integer getIdResponsable() { return idResponsable; }
    public Integer getAvance() { return avance; }
    public String getEstado() { return estado; }
    public String getDescripcion() { return descripcion; }
}

package bo.umsa.sigre.auditorias;

import java.time.LocalDate;

import bo.umsa.sigre.comun.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Hallazgo de auditoría (CU11) o financiero de una conciliación (CU18). */
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "hallazgo")
public class Hallazgo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_hallazgo")
    private Integer id;
    @Column(name = "id_auditoria")
    private Integer idAuditoria;
    @Column(name = "id_riesgo")
    private Integer idRiesgo;
    @Column(name = "id_conciliacion")
    private Integer idConciliacion;
    @Column(name = "id_responsable")
    private Integer idResponsable;
    @Column(name = "id_registrador")
    private Integer idRegistrador;
    private String descripcion;
    private String severidad;
    private String recomendacion;
    @Column(name = "plan_accion")
    private String planAccion;
    @Column(name = "fecha_limite")
    private LocalDate fechaLimite;
    private String estado = "ABIERTO";
    private Boolean financiero = false;
    @Column(name = "fecha_registro")
    private LocalDate fechaRegistro = LocalDate.now();

    protected Hallazgo() {
    }

    public Hallazgo(Integer idAuditoria, Integer idConciliacion, Integer idRiesgo, Integer idResponsable, Integer idRegistrador,
                    String descripcion, String severidad, String recomendacion, String planAccion, LocalDate fechaLimite) {
        this.idAuditoria = idAuditoria;
        this.idConciliacion = idConciliacion;
        this.idRiesgo = idRiesgo;
        this.idResponsable = idResponsable;
        this.idRegistrador = idRegistrador;
        this.descripcion = descripcion;
        this.severidad = severidad;
        this.recomendacion = recomendacion;
        this.planAccion = planAccion;
        this.fechaLimite = fechaLimite;
        this.financiero = idConciliacion != null;
        validarRn08();
    }

    /** RN08: un hallazgo Alto se vincula a un riesgo y lleva plan de acción. */
    private void validarRn08() {
        if ("ALTA".equals(severidad) && (idRiesgo == null || planAccion == null || planAccion.trim().length() < 5)) {
            throw new ReglaNegocioException("RN08: Un hallazgo de severidad Alta debe vincularse a un riesgo y tener un plan de acción");
        }
    }

    public void vincularRiesgo(Integer idRiesgo) {
        this.idRiesgo = idRiesgo;
    }

    public void cambiarEstado(String nuevo, String planAccion) {
        if ("CERRADO".equals(estado)) throw new ReglaNegocioException("CU11: El hallazgo ya está cerrado");
        if (planAccion != null && !planAccion.isBlank()) this.planAccion = planAccion.trim();
        this.estado = nuevo;
        validarRn08();
    }

    public Integer getId() { return id; }
    public Integer getIdRiesgo() { return idRiesgo; }
    public String getSeveridad() { return severidad; }
    public String getEstado() { return estado; }
}

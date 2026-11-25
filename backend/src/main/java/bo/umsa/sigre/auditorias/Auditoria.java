package bo.umsa.sigre.auditorias;

import java.time.LocalDate;

import bo.umsa.sigre.comun.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Auditoría del plan anual (CU10). Estados: PLANIFICADA, EN_EJECUCION, CERRADA. */
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "auditoria")
public class Auditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_auditoria")
    private Integer id;
    @Column(name = "id_filial")
    private Integer idFilial;
    @Column(name = "id_auditor")
    private Integer idAuditor;
    private String codigo;
    private Integer anio;
    private String tipo;
    private String alcance;
    @Column(name = "fecha_inicio")
    private LocalDate fechaInicio;
    @Column(name = "fecha_fin")
    private LocalDate fechaFin;
    private String estado = "PLANIFICADA";

    protected Auditoria() {
    }

    public Auditoria(Integer idFilial, Integer idAuditor, String tipo, String alcance, LocalDate inicio, LocalDate fin) {
        if (inicio == null || fin == null || fin.isBefore(inicio)) {
            throw new ReglaNegocioException("CU10: La fecha de fin no puede ser anterior a la de inicio");
        }
        this.idFilial = idFilial;
        this.idAuditor = idAuditor;
        this.tipo = tipo;
        this.alcance = alcance;
        this.fechaInicio = inicio;
        this.fechaFin = fin;
        this.anio = inicio.getYear();
    }

    public void iniciar() {
        if (!"PLANIFICADA".equals(estado)) throw new ReglaNegocioException("CU10: Solo se inicia una auditoría PLANIFICADA");
        estado = "EN_EJECUCION";
    }

    public void cerrar(long hallazgosAltosAbiertosSinPlan) {
        if (!"EN_EJECUCION".equals(estado)) throw new ReglaNegocioException("CU10: Solo se cierra una auditoría EN_EJECUCION");
        if (hallazgosAltosAbiertosSinPlan > 0) {
            throw new ReglaNegocioException("RN08: Hay hallazgos de severidad Alta sin plan de acción");
        }
        estado = "CERRADA";
    }

    public Integer getId() { return id; }
    public Integer getIdFilial() { return idFilial; }
    public Integer getIdAuditor() { return idAuditor; }
    public String getCodigo() { return codigo; }
    public String getEstado() { return estado; }
}

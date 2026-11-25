package bo.umsa.sigre.riesgos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import bo.umsa.sigre.comun.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Plan de mitigación (CU03, CU04). Estados: BORRADOR, PROPUESTO, APROBADO, CERRADO. */
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "plan_mitigacion")
public class PlanMitigacion {

    public static final String BORRADOR = "BORRADOR";
    public static final String PROPUESTO = "PROPUESTO";
    public static final String APROBADO = "APROBADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_plan")
    private Integer id;
    @Column(name = "id_riesgo")
    private Integer idRiesgo;
    @Column(name = "id_proponente")
    private Integer idProponente;
    @Column(name = "id_aprobador")
    private Integer idAprobador;
    @Enumerated(EnumType.STRING)
    private Estrategia estrategia;
    @Column(name = "costo_estimado")
    private BigDecimal costoEstimado = BigDecimal.ZERO;
    @Column(name = "fecha_inicio")
    private LocalDate fechaInicio = LocalDate.now();
    @Column(name = "fecha_fin")
    private LocalDate fechaFin;
    private String estado = BORRADOR;
    private String justificacion;
    @Column(name = "motivo_rechazo")
    private String motivoRechazo;
    @Column(name = "fecha_propuesta")
    private OffsetDateTime fechaPropuesta;
    @Column(name = "fecha_aprobacion")
    private OffsetDateTime fechaAprobacion;

    protected PlanMitigacion() {
    }

    public PlanMitigacion(Integer idRiesgo, Integer idProponente, Estrategia estrategia, String justificacion) {
        this.idRiesgo = idRiesgo;
        this.idProponente = idProponente;
        this.estrategia = estrategia;
        this.justificacion = justificacion;
    }

    public void editar(Estrategia estrategia, String justificacion) {
        if (!BORRADOR.equals(estado)) throw new ReglaNegocioException("CU03: Solo se modifica un plan en BORRADOR (está " + estado + ")");
        this.estrategia = estrategia;
        this.justificacion = justificacion;
    }

    /** RN05 y RN12: el plan está completo para proponerse. */
    public boolean estaCompleto(long acciones) {
        if (estrategia == Estrategia.ACEPTAR) return justificacion != null && justificacion.trim().length() >= 10;
        return acciones > 0;
    }

    public void proponer(long acciones) {
        if (!BORRADOR.equals(estado)) throw new ReglaNegocioException("CU03: El plan ya fue propuesto (está " + estado + ")");
        if (!estaCompleto(acciones)) {
            throw new ReglaNegocioException(estrategia == Estrategia.ACEPTAR
                    ? "RN12: Aceptar un riesgo requiere una justificación escrita"
                    : "RN05: El plan debe tener al menos una acción con responsable y fecha límite");
        }
        estado = PROPUESTO;
    }

    public void aprobar(Integer idUsuario) {
        if (!PROPUESTO.equals(estado)) throw new ReglaNegocioException("CU04: Solo se aprueba un plan PROPUESTO (está " + estado + ")");
        this.idAprobador = idUsuario;
        this.estado = APROBADO;
    }

    public void rechazar(String motivo) {
        if (!PROPUESTO.equals(estado)) throw new ReglaNegocioException("CU04: Solo se rechaza un plan PROPUESTO (está " + estado + ")");
        if (motivo == null || motivo.trim().length() < 5) throw new ReglaNegocioException("CU04: El rechazo debe registrar el motivo");
        this.motivoRechazo = motivo.trim();
        this.estado = BORRADOR;
    }

    public Integer getId() { return id; }
    public Integer getIdRiesgo() { return idRiesgo; }
    public Integer getIdProponente() { return idProponente; }
    public Estrategia getEstrategia() { return estrategia; }
    public String getEstado() { return estado; }
}

package bo.umsa.sigre.auditorias;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import bo.umsa.sigre.comun.ReglaNegocioException;
import bo.umsa.sigre.riesgos.EstadoValidacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Validación del impacto financiero por el Auditor Financiero (CU17, RN16). La independencia
 * (RN17) la verifica el trigger chk_independencia antes de insertar.
 */
@Entity
@Table(name = "validacion_financiera")
public class ValidacionFinanciera {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_validacion")
    private Integer id;
    @Column(name = "id_evaluacion")
    private Integer idEvaluacion;
    @Column(name = "id_auditor_fin")
    private Integer idAuditorFin;
    private OffsetDateTime fecha = OffsetDateTime.now();
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    private EstadoValidacion resultado;
    @Column(name = "tipo_cambio")
    private BigDecimal tipoCambio;
    @Column(name = "impacto_usd_validado")
    private BigDecimal impactoUsdValidado;
    private String observacion;

    protected ValidacionFinanciera() {
    }

    public ValidacionFinanciera(Integer idEvaluacion, Integer idAuditorFin, EstadoValidacion resultado,
                                BigDecimal tipoCambio, BigDecimal impactoUsdValidado, String observacion) {
        if (resultado != EstadoValidacion.VALIDADA && resultado != EstadoValidacion.OBSERVADA) {
            throw new ReglaNegocioException("CU17: El resultado debe ser VALIDADA u OBSERVADA");
        }
        if (resultado == EstadoValidacion.OBSERVADA && (observacion == null || observacion.trim().length() < 5)) {
            throw new ReglaNegocioException("CU17: Una validación observada debe explicar la observación");
        }
        this.idEvaluacion = idEvaluacion;
        this.idAuditorFin = idAuditorFin;
        this.resultado = resultado;
        this.tipoCambio = tipoCambio;
        this.impactoUsdValidado = impactoUsdValidado;
        this.observacion = observacion == null || observacion.isBlank() ? null : observacion.trim();
    }

    public Integer getId() { return id; }
    public EstadoValidacion getResultado() { return resultado; }
    public BigDecimal getTipoCambio() { return tipoCambio; }
    public BigDecimal getImpactoUsdValidado() { return impactoUsdValidado; }
}

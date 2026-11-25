package bo.umsa.sigre.riesgos;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Evaluación de probabilidad e impacto (CU02). El nivel lo calcula la base de datos con una
 * columna generada (RN02); la vigencia, el tipo de cambio (RN15) y el estado de validación
 * (RN16) los completa el trigger fn_evaluacion_antes_insertar.
 */
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "evaluacion_riesgo")
public class EvaluacionRiesgo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_evaluacion")
    private Integer id;
    @Column(name = "id_riesgo")
    private Integer idRiesgo;
    @Column(name = "id_evaluador")
    private Integer idEvaluador;
    private LocalDate fecha = LocalDate.now();
    @Enumerated(EnumType.STRING)
    private TipoEvaluacion tipo;
    private Integer probabilidad;
    private Integer impacto;
    @Column(insertable = false, updatable = false)
    private Integer valor;
    @Enumerated(EnumType.STRING)
    @Column(insertable = false, updatable = false)
    private NivelRiesgo nivel;
    @Column(name = "impacto_financiero")
    private BigDecimal impactoFinanciero;
    private String moneda;
    @Column(name = "tipo_cambio")
    private BigDecimal tipoCambio;
    @Column(name = "impacto_usd")
    private BigDecimal impactoUsd;
    @Column(name = "vigente_hasta")
    private LocalDate vigenteHasta;
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "estado_validacion")
    private EstadoValidacion estadoValidacion = EstadoValidacion.NO_REQUERIDA;
    private String comentario;

    protected EvaluacionRiesgo() {
    }

    public EvaluacionRiesgo(Integer idRiesgo, Integer idEvaluador, TipoEvaluacion tipo, int probabilidad, int impacto,
                            BigDecimal impactoFinanciero, String moneda, String comentario) {
        this.idRiesgo = idRiesgo;
        this.idEvaluador = idEvaluador;
        this.tipo = tipo;
        this.probabilidad = probabilidad;
        this.impacto = impacto;
        this.impactoFinanciero = impactoFinanciero;
        this.moneda = moneda;
        this.comentario = comentario;
    }

    /** RN02: nivel a partir de la probabilidad y el impacto. */
    public static NivelRiesgo calcularNivel(int probabilidad, int impacto) {
        return NivelRiesgo.desde(probabilidad * impacto);
    }

    /** RN03: la evaluación está vigente durante 12 meses. */
    public boolean esVigente() {
        return vigenteHasta != null && !vigenteHasta.isBefore(LocalDate.now());
    }

    /** RN16: riesgo financiero Alto/Crítico o de impacto igual o mayor al umbral en USD. */
    public static boolean requiereValidacion(CategoriaRiesgo categoria, TipoEvaluacion tipo, NivelRiesgo nivel,
                                             BigDecimal impactoUsd, BigDecimal umbralUsd) {
        return tipo == TipoEvaluacion.INHERENTE && categoria == CategoriaRiesgo.FINANCIERO
                && (nivel.altoOCritico() || (impactoUsd != null && impactoUsd.compareTo(umbralUsd) >= 0));
    }

    public Integer getId() { return id; }
    public Integer getIdRiesgo() { return idRiesgo; }
    public Integer getIdEvaluador() { return idEvaluador; }
    public LocalDate getFecha() { return fecha; }
    public TipoEvaluacion getTipo() { return tipo; }
    public Integer getValor() { return valor; }
    public NivelRiesgo getNivel() { return nivel; }
    public BigDecimal getImpactoUsd() { return impactoUsd; }
    public BigDecimal getImpactoFinanciero() { return impactoFinanciero; }
    public String getMoneda() { return moneda; }
    public BigDecimal getTipoCambio() { return tipoCambio; }
    public LocalDate getVigenteHasta() { return vigenteHasta; }
    public EstadoValidacion getEstadoValidacion() { return estadoValidacion; }
}

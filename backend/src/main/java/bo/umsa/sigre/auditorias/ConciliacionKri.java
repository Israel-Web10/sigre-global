package bo.umsa.sigre.auditorias;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Conciliación mensual de un KRI financiero con el cierre del ERP (CU18, RN18). */
@Entity
@Table(name = "conciliacion_kri")
public class ConciliacionKri {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_conciliacion")
    private Integer id;
    @Column(name = "id_kri")
    private Integer idKri;
    @Column(name = "id_auditor_fin")
    private Integer idAuditorFin;
    private String periodo;
    @Column(name = "valor_sistema")
    private BigDecimal valorSistema;
    @Column(name = "valor_erp")
    private BigDecimal valorErp;
    @Column(name = "diferencia_pct", insertable = false, updatable = false)
    private BigDecimal diferenciaPct;
    @Enumerated(EnumType.STRING)
    private EstadoConciliacion estado;
    @Column(name = "hallazgo_registrado")
    private Boolean hallazgoRegistrado = false;
    private OffsetDateTime fecha = OffsetDateTime.now();

    protected ConciliacionKri() {
    }

    public ConciliacionKri(Integer idKri, Integer idAuditorFin, String periodo, BigDecimal valorSistema,
                           BigDecimal valorErp, BigDecimal toleranciaPct) {
        this.idKri = idKri;
        this.idAuditorFin = idAuditorFin;
        this.periodo = periodo;
        this.valorSistema = valorSistema;
        this.valorErp = valorErp;
        this.estado = diferencia(valorSistema, valorErp).compareTo(toleranciaPct) <= 0
                ? EstadoConciliacion.CONCILIADA : EstadoConciliacion.CON_DIFERENCIA;
    }

    /** RN18: diferencia relativa respecto del valor del ERP, en porcentaje. */
    public static BigDecimal diferencia(BigDecimal sistema, BigDecimal erp) {
        if (erp.signum() == 0) return sistema.signum() == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(100);
        return sistema.subtract(erp).abs().multiply(BigDecimal.valueOf(100)).divide(erp.abs(), 3, RoundingMode.HALF_UP);
    }

    public Integer getId() { return id; }
    public Integer getIdKri() { return idKri; }
    public String getPeriodo() { return periodo; }
    public BigDecimal getDiferenciaPct() { return diferenciaPct; }
    public EstadoConciliacion getEstado() { return estado; }
    public boolean isHallazgoRegistrado() { return Boolean.TRUE.equals(hallazgoRegistrado); }
}

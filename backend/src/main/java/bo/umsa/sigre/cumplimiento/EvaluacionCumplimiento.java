package bo.umsa.sigre.cumplimiento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import bo.umsa.sigre.comun.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Evaluación del cumplimiento de una obligación (CU13). Si "No cumple" nace un riesgo Legal (RN10). */
@Entity
@Table(name = "evaluacion_cumplimiento")
public class EvaluacionCumplimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_eval_cump")
    private Integer id;
    @Column(name = "id_obligacion")
    private Integer idObligacion;
    @Column(name = "id_evaluador")
    private Integer idEvaluador;
    private LocalDate fecha = LocalDate.now();
    private String resultado;
    private BigDecimal porcentaje;
    private String observacion;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "lista_verificacion")
    private List<Map<String, Object>> listaVerificacion;

    protected EvaluacionCumplimiento() {
    }

    public EvaluacionCumplimiento(Integer idObligacion, Integer idEvaluador, String resultado, String observacion,
                                  List<Map<String, Object>> lista) {
        this.idObligacion = idObligacion;
        this.idEvaluador = idEvaluador;
        this.observacion = observacion;
        this.listaVerificacion = lista;
        this.porcentaje = calcularPorcentaje(lista, resultado);
        this.resultado = resultado == null ? resultadoDe(porcentaje) : resultado;
        if ("NO_CUMPLE".equals(this.resultado) && (observacion == null || observacion.trim().length() < 5)) {
            throw new ReglaNegocioException("CU13: Describa la brecha encontrada: se registrará como riesgo Legal (RN10)");
        }
    }

    /** Porcentaje de puntos de la lista de verificación que se cumplen. */
    static BigDecimal calcularPorcentaje(List<Map<String, Object>> lista, String resultado) {
        if (lista == null || lista.isEmpty()) {
            return BigDecimal.valueOf("CUMPLE".equals(resultado) ? 100 : "PARCIAL".equals(resultado) ? 50 : 0);
        }
        long ok = lista.stream().filter(i -> Boolean.TRUE.equals(i.get("cumple"))).count();
        return BigDecimal.valueOf(Math.round(ok * 10000.0 / lista.size()) / 100.0);
    }

    static String resultadoDe(BigDecimal porcentaje) {
        if (porcentaje.compareTo(BigDecimal.valueOf(100)) >= 0) return "CUMPLE";
        if (porcentaje.compareTo(BigDecimal.valueOf(50)) >= 0) return "PARCIAL";
        return "NO_CUMPLE";
    }

    public Integer getId() { return id; }
    public String getResultado() { return resultado; }
    public BigDecimal getPorcentaje() { return porcentaje; }
}

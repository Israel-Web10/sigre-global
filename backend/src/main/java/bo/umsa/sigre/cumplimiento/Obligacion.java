package bo.umsa.sigre.cumplimiento;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import bo.umsa.sigre.comun.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Obligación concreta de una normativa para una filial (CU12, RN09). */
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "obligacion")
public class Obligacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_obligacion")
    private Integer id;
    @Column(name = "id_normativa")
    private Integer idNormativa;
    @Column(name = "id_filial")
    private Integer idFilial;
    private String descripcion;
    private String periodicidad;
    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;
    @Column(name = "estado_cumplimiento")
    private String estadoCumplimiento = "PENDIENTE";
    @Column(name = "id_riesgo_generado")
    private Integer idRiesgoGenerado;

    protected Obligacion() {
    }

    public Obligacion(Integer idNormativa, Integer idFilial, String descripcion, String periodicidad, LocalDate vencimiento) {
        if (vencimiento == null) throw new ReglaNegocioException("CU12: Indique la fecha de vencimiento de la obligación");
        this.idNormativa = idNormativa;
        this.idFilial = idFilial;
        this.descripcion = descripcion;
        this.periodicidad = periodicidad;
        this.fechaVencimiento = vencimiento;
    }

    /** RN09: días que faltan para el vencimiento (negativo si ya venció). */
    public long diasParaVencer() {
        return ChronoUnit.DAYS.between(LocalDate.now(), fechaVencimiento);
    }

    /** Pasa al siguiente período de una obligación recurrente. */
    public void renovar() {
        fechaVencimiento = switch (periodicidad) {
            case "MENSUAL" -> fechaVencimiento.plusMonths(1);
            case "TRIMESTRAL" -> fechaVencimiento.plusMonths(3);
            case "SEMESTRAL" -> fechaVencimiento.plusMonths(6);
            case "ANUAL" -> fechaVencimiento.plusYears(1);
            default -> throw new ReglaNegocioException("CU13: Una obligación única no se renueva");
        };
        estadoCumplimiento = "PENDIENTE";
    }

    public Integer getId() { return id; }
    public Integer getIdNormativa() { return idNormativa; }
    public Integer getIdFilial() { return idFilial; }
    public String getEstadoCumplimiento() { return estadoCumplimiento; }
    public Integer getIdRiesgoGenerado() { return idRiesgoGenerado; }
}

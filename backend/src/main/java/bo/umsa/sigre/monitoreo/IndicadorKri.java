package bo.umsa.sigre.monitoreo;

import java.math.BigDecimal;

import bo.umsa.sigre.comun.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Indicador clave de riesgo (KRI) con umbrales amarillo y rojo (RN06). */
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "indicador_kri")
public class IndicadorKri {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_kri")
    private Integer id;
    @Column(name = "id_riesgo")
    private Integer idRiesgo;
    private String nombre;
    private String unidad;
    @Column(name = "umbral_amarillo")
    private BigDecimal umbralAmarillo;
    @Column(name = "umbral_rojo")
    private BigDecimal umbralRojo;
    private String direccion = "MAYOR";
    private String frecuencia;
    private String fuente;
    private Boolean financiero = false;
    private Boolean activo = true;

    protected IndicadorKri() {
    }

    public IndicadorKri(Integer idRiesgo, String nombre, String unidad, BigDecimal amarillo, BigDecimal rojo,
                        String direccion, String frecuencia, String fuente, boolean financiero) {
        this.idRiesgo = idRiesgo;
        this.nombre = nombre;
        this.unidad = unidad;
        this.direccion = direccion == null ? "MAYOR" : direccion;
        this.frecuencia = frecuencia;
        this.fuente = fuente;
        this.financiero = financiero;
        cambiarUmbrales(amarillo, rojo);
    }

    /** RN06: clasifica un valor en el semáforo. */
    public Semaforo evaluar(BigDecimal valor) {
        if ("MAYOR".equals(direccion)) {
            if (valor.compareTo(umbralRojo) > 0) return Semaforo.ROJO;
            if (valor.compareTo(umbralAmarillo) > 0) return Semaforo.AMARILLO;
        } else {
            if (valor.compareTo(umbralRojo) < 0) return Semaforo.ROJO;
            if (valor.compareTo(umbralAmarillo) < 0) return Semaforo.AMARILLO;
        }
        return Semaforo.VERDE;
    }

    /** CP19: los umbrales deben ser coherentes con la dirección del indicador. */
    public void cambiarUmbrales(BigDecimal amarillo, BigDecimal rojo) {
        if (amarillo == null || rojo == null) throw new ReglaNegocioException("RN06: Indique los umbrales amarillo y rojo");
        boolean ok = "MAYOR".equals(direccion) ? rojo.compareTo(amarillo) > 0 : rojo.compareTo(amarillo) < 0;
        if (!ok) {
            throw new ReglaNegocioException("MAYOR".equals(direccion)
                    ? "RN06: En un indicador creciente el umbral rojo debe ser mayor que el amarillo"
                    : "RN06: En un indicador decreciente el umbral rojo debe ser menor que el amarillo");
        }
        this.umbralAmarillo = amarillo;
        this.umbralRojo = rojo;
    }

    public void activar(boolean activo) {
        this.activo = activo;
    }

    public Integer getId() { return id; }
    public Integer getIdRiesgo() { return idRiesgo; }
    public String getNombre() { return nombre; }
    public String getUnidad() { return unidad; }
    public String getFuente() { return fuente; }
    public String getDireccion() { return direccion; }
    public BigDecimal getUmbralAmarillo() { return umbralAmarillo; }
    public BigDecimal getUmbralRojo() { return umbralRojo; }
    public boolean isFinanciero() { return Boolean.TRUE.equals(financiero); }
    public boolean isActivo() { return Boolean.TRUE.equals(activo); }
}

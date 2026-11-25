package bo.umsa.sigre.monitoreo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Medición de un KRI (CU06). El semáforo lo asigna el trigger fn_medicion_antes (RN06). */
@Entity
@Table(name = "medicion_kri")
public class MedicionKri {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_medicion")
    private Long id;
    @Column(name = "id_kri")
    private Integer idKri;
    private OffsetDateTime fecha = OffsetDateTime.now();
    private BigDecimal valor;
    private String origen;
    @Enumerated(EnumType.STRING)
    private Semaforo semaforo = Semaforo.VERDE;
    @Column(name = "id_usuario")
    private Integer idUsuario;

    protected MedicionKri() {
    }

    public MedicionKri(Integer idKri, BigDecimal valor, String origen, Integer idUsuario) {
        this.idKri = idKri;
        this.valor = valor;
        this.origen = origen;
        this.idUsuario = idUsuario;
    }

    public Long getId() { return id; }
    public Integer getIdKri() { return idKri; }
    public BigDecimal getValor() { return valor; }
    public Semaforo getSemaforo() { return semaforo; }
    public OffsetDateTime getFecha() { return fecha; }
}

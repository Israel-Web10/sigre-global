package bo.umsa.sigre.riesgos;

import bo.umsa.sigre.comun.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Control existente que reduce el riesgo (preventivo, detectivo o correctivo). */
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "control")
public class Control {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_control")
    private Integer id;
    @Column(name = "id_riesgo")
    private Integer idRiesgo;
    private String descripcion;
    private String tipo;
    private Integer efectividad;

    protected Control() {
    }

    public Control(Integer idRiesgo, String descripcion, String tipo, int efectividad) {
        if (efectividad < 1 || efectividad > 5) throw new ReglaNegocioException("CU02: La efectividad del control va de 1 a 5");
        this.idRiesgo = idRiesgo;
        this.descripcion = descripcion;
        this.tipo = tipo;
        this.efectividad = efectividad;
    }

    public Integer getId() { return id; }
    public Integer getIdRiesgo() { return idRiesgo; }
}

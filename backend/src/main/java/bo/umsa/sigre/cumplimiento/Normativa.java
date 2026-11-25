package bo.umsa.sigre.cumplimiento;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Normativa aplicable por jurisdicción (CU12). */
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "normativa")
public class Normativa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_normativa")
    private Integer id;
    private String nombre;
    private String jurisdiccion;
    private String organismo;
    @Column(name = "vigencia_desde")
    private LocalDate vigenciaDesde;
    private String descripcion;

    protected Normativa() {
    }

    public Normativa(String nombre, String jurisdiccion, String organismo, LocalDate vigenciaDesde, String descripcion) {
        this.nombre = nombre.trim();
        this.jurisdiccion = jurisdiccion;
        this.organismo = organismo;
        this.vigenciaDesde = vigenciaDesde;
        this.descripcion = descripcion;
    }

    public Integer getId() { return id; }
    public String getNombre() { return nombre; }
}

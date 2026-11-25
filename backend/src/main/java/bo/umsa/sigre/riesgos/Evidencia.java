package bo.umsa.sigre.riesgos;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Archivo que respalda el cierre de una acción (CU05) o una evaluación de cumplimiento (CU13). */
@Entity
@Table(name = "evidencia")
public class Evidencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_evidencia")
    private Integer id;
    @Column(name = "id_eval_cump")
    private Integer idEvalCump;
    @Column(name = "id_accion")
    private Integer idAccion;
    @Column(name = "id_usuario")
    private Integer idUsuario;
    private String archivo;
    private String ruta;
    @Column(name = "tipo_mime")
    private String tipoMime;
    private Long tamano;
    @Column(name = "fecha_carga")
    private OffsetDateTime fechaCarga = OffsetDateTime.now();
    private String descripcion;

    protected Evidencia() {
    }

    public static Evidencia deAccion(Integer idAccion, Integer idUsuario, String archivo, String tipoMime, long tamano, String descripcion) {
        Evidencia e = new Evidencia();
        e.idAccion = idAccion;
        e.idUsuario = idUsuario;
        e.archivo = archivo;
        e.tipoMime = tipoMime;
        e.tamano = tamano;
        e.descripcion = descripcion;
        return e;
    }

    public static Evidencia deCumplimiento(Integer idEvalCump, Integer idUsuario, String archivo, String tipoMime, long tamano, String descripcion) {
        Evidencia e = deAccion(null, idUsuario, archivo, tipoMime, tamano, descripcion);
        e.idEvalCump = idEvalCump;
        return e;
    }

    public void ubicar(String ruta) {
        this.ruta = ruta;
    }

    public Integer getId() { return id; }
    public String getArchivo() { return archivo; }
    public String getRuta() { return ruta; }
    public String getTipoMime() { return tipoMime; }
}

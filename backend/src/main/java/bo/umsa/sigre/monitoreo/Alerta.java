package bo.umsa.sigre.monitoreo;

import java.time.OffsetDateTime;

import bo.umsa.sigre.comun.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Alerta (CU07, CU08, CU09). El SLA (RN07) lo fija el trigger al insertarla y las fechas de
 * cada estado las registra fn_alerta_transicion, que también valida el diagrama D09.
 */
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "alerta")
public class Alerta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_alerta")
    private Integer id;
    @Column(name = "id_riesgo")
    private Integer idRiesgo;
    @Column(name = "id_kri")
    private Integer idKri;
    @Column(name = "id_asignado")
    private Integer idAsignado;
    @Column(name = "id_asignado_original")
    private Integer idAsignadoOriginal;
    private String tipo;
    private String severidad;
    private String mensaje;
    @Column(name = "fecha_generacion")
    private OffsetDateTime fechaGeneracion;
    @Column(name = "sla_horas")
    private Integer slaHoras;
    @Column(name = "fecha_limite")
    private OffsetDateTime fechaLimite;
    @Enumerated(EnumType.STRING)
    private EstadoAlerta estado;
    @Column(name = "nivel_escalamiento")
    private Integer nivelEscalamiento;
    @Column(name = "accion_correctiva")
    private String accionCorrectiva;

    protected Alerta() {
    }

    private void pasarA(EstadoAlerta destino) {
        if (!estado.puedePasarA(destino)) {
            throw new ReglaNegocioException("D09: Transición de alerta no permitida: " + estado + " → " + destino);
        }
        estado = destino;
    }

    /** CU07 pasos 4 y 5: el servicio de notificaciones la entregó. */
    public void marcarNotificada() {
        pasarA(EstadoAlerta.NOTIFICADA);
    }

    /** CU08: el responsable toma la alerta. */
    public void atender() {
        pasarA(EstadoAlerta.EN_ATENCION);
    }

    /** CU08: registra la acción correctiva y la resuelve. */
    public void resolver(String accion) {
        if (accion == null || accion.trim().length() < 5) {
            throw new ReglaNegocioException("CU08: Para resolver la alerta registre la acción correctiva");
        }
        accionCorrectiva = accion.trim();
        pasarA(EstadoAlerta.RESUELTA);
    }

    public void cerrar() {
        pasarA(EstadoAlerta.CERRADA);
    }

    /** La solución no fue efectiva: vuelve a atención. */
    public void reabrir() {
        pasarA(EstadoAlerta.EN_ATENCION);
    }

    /** CU09: vencido el SLA se escala al superior jerárquico con un nuevo plazo. */
    public void escalar(Integer idSuperior) {
        if (idSuperior == null) throw new ReglaNegocioException("CU09: El responsable no tiene un superior jerárquico registrado");
        pasarA(EstadoAlerta.ESCALADA);
        idAsignado = idSuperior;
        fechaLimite = OffsetDateTime.now().plusHours(slaHoras);
    }

    public boolean slaVencido() {
        return estado.abierta() && fechaLimite != null && fechaLimite.isBefore(OffsetDateTime.now());
    }

    public String getSlaHorasTexto() {
        return slaHoras + " h";
    }

    public Integer getId() { return id; }
    public Integer getIdRiesgo() { return idRiesgo; }
    public Integer getIdAsignado() { return idAsignado; }
    public Integer getIdAsignadoOriginal() { return idAsignadoOriginal; }
    public String getTipo() { return tipo; }
    public String getSeveridad() { return severidad; }
    public String getMensaje() { return mensaje; }
    public EstadoAlerta getEstado() { return estado; }
    public Integer getNivelEscalamiento() { return nivelEscalamiento; }
    public OffsetDateTime getFechaLimite() { return fechaLimite; }
}

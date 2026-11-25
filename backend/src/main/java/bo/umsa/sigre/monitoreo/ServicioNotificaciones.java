package bo.umsa.sigre.monitoreo;

import static bo.umsa.sigre.comun.Consultas.p;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import bo.umsa.sigre.comun.Consultas;
import bo.umsa.sigre.comun.Dominio;
import bo.umsa.sigre.seguridad.UsuarioSesion;

/**
 * Servicio de notificaciones (sistema externo del diagrama de contexto). Entrega cada alerta
 * GENERADA por la aplicación, el correo y, si es crítica, el SMS; luego la marca NOTIFICADA
 * (CU07 pasos 4 y 5). En la demostración el correo y el SMS quedan en una bandeja simulada.
 */
@Service
public class ServicioNotificaciones {

    private static final Logger log = LoggerFactory.getLogger(ServicioNotificaciones.class);

    private final Consultas bd;
    private final Dominio dominio;
    private final TransactionTemplate tx;

    public ServicioNotificaciones(Consultas bd, Dominio dominio, TransactionTemplate tx) {
        this.bd = bd;
        this.dominio = dominio;
        this.tx = tx;
    }

    public int entregarPendientes() {
        List<Integer> pendientes = bd.jdbc().queryForList(
                "SELECT id_alerta FROM alerta WHERE estado = 'GENERADA' ORDER BY fecha_generacion LIMIT 100", Map.of(), Integer.class);
        int n = 0;
        for (Integer id : pendientes) {
            try {
                tx.executeWithoutResult(t -> entregar(id));
                n++;
            } catch (RuntimeException e) {
                log.warn("No se pudo notificar la alerta {}: {}", id, e.getMessage());
                bd.ejecutar("INSERT INTO incidencia_integracion (sistema, operacion, mensaje) VALUES ('NOTIFICACIONES', 'ENTREGAR_ALERTA', :m)",
                        p("m", "Alerta " + id + ": " + e.getMessage()));
            }
        }
        return n;
    }

    private void entregar(int id) {
        Alerta a = dominio.buscar(Alerta.class, id, "La alerta");
        if (a.getEstado() != EstadoAlerta.GENERADA) return;
        String titulo = switch (a.getSeveridad()) {
            case "CRITICA" -> "Alerta crítica";
            case "ADVERTENCIA" -> "Alerta de advertencia";
            default -> "Alerta preventiva";
        } + " · SLA " + a.getSlaHorasTexto();
        bd.valor("SELECT fn_notificar(:u, :t, :m, 'ALERTA', '/alertas?id=' || :id)",
                p("u", a.getIdAsignado(), "t", titulo, "m", a.getMensaje(), "id", id), Object.class);
        bd.ejecutar("""
                INSERT INTO notificacion (id_usuario, titulo, mensaje, tipo, canal, enlace)
                VALUES (:u, :t, :m, 'ALERTA', 'CORREO', '/alertas')""", p("u", a.getIdAsignado(), "t", "[SIGRE] " + titulo, "m", a.getMensaje()));
        if ("CRITICA".equals(a.getSeveridad())) {
            bd.ejecutar("""
                    INSERT INTO notificacion (id_usuario, titulo, mensaje, tipo, canal, enlace)
                    VALUES (:u, 'SMS SIGRE', :m, 'ALERTA', 'SMS', '/alertas')""", p("u", a.getIdAsignado(), "m", recortar(a.getMensaje(), 150)));
        }
        a.marcarNotificada();
        dominio.sincronizar(a);
    }

    private static String recortar(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n - 1) + "…";
    }

    // ------------------------------------------------------------------ bandeja del usuario

    public Map<String, Object> bandeja(UsuarioSesion u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("no_leidas", bd.valor("SELECT count(*) FROM notificacion WHERE id_usuario = :u AND canal = 'APP' AND NOT leida",
                p("u", u.id()), Long.class));
        m.put("notificaciones", bd.lista("""
                SELECT id_notificacion, titulo, mensaje, tipo, enlace, leida, fecha FROM notificacion
                WHERE id_usuario = :u AND canal = 'APP' ORDER BY fecha DESC LIMIT 60""", p("u", u.id())));
        return m;
    }

    /** Correos y SMS simulados que el servicio de notificaciones envió al usuario (o a todos, para el Administrador). */
    public List<Map<String, Object>> mensajesExternos(UsuarioSesion u) {
        boolean todos = u.tiene(UsuarioSesion.ADMIN);
        return bd.lista("""
                SELECT n.id_notificacion, n.titulo, n.mensaje, n.tipo, n.canal, n.fecha, u.nombre_completo AS destinatario, u.email
                FROM notificacion n JOIN usuario u USING (id_usuario)
                WHERE n.canal IN ('CORREO', 'SMS') AND (:todos OR n.id_usuario = :u)
                ORDER BY n.fecha DESC LIMIT 100""", p("todos", todos, "u", u.id()));
    }

    public void marcarLeida(UsuarioSesion u, long id) {
        bd.ejecutar("UPDATE notificacion SET leida = TRUE WHERE id_notificacion = :id AND id_usuario = :u", p("id", id, "u", u.id()));
    }

    public void marcarTodas(UsuarioSesion u) {
        bd.ejecutar("UPDATE notificacion SET leida = TRUE WHERE id_usuario = :u AND NOT leida", p("u", u.id()));
    }
}

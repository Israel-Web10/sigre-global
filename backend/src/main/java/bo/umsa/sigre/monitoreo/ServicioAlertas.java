package bo.umsa.sigre.monitoreo;

import static bo.umsa.sigre.comun.Consultas.p;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import bo.umsa.sigre.comun.Condiciones;
import bo.umsa.sigre.comun.Consultas;
import bo.umsa.sigre.comun.Dominio;
import bo.umsa.sigre.comun.ReglaNegocioException;
import bo.umsa.sigre.seguridad.UsuarioSesion;

/** Atención (CU08) y escalamiento (CU09) de alertas, con el SLA de RN07. */
@Service
public class ServicioAlertas {

    private static final Logger log = LoggerFactory.getLogger(ServicioAlertas.class);

    private final Consultas bd;
    private final Dominio dominio;
    private final TransactionTemplate tx;

    public ServicioAlertas(Consultas bd, Dominio dominio, TransactionTemplate tx) {
        this.bd = bd;
        this.dominio = dominio;
        this.tx = tx;
    }

    public List<Map<String, Object>> listar(UsuarioSesion u, String estado, String severidad, String tipo, boolean mias, boolean abiertas) {
        Condiciones c = new Condiciones()
                .si(estado, "a.estado = :estado", "estado")
                .si(severidad, "a.severidad = :severidad", "severidad")
                .si(tipo, "a.tipo = :tipo", "tipo");
        if (!u.corporativo()) c.y("(a.id_filial = :mi_filial OR a.id_asignado = :yo)").param("mi_filial", u.idFilial()).param("yo", u.id());
        if (mias) c.y("(a.id_asignado = :yo OR a.id_asignado_original = :yo)").param("yo", u.id());
        if (abiertas) c.y("a.estado NOT IN ('RESUELTA', 'CERRADA')");
        return bd.lista("SELECT a.* FROM v_alerta a WHERE TRUE" + c.sql() + " " + """
                 ORDER BY (a.estado IN ('RESUELTA', 'CERRADA')),
                          CASE a.severidad WHEN 'CRITICA' THEN 0 WHEN 'ADVERTENCIA' THEN 1 ELSE 2 END,
                          a.fecha_limite LIMIT 400""", c.params());
    }

    public Map<String, Object> resumen(UsuarioSesion u) {
        Condiciones c = new Condiciones();
        if (!u.corporativo()) c.y("(a.id_filial = :mi_filial OR a.id_asignado = :yo)").param("mi_filial", u.idFilial()).param("yo", u.id());
        c.param("yo", u.id());
        return bd.uno("""
                SELECT count(*) FILTER (WHERE a.estado NOT IN ('RESUELTA', 'CERRADA')) AS abiertas,
                       count(*) FILTER (WHERE a.estado NOT IN ('RESUELTA', 'CERRADA') AND a.severidad = 'CRITICA') AS criticas,
                       count(*) FILTER (WHERE a.estado NOT IN ('RESUELTA', 'CERRADA') AND a.severidad = 'ADVERTENCIA') AS advertencias,
                       count(*) FILTER (WHERE a.estado NOT IN ('RESUELTA', 'CERRADA') AND a.severidad = 'PREVENTIVA') AS preventivas,
                       count(*) FILTER (WHERE a.sla_vencido) AS sla_vencidas,
                       count(*) FILTER (WHERE a.estado = 'ESCALADA') AS escaladas,
                       count(*) FILTER (WHERE a.id_asignado = :yo AND a.estado NOT IN ('RESUELTA', 'CERRADA')) AS mias,
                       count(*) FILTER (WHERE a.estado IN ('RESUELTA', 'CERRADA') AND a.fecha_resolucion <= a.fecha_limite) AS en_plazo,
                       count(*) FILTER (WHERE a.estado IN ('RESUELTA', 'CERRADA')) AS atendidas
                FROM v_alerta a WHERE TRUE""" + c.sql(), c.params());
    }

    public Map<String, Object> detalle(UsuarioSesion u, int id) {
        Map<String, Object> a = bd.uno("SELECT * FROM v_alerta WHERE id_alerta = :id", p("id", id));
        if (a == null) throw ReglaNegocioException.noEncontrado("La alerta");
        verificarAcceso(u, a);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("alerta", a);
        d.put("historial", bd.lista("""
                SELECT b.fecha_hora, b.operacion, b.resultado, b.detalle, coalesce(u.nombre_completo, 'Sistema') AS usuario
                FROM bitacora b LEFT JOIN usuario u USING (id_usuario)
                WHERE b.entidad = 'ALERTA' AND b.id_entidad = :id ORDER BY b.fecha_hora, b.id_bitacora""", p("id", id)));
        d.put("cadena", bd.lista("""
                WITH RECURSIVE cadena AS (
                    SELECT u.id_usuario, u.nombre_completo, u.cargo, u.id_superior, 0 AS nivel
                    FROM usuario u WHERE u.id_usuario = :orig
                    UNION ALL
                    SELECT s.id_usuario, s.nombre_completo, s.cargo, s.id_superior, c.nivel + 1
                    FROM usuario s JOIN cadena c ON s.id_usuario = c.id_superior WHERE c.nivel < 6)
                SELECT id_usuario, nombre_completo, cargo, nivel FROM cadena ORDER BY nivel""",
                p("orig", a.get("id_asignado_original"))));
        d.put("medicion", a.get("id_medicion") == null ? null
                : bd.uno("SELECT * FROM medicion_kri WHERE id_medicion = :m", p("m", a.get("id_medicion"))));
        d.put("permisos", Map.of(
                "atender", puedeOperar(u, a) && List.of("NOTIFICADA", "ESCALADA", "RESUELTA").contains(a.get("estado")),
                "resolver", puedeOperar(u, a) && "EN_ATENCION".equals(a.get("estado")),
                "cerrar", puedeCerrar(u, a) && "RESUELTA".equals(a.get("estado")),
                "escalar", u.tieneAlguno(UsuarioSesion.DIRECTOR, UsuarioSesion.ADMIN)
                        && List.of("NOTIFICADA", "EN_ATENCION").contains(a.get("estado"))));
        return d;
    }

    private static void verificarAcceso(UsuarioSesion u, Map<String, Object> a) {
        Integer filial = (Integer) a.get("id_filial");
        boolean asignado = ((Number) a.get("id_asignado")).intValue() == u.id();
        if (!asignado && !u.accedeA(filial)) throw ReglaNegocioException.prohibido("RN14: No tiene acceso a esta alerta");
    }

    /** CU08 y Tabla 47: atiende, resuelve y cierra la alerta quien la tiene asignada. */
    private static boolean puedeOperar(UsuarioSesion u, Map<String, Object> a) {
        return ((Number) a.get("id_asignado")).intValue() == u.id();
    }

    private static boolean puedeCerrar(UsuarioSesion u, Map<String, Object> a) {
        return puedeOperar(u, a);
    }

    private Alerta alerta(UsuarioSesion u, int id, boolean cerrar) {
        Map<String, Object> a = bd.uno("SELECT * FROM v_alerta WHERE id_alerta = :id", p("id", id));
        if (a == null) throw ReglaNegocioException.noEncontrado("La alerta");
        verificarAcceso(u, a);
        if (!(cerrar ? puedeCerrar(u, a) : puedeOperar(u, a))) {
            throw ReglaNegocioException.prohibido("CU08: Solo el responsable asignado atiende la alerta");
        }
        return dominio.buscar(Alerta.class, id, "La alerta");
    }

    @Transactional
    public Map<String, Object> atender(UsuarioSesion u, int id) {
        Alerta a = alerta(u, id, false);
        a.atender();
        dominio.sincronizar(a);
        return Map.of("id_alerta", id, "estado", a.getEstado().name());
    }

    @Transactional
    public Map<String, Object> resolver(UsuarioSesion u, int id, String accion) {
        Alerta a = alerta(u, id, false);
        a.resolver(accion);
        dominio.sincronizar(a);
        return Map.of("id_alerta", id, "estado", a.getEstado().name());
    }

    @Transactional
    public Map<String, Object> cerrar(UsuarioSesion u, int id) {
        Alerta a = alerta(u, id, true);
        a.cerrar();
        dominio.sincronizar(a);
        return Map.of("id_alerta", id, "estado", a.getEstado().name());
    }

    @Transactional
    public Map<String, Object> reabrir(UsuarioSesion u, int id) {
        Alerta a = alerta(u, id, true);
        a.reabrir();
        dominio.sincronizar(a);
        return Map.of("id_alerta", id, "estado", a.getEstado().name());
    }

    /** Escalamiento manual (por ejemplo, cuando el responsable está ausente). */
    @Transactional
    public Map<String, Object> escalarManual(UsuarioSesion u, int id) {
        u.exigir("escalar alertas", UsuarioSesion.DIRECTOR, UsuarioSesion.ADMIN);
        Alerta a = dominio.buscar(Alerta.class, id, "La alerta");
        escalar(a, "Escalamiento manual de " + u.nombre());
        return Map.of("id_alerta", id, "estado", a.getEstado().name(), "nivel", a.getNivelEscalamiento());
    }

    /** Superior jerárquico del asignado o, si no tiene, el Director de Riesgos (excepción de CU09). */
    private Integer destinoEscalamiento(Integer asignado) {
        return bd.valor("""
                SELECT coalesce(u.id_superior,
                         (SELECT d.id_usuario FROM usuario d JOIN usuario_rol ur USING (id_usuario) JOIN rol r USING (id_rol)
                          WHERE r.codigo = 'DIRECTOR' AND d.activo AND d.id_usuario <> u.id_usuario ORDER BY d.id_usuario LIMIT 1))
                FROM usuario u WHERE u.id_usuario = :u""", p("u", asignado), Integer.class);
    }

    private void escalar(Alerta a, String motivo) {
        Integer anterior = a.getIdAsignado();
        Integer superior = destinoEscalamiento(anterior);
        a.escalar(superior);
        dominio.sincronizar(a);
        String titulo = "Alerta escalada (nivel " + a.getNivelEscalamiento() + ")";
        String mensaje = a.getMensaje() + " · " + motivo + ". Nuevo plazo: " + a.getSlaHorasTexto();
        bd.valor("SELECT fn_notificar(:u, :t, :m, 'ESCALAMIENTO', '/alertas?id=' || :id)",
                p("u", superior, "t", titulo, "m", mensaje, "id", a.getId()), Object.class);
        bd.ejecutar("""
                INSERT INTO notificacion (id_usuario, titulo, mensaje, tipo, canal, enlace)
                VALUES (:u, :t, :m, 'ESCALAMIENTO', 'CORREO', '/alertas')""", p("u", superior, "t", titulo, "m", mensaje));
        bd.valor("SELECT fn_notificar(:u, 'Su alerta fue escalada', :m, 'ESCALAMIENTO', '/alertas')",
                p("u", anterior, "m", a.getMensaje() + " · se escaló a su superior por " + motivo.toLowerCase()), Object.class);
        // CU09 paso 4: el Director de Riesgos también recibe el aviso del escalamiento
        bd.lista("""
                SELECT fn_notificar(u.id_usuario, 'Escalamiento de alerta', :m, 'ESCALAMIENTO', '/alertas')
                FROM usuario u JOIN usuario_rol ur USING (id_usuario) JOIN rol r USING (id_rol)
                WHERE r.codigo = 'DIRECTOR' AND u.activo AND u.id_usuario <> :s""",
                p("m", "Nivel " + a.getNivelEscalamiento() + ": " + a.getMensaje(), "s", superior));
    }

    /** CU09: alertas notificadas que no se atendieron dentro del SLA. */
    public int escalarVencidas() {
        List<Integer> vencidas = bd.jdbc().queryForList("""
                SELECT a.id_alerta FROM alerta a
                WHERE a.estado IN ('NOTIFICADA', 'EN_ATENCION') AND a.fecha_limite < now()
                ORDER BY a.fecha_limite LIMIT 50""", Map.of(), Integer.class);
        int n = 0;
        for (Integer id : vencidas) {
            try {
                tx.executeWithoutResult(t -> escalarUna(id));
                n++;
            } catch (RuntimeException e) {
                log.warn("No se pudo escalar la alerta {}: {}", id, e.getMessage());
            }
        }
        return n;
    }

    private void escalarUna(int id) {
        Alerta a = dominio.buscar(Alerta.class, id, "La alerta");
        boolean pendiente = a.getEstado() == EstadoAlerta.NOTIFICADA || a.getEstado() == EstadoAlerta.EN_ATENCION;
        if (pendiente && a.slaVencido() && destinoEscalamiento(a.getIdAsignado()) != null) {
            escalar(a, "SLA de " + a.getSlaHorasTexto() + " vencido (RN07)");
        }
    }
}

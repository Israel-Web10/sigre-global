package bo.umsa.sigre.auditorias;

import static bo.umsa.sigre.comun.Consultas.p;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bo.umsa.sigre.comun.Condiciones;
import bo.umsa.sigre.comun.Consultas;
import bo.umsa.sigre.comun.Dominio;
import bo.umsa.sigre.comun.ReglaNegocioException;
import bo.umsa.sigre.riesgos.CategoriaRiesgo;
import bo.umsa.sigre.riesgos.Riesgo;
import bo.umsa.sigre.seguridad.UsuarioSesion;

/** Plan anual de auditorías (CU10) y hallazgos (CU11, RN08). */
@Service
public class ServicioAuditorias {

    private final Consultas bd;
    private final Dominio dominio;

    public ServicioAuditorias(Consultas bd, Dominio dominio) {
        this.bd = bd;
        this.dominio = dominio;
    }

    public record NuevaAuditoria(Integer idFilial, Integer idAuditor, String tipo, String alcance, LocalDate fechaInicio, LocalDate fechaFin) { }

    public record NuevoHallazgo(String descripcion, String severidad, String recomendacion, String planAccion, LocalDate fechaLimite,
                                Integer idResponsable, Integer idRiesgo, Boolean crearRiesgo, CategoriaRiesgo categoriaRiesgo) { }

    public record CambioHallazgo(String estado, String planAccion) { }

    public List<Map<String, Object>> listar(UsuarioSesion u, Integer anio, String estado) {
        Condiciones c = new Condiciones().alcance(u, "a.id_filial")
                .si(anio, "a.anio = :anio", "anio").si(estado, "a.estado = :estado", "estado");
        return bd.lista("""
                SELECT a.*, f.nombre AS filial, f.codigo_pais, u.nombre_completo AS auditor,
                       (SELECT count(*) FROM hallazgo h WHERE h.id_auditoria = a.id_auditoria) AS hallazgos,
                       (SELECT count(*) FROM hallazgo h WHERE h.id_auditoria = a.id_auditoria AND h.severidad = 'ALTA') AS hallazgos_altos,
                       (SELECT count(*) FROM hallazgo h WHERE h.id_auditoria = a.id_auditoria AND h.estado <> 'CERRADO') AS hallazgos_abiertos
                FROM auditoria a JOIN filial f USING (id_filial) JOIN usuario u ON u.id_usuario = a.id_auditor
                WHERE TRUE""" + c.sql() + " ORDER BY a.fecha_inicio", c.params());
    }

    public Map<String, Object> detalle(UsuarioSesion u, int id) {
        Map<String, Object> a = bd.uno("""
                SELECT a.*, f.nombre AS filial, f.codigo_pais, u.nombre_completo AS auditor FROM auditoria a
                JOIN filial f USING (id_filial) JOIN usuario u ON u.id_usuario = a.id_auditor WHERE a.id_auditoria = :id""", p("id", id));
        if (a == null) throw ReglaNegocioException.noEncontrado("La auditoría");
        u.exigirFilial((Integer) a.get("id_filial"));
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("auditoria", a);
        d.put("hallazgos", hallazgos(u, id, null, null));
        d.put("riesgos", bd.lista("""
                SELECT id_riesgo, codigo, titulo, nivel, estado FROM v_riesgo WHERE id_filial = :f AND estado <> 'CERRADO'
                ORDER BY coalesce(valor, 0) DESC""", p("f", a.get("id_filial"))));
        return d;
    }

    public List<Map<String, Object>> hallazgos(UsuarioSesion u, Integer auditoria, String estado, Boolean financiero) {
        Condiciones c = new Condiciones().alcance(u, "coalesce(au.id_filial, r.id_filial)")
                .si(auditoria, "h.id_auditoria = :auditoria", "auditoria")
                .si(estado, "h.estado = :estado", "estado")
                .si(financiero, "h.financiero = :financiero", "financiero");
        return bd.lista("""
                SELECT h.*, au.codigo AS auditoria, r.codigo AS riesgo_codigo, r.titulo AS riesgo_titulo,
                       u.nombre_completo AS responsable, coalesce(f.nombre, fr.nombre) AS filial,
                       (h.estado <> 'CERRADO' AND h.fecha_limite < CURRENT_DATE) AS vencido,
                       c.periodo AS periodo_conciliacion, k.nombre AS kri
                FROM hallazgo h LEFT JOIN auditoria au USING (id_auditoria)
                LEFT JOIN filial f ON f.id_filial = au.id_filial
                LEFT JOIN riesgo r ON r.id_riesgo = h.id_riesgo LEFT JOIN filial fr ON fr.id_filial = r.id_filial
                LEFT JOIN conciliacion_kri c ON c.id_conciliacion = h.id_conciliacion LEFT JOIN indicador_kri k ON k.id_kri = c.id_kri
                JOIN usuario u ON u.id_usuario = h.id_responsable
                WHERE TRUE""" + c.sql() + " ORDER BY (h.estado = 'CERRADO'), CASE h.severidad WHEN 'ALTA' THEN 0 WHEN 'MEDIA' THEN 1 ELSE 2 END, h.fecha_limite",
                c.params());
    }

    /** CU10 paso 3: auditores con su carga y disponibilidad en un rango de fechas. */
    public List<Map<String, Object>> auditores(LocalDate inicio, LocalDate fin) {
        LocalDate i = inicio == null ? LocalDate.now() : inicio;
        LocalDate f = fin == null ? i.plusDays(14) : fin;
        return bd.lista("""
                SELECT u.id_usuario, u.nombre_completo, u.cargo, fi.nombre AS filial, string_agg(DISTINCT r.nombre, ', ') AS roles,
                       (SELECT string_agg(a.codigo, ', ') FROM auditoria a WHERE a.id_auditor = u.id_usuario AND a.estado <> 'CERRADA'
                          AND daterange(a.fecha_inicio, a.fecha_fin, '[]') && daterange(:i, :f, '[]')) AS conflicto,
                       (SELECT count(*) FROM auditoria a WHERE a.id_auditor = u.id_usuario AND a.anio = extract(year FROM CAST(:i AS date))) AS carga_anual
                FROM usuario u JOIN usuario_rol ur USING (id_usuario) JOIN rol r USING (id_rol) JOIN filial fi ON fi.id_filial = u.id_filial
                WHERE u.activo AND r.codigo IN ('AUDITOR_INTERNO', 'AUDITOR_FINANCIERO')
                GROUP BY u.id_usuario, fi.nombre ORDER BY u.nombre_completo""", p("i", i, "f", f));
    }

    @Transactional
    public Map<String, Object> planificar(UsuarioSesion u, NuevaAuditoria n) {
        u.exigir("planificar auditorías", UsuarioSesion.AUDITOR_INTERNO, UsuarioSesion.DIRECTOR);
        if (n.idFilial() == null || n.idAuditor() == null || n.tipo() == null) {
            throw new ReglaNegocioException("CU10: Indique filial, auditor y tipo de auditoría");
        }
        if (n.alcance() == null || n.alcance().trim().length() < 10) throw new ReglaNegocioException("CU10: Describa el alcance de la auditoría");
        String conflicto = bd.valor("""
                SELECT codigo FROM auditoria WHERE id_auditor = :a AND estado <> 'CERRADA'
                  AND daterange(fecha_inicio, fecha_fin, '[]') && daterange(:i, :f, '[]') LIMIT 1""",
                p("a", n.idAuditor(), "i", n.fechaInicio(), "f", n.fechaFin()), String.class);
        if (conflicto != null) {
            throw new ReglaNegocioException("CU10: El auditor no tiene disponibilidad: ya tiene la auditoría " + conflicto + " en esas fechas");
        }
        Auditoria a = dominio.guardar(new Auditoria(n.idFilial(), n.idAuditor(), n.tipo(), n.alcance().trim(), n.fechaInicio(), n.fechaFin()));
        bd.valor("SELECT fn_notificar(:u, 'Auditoría asignada', :m, 'TAREA', '/auditorias')",
                p("u", n.idAuditor(), "m", a.getCodigo() + ": " + n.tipo() + " del " + n.fechaInicio() + " al " + n.fechaFin()), Object.class);
        return Map.of("id_auditoria", a.getId(), "codigo", a.getCodigo());
    }

    private Auditoria auditoria(UsuarioSesion u, int id) {
        Auditoria a = dominio.buscar(Auditoria.class, id, "La auditoría");
        u.exigirFilial(a.getIdFilial());
        return a;
    }

    @Transactional
    public Map<String, Object> iniciar(UsuarioSesion u, int id) {
        u.exigir("ejecutar auditorías", UsuarioSesion.AUDITOR_INTERNO, UsuarioSesion.AUDITOR_FINANCIERO);
        Auditoria a = auditoria(u, id);
        a.iniciar();
        dominio.sincronizar(a);
        return Map.of("id_auditoria", id, "estado", a.getEstado());
    }

    @Transactional
    public Map<String, Object> cerrar(UsuarioSesion u, int id) {
        u.exigir("cerrar auditorías", UsuarioSesion.AUDITOR_INTERNO, UsuarioSesion.AUDITOR_FINANCIERO);
        Auditoria a = auditoria(u, id);
        Long sinPlan = bd.valor("""
                SELECT count(*) FROM hallazgo WHERE id_auditoria = :id AND severidad = 'ALTA'
                  AND (id_riesgo IS NULL OR coalesce(length(trim(plan_accion)), 0) < 5)""", p("id", id), Long.class);
        a.cerrar(sinPlan);
        dominio.sincronizar(a);
        return Map.of("id_auditoria", id, "estado", a.getEstado());
    }

    /** CU11: registra el hallazgo; con severidad Alta exige riesgo y plan (RN08) y puede crear el riesgo. */
    @Transactional
    public Map<String, Object> registrarHallazgo(UsuarioSesion u, int idAuditoria, NuevoHallazgo n) {
        u.exigir("registrar hallazgos", UsuarioSesion.AUDITOR_INTERNO, UsuarioSesion.AUDITOR_FINANCIERO);
        Auditoria a = auditoria(u, idAuditoria);
        if (!"EN_EJECUCION".equals(a.getEstado())) throw new ReglaNegocioException("CU11: Solo se registran hallazgos en auditorías EN_EJECUCION");
        validar(n);
        Integer idRiesgo = n.idRiesgo();
        String codigoNuevo = null;
        if (idRiesgo == null && Boolean.TRUE.equals(n.crearRiesgo())) {
            Riesgo r = dominio.guardar(new Riesgo(a.getIdFilial(), n.idResponsable(), u.id(),
                    tituloRiesgo(n.descripcion()), "Riesgo identificado en la auditoría " + a.getCodigo() + ": " + n.descripcion().trim(),
                    n.categoriaRiesgo() == null ? CategoriaRiesgo.OPERATIVO : n.categoriaRiesgo(), "Auditoría interna",
                    n.descripcion().trim(), "Exposición señalada por el hallazgo de auditoría", "HALLAZGO"));
            idRiesgo = r.getId();
            codigoNuevo = r.getCodigo();
        }
        Hallazgo h = dominio.guardar(new Hallazgo(idAuditoria, null, idRiesgo, n.idResponsable(), u.id(), n.descripcion().trim(),
                n.severidad(), n.recomendacion().trim(), n.planAccion(), n.fechaLimite()));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id_hallazgo", h.getId());
        m.put("id_riesgo", idRiesgo);
        m.put("riesgo_creado", codigoNuevo);
        return m;
    }

    static void validar(NuevoHallazgo n) {
        if (n.descripcion() == null || n.descripcion().trim().length() < 10) throw new ReglaNegocioException("CU11: Describa el hallazgo");
        if (n.recomendacion() == null || n.recomendacion().trim().length() < 5) throw new ReglaNegocioException("CU11: Registre la recomendación");
        if (n.severidad() == null) throw new ReglaNegocioException("CU11: Indique la severidad");
        if (n.idResponsable() == null) throw new ReglaNegocioException("CU11: Asigne un responsable");
        if (n.fechaLimite() == null || n.fechaLimite().isBefore(LocalDate.now())) {
            throw new ReglaNegocioException("CU11: La fecha límite no puede ser anterior a la fecha actual");
        }
        if ("ALTA".equals(n.severidad()) && (n.planAccion() == null || n.planAccion().trim().length() < 5)) {
            throw new ReglaNegocioException("RN08: Un hallazgo de severidad Alta debe tener un plan de acción");
        }
        if ("ALTA".equals(n.severidad()) && n.idRiesgo() == null && !Boolean.TRUE.equals(n.crearRiesgo())) {
            throw new ReglaNegocioException("RN08: Un hallazgo de severidad Alta debe vincularse a un riesgo (existente o nuevo)");
        }
    }

    static String tituloRiesgo(String descripcion) {
        String t = descripcion.trim().replaceAll("\\s+", " ");
        return t.length() > 120 ? t.substring(0, 117) + "..." : t;
    }

    @Transactional
    public Map<String, Object> cambiarHallazgo(UsuarioSesion u, int id, CambioHallazgo c) {
        Hallazgo h = dominio.buscar(Hallazgo.class, id, "El hallazgo");
        Map<String, Object> fila = bd.uno("""
                SELECT h.id_responsable, coalesce(au.id_filial, r.id_filial) AS id_filial FROM hallazgo h
                LEFT JOIN auditoria au USING (id_auditoria) LEFT JOIN riesgo r ON r.id_riesgo = h.id_riesgo WHERE h.id_hallazgo = :id""",
                p("id", id));
        u.exigirFilial((Integer) fila.get("id_filial"));
        boolean responsable = ((Number) fila.get("id_responsable")).intValue() == u.id();
        if (!responsable && !u.tieneAlguno(UsuarioSesion.AUDITOR_INTERNO, UsuarioSesion.AUDITOR_FINANCIERO)) {
            throw ReglaNegocioException.prohibido("CU11: Solo el responsable o el auditor actualizan el hallazgo");
        }
        if ("CERRADO".equals(c.estado()) && !u.tieneAlguno(UsuarioSesion.AUDITOR_INTERNO, UsuarioSesion.AUDITOR_FINANCIERO)) {
            throw ReglaNegocioException.prohibido("CU11: El cierre del hallazgo lo verifica el auditor");
        }
        if (!List.of("ABIERTO", "EN_PROCESO", "CERRADO").contains(c.estado())) throw new ReglaNegocioException("CU11: Estado no válido");
        h.cambiarEstado(c.estado(), c.planAccion());
        dominio.sincronizar(h);
        return Map.of("id_hallazgo", id, "estado", h.getEstado());
    }
}

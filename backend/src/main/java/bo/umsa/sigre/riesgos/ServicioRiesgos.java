package bo.umsa.sigre.riesgos;

import static bo.umsa.sigre.comun.Consultas.p;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bo.umsa.sigre.comun.Condiciones;
import bo.umsa.sigre.comun.Consultas;
import bo.umsa.sigre.comun.Dominio;
import bo.umsa.sigre.comun.ReglaNegocioException;
import bo.umsa.sigre.seguridad.UsuarioSesion;

/** Registro (CU01), evaluación (CU02) y consulta de riesgos. */
@Service
public class ServicioRiesgos {

    private final Consultas bd;
    private final Dominio dominio;

    public ServicioRiesgos(Consultas bd, Dominio dominio) {
        this.bd = bd;
        this.dominio = dominio;
    }

    public record NuevoRiesgo(Integer idFilial, Integer idResponsable, String titulo, String descripcion,
                              CategoriaRiesgo categoria, String proceso, String causa, String consecuencia) { }

    public record NuevaEvaluacion(TipoEvaluacion tipo, Integer probabilidad, Integer impacto,
                                  BigDecimal impactoFinanciero, String moneda, String comentario) { }

    public record NuevoControl(String descripcion, String tipo, Integer efectividad) { }

    // ------------------------------------------------------------------ consultas

    public List<Map<String, Object>> listar(UsuarioSesion u, Integer filial, String categoria, String estado,
                                            String nivel, String texto) {
        Condiciones c = new Condiciones().alcance(u, "v.id_filial")
                .si(filial, "v.id_filial = :filial", "filial")
                .si(categoria, "v.categoria = :categoria", "categoria")
                .si(estado, "v.estado = :estado", "estado")
                .si(nivel, "v.nivel = :nivel", "nivel")
                .si(texto == null ? null : "%" + texto.trim().toLowerCase() + "%",
                        "(lower(v.titulo) LIKE :texto OR lower(v.codigo) LIKE :texto OR lower(v.proceso) LIKE :texto)", "texto");
        return bd.lista("""
                SELECT v.id_riesgo, v.codigo, v.titulo, v.categoria, v.proceso, v.estado, v.origen, v.fecha_registro,
                       v.id_filial, v.filial, v.filial_codigo, v.codigo_pais, v.region, v.responsable, v.id_responsable,
                       v.probabilidad, v.impacto, v.valor, v.nivel, v.impacto_usd, v.estado_validacion,
                       v.evaluacion_vigente, v.vigente_hasta, v.apetito_riesgo, v.estrategia, v.estado_plan,
                       v.avance_plan, v.alertas_abiertas,
                       (SELECT count(*) FROM indicador_kri k WHERE k.id_riesgo = v.id_riesgo) AS kris
                FROM v_riesgo v WHERE TRUE""" + c.sql() + " " + """
                 ORDER BY coalesce(v.valor, 0) DESC, v.codigo""", c.params());
    }

    /** Ficha completa del riesgo con su historia (bitácora). */
    public Map<String, Object> detalle(UsuarioSesion u, int id) {
        Map<String, Object> r = bd.uno("SELECT * FROM v_riesgo WHERE id_riesgo = :id", p("id", id));
        if (r == null) throw ReglaNegocioException.noEncontrado("El riesgo");
        u.exigirFilial((Integer) r.get("id_filial"));
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("riesgo", r);
        var pid = p("id", id);
        d.put("evaluaciones", bd.lista("""
                SELECT e.id_evaluacion, e.fecha, e.tipo, e.probabilidad, e.impacto, e.valor, e.nivel, e.impacto_financiero,
                       e.moneda, e.tipo_cambio, e.impacto_usd, e.vigente_hasta, e.estado_validacion::text AS estado_validacion,
                       e.comentario, u.nombre_completo AS evaluador, (e.vigente_hasta >= CURRENT_DATE) AS vigente,
                       vf.resultado::text AS resultado_validacion, vf.observacion AS observacion_validacion,
                       vf.impacto_usd_validado, vf.fecha AS fecha_validacion, ua.nombre_completo AS auditor_financiero
                FROM evaluacion_riesgo e JOIN usuario u ON u.id_usuario = e.id_evaluador
                LEFT JOIN validacion_financiera vf ON vf.id_evaluacion = e.id_evaluacion
                LEFT JOIN usuario ua ON ua.id_usuario = vf.id_auditor_fin
                WHERE e.id_riesgo = :id ORDER BY e.fecha DESC, e.id_evaluacion DESC""", pid));
        d.put("controles", bd.lista("SELECT * FROM control WHERE id_riesgo = :id ORDER BY id_control", pid));
        d.put("plan", bd.uno("""
                SELECT p.*, up.nombre_completo AS proponente, ua.nombre_completo AS aprobador
                FROM plan_mitigacion p LEFT JOIN usuario up ON up.id_usuario = p.id_proponente
                LEFT JOIN usuario ua ON ua.id_usuario = p.id_aprobador WHERE p.id_riesgo = :id""", pid));
        d.put("acciones", bd.lista("""
                SELECT a.*, u.nombre_completo AS responsable, (a.estado <> 'CERRADA' AND a.fecha_limite < CURRENT_DATE) AS vencida,
                       (SELECT count(*) FROM evidencia ev WHERE ev.id_accion = a.id_accion) AS evidencias
                FROM accion_mitigacion a JOIN plan_mitigacion p USING (id_plan) JOIN usuario u ON u.id_usuario = a.id_responsable
                WHERE p.id_riesgo = :id ORDER BY a.fecha_limite, a.id_accion""", pid));
        d.put("evidencias", bd.lista("""
                SELECT ev.id_evidencia, ev.id_accion, ev.archivo, ev.tipo_mime, ev.tamano, ev.fecha_carga, ev.descripcion,
                       u.nombre_completo AS usuario
                FROM evidencia ev JOIN accion_mitigacion a USING (id_accion) JOIN plan_mitigacion p USING (id_plan)
                LEFT JOIN usuario u ON u.id_usuario = ev.id_usuario WHERE p.id_riesgo = :id ORDER BY ev.fecha_carga DESC""", pid));
        d.put("kris", bd.lista("""
                SELECT k.*, m.valor AS ultimo_valor, m.semaforo, m.fecha AS fecha_medicion,
                       (SELECT array_agg(x.valor ORDER BY x.fecha) FROM (SELECT valor, fecha FROM medicion_kri mm
                         WHERE mm.id_kri = k.id_kri ORDER BY fecha DESC LIMIT 24) x) AS serie
                FROM indicador_kri k
                LEFT JOIN LATERAL (SELECT valor, semaforo, fecha FROM medicion_kri mk WHERE mk.id_kri = k.id_kri
                                   ORDER BY fecha DESC LIMIT 1) m ON TRUE
                WHERE k.id_riesgo = :id ORDER BY k.nombre""", pid));
        d.put("alertas", bd.lista("SELECT * FROM v_alerta WHERE id_riesgo = :id ORDER BY fecha_generacion DESC", pid));
        d.put("hallazgos", bd.lista("""
                SELECT h.*, a.codigo AS auditoria, u.nombre_completo AS responsable FROM hallazgo h
                LEFT JOIN auditoria a USING (id_auditoria) JOIN usuario u ON u.id_usuario = h.id_responsable
                WHERE h.id_riesgo = :id ORDER BY h.fecha_registro DESC""", pid));
        d.put("obligacion", bd.uno("""
                SELECT o.id_obligacion, o.descripcion, n.nombre AS normativa FROM obligacion o JOIN normativa n USING (id_normativa)
                WHERE o.id_riesgo_generado = :id""", pid));
        d.put("historial", historial(id));
        d.put("permisos", permisos(u, r));
        return d;
    }

    /** Historia del riesgo y de todo lo que cuelga de él, leída de la bitácora inalterable (RN13). */
    public List<Map<String, Object>> historial(int id) {
        return bd.lista("""
                SELECT b.id_bitacora, b.fecha_hora, b.operacion, b.entidad, b.id_entidad, b.resultado, b.detalle, b.origen,
                       coalesce(u.nombre_completo, 'Sistema') AS usuario
                FROM bitacora b LEFT JOIN usuario u USING (id_usuario)
                WHERE (b.entidad = 'RIESGO' AND b.id_entidad = :id)
                   OR (b.entidad = 'EVALUACION_RIESGO' AND b.id_entidad IN (SELECT id_evaluacion FROM evaluacion_riesgo WHERE id_riesgo = :id))
                   OR (b.entidad = 'PLAN_MITIGACION' AND b.id_entidad IN (SELECT id_plan FROM plan_mitigacion WHERE id_riesgo = :id))
                   OR (b.entidad = 'ACCION_MITIGACION' AND b.id_entidad IN (SELECT a.id_accion FROM accion_mitigacion a
                          JOIN plan_mitigacion p USING (id_plan) WHERE p.id_riesgo = :id))
                   OR (b.entidad = 'CONTROL' AND (b.detalle -> 'registro' ->> 'id_riesgo')::int = :id)
                   OR (b.entidad = 'ALERTA' AND b.id_entidad IN (SELECT id_alerta FROM alerta WHERE id_riesgo = :id))
                   OR (b.entidad = 'VALIDACION_FINANCIERA' AND b.id_entidad IN (SELECT v.id_validacion FROM validacion_financiera v
                          JOIN evaluacion_riesgo e USING (id_evaluacion) WHERE e.id_riesgo = :id))
                ORDER BY b.fecha_hora DESC, b.id_bitacora DESC LIMIT 150""", p("id", id));
    }

    private Map<String, Object> permisos(UsuarioSesion u, Map<String, Object> r) {
        String estado = (String) r.get("estado");
        String estadoPlan = (String) r.get("estado_plan");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("editar", u.tieneAlguno(UsuarioSesion.GESTOR, UsuarioSesion.GERENTE) && !"CERRADO".equals(estado));
        m.put("evaluar", u.tieneAlguno(UsuarioSesion.GESTOR, UsuarioSesion.DIRECTOR)
                && List.of("IDENTIFICADO", "EVALUADO", "MITIGADO").contains(estado));
        m.put("evaluarResidual", u.tieneAlguno(UsuarioSesion.GESTOR, UsuarioSesion.DIRECTOR)
                && List.of("EN_TRATAMIENTO", "MITIGADO").contains(estado));
        m.put("definirPlan", u.tieneAlguno(UsuarioSesion.GESTOR, UsuarioSesion.RESPONSABLE, UsuarioSesion.GERENTE)
                && "EVALUADO".equals(estado) && (estadoPlan == null || "BORRADOR".equals(estadoPlan)));
        m.put("aprobar", u.tieneAlguno(UsuarioSesion.DIRECTOR, UsuarioSesion.GERENTE) && "PROPUESTO".equals(estadoPlan));
        m.put("cerrar", u.tieneAlguno(UsuarioSesion.GESTOR, UsuarioSesion.DIRECTOR) && List.of("MITIGADO", "ACEPTADO").contains(estado));
        m.put("definirKri", u.tieneAlguno(UsuarioSesion.GESTOR, UsuarioSesion.DIRECTOR) && !"CERRADO".equals(estado));
        return m;
    }

    /** CU01 paso 3: posibles duplicados mientras se escribe el título. */
    public List<Map<String, Object>> similares(UsuarioSesion u, Integer filial, String titulo) {
        if (titulo == null || titulo.trim().length() < 4) return List.of();
        List<String> palabras = new ArrayList<>();
        for (String w : titulo.toLowerCase().split("[^\\p{L}\\p{N}]+")) if (w.length() >= 4) palabras.add(w);
        if (palabras.isEmpty()) return List.of();
        Condiciones c = new Condiciones().alcance(u, "r.id_filial").si(filial, "r.id_filial = :filial", "filial");
        StringBuilder coincide = new StringBuilder("(");
        for (int i = 0; i < palabras.size(); i++) {
            if (i > 0) coincide.append(" + ");
            coincide.append("(CASE WHEN lower(r.titulo) LIKE :w").append(i).append(" THEN 1 ELSE 0 END)");
            c.param("w" + i, "%" + palabras.get(i) + "%");
        }
        coincide.append(")");
        c.param("titulo", titulo.trim().toLowerCase()).param("minimo", Math.max(1, (palabras.size() + 1) / 2));
        return bd.lista("SELECT r.id_riesgo, r.codigo, r.titulo, r.estado, f.nombre AS filial, " + coincide + " AS coincidencias, "
                + "(lower(r.titulo) = :titulo) AS identico FROM riesgo r JOIN filial f USING (id_filial) WHERE " + coincide
                + " >= :minimo" + c.sql() + " ORDER BY identico DESC, coincidencias DESC LIMIT 5", c.params());
    }

    // ------------------------------------------------------------------ CU01

    @Transactional
    public Map<String, Object> registrar(UsuarioSesion u, NuevoRiesgo n) {
        u.exigir("registrar riesgos", UsuarioSesion.GESTOR, UsuarioSesion.GERENTE, UsuarioSesion.DIRECTOR);
        Integer filial = n.idFilial() == null ? u.idFilial() : n.idFilial();
        u.exigirFilial(filial);
        validarTexto(n.titulo(), 5, "el título");
        validarTexto(n.descripcion(), 10, "la descripción");
        validarTexto(n.causa(), 5, "la causa");
        validarTexto(n.consecuencia(), 5, "la consecuencia");
        validarTexto(n.proceso(), 3, "el proceso afectado");
        if (n.categoria() == null) throw new ReglaNegocioException("RN01: Seleccione la categoría del riesgo");
        if (n.idResponsable() == null) throw new ReglaNegocioException("CU01: Asigne un responsable al riesgo");
        Boolean duplicado = bd.valor("SELECT EXISTS (SELECT 1 FROM riesgo WHERE id_filial = :f AND lower(titulo) = lower(trim(:t)))",
                p("f", filial, "t", n.titulo()), Boolean.class);
        if (Boolean.TRUE.equals(duplicado)) {
            throw new ReglaNegocioException("CU01: Ya existe un riesgo con el título \"" + n.titulo().trim() + "\" en esta filial");
        }
        Riesgo r = dominio.guardar(new Riesgo(filial, n.idResponsable(), u.id(), n.titulo(), n.descripcion().trim(),
                n.categoria(), n.proceso().trim(), n.causa().trim(), n.consecuencia().trim(), "MANUAL"));
        return Map.of("id_riesgo", r.getId(), "codigo", r.getCodigo(), "estado", r.getEstado().name());
    }

    @Transactional
    public Map<String, Object> actualizar(UsuarioSesion u, int id, NuevoRiesgo n) {
        u.exigir("modificar riesgos", UsuarioSesion.GESTOR, UsuarioSesion.GERENTE);
        Riesgo r = dominio.buscar(Riesgo.class, id, "El riesgo");
        u.exigirFilial(r.getIdFilial());
        validarTexto(n.titulo(), 5, "el título");
        r.actualizar(n.titulo(), n.descripcion(), n.proceso(), n.causa(), n.consecuencia(),
                n.idResponsable() == null ? r.getIdResponsable() : n.idResponsable());
        dominio.sincronizar(r);
        return Map.of("id_riesgo", r.getId(), "codigo", r.getCodigo());
    }

    private static void validarTexto(String s, int minimo, String campo) {
        if (s == null || s.trim().length() < minimo) {
            throw new ReglaNegocioException("CU01: Complete " + campo + " (mínimo " + minimo + " caracteres)");
        }
    }

    // ------------------------------------------------------------------ CU02 y P03

    /** Prototipo P03: cálculo en vivo del nivel, la conversión (RN15) y la validación requerida (RN16). */
    public Map<String, Object> previsualizar(UsuarioSesion u, int idRiesgo, int probabilidad, int impacto,
                                             BigDecimal impactoFinanciero, String moneda) {
        Map<String, Object> r = bd.uno("SELECT id_riesgo, categoria, id_filial, moneda_filial, apetito_riesgo FROM v_riesgo WHERE id_riesgo = :id",
                p("id", idRiesgo));
        if (r == null) throw ReglaNegocioException.noEncontrado("El riesgo");
        u.exigirFilial((Integer) r.get("id_filial"));
        if (probabilidad < 1 || probabilidad > 5 || impacto < 1 || impacto > 5) {
            throw new ReglaNegocioException("RN02: La probabilidad y el impacto van de 1 a 5");
        }
        int valor = probabilidad * impacto;
        NivelRiesgo nivel = NivelRiesgo.desde(valor);
        String mon = moneda == null || moneda.isBlank() ? (String) r.get("moneda_filial") : moneda.toUpperCase();
        BigDecimal tc = bd.valor("SELECT fn_tipo_cambio(:m, CURRENT_DATE)", p("m", mon), BigDecimal.class);
        BigDecimal usd = impactoFinanciero == null || tc == null ? null : impactoFinanciero.divide(tc, 2, RoundingMode.HALF_UP);
        BigDecimal umbral = bd.valor("SELECT fn_param('umbral_validacion_usd')", Map.of(), BigDecimal.class);
        CategoriaRiesgo cat = CategoriaRiesgo.valueOf((String) r.get("categoria"));
        boolean requiere = EvaluacionRiesgo.requiereValidacion(cat, TipoEvaluacion.INHERENTE, nivel, usd, umbral);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("valor", valor);
        m.put("nivel", nivel.name());
        m.put("moneda", mon);
        m.put("tipo_cambio", tc);
        m.put("impacto_usd", usd);
        m.put("umbral_validacion_usd", umbral);
        m.put("requiere_validacion", requiere);
        m.put("motivo_validacion", !requiere ? null : nivel.altoOCritico()
                ? "Riesgo financiero de nivel " + nivel + " (RN16)"
                : "Impacto igual o mayor a " + umbral.toPlainString() + " USD (RN16)");
        m.put("plazo_validacion", requiere ? bd.valor("SELECT fn_sumar_dias_habiles(CURRENT_DATE, fn_param('plazo_validacion_dias_habiles')::int)",
                Map.of(), LocalDate.class).toString() : null);
        m.put("apetito", r.get("apetito_riesgo"));
        m.put("supera_apetito", valor > ((Number) r.get("apetito_riesgo")).intValue());
        m.put("aprobador", nivel.altoOCritico() ? "Director de Riesgos (RN04)" : "Gerente de filial o Director de Riesgos (RN04)");
        m.put("vigente_hasta", LocalDate.now().plusMonths(12).toString());
        return m;
    }

    @Transactional
    public Map<String, Object> evaluar(UsuarioSesion u, int idRiesgo, NuevaEvaluacion n) {
        u.exigir("evaluar riesgos", UsuarioSesion.GESTOR, UsuarioSesion.DIRECTOR);
        Riesgo r = dominio.buscar(Riesgo.class, idRiesgo, "El riesgo");
        u.exigirFilial(r.getIdFilial());
        if (n.probabilidad() == null || n.impacto() == null || n.probabilidad() < 1 || n.probabilidad() > 5
                || n.impacto() < 1 || n.impacto() > 5) {
            throw new ReglaNegocioException("RN02: La probabilidad y el impacto van de 1 a 5");
        }
        TipoEvaluacion tipo = n.tipo() == null ? TipoEvaluacion.INHERENTE : n.tipo();
        if (r.getCategoria() == CategoriaRiesgo.FINANCIERO && tipo == TipoEvaluacion.INHERENTE && n.impactoFinanciero() == null) {
            throw new ReglaNegocioException("RN15: Un riesgo financiero debe estimar su impacto en moneda local");
        }
        EvaluacionRiesgo e = dominio.guardar(new EvaluacionRiesgo(idRiesgo, u.id(), tipo, n.probabilidad(), n.impacto(),
                n.impactoFinanciero(), n.moneda() == null || n.moneda().isBlank() ? null : n.moneda().toUpperCase(), n.comentario()));
        dominio.em().refresh(r);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id_evaluacion", e.getId());
        m.put("valor", e.getValor());
        m.put("nivel", e.getNivel().name());
        m.put("impacto_usd", e.getImpactoUsd());
        m.put("tipo_cambio", e.getTipoCambio());
        m.put("vigente_hasta", e.getVigenteHasta().toString());
        m.put("estado_validacion", e.getEstadoValidacion().name());
        m.put("estado_riesgo", r.getEstado().name());
        return m;
    }

    // ------------------------------------------------------------------ Controles

    @Transactional
    public Map<String, Object> agregarControl(UsuarioSesion u, int idRiesgo, NuevoControl n) {
        u.exigir("registrar controles", UsuarioSesion.GESTOR, UsuarioSesion.RESPONSABLE, UsuarioSesion.DIRECTOR);
        Riesgo r = dominio.buscar(Riesgo.class, idRiesgo, "El riesgo");
        u.exigirFilial(r.getIdFilial());
        if (n.descripcion() == null || n.descripcion().isBlank()) throw new ReglaNegocioException("CU02: Describa el control");
        Control c = dominio.guardar(new Control(idRiesgo, n.descripcion().trim(), n.tipo(), n.efectividad() == null ? 3 : n.efectividad()));
        return Map.of("id_control", c.getId());
    }

    @Transactional
    public void eliminarControl(UsuarioSesion u, int idRiesgo, int idControl) {
        u.exigir("eliminar controles", UsuarioSesion.GESTOR, UsuarioSesion.DIRECTOR);
        Control c = dominio.buscar(Control.class, idControl, "El control");
        Riesgo r = dominio.buscar(Riesgo.class, idRiesgo, "El riesgo");
        if (!c.getIdRiesgo().equals(idRiesgo)) throw ReglaNegocioException.noEncontrado("El control");
        u.exigirFilial(r.getIdFilial());
        dominio.eliminar(c);
    }
}

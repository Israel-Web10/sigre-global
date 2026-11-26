package bo.umsa.sigre.auditorias;

import static bo.umsa.sigre.comun.Consultas.p;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
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
import bo.umsa.sigre.riesgos.EstadoValidacion;
import bo.umsa.sigre.seguridad.UsuarioSesion;

/** Validación financiera (CU17, RN16, RN17) y conciliación mensual con el ERP (CU18, RN18). */
@Service
public class ServicioFinanciero {

    private final Consultas bd;
    private final Dominio dominio;

    public ServicioFinanciero(Consultas bd, Dominio dominio) {
        this.bd = bd;
        this.dominio = dominio;
    }

    public record Validacion(String resultado, String observacion, BigDecimal tipoCambio, BigDecimal impactoUsdValidado) { }

    public record HallazgoFinanciero(String descripcion, String severidad, String recomendacion, String planAccion,
                                     LocalDate fechaLimite, Integer idResponsable) { }

    // ------------------------------------------------------------------ CU17 (prototipo P02)

    public List<Map<String, Object>> pendientes(UsuarioSesion u) {
        Condiciones c = new Condiciones().alcance(u, "r.id_filial");
        return bd.lista("""
                SELECT e.id_evaluacion, e.fecha, e.probabilidad, e.impacto, e.valor, e.nivel, e.impacto_financiero, e.moneda,
                       e.tipo_cambio, e.impacto_usd, r.id_riesgo, r.codigo, r.titulo, r.categoria, r.id_responsable,
                       f.nombre AS filial, f.codigo_pais, ue.nombre_completo AS evaluador, ur.nombre_completo AS responsable,
                       e.id_evaluador,
                       fn_plazo_validacion(e.id_evaluacion) AS fecha_limite,
                       (SELECT count(*) FROM generate_series(e.fecha + 1, CURRENT_DATE, interval '1 day') d
                         WHERE extract(isodow FROM d) < 6) - fn_dias_pospuestos(e.id_evaluacion) AS dias_habiles_transcurridos,
                       (fn_plazo_validacion(e.id_evaluacion) < CURRENT_DATE) AS fuera_de_plazo,
                       fn_tipo_cambio(e.moneda, e.fecha) AS tipo_cambio_erp,
                       round(coalesce(e.impacto_financiero, 0) / nullif(fn_tipo_cambio(e.moneda, e.fecha), 0), 2) AS impacto_usd_erp,
                       (r.id_responsable = :yo OR e.id_evaluador = :yo) AS conflicto,
                       -- CU17, excepción «ERP no disponible»: el plazo queda en pausa mientras está pospuesta
                       (pp.id_posposicion IS NOT NULL) AS pospuesta, pp.desde AS pospuesta_desde, pp.motivo AS motivo_posposicion,
                       ua.nombre_completo AS pospuesta_por, fn_dias_pospuestos(e.id_evaluacion) AS dias_en_pausa
                FROM evaluacion_riesgo e JOIN riesgo r USING (id_riesgo) JOIN filial f ON f.id_filial = r.id_filial
                JOIN usuario ue ON ue.id_usuario = e.id_evaluador JOIN usuario ur ON ur.id_usuario = r.id_responsable
                LEFT JOIN posposicion_validacion pp ON pp.id_evaluacion = e.id_evaluacion AND pp.hasta IS NULL
                LEFT JOIN usuario ua ON ua.id_usuario = pp.id_auditor_fin
                WHERE e.estado_validacion = 'PENDIENTE'""" + c.sql() + " ORDER BY e.fecha, e.id_evaluacion",
                c.param("yo", u.id()).params());
    }

    public Map<String, Object> contexto(UsuarioSesion u, int idEvaluacion) {
        Map<String, Object> e = pendientes(u).stream().filter(x -> ((Number) x.get("id_evaluacion")).intValue() == idEvaluacion)
                .findFirst().orElseThrow(() -> new ReglaNegocioException("CU17: La evaluación no está pendiente de validación"));
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("evaluacion", e);
        d.put("serie_tipo_cambio", bd.lista("""
                SELECT fecha, unidades_por_usd FROM tipo_cambio WHERE moneda = :m AND fecha BETWEEN CAST(:f AS date) - 30 AND CAST(:f AS date) + 5
                ORDER BY fecha""", p("m", e.get("moneda"), "f", LocalDate.parse((String) e.get("fecha")))));
        d.put("kris_financieros", bd.lista("""
                SELECT k.id_kri, k.nombre, k.unidad, m.valor, m.semaforo, m.fecha FROM indicador_kri k
                LEFT JOIN LATERAL (SELECT valor, semaforo, fecha FROM medicion_kri mk WHERE mk.id_kri = k.id_kri ORDER BY fecha DESC LIMIT 1) m ON TRUE
                WHERE k.id_riesgo = :r""", p("r", e.get("id_riesgo"))));
        d.put("historial", bd.lista("""
                SELECT vf.fecha, vf.resultado::text AS resultado, vf.observacion, vf.impacto_usd_validado, u.nombre_completo AS auditor
                FROM validacion_financiera vf JOIN evaluacion_riesgo ev USING (id_evaluacion) JOIN usuario u ON u.id_usuario = vf.id_auditor_fin
                WHERE ev.id_riesgo = :r ORDER BY vf.fecha DESC""", p("r", e.get("id_riesgo"))));
        return d;
    }

    public List<Map<String, Object>> validaciones(UsuarioSesion u) {
        Condiciones c = new Condiciones().alcance(u, "r.id_filial");
        return bd.lista("""
                SELECT vf.id_validacion, vf.fecha, vf.resultado::text AS resultado, vf.tipo_cambio, vf.impacto_usd_validado, vf.observacion,
                       e.impacto_usd AS impacto_usd_declarado, e.nivel, r.id_riesgo, r.codigo, r.titulo, f.nombre AS filial, f.codigo_pais,
                       u.nombre_completo AS auditor
                FROM validacion_financiera vf JOIN evaluacion_riesgo e USING (id_evaluacion) JOIN riesgo r USING (id_riesgo)
                JOIN filial f ON f.id_filial = r.id_filial JOIN usuario u ON u.id_usuario = vf.id_auditor_fin
                WHERE TRUE""" + c.sql() + " ORDER BY vf.fecha DESC LIMIT 200", c.params());
    }

    /** Comprobaciones comunes de CU17: existe, está en el alcance del auditor (RN14), sin conflicto (RN17) y PENDIENTE. */
    private Map<String, Object> pendienteDeValidar(UsuarioSesion u, int idEvaluacion) {
        Map<String, Object> e = bd.uno("""
                SELECT e.estado_validacion::text AS estado_validacion, e.id_evaluador, r.id_responsable, r.id_filial, r.codigo
                FROM evaluacion_riesgo e JOIN riesgo r USING (id_riesgo) WHERE e.id_evaluacion = :e""", p("e", idEvaluacion));
        if (e == null) throw ReglaNegocioException.noEncontrado("La evaluación");
        u.exigirFilial((Integer) e.get("id_filial"));
        if (((Number) e.get("id_responsable")).intValue() == u.id() || ((Number) e.get("id_evaluador")).intValue() == u.id()) {
            throw new ReglaNegocioException("RN17: conflicto de funciones: el auditor es responsable o evaluador del riesgo " + e.get("codigo"));
        }
        if (!"PENDIENTE".equals(e.get("estado_validacion"))) {
            throw new ReglaNegocioException("CU17: La evaluación no está pendiente de validación (estado " + e.get("estado_validacion") + ")");
        }
        return e;
    }

    public record Posposicion(String motivo) { }

    private Map<String, Object> plazo(int idEvaluacion) {
        return bd.uno("""
                SELECT fn_plazo_validacion(:e) AS fecha_limite, fn_dias_pospuestos(:e) AS dias_en_pausa,
                       (fn_plazo_validacion(:e) < CURRENT_DATE) AS fuera_de_plazo""", p("e", idEvaluacion));
    }

    /**
     * CU17, excepción «ERP no disponible»: pospone la validación sin que venza el plazo de 3 días hábiles.
     * El trigger fn_posposicion_antes_insertar vuelve a comprobar RN16/RN17 y abre la incidencia del ERP.
     */
    @Transactional
    public Map<String, Object> posponer(UsuarioSesion u, int idEvaluacion, Posposicion p) {
        u.exigir("posponer la validación financiera", UsuarioSesion.AUDITOR_FINANCIERO);
        pendienteDeValidar(u, idEvaluacion);
        String motivo = p == null || p.motivo() == null ? "" : p.motivo().trim();
        if (motivo.length() < 10) {
            throw new ReglaNegocioException("CU17: Explique en al menos 10 caracteres por qué el ERP no está disponible");
        }
        if (motivo.length() > 300) throw new ReglaNegocioException("CU17: El motivo admite hasta 300 caracteres");
        Map<String, Object> r = new LinkedHashMap<>(bd.uno("""
                INSERT INTO posposicion_validacion (id_evaluacion, id_auditor_fin, motivo) VALUES (:e, :a, :m)
                RETURNING id_posposicion, id_incidencia, desde""", p("e", idEvaluacion, "a", u.id(), "m", motivo)));
        r.putAll(plazo(idEvaluacion));
        return r;
    }

    /** El ERP volvió a responder: se resuelve la incidencia y el plazo sigue corriendo desde donde quedó. */
    @Transactional
    public Map<String, Object> reanudar(UsuarioSesion u, int idEvaluacion) {
        u.exigir("reanudar el plazo de validación", UsuarioSesion.AUDITOR_FINANCIERO);
        pendienteDeValidar(u, idEvaluacion);
        Map<String, Object> pp = bd.uno("""
                SELECT id_posposicion, id_incidencia FROM posposicion_validacion WHERE id_evaluacion = :e AND hasta IS NULL""",
                p("e", idEvaluacion));
        if (pp == null) throw new ReglaNegocioException("CU17: La validación no está pospuesta");
        if (pp.get("id_incidencia") != null) {
            bd.ejecutar("UPDATE incidencia_integracion SET resuelta = TRUE WHERE id_incidencia = :i AND NOT resuelta",
                    p("i", pp.get("id_incidencia")));
        }
        bd.ejecutar("UPDATE posposicion_validacion SET hasta = now() WHERE id_posposicion = :p AND hasta IS NULL",
                p("p", pp.get("id_posposicion")));
        Map<String, Object> r = new LinkedHashMap<>(pp);
        r.putAll(plazo(idEvaluacion));
        return r;
    }

    @Transactional
    public Map<String, Object> validar(UsuarioSesion u, int idEvaluacion, Validacion v) {
        u.exigir("validar el impacto financiero", UsuarioSesion.AUDITOR_FINANCIERO);
        pendienteDeValidar(u, idEvaluacion);
        EstadoValidacion resultado;
        try {
            resultado = EstadoValidacion.valueOf(v.resultado());
        } catch (RuntimeException ex) {
            throw new ReglaNegocioException("CU17: El resultado debe ser VALIDADA u OBSERVADA");
        }
        ValidacionFinanciera vf = dominio.guardar(new ValidacionFinanciera(idEvaluacion, u.id(), resultado, v.tipoCambio(),
                v.impactoUsdValidado(), v.observacion()));
        return Map.of("id_validacion", vf.getId(), "resultado", vf.getResultado().name(),
                "impacto_usd_validado", vf.getImpactoUsdValidado(), "tipo_cambio", vf.getTipoCambio());
    }

    // ------------------------------------------------------------------ CU18

    public List<String> periodos() {
        List<String> r = new ArrayList<>(bd.jdbc().queryForList(
                "SELECT DISTINCT periodo FROM cierre_erp_kri ORDER BY periodo DESC", Map.of(), String.class));
        String anterior = YearMonth.now().minusMonths(1).toString();
        if (!r.contains(anterior)) r.add(0, anterior);
        return r;
    }

    /** Estado de la conciliación de un período: valor del sistema, cierre del ERP y resultado. */
    private static YearMonth periodo(String texto) {
        try {
            return texto == null || texto.isBlank() ? YearMonth.now().minusMonths(1) : YearMonth.parse(texto.trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw new ReglaNegocioException("CU18: Período inválido; use el formato AAAA-MM", org.springframework.http.HttpStatus.BAD_REQUEST);
        }
    }

    public Map<String, Object> conciliacion(UsuarioSesion u, String periodo, Integer filial) {
        YearMonth ym = periodo(periodo);
        String per = ym.toString();
        Condiciones c = new Condiciones().alcance(u, "r.id_filial").si(filial, "r.id_filial = :filial", "filial")
                .param("per", per).param("ini", ym.atDay(1)).param("fin", ym.plusMonths(1).atDay(1))
                .param("tol", bd.valor("SELECT fn_param('tolerancia_conciliacion_pct')", Map.of(), BigDecimal.class));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("periodo", per);
        m.put("tolerancia_pct", c.params().get("tol"));
        m.put("indicadores", bd.lista("""
                SELECT k.id_kri, k.nombre, k.unidad, r.id_riesgo, r.codigo, r.titulo, r.id_filial, f.nombre AS filial, f.codigo_pais,
                       (SELECT valor FROM medicion_kri mk WHERE mk.id_kri = k.id_kri AND mk.fecha >= :ini AND mk.fecha < :fin
                        ORDER BY mk.fecha DESC LIMIT 1) AS valor_sistema_actual,
                       ce.valor AS valor_erp, c.id_conciliacion, c.valor_sistema, c.diferencia_pct, c.estado, c.hallazgo_registrado,
                       c.fecha AS fecha_conciliacion, ua.nombre_completo AS auditor,
                       (SELECT h.id_hallazgo FROM hallazgo h WHERE h.id_conciliacion = c.id_conciliacion LIMIT 1) AS id_hallazgo
                FROM indicador_kri k JOIN riesgo r USING (id_riesgo) JOIN filial f ON f.id_filial = r.id_filial
                LEFT JOIN cierre_erp_kri ce ON ce.id_kri = k.id_kri AND ce.periodo = :per
                LEFT JOIN conciliacion_kri c ON c.id_kri = k.id_kri AND c.periodo = :per
                LEFT JOIN usuario ua ON ua.id_usuario = c.id_auditor_fin
                WHERE k.financiero""" + c.sql() + " ORDER BY f.nombre, k.nombre", c.params()));
        return m;
    }

    /** CU18: concilia los KRI financieros del período que tengan cierre del ERP y aún no estén conciliados. */
    @Transactional
    public Map<String, Object> ejecutar(UsuarioSesion u, String periodo, Integer filial) {
        u.exigir("conciliar con el ERP", UsuarioSesion.AUDITOR_FINANCIERO);
        String per = periodo(periodo).toString();
        if (YearMonth.parse(per).isAfter(YearMonth.now().minusMonths(1))) {
            throw new ReglaNegocioException("CU18: Solo se concilian períodos cerrados (hasta " + YearMonth.now().minusMonths(1) + ")");
        }
        BigDecimal tol = bd.valor("SELECT fn_param('tolerancia_conciliacion_pct')", Map.of(), BigDecimal.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> filas = (List<Map<String, Object>>) conciliacion(u, per, filial).get("indicadores");
        int conciliadas = 0, diferencias = 0, sinDatos = 0;
        List<String> detalle = new ArrayList<>();
        for (Map<String, Object> f : filas) {
            if (f.get("id_conciliacion") != null) continue;
            BigDecimal sistema = (BigDecimal) f.get("valor_sistema_actual");
            BigDecimal erp = (BigDecimal) f.get("valor_erp");
            if (sistema == null || erp == null) {
                sinDatos++;
                detalle.add(f.get("filial") + " · " + f.get("nombre") + ": sin " + (erp == null ? "cierre del ERP" : "medición del período"));
                continue;
            }
            ConciliacionKri c = dominio.guardar(new ConciliacionKri(((Number) f.get("id_kri")).intValue(), u.id(), per, sistema, erp, tol));
            if (c.getEstado() == EstadoConciliacion.CONCILIADA) conciliadas++;
            else diferencias++;
            detalle.add(f.get("filial") + " · " + f.get("nombre") + ": " + c.getEstado() + " (" + c.getDiferenciaPct().stripTrailingZeros().toPlainString() + " %)");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("periodo", per);
        m.put("conciliadas", conciliadas);
        m.put("con_diferencia", diferencias);
        m.put("sin_datos", sinDatos);
        m.put("detalle", detalle);
        return m;
    }

    /** CU18 paso 6 (O7): registra el hallazgo financiero de una conciliación con diferencia. */
    @Transactional
    public Map<String, Object> registrarHallazgo(UsuarioSesion u, int idConciliacion, HallazgoFinanciero n) {
        u.exigir("registrar hallazgos financieros", UsuarioSesion.AUDITOR_FINANCIERO);
        ConciliacionKri c = dominio.buscar(ConciliacionKri.class, idConciliacion, "La conciliación");
        Map<String, Object> k = bd.uno("""
                SELECT r.id_riesgo, r.id_filial, r.id_responsable FROM indicador_kri k JOIN riesgo r USING (id_riesgo) WHERE k.id_kri = :k""",
                p("k", c.getIdKri()));
        u.exigirFilial((Integer) k.get("id_filial"));
        if (c.getEstado() != EstadoConciliacion.CON_DIFERENCIA) {
            throw new ReglaNegocioException("RN18: Solo una conciliación CON_DIFERENCIA origina un hallazgo financiero");
        }
        if (c.isHallazgoRegistrado()) throw new ReglaNegocioException("CU18: El hallazgo de esta conciliación ya fue registrado");
        Integer responsable = n.idResponsable() == null ? (Integer) k.get("id_responsable") : n.idResponsable();
        ServicioAuditorias.validar(new ServicioAuditorias.NuevoHallazgo(n.descripcion(), n.severidad(), n.recomendacion(),
                n.planAccion(), n.fechaLimite(), responsable, (Integer) k.get("id_riesgo"), false, null));
        Hallazgo h = dominio.guardar(new Hallazgo(null, idConciliacion, (Integer) k.get("id_riesgo"), responsable, u.id(),
                n.descripcion().trim(), n.severidad(), n.recomendacion().trim(), n.planAccion(), n.fechaLimite()));
        // Al quedar registrado el hallazgo, la alerta de la diferencia se da por atendida
        bd.lista("SELECT fn_cerrar_alerta_auto(id_alerta, 'Hallazgo financiero registrado (RN18)') FROM alerta "
                + "WHERE id_conciliacion = :c AND estado NOT IN ('RESUELTA', 'CERRADA')", p("c", idConciliacion));
        return Map.of("id_hallazgo", h.getId(), "id_conciliacion", idConciliacion);
    }
}

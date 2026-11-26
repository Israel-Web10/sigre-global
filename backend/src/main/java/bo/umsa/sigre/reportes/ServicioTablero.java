package bo.umsa.sigre.reportes;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import bo.umsa.sigre.comun.Condiciones;
import bo.umsa.sigre.comun.Consultas;
import bo.umsa.sigre.seguridad.UsuarioSesion;

/** Tablero ejecutivo (CU14, prototipo P01) con el alcance por filial de RN14. */
@Service
public class ServicioTablero {

    private final Consultas bd;

    public ServicioTablero(Consultas bd) {
        this.bd = bd;
    }

    public Map<String, Object> tablero(UsuarioSesion u, Integer filial, String categoria, String region, int dias) {
        int periodo = Math.max(7, Math.min(dias, 366));
        Condiciones c = new Condiciones().alcance(u, "v.id_filial")
                .si(filial, "v.id_filial = :filial", "filial")
                .si(categoria, "v.categoria = :categoria", "categoria")
                // RF17: filtro por región (se expresa sobre la filial para reutilizarlo en alertas y obligaciones)
                .si(region, "v.id_filial IN (SELECT id_filial FROM filial WHERE region = :region)", "region")
                .param("dias", periodo);
        String w = " WHERE v.estado <> 'CERRADO'" + c.sql();
        Map<String, Object> p = c.params();
        Map<String, Object> t = new LinkedHashMap<>();

        Map<String, Object> kpi = new LinkedHashMap<>(bd.uno("""
                SELECT count(*) AS riesgos_activos,
                       count(*) FILTER (WHERE v.nivel = 'CRITICO') AS criticos,
                       count(*) FILTER (WHERE v.nivel = 'ALTO') AS altos,
                       count(*) FILTER (WHERE v.estado = 'EN_TRATAMIENTO') AS en_tratamiento,
                       count(*) FILTER (WHERE v.estado = 'MITIGADO') AS mitigados,
                       count(*) FILTER (WHERE v.estado = 'IDENTIFICADO') AS sin_evaluar,
                       count(*) FILTER (WHERE v.estado_plan = 'PROPUESTO') AS planes_por_aprobar,
                       count(*) FILTER (WHERE v.valor > v.apetito_riesgo) AS sobre_apetito,
                       count(*) FILTER (WHERE NOT coalesce(v.evaluacion_vigente, TRUE)) AS evaluaciones_vencidas,
                       coalesce(sum(v.impacto_usd), 0) AS impacto_usd_total,
                       coalesce(sum(v.impacto_usd) FILTER (WHERE v.estado_validacion = 'VALIDADA'), 0) AS impacto_usd_validado,
                       count(*) FILTER (WHERE v.estado_validacion = 'PENDIENTE') AS validaciones_pendientes,
                       round(avg(v.valor), 1) AS nivel_promedio
                FROM v_riesgo v""" + w, p));
        kpi.putAll(bd.uno("""
                SELECT count(*) FILTER (WHERE a.estado NOT IN ('RESUELTA', 'CERRADA')) AS alertas_abiertas,
                       count(*) FILTER (WHERE a.estado NOT IN ('RESUELTA', 'CERRADA') AND a.severidad = 'CRITICA') AS alertas_criticas,
                       count(*) FILTER (WHERE a.sla_vencido) AS sla_vencidas,
                       count(*) FILTER (WHERE a.estado = 'ESCALADA') AS escaladas,
                       round(100.0 * count(*) FILTER (WHERE a.estado IN ('RESUELTA', 'CERRADA') AND a.fecha_resolucion <= a.fecha_limite)
                             / nullif(count(*) FILTER (WHERE a.estado IN ('RESUELTA', 'CERRADA')), 0), 1) AS sla_cumplido_pct
                FROM v_alerta a LEFT JOIN v_riesgo v ON v.id_riesgo = a.id_riesgo
                WHERE TRUE""" + c.sql().replace("v.id_filial", "a.id_filial"), p));
        kpi.putAll(bd.uno("""
                SELECT count(*) AS kri_total, count(*) FILTER (WHERE m.semaforo = 'ROJO') AS kri_rojos,
                       count(*) FILTER (WHERE m.semaforo = 'AMARILLO') AS kri_amarillos
                FROM indicador_kri k JOIN v_riesgo v USING (id_riesgo)
                LEFT JOIN LATERAL (SELECT semaforo FROM medicion_kri mk WHERE mk.id_kri = k.id_kri ORDER BY fecha DESC LIMIT 1) m ON TRUE
                WHERE k.activo""" + c.sql(), p));
        kpi.putAll(bd.uno("""
                SELECT round(100.0 * count(*) FILTER (WHERE o.estado_cumplimiento = 'CUMPLE')
                             / nullif(count(*) FILTER (WHERE o.estado_cumplimiento <> 'PENDIENTE'), 0), 1) AS cumplimiento_pct,
                       count(*) FILTER (WHERE o.estado_cumplimiento = 'PENDIENTE' AND o.fecha_vencimiento <= CURRENT_DATE + 30) AS obligaciones_por_vencer,
                       count(*) FILTER (WHERE o.estado_cumplimiento = 'NO_CUMPLE') AS incumplimientos
                FROM obligacion o WHERE TRUE""" + c.sql().replace("v.id_filial", "o.id_filial").replaceAll(" AND v\\.categoria = :categoria", ""), p));
        t.put("kpi", kpi);

        t.put("matriz", bd.lista("""
                SELECT v.probabilidad, v.impacto, count(*) AS total,
                       (array_agg(v.codigo ORDER BY v.valor DESC))[1 : 6] AS codigos,
                       (array_agg(v.id_riesgo ORDER BY v.valor DESC))[1 : 6] AS ids
                FROM v_riesgo v""" + w + " AND v.probabilidad IS NOT NULL GROUP BY v.probabilidad, v.impacto", p));
        t.put("por_categoria", bd.lista("""
                SELECT v.categoria, count(*) AS total, round(avg(v.valor), 1) AS nivel_promedio,
                       count(*) FILTER (WHERE v.nivel IN ('ALTO', 'CRITICO')) AS altos_criticos,
                       coalesce(sum(v.impacto_usd), 0) AS impacto_usd
                FROM v_riesgo v""" + w + " GROUP BY v.categoria ORDER BY total DESC", p));
        t.put("por_estado", bd.lista("SELECT v.estado, count(*) AS total FROM v_riesgo v WHERE TRUE" + c.sql()
                + " GROUP BY v.estado ORDER BY total DESC", p));
        t.put("por_filial", bd.lista("""
                SELECT f.id_filial, f.codigo, f.nombre, f.pais, f.codigo_pais, f.ciudad, f.region, f.latitud, f.longitud, f.moneda,
                       f.apetito_riesgo, f.casa_matriz,
                       count(v.id_riesgo) FILTER (WHERE v.estado <> 'CERRADO') AS riesgos,
                       count(v.id_riesgo) FILTER (WHERE v.estado <> 'CERRADO' AND v.nivel = 'CRITICO') AS criticos,
                       count(v.id_riesgo) FILTER (WHERE v.estado <> 'CERRADO' AND v.nivel = 'ALTO') AS altos,
                       max(v.valor) FILTER (WHERE v.estado <> 'CERRADO') AS valor_maximo,
                       coalesce(sum(v.impacto_usd) FILTER (WHERE v.estado <> 'CERRADO'), 0) AS impacto_usd,
                       (SELECT count(*) FROM v_alerta a WHERE a.id_filial = f.id_filial AND a.estado NOT IN ('RESUELTA', 'CERRADA')) AS alertas,
                       (SELECT round(100.0 * count(*) FILTER (WHERE o.estado_cumplimiento = 'CUMPLE')
                               / nullif(count(*) FILTER (WHERE o.estado_cumplimiento <> 'PENDIENTE'), 0), 1)
                        FROM obligacion o WHERE o.id_filial = f.id_filial) AS cumplimiento_pct
                FROM filial f LEFT JOIN v_riesgo v ON v.id_filial = f.id_filial""" + c.sql().replace(" AND v.id_filial = :alcance_filial", "")
                + (u.corporativo() ? " WHERE TRUE" : " WHERE f.id_filial = :alcance_filial")
                + (region == null || region.isBlank() ? "" : " AND f.region = :region") + " " + """
                 GROUP BY f.id_filial ORDER BY f.casa_matriz DESC, f.nombre""", p));
        t.put("top_riesgos", bd.lista("""
                SELECT v.id_riesgo, v.codigo, v.titulo, v.categoria, v.filial, v.codigo_pais, v.nivel, v.valor, v.estado,
                       v.impacto_usd, v.avance_plan, v.alertas_abiertas, v.estado_validacion, v.apetito_riesgo
                FROM v_riesgo v""" + w + " AND v.valor IS NOT NULL ORDER BY v.valor DESC, v.impacto_usd DESC NULLS LAST LIMIT 8", p));
        t.put("kri_criticos", bd.lista("""
                SELECT k.id_kri, k.nombre, k.unidad, k.umbral_amarillo, k.umbral_rojo, k.direccion, k.fuente, v.codigo, v.filial,
                       v.codigo_pais, m.valor, m.semaforo, m.fecha,
                       (SELECT array_agg(x.valor ORDER BY x.fecha) FROM (SELECT valor, fecha FROM medicion_kri mm
                         WHERE mm.id_kri = k.id_kri ORDER BY fecha DESC LIMIT 20) x) AS serie
                FROM indicador_kri k JOIN v_riesgo v USING (id_riesgo)
                JOIN LATERAL (SELECT valor, semaforo, fecha FROM medicion_kri mk WHERE mk.id_kri = k.id_kri ORDER BY fecha DESC LIMIT 1) m ON TRUE
                WHERE k.activo AND m.semaforo <> 'VERDE'""" + c.sql() + " " + """
                 ORDER BY CASE m.semaforo WHEN 'ROJO' THEN 0 ELSE 1 END, m.fecha DESC LIMIT 8""", p));
        t.put("tendencia_alertas", bd.lista("""
                SELECT d::date AS dia,
                       count(a.id_alerta) FILTER (WHERE a.severidad = 'CRITICA') AS criticas,
                       count(a.id_alerta) FILTER (WHERE a.severidad = 'ADVERTENCIA') AS advertencias,
                       count(a.id_alerta) FILTER (WHERE a.severidad = 'PREVENTIVA') AS preventivas
                FROM generate_series(CURRENT_DATE - (CAST(:dias AS int) - 1), CURRENT_DATE, interval '1 day') d
                LEFT JOIN v_alerta a ON a.fecha_generacion::date = d::date""" + c.sql().replace("v.id_filial", "a.id_filial")
                .replaceAll(" AND v\\.categoria = :categoria", " AND a.categoria = :categoria") + " " + """
                 GROUP BY d ORDER BY d""", p));
        t.put("tendencia_riesgos", bd.lista("""
                SELECT to_char(m, 'YYYY-MM') AS mes,
                       (SELECT count(*) FROM v_riesgo v WHERE date_trunc('month', v.fecha_registro) = m""" + c.sql() + " " + """
                       ) AS registrados,
                       (SELECT round(avg(e.valor), 1) FROM evaluacion_riesgo e JOIN v_riesgo v USING (id_riesgo)
                         WHERE date_trunc('month', e.fecha) = m AND e.tipo = 'INHERENTE'""" + c.sql() + " " + """
                       ) AS nivel_inherente,
                       (SELECT round(avg(e.valor), 1) FROM evaluacion_riesgo e JOIN v_riesgo v USING (id_riesgo)
                         WHERE date_trunc('month', e.fecha) = m AND e.tipo = 'RESIDUAL'""" + c.sql() + " " + """
                       ) AS nivel_residual
                FROM generate_series(date_trunc('month', CURRENT_DATE) - interval '11 months', date_trunc('month', CURRENT_DATE),
                                     interval '1 month') m ORDER BY m""", p));
        t.put("cumplimiento", bd.lista("""
                SELECT n.id_normativa, n.nombre, n.jurisdiccion, n.organismo, count(o.id_obligacion) AS obligaciones,
                       round(100.0 * count(*) FILTER (WHERE o.estado_cumplimiento = 'CUMPLE')
                             / nullif(count(*) FILTER (WHERE o.estado_cumplimiento <> 'PENDIENTE'), 0), 1) AS porcentaje
                FROM normativa n JOIN obligacion o USING (id_normativa) WHERE TRUE"""
                + c.sql().replace("v.id_filial", "o.id_filial").replaceAll(" AND v\\.categoria = :categoria", "") + " " + """
                 GROUP BY n.id_normativa ORDER BY porcentaje NULLS LAST LIMIT 10""", p));
        t.put("actividad", bd.lista("""
                SELECT b.id_bitacora, b.fecha_hora, b.operacion, b.entidad, b.id_entidad, b.resultado,
                       coalesce(u.nombre_completo, 'Sistema') AS usuario
                FROM bitacora b LEFT JOIN usuario u USING (id_usuario)""" + (u.corporativo() ? "" : " WHERE b.id_usuario = :yo") + " " + """
                 ORDER BY b.fecha_hora DESC, b.id_bitacora DESC LIMIT 12""", c.param("yo", u.id()).params()));
        String filtroAlertas = c.sql().replace("v.id_filial", "a.id_filial").replace("v.categoria", "a.categoria");
        t.put("alertas_abiertas", bd.lista("""
                SELECT a.id_alerta, a.tipo, a.severidad, a.estado, a.mensaje, a.asignado, a.segundos_restantes, a.sla_vencido,
                       a.riesgo_codigo, a.filial, a.nivel_escalamiento
                FROM v_alerta a WHERE a.estado NOT IN ('RESUELTA', 'CERRADA')""" + filtroAlertas + " " + """
                 ORDER BY CASE a.severidad WHEN 'CRITICA' THEN 0 WHEN 'ADVERTENCIA' THEN 1 ELSE 2 END, a.fecha_limite LIMIT 8""", p));
        String filtroObligaciones = c.sql().replace("v.id_filial", "o.id_filial").replaceAll(" AND v\\.categoria = :categoria", "");
        t.put("obligaciones_por_vencer", bd.lista("""
                SELECT o.id_obligacion, o.descripcion, n.nombre AS normativa, n.organismo, f.nombre AS filial, f.codigo_pais,
                       o.fecha_vencimiento, (o.fecha_vencimiento - CURRENT_DATE) AS dias
                FROM obligacion o JOIN normativa n USING (id_normativa) JOIN filial f ON f.id_filial = o.id_filial
                WHERE o.estado_cumplimiento = 'PENDIENTE' AND o.fecha_vencimiento <= CURRENT_DATE + 30""" + filtroObligaciones + " " + """
                 ORDER BY o.fecha_vencimiento LIMIT 8""", p));
        t.put("regiones", bd.jdbc().queryForList("SELECT DISTINCT region FROM filial WHERE activa ORDER BY 1", Map.of(), String.class));
        t.put("periodo_dias", periodo);
        return t;
    }
}

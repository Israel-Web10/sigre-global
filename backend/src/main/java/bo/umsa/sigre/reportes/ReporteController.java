package bo.umsa.sigre.reportes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import bo.umsa.sigre.administracion.RegistroBitacora;
import bo.umsa.sigre.comun.Condiciones;
import bo.umsa.sigre.comun.Consultas;
import bo.umsa.sigre.comun.ReglaNegocioException;
import bo.umsa.sigre.reportes.XlsxEscritor.Columna;
import bo.umsa.sigre.seguridad.UsuarioSesion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Tablero y reportes", description = "CU14: tablero ejecutivo y reportes exportables (RF22, RF24)")
public class ReporteController {

    private final ServicioTablero tablero;
    private final Consultas bd;
    private final RegistroBitacora bitacora;

    public ReporteController(ServicioTablero tablero, Consultas bd, RegistroBitacora bitacora) {
        this.tablero = tablero;
        this.bd = bd;
        this.bitacora = bitacora;
    }

    private record Reporte(String clave, String titulo, String descripcion, String[] roles) { }

    private static final String[] GERENCIA = {UsuarioSesion.DIRECTOR, UsuarioSesion.GESTOR, UsuarioSesion.GERENTE,
            UsuarioSesion.AUDITOR_INTERNO, UsuarioSesion.AUDITOR_FINANCIERO, UsuarioSesion.OFICIAL, UsuarioSesion.ADMIN};

    private static final List<Reporte> REPORTES = List.of(
            new Reporte("riesgos", "Registro de riesgos", "Catálogo con nivel inherente, plan y avance (RF18)", GERENCIA),
            new Reporte("planes", "Planes de mitigación", "Estrategia, acciones, costo, avance y aprobación (RF18, CU03, CU04)", GERENCIA),
            new Reporte("financiero", "Exposición financiera validada", "Impacto declarado frente al validado y conciliaciones (RF24)",
                    new String[]{UsuarioSesion.DIRECTOR, UsuarioSesion.AUDITOR_FINANCIERO, UsuarioSesion.AUDITOR_INTERNO, UsuarioSesion.GESTOR}),
            new Reporte("cumplimiento", "Estado del cumplimiento normativo", "Obligaciones por filial con su vencimiento y riesgo legal (RF18, RF16)", GERENCIA),
            new Reporte("alertas", "Atención de alertas y SLA", "Tiempos de atención, resolución y escalamiento (RF18, RN07)", GERENCIA),
            new Reporte("auditorias", "Hallazgos de auditoría", "Hallazgos por auditoría con su seguimiento (RF18, RF14)",
                    new String[]{UsuarioSesion.DIRECTOR, UsuarioSesion.AUDITOR_INTERNO, UsuarioSesion.AUDITOR_FINANCIERO, UsuarioSesion.ADMIN}),
            new Reporte("bitacora", "Bitácora de auditoría", "Operaciones registradas por los triggers (RF20, RN13)",
                    new String[]{UsuarioSesion.AUDITOR_INTERNO, UsuarioSesion.AUDITOR_FINANCIERO, UsuarioSesion.DIRECTOR, UsuarioSesion.ADMIN}));

    @GetMapping("/api/tablero")
    @Operation(summary = "CU14: indicadores, mapa de calor, filiales y tendencias")
    public Map<String, Object> tablero(@RequestParam(required = false) Integer filial, @RequestParam(required = false) String categoria,
                                       @RequestParam(required = false) String region, @RequestParam(defaultValue = "30") int dias) {
        return tablero.tablero(UsuarioSesion.actual(), filial, categoria, region, dias);
    }

    @GetMapping("/api/reportes")
    public List<Map<String, Object>> reportes() {
        UsuarioSesion u = UsuarioSesion.actual();
        return REPORTES.stream().filter(r -> u.tieneAlguno(r.roles()))
                .map(r -> Map.<String, Object>of("clave", r.clave(), "titulo", r.titulo(), "descripcion", r.descripcion())).toList();
    }

    @GetMapping("/api/reportes/{clave}.{formato}")
    @Operation(summary = "Descargar un reporte en Excel (xlsx), PDF o CSV (RF18, RNF08)")
    public ResponseEntity<byte[]> descargar(@PathVariable String clave, @PathVariable String formato,
                                            @RequestParam(required = false) Integer filial) {
        if (!List.of("xlsx", "pdf", "csv").contains(formato)) throw ReglaNegocioException.noEncontrado("El formato " + formato);
        UsuarioSesion u = UsuarioSesion.actual();
        Reporte r = REPORTES.stream().filter(x -> x.clave().equals(clave)).findFirst()
                .orElseThrow(() -> ReglaNegocioException.noEncontrado("El reporte"));
        u.exigir("obtener el reporte " + r.titulo(), r.roles());
        Condiciones c = new Condiciones().alcance(u, "id_filial").si(filial, "id_filial = :filial", "filial");
        String alcance = u.corporativo() ? (filial == null ? "todas las filiales" : "filial seleccionada") : u.filial();
        String sub = "Generado el " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + " por "
                + u.nombre() + " · Alcance: " + alcance + " · SIGRE Global";
        byte[] libro = switch (clave) {
            case "riesgos" -> generar(formato, "Riesgos", r.titulo(), sub, List.of(
                    new Columna("codigo", "Código", 14), new Columna("titulo", "Riesgo", 46), new Columna("categoria", "Categoría", 14),
                    new Columna("filial", "Filial", 20), new Columna("estado", "Estado", 15), new Columna("probabilidad", "P", 5),
                    new Columna("impacto", "I", 5), new Columna("valor", "Nivel", 7), new Columna("nivel", "Clasificación", 12),
                    new Columna("apetito_riesgo", "Apetito", 8), new Columna("impacto_usd", "Impacto USD", 15),
                    new Columna("estado_validacion", "Validación", 14), new Columna("estrategia", "Estrategia", 12),
                    new Columna("estado_plan", "Plan", 11), new Columna("avance_plan", "Avance %", 9),
                    new Columna("alertas_abiertas", "Alertas", 8), new Columna("responsable", "Responsable", 24),
                    new Columna("vigente_hasta", "Vigente hasta", 13)),
                    bd.lista("SELECT * FROM v_riesgo WHERE TRUE" + c.sql() + " ORDER BY coalesce(valor, 0) DESC, codigo", c.params()));
            case "planes" -> generar(formato, "Planes", r.titulo(), sub, List.of(
                    new Columna("codigo", "Riesgo", 14), new Columna("titulo", "Título", 40), new Columna("filial", "Filial", 20),
                    new Columna("nivel", "Nivel", 10), new Columna("estrategia", "Estrategia", 12), new Columna("estado_plan", "Estado", 12),
                    new Columna("acciones", "Acciones", 9), new Columna("cerradas", "Cerradas", 9), new Columna("avance", "Avance %", 9),
                    new Columna("costo_estimado", "Costo", 13), new Columna("fecha_fin", "Fin previsto", 12),
                    new Columna("proponente", "Propuesto por", 20), new Columna("aprobador", "Aprobado por", 20)),
                    bd.lista("""
                            SELECT v.codigo, v.titulo, v.filial, v.nivel, p.estrategia, p.estado AS estado_plan, p.costo_estimado, p.fecha_fin,
                                   (SELECT count(*) FROM accion_mitigacion a WHERE a.id_plan = p.id_plan) AS acciones,
                                   (SELECT count(*) FROM accion_mitigacion a WHERE a.id_plan = p.id_plan AND a.estado = 'CERRADA') AS cerradas,
                                   v.avance_plan AS avance, up.nombre_completo AS proponente, ua.nombre_completo AS aprobador
                            FROM plan_mitigacion p JOIN v_riesgo v ON v.id_riesgo = p.id_riesgo
                            LEFT JOIN usuario up ON up.id_usuario = p.id_proponente LEFT JOIN usuario ua ON ua.id_usuario = p.id_aprobador
                            WHERE TRUE""" + c.sql().replace("id_filial", "v.id_filial") + " ORDER BY v.valor DESC NULLS LAST, v.codigo", c.params()));
            case "financiero" -> generar(formato, "Financiero", r.titulo(), sub, List.of(
                    new Columna("codigo", "Código", 14), new Columna("titulo", "Riesgo", 42), new Columna("filial", "Filial", 20),
                    new Columna("nivel", "Nivel", 10), new Columna("impacto_financiero", "Impacto local", 16), new Columna("moneda", "Moneda", 8),
                    new Columna("tipo_cambio", "TC evaluación", 13), new Columna("impacto_usd", "Impacto USD declarado", 18),
                    new Columna("estado_validacion", "Validación", 14), new Columna("impacto_usd_validado", "Impacto USD validado", 18),
                    new Columna("tc_validado", "TC validado", 12), new Columna("auditor", "Auditor financiero", 22),
                    new Columna("observacion", "Observación", 40)),
                    bd.lista("""
                            SELECT v.*, vf.impacto_usd_validado, vf.tipo_cambio AS tc_validado, vf.observacion, u.nombre_completo AS auditor
                            FROM v_riesgo v LEFT JOIN validacion_financiera vf ON vf.id_evaluacion = v.id_evaluacion
                            LEFT JOIN usuario u ON u.id_usuario = vf.id_auditor_fin
                            WHERE v.impacto_financiero IS NOT NULL""" + c.sql().replace("id_filial", "v.id_filial")
                            + " ORDER BY v.impacto_usd DESC NULLS LAST", c.params()));
            case "cumplimiento" -> generar(formato, "Cumplimiento", r.titulo(), sub, List.of(
                    new Columna("normativa", "Normativa", 40), new Columna("organismo", "Organismo", 14), new Columna("filial", "Filial", 20),
                    new Columna("descripcion", "Obligación", 50), new Columna("periodicidad", "Periodicidad", 12),
                    new Columna("fecha_vencimiento", "Vence", 12), new Columna("dias", "Días", 7),
                    new Columna("estado_cumplimiento", "Estado", 12), new Columna("riesgo", "Riesgo legal", 14)),
                    bd.lista("""
                            SELECT n.nombre AS normativa, n.organismo, f.nombre AS filial, o.descripcion, o.periodicidad, o.fecha_vencimiento,
                                   (o.fecha_vencimiento - CURRENT_DATE) AS dias, o.estado_cumplimiento, r.codigo AS riesgo
                            FROM obligacion o JOIN normativa n USING (id_normativa) JOIN filial f ON f.id_filial = o.id_filial
                            LEFT JOIN riesgo r ON r.id_riesgo = o.id_riesgo_generado WHERE TRUE"""
                            + c.sql().replace("id_filial", "o.id_filial") + " ORDER BY o.fecha_vencimiento", c.params()));
            case "alertas" -> generar(formato, "Alertas", r.titulo(), sub, List.of(
                    new Columna("id_alerta", "N.º", 6), new Columna("tipo", "Tipo", 22), new Columna("severidad", "Severidad", 12),
                    new Columna("riesgo_codigo", "Riesgo", 14), new Columna("filial", "Filial", 20), new Columna("mensaje", "Mensaje", 60),
                    new Columna("estado", "Estado", 12), new Columna("asignado", "Asignado", 22), new Columna("sla_horas", "SLA h", 7),
                    new Columna("fecha_generacion", "Generada", 16), new Columna("horas_atencion", "Horas a atención", 14),
                    new Columna("horas_resolucion", "Horas a resolución", 15), new Columna("nivel_escalamiento", "Escalamientos", 12),
                    new Columna("sla_cumplido", "SLA cumplido", 12)),
                    bd.lista("""
                            SELECT a.*, round(extract(epoch FROM (a.fecha_atencion - a.fecha_generacion)) / 3600.0, 1) AS horas_atencion,
                                   round(extract(epoch FROM (a.fecha_resolucion - a.fecha_generacion)) / 3600.0, 1) AS horas_resolucion,
                                   CASE WHEN a.fecha_resolucion IS NULL THEN NULL ELSE a.fecha_resolucion <= a.fecha_limite END AS sla_cumplido
                            FROM v_alerta a WHERE TRUE""" + c.sql().replace("id_filial", "a.id_filial")
                            + " ORDER BY a.fecha_generacion DESC", c.params()));
            case "auditorias" -> generar(formato, "Hallazgos", r.titulo(), sub, List.of(
                    new Columna("auditoria", "Auditoría", 14), new Columna("tipo", "Tipo", 13), new Columna("filial", "Filial", 20),
                    new Columna("estado_auditoria", "Estado auditoría", 14), new Columna("descripcion", "Hallazgo", 60),
                    new Columna("severidad", "Severidad", 10), new Columna("riesgo", "Riesgo", 14), new Columna("responsable", "Responsable", 22),
                    new Columna("fecha_limite", "Fecha límite", 12), new Columna("estado", "Estado", 12)),
                    bd.lista("""
                            SELECT a.codigo AS auditoria, a.tipo, f.nombre AS filial, a.estado AS estado_auditoria, h.descripcion, h.severidad,
                                   r.codigo AS riesgo, u.nombre_completo AS responsable, h.fecha_limite, h.estado
                            FROM auditoria a JOIN filial f USING (id_filial) LEFT JOIN hallazgo h USING (id_auditoria)
                            LEFT JOIN riesgo r ON r.id_riesgo = h.id_riesgo LEFT JOIN usuario u ON u.id_usuario = h.id_responsable
                            WHERE TRUE""" + c.sql().replace("id_filial", "a.id_filial") + " ORDER BY a.fecha_inicio, h.id_hallazgo", c.params()));
            case "bitacora" -> generar(formato, "Bitácora", r.titulo(), sub, List.of(
                    new Columna("id_bitacora", "N.º", 8), new Columna("fecha_hora", "Fecha y hora", 17), new Columna("usuario", "Usuario", 24),
                    new Columna("operacion", "Operación", 26), new Columna("entidad", "Entidad", 20), new Columna("id_entidad", "Id", 7),
                    new Columna("resultado", "Resultado", 11), new Columna("origen", "Origen", 11), new Columna("detalle_texto", "Detalle", 80)),
                    bd.lista("""
                            SELECT b.id_bitacora, b.fecha_hora, coalesce(u.nombre_completo, 'Sistema') AS usuario, b.operacion, b.entidad,
                                   b.id_entidad, b.resultado, b.origen, left(b.detalle::text, 500) AS detalle_texto
                            FROM bitacora b LEFT JOIN usuario u USING (id_usuario) ORDER BY b.fecha_hora DESC LIMIT 5000"""));
            default -> throw ReglaNegocioException.noEncontrado("El reporte");
        };
        bitacora.registrar(u.id(), "EXPORTAR_REPORTE", "REPORTE", null, "OK", Map.of("reporte", clave, "formato", formato, "alcance", alcance));
        String nombre = "SIGRE_" + clave + "_" + LocalDate.now() + "." + formato;
        String tipo = switch (formato) {
            case "pdf" -> "application/pdf";
            case "csv" -> "text/csv;charset=UTF-8";
            default -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(tipo))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nombre).build().toString())
                .body(libro);
    }

    /** Genera el reporte en el formato pedido a partir de las mismas columnas y filas. */
    private static byte[] generar(String formato, String hoja, String titulo, String sub, List<Columna> columnas,
                                  List<Map<String, Object>> filas) {
        return switch (formato) {
            case "pdf" -> PdfTabla.documento(titulo, sub, columnas, filas);
            case "csv" -> csv(columnas, filas);
            default -> XlsxEscritor.libro(hoja, titulo, sub, columnas, filas);
        };
    }

    /** CSV separado por punto y coma, con BOM para que Excel en español lo abra con tildes (RNF08). */
    private static byte[] csv(List<Columna> columnas, List<Map<String, Object>> filas) {
        StringBuilder b = new StringBuilder("\uFEFF");
        b.append(String.join(";", columnas.stream().map(c -> celda(c.titulo())).toList())).append("\r\n");
        for (Map<String, Object> f : filas) {
            b.append(String.join(";", columnas.stream().map(c -> celda(f.get(c.clave()))).toList())).append("\r\n");
        }
        return b.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String celda(Object v) {
        if (v == null) return "";
        String t = v instanceof List<?> l ? String.join(", ", l.stream().map(String::valueOf).toList()) : String.valueOf(v);
        // Evita que Excel interprete el texto como fórmula (inyección CSV)
        if (!t.isEmpty() && "=+-@".indexOf(t.charAt(0)) >= 0 && !(v instanceof Number)) t = "'" + t;
        return "\"" + t.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\"";
    }
}

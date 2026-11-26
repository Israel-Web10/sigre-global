package bo.umsa.sigre.cumplimiento;

import static bo.umsa.sigre.comun.Consultas.p;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import bo.umsa.sigre.comun.AlmacenEvidencias;
import bo.umsa.sigre.comun.Condiciones;
import bo.umsa.sigre.comun.Consultas;
import bo.umsa.sigre.comun.Dominio;
import bo.umsa.sigre.comun.ReglaNegocioException;
import bo.umsa.sigre.riesgos.Evidencia;
import bo.umsa.sigre.seguridad.UsuarioSesion;

/** Cumplimiento normativo: normativas y obligaciones (CU12, RN09) y su evaluación (CU13, RN10). */
@Service
public class ServicioCumplimiento {

    private final Consultas bd;
    private final Dominio dominio;
    private final AlmacenEvidencias almacen;

    public ServicioCumplimiento(Consultas bd, Dominio dominio, AlmacenEvidencias almacen) {
        this.bd = bd;
        this.dominio = dominio;
        this.almacen = almacen;
    }

    public record NuevaNormativa(String nombre, String jurisdiccion, String organismo, LocalDate vigenciaDesde, String descripcion) { }

    public record NuevaObligacion(Integer idFilial, String descripcion, String periodicidad, LocalDate fechaVencimiento) { }

    public record NuevaEvaluacion(String resultado, String observacion, List<Map<String, Object>> listaVerificacion) { }

    /** Novedades que publica el servicio normativo externo (simulado) y que aún no están registradas. */
    private static final List<Map<String, Object>> NOVEDADES = List.of(
            Map.of("codigo", "ASFI-2026-118", "nombre", "Reglamento de gestión de riesgo tecnológico", "jurisdiccion", "Bolivia",
                    "organismo", "ASFI", "vigencia", "2026-12-01",
                    "descripcion", "Exige un plan de continuidad tecnológica y pruebas anuales de recuperación ante desastres."),
            Map.of("codigo", "SBS-RES-2026-0451", "nombre", "Resolución SBS de ciberseguridad para empresas supervisadas", "jurisdiccion", "Perú",
                    "organismo", "SBS", "vigencia", "2027-01-15",
                    "descripcion", "Establece la notificación de incidentes de ciberseguridad en un plazo máximo de 24 horas."),
            Map.of("codigo", "CMF-NCG-532", "nombre", "Norma de carácter general sobre gestión integral de riesgos", "jurisdiccion", "Chile",
                    "organismo", "CMF", "vigencia", "2027-02-01",
                    "descripcion", "Solicita un informe anual del directorio sobre el apetito y la tolerancia al riesgo."),
            Map.of("codigo", "UE-DORA-RTS-2026", "nombre", "Normas técnicas de resiliencia operativa digital (DORA)", "jurisdiccion", "España",
                    "organismo", "Unión Europea", "vigencia", "2026-12-20",
                    "descripcion", "Registro de proveedores tecnológicos críticos y pruebas de resiliencia operativa."));

    public List<Map<String, Object>> normativas(UsuarioSesion u) {
        // RN14: fuera de la casa matriz solo se cuentan las obligaciones de la propia filial
        String alcance = u.corporativo() ? "" : " AND o.id_filial = :filial";
        return bd.lista("""
                SELECT n.*, count(o.id_obligacion) AS obligaciones,
                       count(o.id_obligacion) FILTER (WHERE o.estado_cumplimiento = 'CUMPLE') AS cumple,
                       count(o.id_obligacion) FILTER (WHERE o.estado_cumplimiento = 'PARCIAL') AS parcial,
                       count(o.id_obligacion) FILTER (WHERE o.estado_cumplimiento = 'NO_CUMPLE') AS no_cumple,
                       count(o.id_obligacion) FILTER (WHERE o.estado_cumplimiento = 'PENDIENTE') AS pendiente,
                       round(100.0 * count(o.id_obligacion) FILTER (WHERE o.estado_cumplimiento = 'CUMPLE')
                             / nullif(count(o.id_obligacion) FILTER (WHERE o.estado_cumplimiento <> 'PENDIENTE'), 0), 1) AS porcentaje,
                       min(o.fecha_vencimiento) FILTER (WHERE o.estado_cumplimiento = 'PENDIENTE') AS proximo_vencimiento
                FROM normativa n LEFT JOIN obligacion o ON o.id_normativa = n.id_normativa""" + alcance + " " + """
                 GROUP BY n.id_normativa ORDER BY n.jurisdiccion, n.nombre""", p("filial", u.idFilial()));
    }

    public List<Map<String, Object>> obligaciones(UsuarioSesion u, Integer normativa, Integer filial, String estado) {
        Condiciones c = new Condiciones().alcance(u, "o.id_filial")
                .si(normativa, "o.id_normativa = :normativa", "normativa")
                .si(filial, "o.id_filial = :filial", "filial")
                .si(estado, "o.estado_cumplimiento = :estado", "estado");
        return bd.lista("""
                SELECT o.*, n.nombre AS normativa, n.organismo, n.jurisdiccion, f.nombre AS filial, f.codigo_pais,
                       (o.fecha_vencimiento - CURRENT_DATE) AS dias_para_vencer, r.codigo AS riesgo_codigo,
                       (SELECT max(ec.fecha) FROM evaluacion_cumplimiento ec WHERE ec.id_obligacion = o.id_obligacion) AS ultima_evaluacion,
                       (SELECT count(*) FROM alerta a WHERE a.id_obligacion = o.id_obligacion AND a.estado NOT IN ('RESUELTA', 'CERRADA')) AS alertas
                FROM obligacion o JOIN normativa n USING (id_normativa) JOIN filial f ON f.id_filial = o.id_filial
                LEFT JOIN riesgo r ON r.id_riesgo = o.id_riesgo_generado
                WHERE TRUE""" + c.sql() + " ORDER BY (o.estado_cumplimiento <> 'PENDIENTE'), o.fecha_vencimiento", c.params());
    }

    public Map<String, Object> obligacion(UsuarioSesion u, int id) {
        Map<String, Object> o = obligaciones(u, null, null, null).stream()
                .filter(x -> ((Number) x.get("id_obligacion")).intValue() == id).findFirst()
                .orElseThrow(() -> ReglaNegocioException.noEncontrado("La obligación"));
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("obligacion", o);
        d.put("evaluaciones", bd.lista("""
                SELECT ec.*, u.nombre_completo AS evaluador FROM evaluacion_cumplimiento ec JOIN usuario u ON u.id_usuario = ec.id_evaluador
                WHERE ec.id_obligacion = :id ORDER BY ec.fecha DESC, ec.id_eval_cump DESC""", p("id", id)));
        d.put("evidencias", bd.lista("""
                SELECT ev.id_evidencia, ev.id_eval_cump, ev.archivo, ev.tipo_mime, ev.tamano, ev.fecha_carga, ev.descripcion
                FROM evidencia ev JOIN evaluacion_cumplimiento ec USING (id_eval_cump) WHERE ec.id_obligacion = :id
                ORDER BY ev.fecha_carga DESC""", p("id", id)));
        d.put("alertas", bd.lista("SELECT * FROM v_alerta WHERE id_obligacion = :id ORDER BY fecha_generacion DESC", p("id", id)));
        d.put("lista_sugerida", List.of(
                Map.of("punto", "La obligación tiene un responsable asignado en la filial", "cumple", false),
                Map.of("punto", "Existe evidencia documental del período evaluado", "cumple", false),
                Map.of("punto", "El reporte se presentó al organismo dentro del plazo", "cumple", false),
                Map.of("punto", "No hay observaciones pendientes del organismo regulador", "cumple", false)));
        return d;
    }

    @Transactional
    public Map<String, Object> registrarNormativa(UsuarioSesion u, NuevaNormativa n) {
        u.exigir("registrar normativas", UsuarioSesion.OFICIAL);
        if (n.nombre() == null || n.nombre().trim().length() < 5) throw new ReglaNegocioException("CU12: Indique el nombre de la normativa");
        if (n.jurisdiccion() == null || n.organismo() == null || n.vigenciaDesde() == null) {
            throw new ReglaNegocioException("CU12: Indique jurisdicción, organismo y fecha de vigencia");
        }
        Boolean existe = bd.valor("SELECT EXISTS (SELECT 1 FROM normativa WHERE lower(nombre) = lower(trim(:n)) AND jurisdiccion = :j)",
                p("n", n.nombre(), "j", n.jurisdiccion()), Boolean.class);
        if (Boolean.TRUE.equals(existe)) throw new ReglaNegocioException("CU12: La normativa ya está registrada para " + n.jurisdiccion());
        Normativa nv = dominio.guardar(new Normativa(n.nombre(), n.jurisdiccion(), n.organismo(), n.vigenciaDesde(), n.descripcion()));
        return Map.of("id_normativa", nv.getId());
    }

    @Transactional
    public Map<String, Object> registrarObligacion(UsuarioSesion u, int idNormativa, NuevaObligacion n) {
        u.exigir("registrar obligaciones", UsuarioSesion.OFICIAL);
        dominio.buscar(Normativa.class, idNormativa, "La normativa");
        if (n.idFilial() == null) throw new ReglaNegocioException("CU12: Indique la filial obligada");
        u.exigirFilial(n.idFilial());
        if (n.descripcion() == null || n.descripcion().trim().length() < 5) throw new ReglaNegocioException("CU12: Describa la obligación");
        Obligacion o = dominio.guardar(new Obligacion(idNormativa, n.idFilial(), n.descripcion().trim(),
                n.periodicidad() == null ? "ANUAL" : n.periodicidad(), n.fechaVencimiento()));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id_obligacion", o.getId());
        m.put("dias_para_vencer", o.diasParaVencer());
        m.put("alerta_generada", bd.valor("SELECT EXISTS (SELECT 1 FROM alerta WHERE id_obligacion = :o)", p("o", o.getId()), Boolean.class));
        return m;
    }

    /** CU13: evalúa el cumplimiento; si no cumple, el trigger registra el riesgo Legal (RN10). */
    @Transactional
    public Map<String, Object> evaluar(UsuarioSesion u, int idObligacion, NuevaEvaluacion n) {
        u.exigir("evaluar el cumplimiento", UsuarioSesion.OFICIAL);
        Obligacion o = dominio.buscar(Obligacion.class, idObligacion, "La obligación");
        u.exigirFilial(o.getIdFilial());
        String resultado = n.resultado() == null || n.resultado().isBlank() ? null : n.resultado();
        if (resultado != null && !List.of("CUMPLE", "PARCIAL", "NO_CUMPLE").contains(resultado)) {
            throw new ReglaNegocioException("CU13: Resultado no válido");
        }
        EvaluacionCumplimiento e = dominio.guardar(new EvaluacionCumplimiento(idObligacion, u.id(), resultado, n.observacion(),
                n.listaVerificacion()));
        dominio.em().refresh(o);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id_eval_cump", e.getId());
        m.put("resultado", e.getResultado());
        m.put("porcentaje", e.getPorcentaje());
        m.put("id_riesgo_generado", o.getIdRiesgoGenerado());
        m.put("riesgo_generado", o.getIdRiesgoGenerado() == null ? null
                : bd.valor("SELECT codigo FROM riesgo WHERE id_riesgo = :r", p("r", o.getIdRiesgoGenerado()), String.class));
        return m;
    }

    @Transactional
    public Map<String, Object> cargarEvidencia(UsuarioSesion u, int idEvalCump, MultipartFile archivo, String descripcion) {
        u.exigir("cargar evidencias de cumplimiento", UsuarioSesion.OFICIAL);
        EvaluacionCumplimiento e = dominio.buscar(EvaluacionCumplimiento.class, idEvalCump, "La evaluación de cumplimiento");
        Integer filial = bd.valor("SELECT o.id_filial FROM evaluacion_cumplimiento ec JOIN obligacion o USING (id_obligacion) WHERE ec.id_eval_cump = :e",
                p("e", e.getId()), Integer.class);
        u.exigirFilial(filial);
        String ruta = almacen.guardar(archivo, "cumplimiento/" + idEvalCump);
        Evidencia ev = Evidencia.deCumplimiento(idEvalCump, u.id(), AlmacenEvidencias.limpiarNombre(archivo.getOriginalFilename()),
                AlmacenEvidencias.tipoMime(archivo.getOriginalFilename()), archivo.getSize(), descripcion);
        ev.ubicar(ruta);
        dominio.guardar(ev);
        return Map.of("id_evidencia", ev.getId());
    }

    @Transactional
    public Map<String, Object> renovar(UsuarioSesion u, int idObligacion) {
        u.exigir("renovar obligaciones", UsuarioSesion.OFICIAL);
        Obligacion o = dominio.buscar(Obligacion.class, idObligacion, "La obligación");
        u.exigirFilial(o.getIdFilial());
        o.renovar();
        dominio.sincronizar(o);
        return Map.of("id_obligacion", o.getId(), "dias_para_vencer", o.diasParaVencer());
    }

    // ------------------------------------------------------------------ servicio normativo externo (simulado)

    public List<Map<String, Object>> novedades() {
        return NOVEDADES.stream().map(n -> {
            Map<String, Object> m = new LinkedHashMap<>(n);
            m.put("registrada", bd.valor("SELECT EXISTS (SELECT 1 FROM normativa WHERE nombre = :n AND jurisdiccion = :j)",
                    p("n", n.get("nombre"), "j", n.get("jurisdiccion")), Boolean.class));
            return m;
        }).toList();
    }

    @Transactional
    public Map<String, Object> importar(UsuarioSesion u, String codigo) {
        Map<String, Object> n = NOVEDADES.stream().filter(x -> x.get("codigo").equals(codigo)).findFirst()
                .orElseThrow(() -> ReglaNegocioException.noEncontrado("La novedad normativa"));
        return registrarNormativa(u, new NuevaNormativa((String) n.get("nombre"), (String) n.get("jurisdiccion"), (String) n.get("organismo"),
                LocalDate.parse((String) n.get("vigencia")), n.get("descripcion") + " (" + codigo + ")"));
    }
}

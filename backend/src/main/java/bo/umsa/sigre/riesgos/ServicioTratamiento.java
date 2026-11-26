package bo.umsa.sigre.riesgos;

import static bo.umsa.sigre.comun.Consultas.p;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bo.umsa.sigre.comun.Consultas;
import bo.umsa.sigre.comun.Dominio;
import bo.umsa.sigre.comun.ReglaNegocioException;
import bo.umsa.sigre.seguridad.UsuarioSesion;
import jakarta.persistence.NoResultException;

/** Plan de mitigación (CU03) y aprobación del tratamiento (CU04, RN03, RN04, RN05, RN12, RN16). */
@Service
public class ServicioTratamiento {

    private final Consultas bd;
    private final Dominio dominio;

    public ServicioTratamiento(Consultas bd, Dominio dominio) {
        this.bd = bd;
        this.dominio = dominio;
    }

    public record DatosPlan(Estrategia estrategia, String justificacion) { }

    public record NuevaAccion(String descripcion, Integer idResponsable, LocalDate fechaLimite, BigDecimal costo) { }

    private PlanMitigacion plan(int idRiesgo) {
        try {
            return dominio.em().createQuery("SELECT p FROM PlanMitigacion p WHERE p.idRiesgo = :r", PlanMitigacion.class)
                    .setParameter("r", idRiesgo).getSingleResult();
        } catch (NoResultException e) {
            throw new ReglaNegocioException("CU03: El riesgo todavía no tiene un plan de mitigación");
        }
    }

    private Riesgo riesgo(UsuarioSesion u, int idRiesgo) {
        Riesgo r = dominio.buscar(Riesgo.class, idRiesgo, "El riesgo");
        u.exigirFilial(r.getIdFilial());
        return r;
    }

    // ------------------------------------------------------------------ CU03

    @Transactional
    public Map<String, Object> guardarPlan(UsuarioSesion u, int idRiesgo, DatosPlan d) {
        u.exigir("definir planes de mitigación", UsuarioSesion.GESTOR);
        Riesgo r = riesgo(u, idRiesgo);
        if (d.estrategia() == null) throw new ReglaNegocioException("CU03: Seleccione la estrategia de tratamiento");
        Long existe = dominio.em().createQuery("SELECT count(p) FROM PlanMitigacion p WHERE p.idRiesgo = :r", Long.class)
                .setParameter("r", idRiesgo).getSingleResult();
        PlanMitigacion p;
        if (existe == 0) {
            if (r.getEstado() != EstadoRiesgo.EVALUADO) {
                throw new ReglaNegocioException("CU03: El plan de mitigación se define para riesgos EVALUADO (el riesgo "
                        + r.getCodigo() + " está " + r.getEstado() + ")");
            }
            p = dominio.guardar(new PlanMitigacion(idRiesgo, u.id(), d.estrategia(), d.justificacion()));
        } else {
            p = plan(idRiesgo);
            p.editar(d.estrategia(), d.justificacion());
            dominio.sincronizar(p);
        }
        return Map.of("id_plan", p.getId(), "estado", p.getEstado(), "estrategia", p.getEstrategia().name());
    }

    @Transactional
    public Map<String, Object> agregarAccion(UsuarioSesion u, int idRiesgo, NuevaAccion a) {
        u.exigir("definir planes de mitigación", UsuarioSesion.GESTOR);
        riesgo(u, idRiesgo);
        PlanMitigacion p = plan(idRiesgo);
        if (!PlanMitigacion.BORRADOR.equals(p.getEstado())) {
            throw new ReglaNegocioException("CU03: Solo se agregan acciones a un plan en BORRADOR (el plan está " + p.getEstado() + ")");
        }
        if (a.descripcion() == null || a.descripcion().trim().length() < 5) throw new ReglaNegocioException("RN05: Describa la acción");
        if (a.idResponsable() == null) throw new ReglaNegocioException("RN05: Toda acción necesita un responsable");
        if (a.fechaLimite() == null) throw new ReglaNegocioException("RN05: Toda acción necesita una fecha límite");
        if (a.fechaLimite().isBefore(LocalDate.now())) throw new ReglaNegocioException("CU03: La fecha límite de la acción no puede ser anterior a hoy");
        AccionMitigacion nueva = dominio.guardar(new AccionMitigacion(p.getId(), a.idResponsable(), a.descripcion().trim(),
                a.fechaLimite(), a.costo()));
        return Map.of("id_accion", nueva.getId());
    }

    @Transactional
    public void eliminarAccion(UsuarioSesion u, int idRiesgo, int idAccion) {
        u.exigir("definir planes de mitigación", UsuarioSesion.GESTOR);
        riesgo(u, idRiesgo);
        PlanMitigacion p = plan(idRiesgo);
        AccionMitigacion a = dominio.buscar(AccionMitigacion.class, idAccion, "La acción");
        if (!a.getIdPlan().equals(p.getId())) throw ReglaNegocioException.noEncontrado("La acción");
        if (!PlanMitigacion.BORRADOR.equals(p.getEstado())) throw new ReglaNegocioException("CU03: Solo se quitan acciones de un plan en BORRADOR");
        dominio.eliminar(a);
    }

    @Transactional
    public Map<String, Object> proponer(UsuarioSesion u, int idRiesgo) {
        u.exigir("proponer tratamientos", UsuarioSesion.GESTOR);
        riesgo(u, idRiesgo);
        PlanMitigacion p = plan(idRiesgo);
        Long acciones = dominio.em().createQuery("SELECT count(a) FROM AccionMitigacion a WHERE a.idPlan = :p", Long.class)
                .setParameter("p", p.getId()).getSingleResult();
        p.proponer(acciones);
        dominio.sincronizar(p);
        return Map.of("id_plan", p.getId(), "estado", p.getEstado());
    }

    // ------------------------------------------------------------------ CU04

    /**
     * Lista de verificación del aprobador (CU04 paso 3). Es la misma que aplica el trigger
     * fn_plan_antes_actualizar; aquí se muestra antes de intentar la aprobación.
     */
    public Map<String, Object> verificacion(UsuarioSesion u, int idRiesgo) {
        Map<String, Object> r = bd.uno("SELECT * FROM v_riesgo WHERE id_riesgo = :id", p("id", idRiesgo));
        if (r == null) throw ReglaNegocioException.noEncontrado("El riesgo");
        u.exigirFilial((Integer) r.get("id_filial"));
        Map<String, Object> plan = bd.uno("SELECT * FROM plan_mitigacion WHERE id_riesgo = :id", p("id", idRiesgo));
        Map<String, Object> vig = bd.uno("""
                SELECT (e).id_evaluacion, (e).nivel, (e).valor, (e).vigente_hasta, (e).estado_validacion::text AS estado_validacion
                FROM (SELECT fn_evaluacion_vigente(:id) AS e) x""", p("id", idRiesgo));
        boolean hayVigente = vig != null && vig.get("id_evaluacion") != null;
        String nivel = hayVigente ? (String) vig.get("nivel") : null;
        String estrategia = plan == null ? null : (String) plan.get("estrategia");
        Long acciones = plan == null ? 0L : bd.valor("SELECT count(*) FROM accion_mitigacion WHERE id_plan = :p",
                p("p", plan.get("id_plan")), Long.class);
        Long incompletas = plan == null ? 0L : bd.valor(
                "SELECT count(*) FROM accion_mitigacion WHERE id_plan = :p AND (id_responsable IS NULL OR fecha_limite IS NULL)",
                p("p", plan.get("id_plan")), Long.class);

        List<Map<String, Object>> items = new ArrayList<>();
        items.add(item("ESTADO", "CU04", "El plan está propuesto para aprobación",
                plan != null && "PROPUESTO".equals(plan.get("estado")),
                plan == null ? "Sin plan" : "Estado del plan: " + plan.get("estado")));
        items.add(item("VIGENCIA", "RN03", "La evaluación del riesgo está vigente (12 meses)", hayVigente,
                hayVigente ? "Vigente hasta " + vig.get("vigente_hasta") + " · nivel " + nivel + " (" + vig.get("valor") + ")"
                        : "La última evaluación venció: se devuelve para reevaluación (CU02)"));
        String val = hayVigente ? (String) vig.get("estado_validacion") : null;
        items.add(item("VALIDACION", "RN16", "Impacto financiero validado por el Auditor Financiero",
                val != null && !List.of("PENDIENTE", "OBSERVADA").contains(val),
                val == null ? "Sin evaluación vigente" : switch (val) {
                    case "NO_REQUERIDA" -> "No requiere validación financiera";
                    case "VALIDADA" -> "Validado por el Auditor Financiero (CU17)";
                    case "PENDIENTE" -> "Pendiente de validación del Auditor Financiero (CU17)";
                    default -> "Observado por el Auditor Financiero: debe reevaluarse";
                }));
        boolean aceptar = "ACEPTAR".equals(estrategia);
        items.add(item("ACCIONES", aceptar ? "RN12" : "RN05",
                aceptar ? "La aceptación tiene una justificación escrita" : "Cada acción tiene responsable y fecha límite",
                aceptar ? plan.get("justificacion") != null && ((String) plan.get("justificacion")).trim().length() >= 10
                        : acciones > 0 && incompletas == 0,
                aceptar ? "Estrategia ACEPTAR" : acciones + " acción(es) registradas"));
        boolean director = u.tiene(UsuarioSesion.DIRECTOR);
        boolean gerenteFilial = u.tiene(UsuarioSesion.GERENTE) && u.idFilial() == (Integer) r.get("id_filial");
        boolean altoCritico = "ALTO".equals(nivel) || "CRITICO".equals(nivel);
        boolean atribucion = aceptar || altoCritico ? director : (director || gerenteFilial);
        items.add(item("ATRIBUCION", aceptar ? "RN12" : "RN04",
                aceptar ? "Aceptar un riesgo lo aprueba el Director de Riesgos"
                        : altoCritico ? "Nivel Alto o Crítico: aprueba el Director de Riesgos"
                        : "Nivel Bajo o Medio: aprueba el Gerente de la filial o el Director",
                atribucion, atribucion ? "Usted tiene la atribución" : altoCritico || aceptar
                        ? "Se deriva al Director de Riesgos" : "Corresponde al Gerente de la filial o al Director"));
        boolean segregacion = plan == null || plan.get("id_proponente") == null || ((Number) plan.get("id_proponente")).intValue() != u.id();
        items.add(item("SEGREGACION", "RN04", "Quien propone el plan no lo aprueba", segregacion,
                segregacion ? "Usuario distinto del proponente" : "Usted propuso este plan"));

        boolean aprobable = items.stream().allMatch(i -> Boolean.TRUE.equals(i.get("cumple")));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("riesgo", r);
        m.put("plan", plan);
        m.put("items", items);
        m.put("aprobable", aprobable);
        m.put("acciones", bd.lista("""
                SELECT a.id_accion, a.descripcion, a.fecha_limite, a.costo, u.nombre_completo AS responsable
                FROM accion_mitigacion a JOIN usuario u ON u.id_usuario = a.id_responsable
                WHERE a.id_plan = :p ORDER BY a.fecha_limite""", p("p", plan == null ? -1 : plan.get("id_plan"))));
        return m;
    }

    private static Map<String, Object> item(String clave, String regla, String descripcion, boolean cumple, String detalle) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("clave", clave);
        m.put("regla", regla);
        m.put("descripcion", descripcion);
        m.put("cumple", cumple);
        m.put("detalle", detalle);
        return m;
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> aprobar(UsuarioSesion u, int idRiesgo) {
        u.exigir("aprobar tratamientos", UsuarioSesion.DIRECTOR, UsuarioSesion.GERENTE);
        Map<String, Object> v = verificacion(u, idRiesgo);
        for (Map<String, Object> i : (List<Map<String, Object>>) v.get("items")) {
            if (!Boolean.TRUE.equals(i.get("cumple"))) {
                throw new ReglaNegocioException(i.get("regla") + ": " + i.get("descripcion") + ". " + i.get("detalle"));
            }
        }
        PlanMitigacion p = plan(idRiesgo);
        p.aprobar(u.id());
        dominio.sincronizar(p);
        Riesgo r = dominio.buscar(Riesgo.class, idRiesgo, "El riesgo");
        dominio.em().refresh(r);
        return Map.of("id_plan", p.getId(), "estado", p.getEstado(), "estado_riesgo", r.getEstado().name());
    }

    @Transactional
    public Map<String, Object> rechazar(UsuarioSesion u, int idRiesgo, String motivo) {
        u.exigir("rechazar tratamientos", UsuarioSesion.DIRECTOR, UsuarioSesion.GERENTE);
        riesgo(u, idRiesgo);
        PlanMitigacion p = plan(idRiesgo);
        p.rechazar(motivo);
        dominio.sincronizar(p);
        return Map.of("id_plan", p.getId(), "estado", p.getEstado());
    }

    /** Planes que esperan la aprobación del usuario según su atribución (RN04). */
    public List<Map<String, Object>> pendientesDeAprobacion(UsuarioSesion u) {
        if (!u.tieneAlguno(UsuarioSesion.DIRECTOR, UsuarioSesion.GERENTE)) return List.of();
        return bd.lista("""
                SELECT v.id_riesgo, v.codigo, v.titulo, v.categoria, v.filial, v.codigo_pais, v.nivel, v.valor, v.estrategia,
                       v.estado_validacion, p.fecha_propuesta, p.costo_estimado, up.nombre_completo AS proponente,
                       (SELECT count(*) FROM accion_mitigacion a WHERE a.id_plan = p.id_plan) AS acciones
                FROM v_riesgo v JOIN plan_mitigacion p ON p.id_riesgo = v.id_riesgo
                LEFT JOIN usuario up ON up.id_usuario = p.id_proponente
                WHERE p.estado = 'PROPUESTO'
                  AND (:director OR (v.id_filial = :filial AND v.nivel IN ('BAJO', 'MEDIO') AND v.estrategia <> 'ACEPTAR'))
                ORDER BY v.valor DESC NULLS LAST, p.fecha_propuesta""",
                p("director", u.tiene(UsuarioSesion.DIRECTOR), "filial", u.idFilial()));
    }

    /** Cierre del riesgo mitigado o aceptado (D08). */
    @Transactional
    public Map<String, Object> cerrarRiesgo(UsuarioSesion u, int idRiesgo) {
        u.exigir("cerrar riesgos", UsuarioSesion.GESTOR, UsuarioSesion.DIRECTOR);
        Riesgo r = riesgo(u, idRiesgo);
        r.cambiarEstado(EstadoRiesgo.CERRADO);
        dominio.sincronizar(r);
        bd.ejecutar("UPDATE plan_mitigacion SET estado = 'CERRADO' WHERE id_riesgo = :id AND estado = 'APROBADO'", p("id", idRiesgo));
        return Map.of("id_riesgo", r.getId(), "estado", r.getEstado().name());
    }
}

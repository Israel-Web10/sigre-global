package bo.umsa.sigre.riesgos;

import static bo.umsa.sigre.comun.Consultas.p;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import bo.umsa.sigre.comun.AlmacenEvidencias;
import bo.umsa.sigre.comun.Consultas;
import bo.umsa.sigre.comun.Dominio;
import bo.umsa.sigre.comun.ReglaNegocioException;
import bo.umsa.sigre.seguridad.UsuarioSesion;

/** Seguimiento de las acciones de mitigación (CU05) y sus evidencias. */
@Service
public class ServicioAcciones {

    private final Consultas bd;
    private final Dominio dominio;
    private final AlmacenEvidencias almacen;

    public ServicioAcciones(Consultas bd, Dominio dominio, AlmacenEvidencias almacen) {
        this.bd = bd;
        this.dominio = dominio;
        this.almacen = almacen;
    }

    public List<Map<String, Object>> misAcciones(UsuarioSesion u) {
        return bd.lista("""
                SELECT a.id_accion, a.descripcion, a.fecha_limite, a.costo, a.avance, a.estado, a.comentario, a.fecha_cierre,
                       p.estado AS estado_plan, p.estrategia, r.id_riesgo, r.codigo, r.titulo, r.categoria, f.nombre AS filial,
                       f.codigo_pais, (a.fecha_limite - CURRENT_DATE) AS dias_restantes,
                       (a.estado <> 'CERRADA' AND a.fecha_limite < CURRENT_DATE) AS vencida,
                       (SELECT count(*) FROM evidencia ev WHERE ev.id_accion = a.id_accion) AS evidencias
                FROM accion_mitigacion a JOIN plan_mitigacion p USING (id_plan) JOIN riesgo r USING (id_riesgo)
                JOIN filial f ON f.id_filial = r.id_filial
                WHERE a.id_responsable = :u
                ORDER BY (a.estado = 'CERRADA'), a.fecha_limite""", p("u", u.id()));
    }

    private AccionMitigacion accionPropia(UsuarioSesion u, int idAccion) {
        AccionMitigacion a = dominio.buscar(AccionMitigacion.class, idAccion, "La acción");
        if (!a.getIdResponsable().equals(u.id())) {
            throw ReglaNegocioException.prohibido("CU05: Solo el responsable de la acción registra su avance");
        }
        Integer filial = bd.valor("""
                SELECT r.id_filial FROM accion_mitigacion a JOIN plan_mitigacion p USING (id_plan) JOIN riesgo r USING (id_riesgo)
                WHERE a.id_accion = :a""", p("a", idAccion), Integer.class);
        u.exigirFilial(filial);
        return a;
    }

    @Transactional
    public Map<String, Object> registrarAvance(UsuarioSesion u, int idAccion, int avance, String comentario) {
        AccionMitigacion a = accionPropia(u, idAccion);
        if (avance == 100) {
            Long evidencias = bd.valor("SELECT count(*) FROM evidencia WHERE id_accion = :a", p("a", idAccion), Long.class);
            if (evidencias == 0) throw new ReglaNegocioException("CU05: Para cerrar la acción se requiere al menos una evidencia");
        }
        a.registrarAvance(avance, comentario);
        dominio.sincronizar(a);
        return Map.of("id_accion", a.getId(), "avance", a.getAvance(), "estado", a.getEstado());
    }

    @Transactional
    public Map<String, Object> cargarEvidencia(UsuarioSesion u, int idAccion, MultipartFile archivo, String descripcion) {
        AccionMitigacion a = accionPropia(u, idAccion);
        if ("CERRADA".equals(a.getEstado())) throw new ReglaNegocioException("CU05: La acción ya está cerrada");
        String ruta = almacen.guardar(archivo, "acciones/" + idAccion);
        Evidencia e = Evidencia.deAccion(idAccion, u.id(), AlmacenEvidencias.limpiarNombre(archivo.getOriginalFilename()),
                AlmacenEvidencias.tipoMime(archivo.getOriginalFilename()), archivo.getSize(), descripcion);
        e.ubicar(ruta);
        dominio.guardar(e);
        return Map.of("id_evidencia", e.getId(), "archivo", e.getArchivo());
    }

    /** Devuelve el archivo si el usuario tiene acceso a la filial del riesgo o de la obligación (RN14). */
    public Map.Entry<Evidencia, Path> archivo(UsuarioSesion u, int idEvidencia) {
        Evidencia e = dominio.buscar(Evidencia.class, idEvidencia, "La evidencia");
        Integer filial = bd.valor("""
                SELECT coalesce(r.id_filial, o.id_filial) FROM evidencia ev
                LEFT JOIN accion_mitigacion a ON a.id_accion = ev.id_accion
                LEFT JOIN plan_mitigacion p ON p.id_plan = a.id_plan
                LEFT JOIN riesgo r ON r.id_riesgo = p.id_riesgo
                LEFT JOIN evaluacion_cumplimiento ec ON ec.id_eval_cump = ev.id_eval_cump
                LEFT JOIN obligacion o ON o.id_obligacion = ec.id_obligacion
                WHERE ev.id_evidencia = :e""", p("e", idEvidencia), Integer.class);
        u.exigirFilial(filial);
        return Map.entry(e, almacen.abrir(e.getRuta()));
    }
}

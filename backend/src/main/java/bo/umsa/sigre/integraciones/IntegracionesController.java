package bo.umsa.sigre.integraciones;

import static bo.umsa.sigre.comun.Consultas.p;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import bo.umsa.sigre.comun.Consultas;
import bo.umsa.sigre.monitoreo.ServicioEventos;
import bo.umsa.sigre.seguridad.UsuarioSesion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Integraciones", description = "ERP corporativo, SIEM, servicio normativo y notificaciones (simulados)")
public class IntegracionesController {

    private final ServicioErp erp;
    private final SimuladorIntegraciones simulador;
    private final ServicioEventos eventos;
    private final TareasProgramadas tareas;
    private final Consultas bd;
    private final boolean demo;

    public IntegracionesController(ServicioErp erp, SimuladorIntegraciones simulador, ServicioEventos eventos,
                                   TareasProgramadas tareas, Consultas bd,
                                   @org.springframework.beans.factory.annotation.Value("${sigre.demo.habilitado:false}") boolean demo) {
        this.erp = erp;
        this.simulador = simulador;
        this.eventos = eventos;
        this.tareas = tareas;
        this.bd = bd;
        this.demo = demo;
    }

    private void soloDemo() {
        if (!demo) throw new bo.umsa.sigre.comun.ReglaNegocioException("La simulación de integraciones solo existe en el modo de demostración",
                org.springframework.http.HttpStatus.NOT_FOUND);
    }

    public record Simulador(Boolean activo) { }
    public record Evento(String zona) { }

    @GetMapping("/api/erp/tipos-cambio")
    @Operation(summary = "ERP: tipo de cambio vigente por moneda (RN15)")
    public List<Map<String, Object>> tiposCambio(@RequestParam(required = false) LocalDate fecha) {
        return erp.tiposDeCambio(fecha == null ? LocalDate.now() : fecha);
    }

    @GetMapping("/api/erp/tipos-cambio/{moneda}")
    public List<Map<String, Object>> historico(@PathVariable String moneda, @RequestParam(defaultValue = "90") int dias) {
        return erp.historico(moneda.toUpperCase(), dias);
    }

    @GetMapping("/api/erp/cierres")
    @Operation(summary = "ERP: cierres contables mensuales de los KRI financieros (RN18)")
    public List<Map<String, Object>> cierres(@RequestParam(required = false) String periodo) {
        UsuarioSesion.actual().exigir("consultar los cierres del ERP", UsuarioSesion.AUDITOR_FINANCIERO, UsuarioSesion.AUDITOR_INTERNO,
                UsuarioSesion.DIRECTOR, UsuarioSesion.GESTOR);
        return erp.cierres(periodo);
    }

    @GetMapping("/api/integraciones")
    @Operation(summary = "Estado de las integraciones, el simulador y las tareas programadas")
    public Map<String, Object> estado() {
        UsuarioSesion.actual().exigir("ver las integraciones", UsuarioSesion.ADMIN, UsuarioSesion.GESTOR, UsuarioSesion.DIRECTOR);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("demo", demo);
        m.put("simulador", simulador.estado());
        m.put("eventos", eventos.estado());
        m.put("tareas", tareas.estado());
        m.put("sistemas", bd.lista("""
                SELECT s.sistema,
                       (SELECT count(*) FROM incidencia_integracion i WHERE i.sistema = s.sistema AND NOT i.resuelta) AS incidencias,
                       CASE s.sistema
                         WHEN 'ERP' THEN (SELECT max(fecha)::text FROM tipo_cambio)
                         WHEN 'SIEM' THEN (SELECT max(fecha)::text FROM medicion_kri WHERE origen = 'SIEM')
                         WHEN 'NOTIFICACIONES' THEN (SELECT max(fecha)::text FROM notificacion)
                         ELSE (SELECT max(vigencia_desde)::text FROM normativa) END AS ultima_actividad,
                       CASE s.sistema
                         WHEN 'ERP' THEN (SELECT count(*) FROM medicion_kri WHERE origen = 'ERP' AND fecha >= CURRENT_DATE)
                         WHEN 'SIEM' THEN (SELECT count(*) FROM medicion_kri WHERE origen = 'SIEM' AND fecha >= CURRENT_DATE)
                         WHEN 'NOTIFICACIONES' THEN (SELECT count(*) FROM notificacion WHERE fecha >= CURRENT_DATE)
                         ELSE (SELECT count(*) FROM normativa) END AS operaciones_hoy
                FROM (VALUES ('ERP'), ('SIEM'), ('NORMATIVO'), ('NOTIFICACIONES')) AS s(sistema)"""));
        m.put("incidencias", bd.lista("SELECT * FROM incidencia_integracion ORDER BY fecha DESC LIMIT 20"));
        m.put("ultimas_mediciones", bd.lista("""
                SELECT m.id_medicion, m.fecha, m.valor, m.origen, m.semaforo, k.nombre AS kri, k.unidad, f.codigo_pais
                FROM medicion_kri m JOIN indicador_kri k USING (id_kri) JOIN riesgo r USING (id_riesgo)
                JOIN filial f ON f.id_filial = r.id_filial
                WHERE m.origen IN ('ERP', 'SIEM') ORDER BY m.fecha DESC LIMIT 15"""));
        return m;
    }

    @PostMapping("/api/integraciones/simulador")
    @Operation(summary = "Encender o apagar el simulador del ERP y el SIEM")
    public Map<String, Object> simulador(@RequestBody Simulador s) {
        UsuarioSesion.actual().exigir("administrar integraciones", UsuarioSesion.ADMIN, UsuarioSesion.GESTOR, UsuarioSesion.DIRECTOR);
        soloDemo();
        simulador.activar(Boolean.TRUE.equals(s.activo()));
        return simulador.estado();
    }

    @PostMapping("/api/integraciones/kri/{id}/evento")
    @Operation(summary = "Simular que el ERP o el SIEM publica un valor (VERDE, AMARILLO o ROJO)")
    public Map<String, Object> evento(@PathVariable int id, @RequestBody Evento e) {
        UsuarioSesion u = UsuarioSesion.actual();
        u.exigir("simular eventos", UsuarioSesion.ADMIN, UsuarioSesion.GESTOR, UsuarioSesion.DIRECTOR);
        soloDemo();
        Integer filial = bd.valor("SELECT r.id_filial FROM indicador_kri k JOIN riesgo r USING (id_riesgo) WHERE k.id_kri = :k",
                p("k", id), Integer.class);
        u.exigirFilial(filial);
        String zona = e.zona() == null ? "ROJO" : e.zona().toUpperCase();
        return simulador.publicar(id, List.of("VERDE", "AMARILLO", "ROJO").contains(zona) ? zona : "ROJO");
    }

    @PostMapping("/api/integraciones/incidencias/resolver")
    public Map<String, Object> resolverIncidencias() {
        UsuarioSesion.actual().exigir("administrar integraciones", UsuarioSesion.ADMIN);
        return Map.of("resueltas", bd.ejecutar("UPDATE incidencia_integracion SET resuelta = TRUE WHERE NOT resuelta", Map.of()));
    }
}

package bo.umsa.sigre.auditorias;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import bo.umsa.sigre.comun.Intentos;
import bo.umsa.sigre.seguridad.UsuarioSesion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Auditorías", description = "CU10, CU11, CU17 y CU18: auditorías, hallazgos, validación y conciliación financiera")
public class AuditoriaController {

    private final ServicioAuditorias auditorias;
    private final ServicioFinanciero financiero;
    private final Intentos intentos;

    public AuditoriaController(ServicioAuditorias auditorias, ServicioFinanciero financiero, Intentos intentos) {
        this.auditorias = auditorias;
        this.financiero = financiero;
        this.intentos = intentos;
    }

    public record Ejecucion(String periodo, Integer idFilial) { }

    @GetMapping("/api/auditorias")
    @Operation(summary = "Plan anual de auditorías")
    public List<Map<String, Object>> listar(@RequestParam(required = false) Integer anio, @RequestParam(required = false) String estado) {
        return auditorias.listar(UsuarioSesion.actual(), anio, estado);
    }

    @GetMapping("/api/auditorias/{id}")
    public Map<String, Object> detalle(@PathVariable int id) {
        return auditorias.detalle(UsuarioSesion.actual(), id);
    }

    @GetMapping("/api/auditorias/auditores")
    @Operation(summary = "CU10: auditores con su disponibilidad")
    public List<Map<String, Object>> auditores(@RequestParam(required = false) LocalDate inicio, @RequestParam(required = false) LocalDate fin) {
        return auditorias.auditores(inicio, fin);
    }

    @PostMapping("/api/auditorias")
    @Operation(summary = "CU10: planificar una auditoría")
    public Map<String, Object> planificar(@RequestBody ServicioAuditorias.NuevaAuditoria n) {
        UsuarioSesion u = UsuarioSesion.actual();
        return intentos.registrar(u, "PLANIFICAR_AUDITORIA", "AUDITORIA", null, () -> auditorias.planificar(u, n));
    }

    @PostMapping("/api/auditorias/{id}/iniciar")
    public Map<String, Object> iniciar(@PathVariable int id) {
        return auditorias.iniciar(UsuarioSesion.actual(), id);
    }

    @PostMapping("/api/auditorias/{id}/cerrar")
    public Map<String, Object> cerrar(@PathVariable int id) {
        UsuarioSesion u = UsuarioSesion.actual();
        return intentos.registrar(u, "CERRAR_AUDITORIA", "AUDITORIA", id, () -> auditorias.cerrar(u, id));
    }

    @PostMapping("/api/auditorias/{id}/hallazgos")
    @Operation(summary = "CU11: registrar un hallazgo (RN08)")
    public Map<String, Object> hallazgo(@PathVariable int id, @RequestBody ServicioAuditorias.NuevoHallazgo n) {
        UsuarioSesion u = UsuarioSesion.actual();
        return intentos.registrar(u, "REGISTRAR_HALLAZGO", "AUDITORIA", id, () -> auditorias.registrarHallazgo(u, id, n));
    }

    @GetMapping("/api/hallazgos")
    public List<Map<String, Object>> hallazgos(@RequestParam(required = false) String estado,
                                               @RequestParam(required = false) Boolean financiero) {
        return auditorias.hallazgos(UsuarioSesion.actual(), null, estado, financiero);
    }

    @PutMapping("/api/hallazgos/{id}")
    public Map<String, Object> cambiarHallazgo(@PathVariable int id, @RequestBody ServicioAuditorias.CambioHallazgo c) {
        return auditorias.cambiarHallazgo(UsuarioSesion.actual(), id, c);
    }

    // ------------------------------------------------------------------ CU17

    @GetMapping("/api/validacion-financiera/pendientes")
    @Operation(summary = "CU17: evaluaciones pendientes de validación con el contraste del ERP (P02)")
    public List<Map<String, Object>> pendientes() {
        return financiero.pendientes(UsuarioSesion.actual());
    }

    @GetMapping("/api/validacion-financiera/pendientes/{idEvaluacion}")
    public Map<String, Object> contexto(@PathVariable int idEvaluacion) {
        return financiero.contexto(UsuarioSesion.actual(), idEvaluacion);
    }

    @GetMapping("/api/validacion-financiera")
    public List<Map<String, Object>> validaciones() {
        return financiero.validaciones(UsuarioSesion.actual());
    }

    @PostMapping("/api/validacion-financiera/{idEvaluacion}")
    @Operation(summary = "CU17: validar u observar el impacto financiero (RN17 en el trigger chk_independencia)")
    public Map<String, Object> validar(@PathVariable int idEvaluacion, @RequestBody ServicioFinanciero.Validacion v) {
        UsuarioSesion u = UsuarioSesion.actual();
        return intentos.registrar(u, "VALIDAR_IMPACTO", "EVALUACION_RIESGO", idEvaluacion,
                () -> financiero.validar(u, idEvaluacion, v));
    }

    // ------------------------------------------------------------------ CU18

    @GetMapping("/api/conciliacion/periodos")
    public List<String> periodos() {
        return financiero.periodos();
    }

    @GetMapping("/api/conciliacion")
    @Operation(summary = "CU18: valores del sistema frente al cierre del ERP en un período")
    public Map<String, Object> conciliacion(@RequestParam(required = false) String periodo, @RequestParam(required = false) Integer filial) {
        return financiero.conciliacion(UsuarioSesion.actual(), periodo, filial);
    }

    @PostMapping("/api/conciliacion/ejecutar")
    @Operation(summary = "CU18: ejecutar la conciliación mensual (tolerancia RN18)")
    public Map<String, Object> ejecutar(@RequestBody Ejecucion e) {
        UsuarioSesion u = UsuarioSesion.actual();
        return intentos.registrar(u, "CONCILIAR_KRI", "CONCILIACION_KRI", null, () -> financiero.ejecutar(u, e.periodo(), e.idFilial()));
    }

    @PostMapping("/api/conciliacion/{id}/hallazgo")
    @Operation(summary = "CU18: registrar el hallazgo financiero de una diferencia")
    public Map<String, Object> hallazgoFinanciero(@PathVariable int id, @RequestBody ServicioFinanciero.HallazgoFinanciero n) {
        UsuarioSesion u = UsuarioSesion.actual();
        return intentos.registrar(u, "REGISTRAR_HALLAZGO", "CONCILIACION_KRI", id, () -> financiero.registrarHallazgo(u, id, n));
    }
}

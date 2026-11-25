package bo.umsa.sigre.riesgos;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import bo.umsa.sigre.comun.Intentos;
import bo.umsa.sigre.seguridad.UsuarioSesion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/riesgos")
@Tag(name = "Riesgos", description = "CU01 a CU04: registro, evaluación, plan de mitigación y aprobación")
public class RiesgoController {

    private final ServicioRiesgos riesgos;
    private final ServicioTratamiento tratamiento;
    private final Intentos intentos;

    public RiesgoController(ServicioRiesgos riesgos, ServicioTratamiento tratamiento, Intentos intentos) {
        this.riesgos = riesgos;
        this.tratamiento = tratamiento;
        this.intentos = intentos;
    }

    public record Previsualizacion(Integer probabilidad, Integer impacto, BigDecimal impactoFinanciero, String moneda) { }
    public record Motivo(String motivo) { }

    @GetMapping
    @Operation(summary = "Catálogo de riesgos visible para el usuario (RN14)")
    public List<Map<String, Object>> listar(@RequestParam(required = false) Integer filial,
                                            @RequestParam(required = false) String categoria,
                                            @RequestParam(required = false) String estado,
                                            @RequestParam(required = false) String nivel,
                                            @RequestParam(required = false) String q) {
        return riesgos.listar(UsuarioSesion.actual(), filial, categoria, estado, nivel, q);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ficha del riesgo con evaluaciones, plan, KRI, alertas e historial")
    public Map<String, Object> detalle(@PathVariable int id) {
        return riesgos.detalle(UsuarioSesion.actual(), id);
    }

    @GetMapping("/similares")
    @Operation(summary = "CU01: riesgos con un título parecido (control de duplicados)")
    public List<Map<String, Object>> similares(@RequestParam(required = false) Integer filial, @RequestParam String titulo) {
        return riesgos.similares(UsuarioSesion.actual(), filial, titulo);
    }

    @PostMapping
    @Operation(summary = "CU01: registrar un riesgo")
    public Map<String, Object> registrar(@RequestBody ServicioRiesgos.NuevoRiesgo n) {
        UsuarioSesion u = UsuarioSesion.actual();
        return intentos.registrar(u, "REGISTRAR_RIESGO", "RIESGO", null, () -> riesgos.registrar(u, n));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modificar los datos descriptivos del riesgo")
    public Map<String, Object> actualizar(@PathVariable int id, @RequestBody ServicioRiesgos.NuevoRiesgo n) {
        return riesgos.actualizar(UsuarioSesion.actual(), id, n);
    }

    @PostMapping("/{id}/evaluaciones/previsualizar")
    @Operation(summary = "P03: cálculo en vivo del nivel, la conversión a USD y la validación requerida")
    public Map<String, Object> previsualizar(@PathVariable int id, @RequestBody Previsualizacion v) {
        return riesgos.previsualizar(UsuarioSesion.actual(), id, v.probabilidad() == null ? 1 : v.probabilidad(),
                v.impacto() == null ? 1 : v.impacto(), v.impactoFinanciero(), v.moneda());
    }

    @PostMapping("/{id}/evaluaciones")
    @Operation(summary = "CU02: evaluar el riesgo (inherente) o registrar la evaluación residual (CU05)")
    public Map<String, Object> evaluar(@PathVariable int id, @RequestBody ServicioRiesgos.NuevaEvaluacion n) {
        UsuarioSesion u = UsuarioSesion.actual();
        return intentos.registrar(u, "EVALUAR_RIESGO", "RIESGO", id, () -> riesgos.evaluar(u, id, n));
    }

    @PostMapping("/{id}/controles")
    @Operation(summary = "Registrar un control existente")
    public Map<String, Object> agregarControl(@PathVariable int id, @RequestBody ServicioRiesgos.NuevoControl c) {
        return riesgos.agregarControl(UsuarioSesion.actual(), id, c);
    }

    @DeleteMapping("/{id}/controles/{idControl}")
    public Map<String, Object> eliminarControl(@PathVariable int id, @PathVariable int idControl) {
        riesgos.eliminarControl(UsuarioSesion.actual(), id, idControl);
        return Map.of("eliminado", true);
    }

    @PutMapping("/{id}/plan")
    @Operation(summary = "CU03: crear o modificar el plan de mitigación en borrador")
    public Map<String, Object> guardarPlan(@PathVariable int id, @RequestBody ServicioTratamiento.DatosPlan d) {
        return tratamiento.guardarPlan(UsuarioSesion.actual(), id, d);
    }

    @PostMapping("/{id}/plan/acciones")
    @Operation(summary = "CU03: agregar una acción con responsable y fecha límite (RN05)")
    public Map<String, Object> agregarAccion(@PathVariable int id, @RequestBody ServicioTratamiento.NuevaAccion a) {
        return tratamiento.agregarAccion(UsuarioSesion.actual(), id, a);
    }

    @DeleteMapping("/{id}/plan/acciones/{idAccion}")
    public Map<String, Object> eliminarAccion(@PathVariable int id, @PathVariable int idAccion) {
        tratamiento.eliminarAccion(UsuarioSesion.actual(), id, idAccion);
        return Map.of("eliminado", true);
    }

    @PostMapping("/{id}/plan/proponer")
    @Operation(summary = "CU03: proponer el plan para su aprobación")
    public Map<String, Object> proponer(@PathVariable int id) {
        UsuarioSesion u = UsuarioSesion.actual();
        return intentos.registrar(u, "PROPONER_PLAN", "RIESGO", id, () -> tratamiento.proponer(u, id));
    }

    @GetMapping("/{id}/plan/verificacion")
    @Operation(summary = "CU04: lista de verificación del aprobador (RN03, RN04, RN05, RN12, RN16)")
    public Map<String, Object> verificacion(@PathVariable int id) {
        return tratamiento.verificacion(UsuarioSesion.actual(), id);
    }

    @PostMapping("/{id}/plan/aprobar")
    @Operation(summary = "CU04: aprobar el tratamiento")
    public Map<String, Object> aprobar(@PathVariable int id) {
        UsuarioSesion u = UsuarioSesion.actual();
        return intentos.registrar(u, "APROBAR_TRATAMIENTO", "RIESGO", id, () -> tratamiento.aprobar(u, id));
    }

    @PostMapping("/{id}/plan/rechazar")
    @Operation(summary = "CU04: rechazar el tratamiento con su motivo")
    public Map<String, Object> rechazar(@PathVariable int id, @RequestBody Motivo m) {
        UsuarioSesion u = UsuarioSesion.actual();
        return intentos.registrar(u, "RECHAZAR_TRATAMIENTO", "RIESGO", id, () -> tratamiento.rechazar(u, id, m.motivo()));
    }

    @GetMapping("/aprobaciones")
    @Operation(summary = "Planes que esperan la aprobación del usuario (RN04)")
    public List<Map<String, Object>> aprobaciones() {
        return tratamiento.pendientesDeAprobacion(UsuarioSesion.actual());
    }

    @PostMapping("/{id}/cerrar")
    @Operation(summary = "Cerrar un riesgo mitigado o aceptado (D08)")
    public Map<String, Object> cerrar(@PathVariable int id) {
        UsuarioSesion u = UsuarioSesion.actual();
        return intentos.registrar(u, "CERRAR_RIESGO", "RIESGO", id, () -> tratamiento.cerrarRiesgo(u, id));
    }
}

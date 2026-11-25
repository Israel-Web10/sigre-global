package bo.umsa.sigre.cumplimiento;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import bo.umsa.sigre.comun.Intentos;
import bo.umsa.sigre.seguridad.UsuarioSesion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/cumplimiento")
@Tag(name = "Cumplimiento", description = "CU12 y CU13: normativas, obligaciones y evaluación del cumplimiento")
public class CumplimientoController {

    private final ServicioCumplimiento cumplimiento;
    private final Intentos intentos;

    public CumplimientoController(ServicioCumplimiento cumplimiento, Intentos intentos) {
        this.cumplimiento = cumplimiento;
        this.intentos = intentos;
    }

    @GetMapping("/normativas")
    @Operation(summary = "Normativas con el porcentaje de cumplimiento de sus obligaciones")
    public List<Map<String, Object>> normativas() {
        return cumplimiento.normativas(UsuarioSesion.actual());
    }

    @PostMapping("/normativas")
    @Operation(summary = "CU12: registrar una normativa")
    public Map<String, Object> registrarNormativa(@RequestBody ServicioCumplimiento.NuevaNormativa n) {
        UsuarioSesion u = UsuarioSesion.actual();
        return intentos.registrar(u, "REGISTRAR_NORMATIVA", "NORMATIVA", null, () -> cumplimiento.registrarNormativa(u, n));
    }

    @PostMapping("/normativas/{id}/obligaciones")
    @Operation(summary = "CU12: registrar una obligación para una filial (RN09 genera el aviso si vence en 30 días)")
    public Map<String, Object> registrarObligacion(@PathVariable int id, @RequestBody ServicioCumplimiento.NuevaObligacion n) {
        return cumplimiento.registrarObligacion(UsuarioSesion.actual(), id, n);
    }

    @GetMapping("/obligaciones")
    public List<Map<String, Object>> obligaciones(@RequestParam(required = false) Integer normativa,
                                                  @RequestParam(required = false) Integer filial,
                                                  @RequestParam(required = false) String estado) {
        return cumplimiento.obligaciones(UsuarioSesion.actual(), normativa, filial, estado);
    }

    @GetMapping("/obligaciones/{id}")
    public Map<String, Object> obligacion(@PathVariable int id) {
        return cumplimiento.obligacion(UsuarioSesion.actual(), id);
    }

    @PostMapping("/obligaciones/{id}/evaluaciones")
    @Operation(summary = "CU13: evaluar el cumplimiento (si no cumple se registra el riesgo Legal, RN10)")
    public Map<String, Object> evaluar(@PathVariable int id, @RequestBody ServicioCumplimiento.NuevaEvaluacion n) {
        UsuarioSesion u = UsuarioSesion.actual();
        return intentos.registrar(u, "EVALUAR_CUMPLIMIENTO", "OBLIGACION", id, () -> cumplimiento.evaluar(u, id, n));
    }

    @PostMapping("/obligaciones/{id}/renovar")
    public Map<String, Object> renovar(@PathVariable int id) {
        return cumplimiento.renovar(UsuarioSesion.actual(), id);
    }

    @PostMapping(value = "/evaluaciones/{id}/evidencias", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> evidencia(@PathVariable int id, @RequestParam("archivo") MultipartFile archivo,
                                         @RequestParam(value = "descripcion", required = false) String descripcion) {
        return cumplimiento.cargarEvidencia(UsuarioSesion.actual(), id, archivo, descripcion);
    }

    @GetMapping("/servicio-normativo/novedades")
    @Operation(summary = "Servicio normativo externo: novedades publicadas (simulado)")
    public List<Map<String, Object>> novedades() {
        return cumplimiento.novedades();
    }

    @PostMapping("/servicio-normativo/{codigo}/importar")
    public Map<String, Object> importar(@PathVariable String codigo) {
        return cumplimiento.importar(UsuarioSesion.actual(), codigo);
    }
}

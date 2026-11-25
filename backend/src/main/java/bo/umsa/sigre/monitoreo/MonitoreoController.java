package bo.umsa.sigre.monitoreo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import bo.umsa.sigre.comun.Intentos;
import bo.umsa.sigre.seguridad.UsuarioSesion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Monitoreo", description = "CU06 a CU09: KRI, alertas, notificaciones y eventos en tiempo real")
public class MonitoreoController {

    private final ServicioKri kri;
    private final ServicioAlertas alertas;
    private final ServicioNotificaciones notificaciones;
    private final ServicioEventos eventos;
    private final Intentos intentos;

    public MonitoreoController(ServicioKri kri, ServicioAlertas alertas, ServicioNotificaciones notificaciones,
                               ServicioEventos eventos, Intentos intentos) {
        this.kri = kri;
        this.alertas = alertas;
        this.notificaciones = notificaciones;
        this.eventos = eventos;
        this.intentos = intentos;
    }

    public record Valor(BigDecimal valor) { }
    public record Accion(String accionCorrectiva) { }
    public record Activo(Boolean activo) { }

    // ------------------------------------------------------------------ KRI

    @GetMapping("/api/kri")
    @Operation(summary = "Indicadores con su último valor, semáforo y serie reciente")
    public List<Map<String, Object>> listarKri(@RequestParam(required = false) Integer filial,
                                               @RequestParam(required = false) String fuente,
                                               @RequestParam(required = false) String semaforo,
                                               @RequestParam(required = false) Integer riesgo) {
        return kri.listar(UsuarioSesion.actual(), filial, fuente, semaforo, riesgo);
    }

    @GetMapping("/api/kri/{id}")
    public Map<String, Object> detalleKri(@PathVariable int id, @RequestParam(defaultValue = "120") int limite) {
        return kri.detalle(UsuarioSesion.actual(), id, limite);
    }

    @PostMapping("/api/kri")
    @Operation(summary = "Definir un indicador para un riesgo")
    public Map<String, Object> definirKri(@RequestBody ServicioKri.NuevoKri n) {
        return kri.definir(UsuarioSesion.actual(), n);
    }

    @PostMapping("/api/kri/{id}/mediciones")
    @Operation(summary = "CU06: registrar una medición manual (el semáforo y la alerta los genera el trigger)")
    public Map<String, Object> medir(@PathVariable int id, @RequestBody Valor v) {
        UsuarioSesion u = UsuarioSesion.actual();
        return intentos.registrar(u, "REGISTRAR_MEDICION", "INDICADOR_KRI", id, () -> kri.registrarMedicion(u, id, v.valor()));
    }

    @PutMapping("/api/kri/{id}/umbrales")
    @Operation(summary = "CP19: modificar los umbrales del indicador (queda en la bitácora)")
    public Map<String, Object> umbrales(@PathVariable int id, @RequestBody ServicioKri.Umbrales n) {
        UsuarioSesion u = UsuarioSesion.actual();
        return intentos.registrar(u, "MODIFICAR_UMBRAL", "INDICADOR_KRI", id, () -> kri.cambiarUmbrales(u, id, n));
    }

    @PutMapping("/api/kri/{id}/activo")
    public Map<String, Object> activarKri(@PathVariable int id, @RequestBody Activo a) {
        return kri.activar(UsuarioSesion.actual(), id, !Boolean.FALSE.equals(a.activo()));
    }

    // ------------------------------------------------------------------ Alertas

    @GetMapping("/api/alertas")
    @Operation(summary = "Alertas visibles para el usuario con el tiempo restante del SLA")
    public List<Map<String, Object>> listarAlertas(@RequestParam(required = false) String estado,
                                                   @RequestParam(required = false) String severidad,
                                                   @RequestParam(required = false) String tipo,
                                                   @RequestParam(defaultValue = "false") boolean mias,
                                                   @RequestParam(defaultValue = "false") boolean abiertas) {
        return alertas.listar(UsuarioSesion.actual(), estado, severidad, tipo, mias, abiertas);
    }

    @GetMapping("/api/alertas/resumen")
    public Map<String, Object> resumenAlertas() {
        return alertas.resumen(UsuarioSesion.actual());
    }

    @GetMapping("/api/alertas/{id}")
    public Map<String, Object> detalleAlerta(@PathVariable int id) {
        return alertas.detalle(UsuarioSesion.actual(), id);
    }

    @PostMapping("/api/alertas/{id}/atender")
    @Operation(summary = "CU08: tomar la alerta (EN_ATENCION)")
    public Map<String, Object> atender(@PathVariable int id) {
        UsuarioSesion u = UsuarioSesion.actual();
        return intentos.registrar(u, "ATENDER_ALERTA", "ALERTA", id, () -> alertas.atender(u, id));
    }

    @PostMapping("/api/alertas/{id}/resolver")
    @Operation(summary = "CU08: registrar la acción correctiva y resolver")
    public Map<String, Object> resolver(@PathVariable int id, @RequestBody Accion a) {
        UsuarioSesion u = UsuarioSesion.actual();
        return intentos.registrar(u, "RESOLVER_ALERTA", "ALERTA", id, () -> alertas.resolver(u, id, a.accionCorrectiva()));
    }

    @PostMapping("/api/alertas/{id}/cerrar")
    public Map<String, Object> cerrar(@PathVariable int id) {
        UsuarioSesion u = UsuarioSesion.actual();
        return intentos.registrar(u, "CERRAR_ALERTA", "ALERTA", id, () -> alertas.cerrar(u, id));
    }

    @PostMapping("/api/alertas/{id}/reabrir")
    public Map<String, Object> reabrir(@PathVariable int id) {
        return alertas.reabrir(UsuarioSesion.actual(), id);
    }

    @PostMapping("/api/alertas/{id}/escalar")
    @Operation(summary = "CU09: escalar manualmente al superior jerárquico")
    public Map<String, Object> escalar(@PathVariable int id) {
        UsuarioSesion u = UsuarioSesion.actual();
        return intentos.registrar(u, "ESCALAR_ALERTA", "ALERTA", id, () -> alertas.escalarManual(u, id));
    }

    // ------------------------------------------------------------------ Notificaciones y eventos

    @GetMapping("/api/notificaciones")
    public Map<String, Object> bandeja() {
        return notificaciones.bandeja(UsuarioSesion.actual());
    }

    @GetMapping("/api/notificaciones/externas")
    @Operation(summary = "Correos y SMS enviados por el servicio de notificaciones (bandeja simulada)")
    public List<Map<String, Object>> externas() {
        return notificaciones.mensajesExternos(UsuarioSesion.actual());
    }

    @PostMapping("/api/notificaciones/{id}/leida")
    public Map<String, Object> leida(@PathVariable long id) {
        notificaciones.marcarLeida(UsuarioSesion.actual(), id);
        return Map.of("ok", true);
    }

    @PostMapping("/api/notificaciones/leidas")
    public Map<String, Object> todasLeidas() {
        notificaciones.marcarTodas(UsuarioSesion.actual());
        return Map.of("ok", true);
    }

    @GetMapping(value = "/api/eventos", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Flujo de eventos en tiempo real (Server-Sent Events)")
    public SseEmitter eventos() {
        return eventos.suscribir(UsuarioSesion.actual());
    }
}

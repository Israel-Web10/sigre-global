package bo.umsa.sigre.integraciones;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import bo.umsa.sigre.comun.Consultas;
import bo.umsa.sigre.monitoreo.ServicioAlertas;
import bo.umsa.sigre.monitoreo.ServicioNotificaciones;

/**
 * Servidor de trabajos (sección 10.1): entrega de alertas, escalamiento por SLA (CU09),
 * avisos que dependen del calendario (RN09, RN16, RF14) y sincronización con el ERP.
 * Las tareas corren a nombre del sistema (en la bitácora figuran como "Sistema").
 */
@Component
public class TareasProgramadas {

    private static final Logger log = LoggerFactory.getLogger(TareasProgramadas.class);

    private final ServicioNotificaciones notificaciones;
    private final ServicioAlertas alertas;
    private final ServicioErp erp;
    private final Consultas bd;
    private final Map<String, Object> ultimas = new LinkedHashMap<>();

    public TareasProgramadas(ServicioNotificaciones notificaciones, ServicioAlertas alertas, ServicioErp erp, Consultas bd) {
        this.notificaciones = notificaciones;
        this.alertas = alertas;
        this.erp = erp;
        this.bd = bd;
    }

    public Map<String, Object> estado() {
        synchronized (ultimas) {
            return new LinkedHashMap<>(ultimas);
        }
    }

    private void anotar(String tarea, Object resultado) {
        synchronized (ultimas) {
            ultimas.put(tarea, Map.of("fecha", OffsetDateTime.now().toString(), "resultado", resultado));
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alIniciar() {
        sincronizarErp();
        generarAvisos();
    }

    @Scheduled(fixedDelayString = "${sigre.tareas.notificacion-ms:5000}", initialDelay = 4000)
    public void entregarAlertas() {
        try {
            int n = notificaciones.entregarPendientes();
            if (n > 0) anotar("notificacion", n + " alerta(s) notificadas");
        } catch (RuntimeException e) {
            log.warn("Entrega de alertas: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${sigre.tareas.escalamiento-ms:60000}", initialDelay = 20000)
    public void escalar() {
        try {
            int n = alertas.escalarVencidas();
            anotar("escalamiento", n + " alerta(s) escaladas");
            if (n > 0) log.info("CU09: {} alerta(s) escaladas por SLA vencido", n);
        } catch (RuntimeException e) {
            log.warn("Escalamiento: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${sigre.tareas.alertas-programadas-ms:120000}", initialDelay = 60000)
    public void generarAvisos() {
        try {
            Integer n = bd.valor("SELECT fn_generar_alertas_programadas()", Map.of(), Integer.class);
            anotar("avisos", n + " aviso(s) generados");
        } catch (RuntimeException e) {
            log.warn("Avisos programados: {}", e.getMessage());
        }
    }

    /** RF23: el primer día de cada mes recuerda al Auditor Financiero conciliar el período cerrado (CU18). */
    @Scheduled(cron = "0 0 7 1 * *")
    public void recordarConciliacion() {
        try {
            String periodo = java.time.YearMonth.now().minusMonths(1).toString();
            bd.lista("""
                    SELECT fn_notificar(u.id_usuario, 'Conciliación mensual pendiente',
                           'Concilie los KRI financieros del período ' || :p || ' con el cierre del ERP (CU18, RN18).', 'TAREA', '/conciliacion')
                    FROM usuario u JOIN usuario_rol ur USING (id_usuario) JOIN rol r USING (id_rol)
                    WHERE r.codigo = 'AUDITOR_FINANCIERO' AND u.activo""", java.util.Map.of("p", periodo));
            anotar("conciliacion", "recordatorio del período " + periodo);
        } catch (RuntimeException e) {
            log.warn("Recordatorio de conciliación: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "0 5 0 * * *")
    public void sincronizarErp() {
        try {
            anotar("erp", erp.sincronizarTiposDeCambio() + " tipo(s) de cambio");
        } catch (RuntimeException e) {
            log.warn("Sincronización con el ERP: {}", e.getMessage());
        }
    }
}

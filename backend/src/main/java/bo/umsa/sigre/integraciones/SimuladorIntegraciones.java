package bo.umsa.sigre.integraciones;

import static bo.umsa.sigre.comun.Consultas.p;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import bo.umsa.sigre.comun.Consultas;
import bo.umsa.sigre.comun.ReglaNegocioException;

/**
 * Simula los sistemas externos que alimentan los KRI automáticos: el ERP corporativo
 * (indicadores financieros y operativos) y el SIEM de seguridad (indicadores tecnológicos).
 * Cada cierto tiempo publica una medición; con baja probabilidad publica un valor fuera de
 * umbral para que el trigger genere la alerta (CU07) y se vea el flujo en tiempo real.
 */
@Service
public class SimuladorIntegraciones {

    private static final Logger log = LoggerFactory.getLogger(SimuladorIntegraciones.class);

    private final Consultas bd;
    private volatile boolean activo;
    private volatile OffsetDateTime ultimaEjecucion;
    private volatile Map<String, Object> ultimaMedicion;
    private final AtomicLong publicadas = new AtomicLong();
    private final AtomicLong fueraDeUmbral = new AtomicLong();
    private long siguiente;

    public SimuladorIntegraciones(Consultas bd, @Value("${sigre.simulador.activo:${sigre.demo.habilitado:false}}") boolean demo) {
        this.bd = bd;
        this.activo = demo;
    }

    public boolean isActivo() {
        return activo;
    }

    public void activar(boolean activo) {
        this.activo = activo;
    }

    public Map<String, Object> estado() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("activo", activo);
        m.put("intervalo_seg", intervalo());
        m.put("probabilidad_evento", probabilidad());
        m.put("publicadas", publicadas.get());
        m.put("fuera_de_umbral", fueraDeUmbral.get());
        m.put("ultima_ejecucion", ultimaEjecucion == null ? null : ultimaEjecucion.toString());
        m.put("ultima_medicion", ultimaMedicion);
        return m;
    }

    private int intervalo() {
        BigDecimal v = bd.valor("SELECT fn_param('simulador_intervalo_seg')", Map.of(), BigDecimal.class);
        return v == null ? 6 : Math.max(2, v.intValue());
    }

    private double probabilidad() {
        BigDecimal v = bd.valor("SELECT fn_param('simulador_prob_evento')", Map.of(), BigDecimal.class);
        return v == null ? 0.12 : v.doubleValue();
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 15000)
    public void ciclo() {
        if (!activo) return;
        long ahora = System.currentTimeMillis();
        if (ahora < siguiente) return;
        siguiente = ahora + intervalo() * 1000L;
        try {
            publicarAleatoria();
        } catch (RuntimeException e) {
            log.debug("Simulador: {}", e.getMessage());
            bd.ejecutar("INSERT INTO incidencia_integracion (sistema, operacion, mensaje) VALUES ('ERP', 'PUBLICAR_MEDICION', :m)",
                    p("m", e.getMessage() == null ? "Error" : e.getMessage().substring(0, Math.min(390, e.getMessage().length()))));
        }
    }

    private void publicarAleatoria() {
        List<Map<String, Object>> kris = bd.lista("""
                SELECT k.id_kri FROM indicador_kri k JOIN riesgo r USING (id_riesgo)
                WHERE k.activo AND k.fuente IN ('ERP', 'SIEM') AND r.estado <> 'CERRADO'""");
        if (kris.isEmpty()) return;
        int id = (Integer) kris.get(ThreadLocalRandom.current().nextInt(kris.size())).get("id_kri");
        double r = ThreadLocalRandom.current().nextDouble();
        double prob = probabilidad();
        String zona = r < prob * 0.45 ? "ROJO" : r < prob ? "AMARILLO" : "VERDE";
        publicar(id, zona);
    }

    /** Publica una medición del sistema externo en la zona pedida del semáforo. */
    public Map<String, Object> publicar(int idKri, String zona) {
        Map<String, Object> k = bd.uno("""
                SELECT k.*, (SELECT valor FROM medicion_kri m WHERE m.id_kri = k.id_kri ORDER BY fecha DESC LIMIT 1) AS ultimo
                FROM indicador_kri k WHERE k.id_kri = :k""", p("k", idKri));
        if (k == null) throw ReglaNegocioException.noEncontrado("El indicador");
        if (!Boolean.TRUE.equals(k.get("activo"))) throw new ReglaNegocioException("CU06: El indicador está inactivo");
        double am = ((BigDecimal) k.get("umbral_amarillo")).doubleValue();
        double ro = ((BigDecimal) k.get("umbral_rojo")).doubleValue();
        boolean mayor = "MAYOR".equals(k.get("direccion"));
        double span = Math.max(Math.abs(ro - am), Math.abs(am) * 0.05 + 0.01);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double s = mayor ? 1 : -1;
        double objetivo = switch (zona) {
            case "ROJO" -> ro + s * span * rnd.nextDouble(0.05, 0.8);
            case "AMARILLO" -> am + s * span * rnd.nextDouble(0.08, 0.9);
            default -> am - s * span * rnd.nextDouble(0.2, 1.8);
        };
        if ("VERDE".equals(zona) && k.get("ultimo") instanceof BigDecimal u) {
            double ult = u.doubleValue();
            boolean ultVerde = mayor ? ult <= am : ult >= am;
            if (ultVerde) objetivo = ult * 0.55 + objetivo * 0.45;
        }
        if (am >= 0 && ro >= 0 && objetivo < 0) objetivo = Math.abs(objetivo) * 0.2;
        BigDecimal valor = BigDecimal.valueOf(objetivo).setScale(Math.abs(am) >= 100 ? 0 : 2, RoundingMode.HALF_UP);
        String origen = (String) k.get("fuente");
        if (!"ERP".equals(origen) && !"SIEM".equals(origen)) origen = "ERP";
        Long id = bd.valor("INSERT INTO medicion_kri (id_kri, valor, origen) VALUES (:k, :v, :o) RETURNING id_medicion",
                p("k", idKri, "v", valor, "o", origen), Long.class);
        Map<String, Object> m = bd.uno("SELECT id_medicion, valor, semaforo, fecha, origen FROM medicion_kri WHERE id_medicion = :m",
                p("m", id));
        m.put("kri", k.get("nombre"));
        ultimaMedicion = m;
        ultimaEjecucion = OffsetDateTime.now();
        publicadas.incrementAndGet();
        if (!"VERDE".equals(m.get("semaforo"))) fueraDeUmbral.incrementAndGet();
        return m;
    }
}

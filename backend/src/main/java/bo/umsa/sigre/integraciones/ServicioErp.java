package bo.umsa.sigre.integraciones;

import static bo.umsa.sigre.comun.Consultas.p;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import bo.umsa.sigre.comun.Consultas;

/**
 * Conector con el ERP corporativo (simulado). Publica el tipo de cambio diario (RN15) y los
 * cierres contables mensuales de los KRI financieros (RN18).
 */
@Service
public class ServicioErp {

    private static final Logger log = LoggerFactory.getLogger(ServicioErp.class);

    private final Consultas bd;

    public ServicioErp(Consultas bd) {
        this.bd = bd;
    }

    /** Completa los tipos de cambio que falten hasta hoy con una variación diaria pequeña. */
    public int sincronizarTiposDeCambio() {
        int n = 0;
        List<Map<String, Object>> monedas = bd.lista("SELECT DISTINCT moneda FROM filial WHERE moneda <> 'USD'");
        for (Map<String, Object> m : monedas) {
            String moneda = (String) m.get("moneda");
            Map<String, Object> ultimo = bd.uno("""
                    SELECT fecha, unidades_por_usd FROM tipo_cambio WHERE moneda = :m ORDER BY fecha DESC LIMIT 1""", p("m", moneda));
            if (ultimo == null) continue;
            LocalDate fecha = LocalDate.parse((String) ultimo.get("fecha"));
            BigDecimal valor = (BigDecimal) ultimo.get("unidades_por_usd");
            while (fecha.isBefore(LocalDate.now())) {
                fecha = fecha.plusDays(1);
                if (!"BOB".equals(moneda)) {
                    double variacion = ThreadLocalRandom.current().nextGaussian() * 0.0025;
                    valor = valor.multiply(BigDecimal.valueOf(1 + variacion)).setScale(6, RoundingMode.HALF_UP);
                }
                bd.ejecutar("INSERT INTO tipo_cambio (fecha, moneda, unidades_por_usd) VALUES (:f, :m, :v) ON CONFLICT DO NOTHING",
                        p("f", fecha, "m", moneda, "v", valor));
                n++;
            }
        }
        if (n > 0) log.info("ERP: {} tipos de cambio sincronizados", n);
        return n;
    }

    public List<Map<String, Object>> tiposDeCambio(LocalDate fecha) {
        return bd.lista("""
                SELECT DISTINCT ON (moneda) moneda, fecha, unidades_por_usd FROM tipo_cambio
                WHERE fecha <= :f ORDER BY moneda, fecha DESC""", p("f", fecha));
    }

    public List<Map<String, Object>> historico(String moneda, int dias) {
        return bd.lista("""
                SELECT fecha, unidades_por_usd FROM tipo_cambio WHERE moneda = :m AND fecha >= CURRENT_DATE - :d
                ORDER BY fecha""", p("m", moneda, "d", Math.max(7, Math.min(dias, 500))));
    }

    public List<Map<String, Object>> cierres(String periodo) {
        return bd.lista("""
                SELECT c.id_kri, c.periodo, c.valor, k.nombre, k.unidad, r.codigo, f.nombre AS filial
                FROM cierre_erp_kri c JOIN indicador_kri k USING (id_kri) JOIN riesgo r USING (id_riesgo)
                JOIN filial f ON f.id_filial = r.id_filial WHERE (:p = '' OR c.periodo = :p) ORDER BY c.periodo DESC, f.nombre""",
                p("p", periodo == null ? "" : periodo));
    }
}

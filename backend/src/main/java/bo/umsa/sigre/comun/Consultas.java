package bo.umsa.sigre.comun;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Consultas de lectura (modelos de consulta para pantallas y reportes).
 * Las escrituras del dominio pasan por JPA/Hibernate; las vistas agregadas y los tableros
 * se leen directamente con SQL. Fechas en ISO 8601 y JSONB como objetos.
 */
@Component
public class Consultas {

    private static final ZoneId ZONA = ZoneId.of("America/La_Paz");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ColumnMapRowMapper filas;

    public Consultas(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.filas = new ColumnMapRowMapper() {
            @Override
            protected Object getColumnValue(ResultSet rs, int index) throws SQLException {
                return convertir(JdbcUtils.getResultSetValue(rs, index));
            }
        };
    }

    private Object convertir(Object v) throws SQLException {
        if (v instanceof Timestamp ts) return ts.toInstant().atZone(ZONA).toOffsetDateTime().toString();
        if (v instanceof java.sql.Date d) return d.toLocalDate().toString();
        if (v instanceof PGobject pg) {
            if (pg.getValue() == null) return null;
            if ("json".equals(pg.getType()) || "jsonb".equals(pg.getType())) {
                try {
                    return mapper.readValue(pg.getValue(), Object.class);
                } catch (Exception e) {
                    return pg.getValue();
                }
            }
            return pg.getValue();
        }
        if (v instanceof Array a) return Arrays.asList((Object[]) a.getArray());
        return v;
    }

    public List<Map<String, Object>> lista(String sql, Map<String, ?> p) {
        return jdbc.query(sql, p, filas);
    }

    public List<Map<String, Object>> lista(String sql) {
        return lista(sql, Map.of());
    }

    public Map<String, Object> uno(String sql, Map<String, ?> p) {
        List<Map<String, Object>> r = lista(sql, p);
        return r.isEmpty() ? null : r.get(0);
    }

    public <T> T valor(String sql, Map<String, ?> p, Class<T> tipo) {
        List<T> r = jdbc.queryForList(sql, p, tipo);
        return r.isEmpty() ? null : r.get(0);
    }

    public int ejecutar(String sql, Map<String, ?> p) {
        return jdbc.update(sql, p);
    }

    public NamedParameterJdbcTemplate jdbc() {
        return jdbc;
    }

    /** Arma un mapa de parámetros que admite valores nulos: p("a", 1, "b", null). */
    public static Map<String, Object> p(Object... claveValor) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < claveValor.length; i += 2) m.put((String) claveValor[i], claveValor[i + 1]);
        return m;
    }
}

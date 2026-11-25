package bo.umsa.sigre.comun;

import java.util.HashMap;
import java.util.Map;

import bo.umsa.sigre.seguridad.UsuarioSesion;

/** Arma los filtros opcionales de una consulta sin concatenar valores en el SQL. */
public final class Condiciones {

    private final StringBuilder sql = new StringBuilder();
    private final Map<String, Object> params = new HashMap<>();

    public Condiciones y(String condicion) {
        sql.append(" AND ").append(condicion);
        return this;
    }

    /** Agrega la condición solo si el valor viene informado. */
    public Condiciones si(Object valor, String condicion, String nombre) {
        if (valor == null || (valor instanceof String s && s.isBlank())) return this;
        sql.append(" AND ").append(condicion);
        params.put(nombre, valor instanceof String s ? s.trim() : valor);
        return this;
    }

    /** RN14: un usuario que no es corporativo solo ve su filial. */
    public Condiciones alcance(UsuarioSesion u, String columnaFilial) {
        if (!u.corporativo()) {
            sql.append(" AND ").append(columnaFilial).append(" = :alcance_filial");
            params.put("alcance_filial", u.idFilial());
        }
        return this;
    }

    public Condiciones param(String nombre, Object valor) {
        params.put(nombre, valor);
        return this;
    }

    public String sql() {
        return sql.toString();
    }

    public Map<String, Object> params() {
        return params;
    }
}

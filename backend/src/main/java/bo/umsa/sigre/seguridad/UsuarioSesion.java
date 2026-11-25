package bo.umsa.sigre.seguridad;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.core.context.SecurityContextHolder;

import bo.umsa.sigre.comun.ReglaNegocioException;

/** Usuario autenticado, con sus roles y su filial (RN14). */
public record UsuarioSesion(int id, String login, String nombre, String cargo, int idFilial, String filial,
                            String filialCodigo, Set<String> roles, boolean corporativo, String idioma) {

    public static final String GESTOR = "GESTOR";
    public static final String RESPONSABLE = "RESPONSABLE";
    public static final String AUDITOR_INTERNO = "AUDITOR_INTERNO";
    public static final String OFICIAL = "OFICIAL_CUMPLIMIENTO";
    public static final String DIRECTOR = "DIRECTOR";
    public static final String GERENTE = "GERENTE_FILIAL";
    public static final String ADMIN = "ADMIN";
    public static final String AUDITOR_FINANCIERO = "AUDITOR_FINANCIERO";

    public boolean tiene(String rol) {
        return roles.contains(rol);
    }

    public boolean tieneAlguno(String... lista) {
        for (String r : lista) if (roles.contains(r)) return true;
        return false;
    }

    /** RN14: acceso a una filial. */
    public boolean accedeA(Integer filial) {
        return corporativo || (filial != null && filial == idFilial);
    }

    public void exigir(String operacion, String... rolesPermitidos) {
        if (!tieneAlguno(rolesPermitidos)) {
            throw ReglaNegocioException.prohibido("Su rol no permite " + operacion + " (matriz de permisos, Tabla 47)");
        }
    }

    public void exigirFilial(Integer filial) {
        if (!accedeA(filial)) {
            throw ReglaNegocioException.prohibido("RN14: No tiene acceso a la información de esa filial");
        }
    }

    public Map<String, Object> comoMapa() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("login", login);
        m.put("nombre", nombre);
        m.put("cargo", cargo);
        m.put("idFilial", idFilial);
        m.put("filial", filial);
        m.put("filialCodigo", filialCodigo);
        m.put("roles", List.copyOf(roles));
        m.put("corporativo", corporativo);
        m.put("idioma", idioma);
        return m;
    }

    /** Usuario de la petición actual. */
    public static UsuarioSesion actual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioSesion u) return u;
        throw new ReglaNegocioException("Sesión no válida", org.springframework.http.HttpStatus.UNAUTHORIZED);
    }
}

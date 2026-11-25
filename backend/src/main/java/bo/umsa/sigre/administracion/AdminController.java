package bo.umsa.sigre.administracion;

import static bo.umsa.sigre.comun.Consultas.p;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import bo.umsa.sigre.comun.Consultas;
import bo.umsa.sigre.comun.Intentos;
import bo.umsa.sigre.comun.ReglaNegocioException;
import bo.umsa.sigre.seguridad.UsuarioSesion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Administración", description = "CU15: usuarios, roles (RN17), filiales, apetito de riesgo y parámetros")
public class AdminController {

    private final Consultas bd;
    private final PasswordEncoder claves;
    private final Intentos intentos;

    public AdminController(Consultas bd, PasswordEncoder claves, Intentos intentos) {
        this.bd = bd;
        this.claves = claves;
        this.intentos = intentos;
    }

    public record DatosUsuario(String nombreCompleto, String login, String email, String cargo, Integer idFilial, Integer idSuperior,
                               String idioma, Boolean activo, List<String> roles, String clave) { }
    public record Clave(String clave) { }
    public record DatosFilial(Integer apetitoRiesgo, Boolean activa) { }
    public record Valor(String valor) { }

    /** Matriz de permisos por rol (Tabla 47 del informe). */
    private static final Map<String, List<String>> PERMISOS = Map.of(
            "GESTOR", List.of("Registrar y evaluar riesgos", "Definir planes e indicadores", "Cerrar riesgos", "Ver todas las filiales"),
            "RESPONSABLE", List.of("Ejecutar acciones de mitigación", "Cargar evidencias", "Registrar mediciones", "Atender alertas"),
            "AUDITOR_INTERNO", List.of("Planificar auditorías", "Registrar hallazgos", "Consultar la bitácora"),
            "OFICIAL_CUMPLIMIENTO", List.of("Registrar normativas y obligaciones", "Evaluar el cumplimiento"),
            "DIRECTOR", List.of("Aprobar riesgos Altos y Críticos", "Aceptar riesgos", "Definir el apetito de riesgo", "Tablero corporativo"),
            "GERENTE_FILIAL", List.of("Aprobar riesgos Bajos y Medios de su filial", "Tablero de su filial"),
            "ADMIN", List.of("Gestionar usuarios y roles", "Gestionar filiales y parámetros", "Consultar la bitácora"),
            "AUDITOR_FINANCIERO", List.of("Validar el impacto financiero", "Conciliar KRI con el ERP", "Registrar hallazgos financieros"));

    private static UsuarioSesion admin() {
        UsuarioSesion u = UsuarioSesion.actual();
        u.exigir("administrar el sistema", UsuarioSesion.ADMIN);
        return u;
    }

    @GetMapping("/usuarios")
    public List<Map<String, Object>> usuarios() {
        admin();
        return bd.lista("""
                SELECT u.id_usuario, u.nombre_completo, u.login, u.email, u.cargo, u.idioma, u.activo, u.totp_activo, u.ultimo_acceso,
                       u.creado_en, u.id_filial, f.nombre AS filial, f.codigo_pais, u.id_superior, s.nombre_completo AS superior,
                       coalesce(array_agg(r.codigo ORDER BY r.codigo) FILTER (WHERE r.codigo IS NOT NULL), '{}') AS roles
                FROM usuario u JOIN filial f USING (id_filial) LEFT JOIN usuario s ON s.id_usuario = u.id_superior
                LEFT JOIN usuario_rol ur ON ur.id_usuario = u.id_usuario LEFT JOIN rol r USING (id_rol)
                GROUP BY u.id_usuario, f.nombre, f.codigo_pais, s.nombre_completo ORDER BY u.nombre_completo""");
    }

    private static void validarClave(String clave) {
        if (clave == null || clave.length() < 8 || !clave.matches(".*[A-Za-z].*") || !clave.matches(".*\\d.*")) {
            throw new ReglaNegocioException("RNF01: La clave debe tener al menos 8 caracteres, con letras y números");
        }
    }

    @PostMapping("/usuarios")
    @Operation(summary = "CU15: crear un usuario con sus roles (RN17 en el trigger trg_usuario_rol_sod)")
    @Transactional
    public Map<String, Object> crear(@RequestBody DatosUsuario d) {
        UsuarioSesion u = admin();
        return intentos.registrar(u, "CREAR_USUARIO", "USUARIO", null, () -> {
            if (d.login() == null || !d.login().trim().toLowerCase().matches("[a-z0-9._-]{3,40}")) {
                throw new ReglaNegocioException("CU15: El usuario debe tener entre 3 y 40 letras minúsculas, números o . _ -");
            }
            if (d.nombreCompleto() == null || d.nombreCompleto().trim().length() < 5) throw new ReglaNegocioException("CU15: Indique el nombre completo");
            if (d.email() == null || !d.email().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new ReglaNegocioException("CU15: Correo no válido");
            if (d.idFilial() == null) throw new ReglaNegocioException("CU15: Indique la filial");
            if (d.roles() == null || d.roles().isEmpty()) throw new ReglaNegocioException("CU15: Asigne al menos un rol");
            validarClave(d.clave());
            Integer id = bd.valor("""
                    INSERT INTO usuario (id_filial, id_superior, nombre_completo, login, email, cargo, clave_hash, idioma)
                    VALUES (:f, :s, :n, :l, :e, :c, :h, :i) RETURNING id_usuario""",
                    p("f", d.idFilial(), "s", d.idSuperior(), "n", d.nombreCompleto().trim(), "l", d.login().trim().toLowerCase(),
                            "e", d.email().trim().toLowerCase(), "c", d.cargo(), "h", claves.encode(d.clave()),
                            "i", d.idioma() == null ? "es" : d.idioma()), Integer.class);
            asignarRoles(id, new TreeSet<>(d.roles()));
            return Map.of("id_usuario", id);
        });
    }

    @PutMapping("/usuarios/{id}")
    @Operation(summary = "CU15: modificar un usuario, activarlo o desactivarlo y cambiar sus roles")
    @Transactional
    public Map<String, Object> modificar(@PathVariable int id, @RequestBody DatosUsuario d) {
        UsuarioSesion u = admin();
        return intentos.registrar(u, "MODIFICAR_USUARIO", "USUARIO", id, () -> {
            if (id == u.id() && Boolean.FALSE.equals(d.activo())) throw new ReglaNegocioException("CU15: No puede desactivar su propia cuenta");
            if (d.idSuperior() != null && d.idSuperior() == id) throw new ReglaNegocioException("CU15: Un usuario no puede ser su propio superior");
            bd.ejecutar("""
                    UPDATE usuario SET nombre_completo = coalesce(:n, nombre_completo), email = coalesce(:e, email),
                           cargo = coalesce(:c, cargo), id_filial = coalesce(:f, id_filial), id_superior = :s,
                           idioma = coalesce(:i, idioma), activo = coalesce(:a, activo)
                    WHERE id_usuario = :id""",
                    p("n", d.nombreCompleto(), "e", d.email(), "c", d.cargo(), "f", d.idFilial(), "s", d.idSuperior(),
                            "i", d.idioma(), "a", d.activo(), "id", id));
            if (d.roles() != null) asignarRoles(id, new TreeSet<>(d.roles()));
            return Map.of("id_usuario", id);
        });
    }

    private void asignarRoles(int id, Set<String> nuevos) {
        List<String> actuales = bd.jdbc().queryForList(
                "SELECT r.codigo FROM usuario_rol ur JOIN rol r USING (id_rol) WHERE ur.id_usuario = :u", p("u", id), String.class);
        for (String r : actuales) {
            if (!nuevos.contains(r)) {
                bd.ejecutar("DELETE FROM usuario_rol WHERE id_usuario = :u AND id_rol = (SELECT id_rol FROM rol WHERE codigo = :r)",
                        p("u", id, "r", r));
            }
        }
        if (nuevos.contains(UsuarioSesion.AUDITOR_FINANCIERO) && nuevos.contains(UsuarioSesion.GESTOR)) {
            throw new ReglaNegocioException("RN17: El rol Auditor Financiero no se combina con Gestor de Riesgos (separación de funciones)");
        }
        for (String r : nuevos) {
            if (!actuales.contains(r)) {
                int n = bd.ejecutar("INSERT INTO usuario_rol (id_usuario, id_rol) SELECT :u, id_rol FROM rol WHERE codigo = :r", p("u", id, "r", r));
                if (n == 0) throw new ReglaNegocioException("CU15: El rol " + r + " no existe");
            }
        }
    }

    @PostMapping("/usuarios/{id}/clave")
    @Operation(summary = "Restablecer la clave de un usuario")
    public Map<String, Object> restablecer(@PathVariable int id, @RequestBody Clave c) {
        UsuarioSesion u = admin();
        validarClave(c.clave());
        bd.ejecutar("UPDATE usuario SET clave_hash = :h, totp_activo = FALSE, totp_secreto = NULL WHERE id_usuario = :id",
                p("h", claves.encode(c.clave()), "id", id));
        return Map.of("id_usuario", id, "restablecida", true, "por", u.login());
    }

    @GetMapping("/roles")
    public List<Map<String, Object>> roles() {
        admin();
        List<Map<String, Object>> roles = bd.lista("""
                SELECT r.*, (SELECT count(*) FROM usuario_rol ur JOIN usuario u USING (id_usuario) WHERE ur.id_rol = r.id_rol AND u.activo) AS usuarios
                FROM rol r ORDER BY r.id_rol""");
        for (Map<String, Object> r : roles) r.put("permisos", PERMISOS.getOrDefault((String) r.get("codigo"), List.of()));
        return roles;
    }

    @GetMapping("/filiales")
    public List<Map<String, Object>> filiales() {
        UsuarioSesion.actual().exigir("administrar filiales", UsuarioSesion.ADMIN, UsuarioSesion.DIRECTOR);
        return bd.lista("""
                SELECT f.*, (SELECT count(*) FROM usuario u WHERE u.id_filial = f.id_filial AND u.activo) AS usuarios,
                       (SELECT count(*) FROM riesgo r WHERE r.id_filial = f.id_filial AND r.estado <> 'CERRADO') AS riesgos
                FROM filial f ORDER BY f.casa_matriz DESC, f.nombre""");
    }

    @PutMapping("/filiales/{id}")
    @Operation(summary = "Definir el apetito de riesgo de una filial (RN11)")
    public Map<String, Object> filial(@PathVariable int id, @RequestBody DatosFilial d) {
        UsuarioSesion u = UsuarioSesion.actual();
        u.exigir("definir el apetito de riesgo", UsuarioSesion.DIRECTOR, UsuarioSesion.ADMIN);
        if (d.apetitoRiesgo() != null && (d.apetitoRiesgo() < 1 || d.apetitoRiesgo() > 25)) {
            throw new ReglaNegocioException("RN11: El apetito de riesgo va de 1 a 25");
        }
        return intentos.registrar(u, "MODIFICAR_APETITO", "FILIAL", id, () -> {
            bd.ejecutar("UPDATE filial SET apetito_riesgo = coalesce(:a, apetito_riesgo), activa = coalesce(:act, activa) WHERE id_filial = :id",
                    p("a", d.apetitoRiesgo(), "act", d.activa(), "id", id));
            return Map.of("id_filial", id);
        });
    }

    @GetMapping("/parametros")
    public List<Map<String, Object>> parametros() {
        UsuarioSesion.actual().exigir("consultar parámetros", UsuarioSesion.ADMIN, UsuarioSesion.DIRECTOR);
        return bd.lista("SELECT * FROM parametro ORDER BY regla NULLS LAST, clave");
    }

    @PutMapping("/parametros/{clave}")
    @Operation(summary = "Modificar un parámetro de las reglas de negocio")
    public Map<String, Object> parametro(@PathVariable String clave, @RequestBody Valor v) {
        UsuarioSesion u = admin();
        return intentos.registrar(u, "MODIFICAR_PARAMETRO", "PARAMETRO", null, () -> {
            Map<String, Object> actual = bd.uno("SELECT * FROM parametro WHERE clave = :c", p("c", clave));
            if (actual == null) throw ReglaNegocioException.noEncontrado("El parámetro");
            if ("NUMERO".equals(actual.get("tipo"))) {
                BigDecimal n;
                try {
                    n = new BigDecimal(v.valor().trim());
                } catch (RuntimeException e) {
                    throw new ReglaNegocioException("CU15: El valor debe ser numérico");
                }
                if (actual.get("minimo") instanceof BigDecimal min && n.compareTo(min) < 0
                        || actual.get("maximo") instanceof BigDecimal max && n.compareTo(max) > 0) {
                    throw new ReglaNegocioException("CU15: El valor está fuera del rango permitido (" + actual.get("minimo") + " a "
                            + (actual.get("maximo") == null ? "sin límite" : actual.get("maximo")) + ")");
                }
            }
            bd.ejecutar("UPDATE parametro SET valor = :v WHERE clave = :c", p("v", v.valor().trim(), "c", clave));
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("clave", clave);
            r.put("valor", v.valor().trim());
            return r;
        });
    }
}

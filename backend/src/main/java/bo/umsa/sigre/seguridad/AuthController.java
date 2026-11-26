package bo.umsa.sigre.seguridad;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import bo.umsa.sigre.comun.Consultas;
import bo.umsa.sigre.comun.ReglaNegocioException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Seguridad", description = "Inicio de sesión, doble factor y perfil (RNF01)")
public class AuthController {

    private final ServicioSesion sesiones;
    private final TotpService totp;
    private final Consultas bd;
    private final PasswordEncoder claves;
    private final ValidadorSesiones validador;
    private final boolean demo;
    private final String claveDemo;

    public AuthController(ServicioSesion sesiones, TotpService totp, Consultas bd, PasswordEncoder claves, ValidadorSesiones validador,
                          @Value("${sigre.demo.habilitado:false}") boolean demo,
                          @Value("${sigre.demo.clave-usuarios:}") String claveDemo) {
        this.sesiones = sesiones;
        this.totp = totp;
        this.bd = bd;
        this.claves = claves;
        this.validador = validador;
        this.demo = demo;
        this.claveDemo = claveDemo;
    }

    public record Login(@NotBlank String login, @NotBlank String clave) { }
    public record SegundoFactor(String desafio, String codigo) { }
    public record Codigo(String codigo) { }
    public record Demo(String login) { }
    public record CambioClave(String actual, String nueva) { }

    @PostMapping("/login")
    @Operation(summary = "Inicia sesión con usuario y clave")
    public Map<String, Object> login(@RequestBody @jakarta.validation.Valid Login l) {
        return sesiones.login(l.login(), l.clave());
    }

    @PostMapping("/2fa")
    @Operation(summary = "Completa el inicio de sesión con el código TOTP")
    public Map<String, Object> segundoFactor(@RequestBody SegundoFactor s) {
        return sesiones.segundoFactor(s.desafio(), s.codigo());
    }

    @GetMapping("/yo")
    @Operation(summary = "Datos del usuario autenticado")
    public Map<String, Object> yo() {
        UsuarioSesion u = UsuarioSesion.actual();
        Map<String, Object> r = new LinkedHashMap<>(u.comoMapa());
        Map<String, Object> f = sesiones.fila(u.id());
        r.put("email", f.get("email"));
        r.put("totpActivo", f.get("totp_activo"));
        return r;
    }

    /** Usuarios de prueba para el inicio de sesión rápido de la demostración. */
    @GetMapping("/demo-usuarios")
    public Map<String, Object> demoUsuarios() {
        if (!demo) return Map.of("habilitado", false);
        var lista = bd.lista("""
                SELECT u.login, u.nombre_completo AS nombre, u.cargo, f.nombre AS filial, f.codigo_pais,
                       string_agg(r.nombre, ', ' ORDER BY r.nombre) AS roles, min(r.codigo) AS rol
                FROM usuario u JOIN filial f USING (id_filial)
                JOIN usuario_rol ur USING (id_usuario) JOIN rol r USING (id_rol)
                WHERE u.login IN ('crojas','mquispe','lmamani','jcondori','pvargas','dflores','rgutierrez','lparedes','cmunoz','admin','omendoza','sgomez')
                GROUP BY u.id_usuario, f.id_filial
                ORDER BY array_position(ARRAY['crojas','mquispe','lmamani','jcondori','pvargas','dflores','rgutierrez','lparedes','cmunoz','omendoza','sgomez','admin'], u.login)""");
        // La clave no se envía al navegador: el acceso rápido se resuelve en el servidor (POST /api/auth/demo)
        return Map.of("habilitado", true, "usuarios", lista);
    }

    /** Acceso rápido de la demostración: entra con la clave de demostración configurada en el servidor. */
    @PostMapping("/demo")
    @Operation(summary = "Ingreso rápido de un usuario de demostración (solo con sigre.demo.habilitado=true)")
    public Map<String, Object> ingresoDemo(@RequestBody Demo d) {
        if (!demo || claveDemo == null || claveDemo.isBlank()) {
            throw new ReglaNegocioException("El acceso rápido de demostración está deshabilitado", HttpStatus.NOT_FOUND);
        }
        return sesiones.login(d.login(), claveDemo);
    }

    @PostMapping("/2fa/iniciar")
    @Operation(summary = "Genera el secreto TOTP y el código QR para activar el doble factor")
    public Map<String, Object> iniciarDobleFactor(@RequestBody(required = false) Codigo c) {
        UsuarioSesion u = UsuarioSesion.actual();
        Map<String, Object> f = sesiones.fila(u.id());
        // Si ya hay doble factor activo, cambiar de teléfono exige el código vigente
        if (Boolean.TRUE.equals(f.get("totp_activo")) && (c == null || !totp.verificar((String) f.get("totp_secreto"), c.codigo()))) {
            throw new ReglaNegocioException("RNF01: Para reconfigurar el doble factor ingrese el código actual", HttpStatus.BAD_REQUEST);
        }
        String secreto = totp.nuevoSecreto();
        // El secreto nuevo queda pendiente: el vigente sigue protegiendo la cuenta hasta confirmar
        bd.ejecutar("UPDATE usuario SET totp_pendiente = :s WHERE id_usuario = :id", Consultas.p("s", secreto, "id", u.id()));
        return Map.of("secreto", secreto, "url", totp.urlOtpauth(u.login(), secreto));
    }

    @PostMapping("/2fa/confirmar")
    @Operation(summary = "Activa el doble factor tras verificar un código")
    public Map<String, Object> confirmarDobleFactor(@RequestBody Codigo c) {
        UsuarioSesion u = UsuarioSesion.actual();
        String secreto = (String) sesiones.fila(u.id()).get("totp_pendiente");
        if (secreto == null) throw new ReglaNegocioException("RNF01: Primero genere el código QR del doble factor");
        if (!totp.verificar(secreto, c.codigo())) {
            throw new ReglaNegocioException("RNF01: El código no coincide; revise la hora del teléfono e intente de nuevo");
        }
        bd.ejecutar("UPDATE usuario SET totp_secreto = totp_pendiente, totp_pendiente = NULL, totp_activo = true WHERE id_usuario = :id",
                Consultas.p("id", u.id()));
        return Map.of("totpActivo", true);
    }

    @PostMapping("/2fa/desactivar")
    @Operation(summary = "Desactiva el doble factor")
    public Map<String, Object> desactivarDobleFactor(@RequestBody Codigo c) {
        UsuarioSesion u = UsuarioSesion.actual();
        String secreto = (String) sesiones.fila(u.id()).get("totp_secreto");
        if (!totp.verificar(secreto, c.codigo())) {
            throw new ReglaNegocioException("RNF01: Código incorrecto");
        }
        bd.ejecutar("UPDATE usuario SET totp_activo = false, totp_secreto = NULL WHERE id_usuario = :id", Consultas.p("id", u.id()));
        return Map.of("totpActivo", false);
    }

    @PostMapping("/clave")
    @Operation(summary = "Cambia la clave del usuario (hash Argon2id)")
    public Map<String, Object> cambiarClave(@RequestBody CambioClave c) {
        UsuarioSesion u = UsuarioSesion.actual();
        String hash = (String) sesiones.fila(u.id()).get("clave_hash");
        if (c.actual() == null || !claves.matches(c.actual(), hash)) {
            throw new ReglaNegocioException("La clave actual no es correcta", HttpStatus.BAD_REQUEST);
        }
        if (c.nueva() == null || c.nueva().length() < 8 || !c.nueva().matches(".*\\d.*") || !c.nueva().matches(".*[A-Za-z].*")) {
            throw new ReglaNegocioException("RNF01: La nueva clave debe tener al menos 8 caracteres, letras y números", HttpStatus.BAD_REQUEST);
        }
        bd.ejecutar("UPDATE usuario SET clave_hash = :h WHERE id_usuario = :id", Consultas.p("h", claves.encode(c.nueva()), "id", u.id()));
        // Las demás sesiones abiertas dejan de valer; esta recibe un token nuevo
        validador.revocar(u.id());
        return sesiones.emitirSesion(sesiones.fila(u.id()));
    }
}

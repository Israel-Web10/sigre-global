package bo.umsa.sigre.seguridad;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import bo.umsa.sigre.administracion.RegistroBitacora;
import bo.umsa.sigre.comun.Consultas;
import bo.umsa.sigre.comun.ReglaNegocioException;

/** Inicio de sesión con clave y, si está activo, doble factor TOTP (RNF01). */
@Service
public class ServicioSesion {

    private static final String SQL_USUARIO = """
            SELECT u.id_usuario, u.login, u.nombre_completo, u.cargo, u.idioma, u.activo, u.clave_hash,
                   u.totp_activo, u.totp_secreto, u.totp_pendiente, u.email, extract(epoch FROM u.sesion_desde)::bigint AS sesion_desde, u.id_filial, f.nombre AS filial, f.codigo AS filial_codigo,
                   coalesce(array_agg(r.codigo ORDER BY r.codigo) FILTER (WHERE r.codigo IS NOT NULL), '{}') AS roles,
                   coalesce(bool_or(r.corporativo), false) AS corporativo
            FROM usuario u
            JOIN filial f ON f.id_filial = u.id_filial
            LEFT JOIN usuario_rol ur ON ur.id_usuario = u.id_usuario
            LEFT JOIN rol r ON r.id_rol = ur.id_rol
            WHERE %s
            GROUP BY u.id_usuario, f.id_filial""";

    private final Consultas bd;
    private final PasswordEncoder claves;
    private final JwtService jwt;
    private final TotpService totp;
    private final RegistroBitacora bitacora;
    private final ControlIntentos intentos;

    public ServicioSesion(Consultas bd, PasswordEncoder claves, JwtService jwt, TotpService totp, RegistroBitacora bitacora,
                          ControlIntentos intentos) {
        this.bd = bd;
        this.claves = claves;
        this.jwt = jwt;
        this.totp = totp;
        this.bitacora = bitacora;
        this.intentos = intentos;
    }

    public Map<String, Object> fila(int id) {
        return bd.uno(SQL_USUARIO.formatted("u.id_usuario = :v"), Consultas.p("v", id));
    }

    public Map<String, Object> login(String login, String clave) {
        String claveIntentos = "login:" + (login == null ? "" : login.trim().toLowerCase());
        intentos.verificarBloqueo(claveIntentos);
        Map<String, Object> u = bd.uno(SQL_USUARIO.formatted("u.login = lower(:v)"), Consultas.p("v", login == null ? "" : login.trim()));
        if (u == null || u.get("clave_hash") == null || !claves.matches(clave == null ? "" : clave, (String) u.get("clave_hash"))) {
            intentos.registrarFallo(claveIntentos);
            bitacora.registrar(u == null ? null : (Integer) u.get("id_usuario"), "INICIAR_SESION", "USUARIO",
                    u == null ? null : (Integer) u.get("id_usuario"), "FALLIDO", Map.of("login", String.valueOf(login)));
            throw new ReglaNegocioException("Usuario o clave incorrectos", HttpStatus.UNAUTHORIZED);
        }
        if (!Boolean.TRUE.equals(u.get("activo"))) {
            throw new ReglaNegocioException("El usuario está desactivado", HttpStatus.FORBIDDEN);
        }
        intentos.registrarExito(claveIntentos);
        int id = (Integer) u.get("id_usuario");
        if (Boolean.TRUE.equals(u.get("totp_activo"))) {
            String desafio = jwt.emitir(Map.of("tipo", "2fa", "sub", id, "jti", java.util.UUID.randomUUID().toString()), 5);
            return Map.of("requiere2fa", true, "desafio", desafio);
        }
        return emitirSesion(u);
    }

    public Map<String, Object> segundoFactor(String desafio, String codigo) {
        Map<String, Object> d = desafio == null ? null : jwt.verificar(desafio);
        if (d == null || !"2fa".equals(d.get("tipo"))) {
            throw new ReglaNegocioException("El desafío de doble factor expiró; vuelva a iniciar sesión", HttpStatus.UNAUTHORIZED);
        }
        String jti = String.valueOf(d.get("jti"));
        intentos.verificarBloqueo("2fa:" + jti);
        Map<String, Object> u = fila(((Number) d.get("sub")).intValue());
        if (u == null || !Boolean.TRUE.equals(u.get("activo"))) {
            throw new ReglaNegocioException("El usuario está desactivado", HttpStatus.FORBIDDEN);
        }
        Long paso = totp.pasoVerificado((String) u.get("totp_secreto"), codigo);
        if (paso == null) {
            intentos.registrarFallo("2fa:" + jti);
            bitacora.registrar(u == null ? null : (Integer) u.get("id_usuario"), "VERIFICAR_DOBLE_FACTOR", "USUARIO",
                    u == null ? null : (Integer) u.get("id_usuario"), "FALLIDO", Map.of());
            throw new ReglaNegocioException("RNF01: Código de verificación incorrecto", HttpStatus.UNAUTHORIZED);
        }
        intentos.consumirPasoTotp((Integer) u.get("id_usuario"), paso);
        intentos.consumirDesafio(jti, 5 * 60);
        return emitirSesion(u);
    }

    public Map<String, Object> emitirSesion(Map<String, Object> u) {
        UsuarioSesion s = aSesion(u);
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("tipo", "acceso");
        datos.put("sub", s.id());
        datos.put("login", s.login());
        datos.put("nombre", s.nombre());
        datos.put("cargo", s.cargo());
        datos.put("idFilial", s.idFilial());
        datos.put("filial", s.filial());
        datos.put("filialCodigo", s.filialCodigo());
        datos.put("roles", List.copyOf(s.roles()));
        datos.put("corporativo", s.corporativo());
        datos.put("idioma", s.idioma());
        datos.put("ver", ((Number) u.get("sesion_desde")).longValue());
        String token = jwt.emitir(datos, jwt.minutosSesion());
        bd.ejecutar("UPDATE usuario SET ultimo_acceso = now() WHERE id_usuario = :id", Consultas.p("id", s.id()));
        bitacora.registrar(s.id(), "INICIAR_SESION", "USUARIO", s.id(), "OK", Map.of("login", s.login()));
        return Map.of("token", token, "usuario", s.comoMapa(), "minutos", jwt.minutosSesion());
    }

    @SuppressWarnings("unchecked")
    public static UsuarioSesion aSesion(Map<String, Object> u) {
        Set<String> roles = new LinkedHashSet<>((List<String>) u.get("roles"));
        return new UsuarioSesion((Integer) u.get("id_usuario"), (String) u.get("login"), (String) u.get("nombre_completo"),
                (String) u.get("cargo"), (Integer) u.get("id_filial"), (String) u.get("filial"), (String) u.get("filial_codigo"),
                roles, Boolean.TRUE.equals(u.get("corporativo")), String.valueOf(u.get("idioma")).trim());
    }
}

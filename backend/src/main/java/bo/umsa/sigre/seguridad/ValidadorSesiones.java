package bo.umsa.sigre.seguridad;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import bo.umsa.sigre.comun.Consultas;

/**
 * Revoca tokens: cada token lleva la "versión" de sesión del usuario (usuario.sesion_desde).
 * Si el usuario se desactiva, cambia de roles o de clave, la versión avanza y los tokens
 * anteriores dejan de valer. Se consulta la base como máximo cada 30 s por usuario.
 */
@Component
public class ValidadorSesiones {

    private static final long VIGENCIA_MS = 30_000;

    private record Estado(long version, boolean activo, long leido) { }

    private final Consultas bd;
    private final Map<Integer, Estado> cache = new ConcurrentHashMap<>();

    public ValidadorSesiones(Consultas bd) {
        this.bd = bd;
    }

    public boolean vigente(int idUsuario, Object versionToken) {
        if (!(versionToken instanceof Number v)) return false;
        Estado e = cache.get(idUsuario);
        if (e == null || System.currentTimeMillis() - e.leido() > VIGENCIA_MS) {
            Map<String, Object> f = bd.uno("""
                    SELECT extract(epoch FROM sesion_desde)::bigint AS version, activo FROM usuario WHERE id_usuario = :id""",
                    Consultas.p("id", idUsuario));
            if (f == null) return false;
            e = new Estado(((Number) f.get("version")).longValue(), Boolean.TRUE.equals(f.get("activo")), System.currentTimeMillis());
            cache.put(idUsuario, e);
        }
        return e.activo() && v.longValue() >= e.version();
    }

    /** Invalida los tokens anteriores del usuario (y su caché). */
    public void revocar(int idUsuario) {
        bd.ejecutar("UPDATE usuario SET sesion_desde = now() WHERE id_usuario = :id", Consultas.p("id", idUsuario));
        cache.remove(idUsuario);
    }

    public void olvidar(int idUsuario) {
        cache.remove(idUsuario);
    }
}

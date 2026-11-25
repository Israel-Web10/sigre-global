package bo.umsa.sigre.seguridad;

/**
 * Usuario de la petición en curso. La conexión a la base de datos lo publica con
 * set_config('app.id_usuario', ...) para que los triggers de bitácora sepan quién operó.
 */
public final class ContextoUsuario {

    private static final ThreadLocal<Integer> USUARIO = new ThreadLocal<>();

    private ContextoUsuario() {
    }

    public static void establecer(Integer idUsuario) {
        USUARIO.set(idUsuario);
    }

    public static Integer actual() {
        return USUARIO.get();
    }

    public static void limpiar() {
        USUARIO.remove();
    }

    /** Ejecuta una tarea a nombre de un usuario (o del sistema si es null). */
    public static void como(Integer idUsuario, Runnable tarea) {
        Integer anterior = USUARIO.get();
        USUARIO.set(idUsuario);
        try {
            tarea.run();
        } finally {
            if (anterior == null) USUARIO.remove();
            else USUARIO.set(anterior);
        }
    }
}

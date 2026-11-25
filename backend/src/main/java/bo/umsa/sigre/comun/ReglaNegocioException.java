package bo.umsa.sigre.comun;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;

/**
 * Violación de una regla de negocio. El mensaje empieza con el código de la regla
 * (por ejemplo "RN08: ...") igual que los mensajes de los triggers de la base de datos.
 */
public class ReglaNegocioException extends RuntimeException {

    private static final Pattern CODIGO = Pattern.compile("^(RN\\d{2}|CU\\d{2}|D0\\d|RF\\d{2}|RNF\\d{2})\\s*:\\s*(.*)$", Pattern.DOTALL);

    private final HttpStatus estado;

    public ReglaNegocioException(String mensaje) {
        this(mensaje, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public ReglaNegocioException(String mensaje, HttpStatus estado) {
        super(mensaje);
        this.estado = estado;
    }

    public HttpStatus getEstado() {
        return estado;
    }

    /** Código de la regla citada al inicio del mensaje, o null. */
    public static String regla(String mensaje) {
        if (mensaje == null) return null;
        Matcher m = CODIGO.matcher(mensaje.trim());
        return m.matches() ? m.group(1) : null;
    }

    /** Mensaje sin el prefijo del código de la regla. */
    public static String texto(String mensaje) {
        if (mensaje == null) return null;
        Matcher m = CODIGO.matcher(mensaje.trim());
        return m.matches() ? m.group(2) : mensaje;
    }

    public static ReglaNegocioException noEncontrado(String que) {
        return new ReglaNegocioException(que + " no existe", HttpStatus.NOT_FOUND);
    }

    public static ReglaNegocioException prohibido(String mensaje) {
        return new ReglaNegocioException(mensaje, HttpStatus.FORBIDDEN);
    }
}

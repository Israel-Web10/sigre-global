package bo.umsa.sigre.comun;

import java.io.IOException;
import java.sql.SQLException;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Traduce los errores a respuestas JSON uniformes. Los errores que lanzan los triggers
 * de PostgreSQL (RAISE EXCEPTION 'RN08: ...') llegan al usuario con el código de la regla.
 */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<Map<String, Object>> regla(ReglaNegocioException e) {
        return respuesta(e.getEstado(), e.getMessage(), "Regla de negocio");
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<Map<String, Object>> acceso(Exception e) {
        return respuesta(HttpStatus.FORBIDDEN, "Su rol no tiene permiso para esta operación (matriz de permisos, Tabla 47)", "Acceso denegado");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validacion(MethodArgumentNotValidException e) {
        String detalle = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return respuesta(HttpStatus.BAD_REQUEST, "Datos incompletos o inválidos: " + detalle, "Validación");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> parametro(MissingServletRequestParameterException e) {
        return respuesta(HttpStatus.BAD_REQUEST, "Falta el parámetro " + e.getParameterName(), "Validación");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> tamano(MaxUploadSizeExceededException e) {
        return respuesta(HttpStatus.PAYLOAD_TOO_LARGE, "El archivo supera el tamaño máximo de 15 MB", "Validación");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> noEncontrado(NoResourceFoundException e) {
        return respuesta(HttpStatus.NOT_FOUND, "Recurso no encontrado", "No encontrado");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> cuerpoIlegible(HttpMessageNotReadableException e) {
        return respuesta(HttpStatus.BAD_REQUEST, "El cuerpo de la petición no es un JSON válido o tiene valores de tipo incorrecto", "Validación");
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, DateTimeParseException.class})
    public ResponseEntity<Map<String, Object>> tipoInvalido(Exception e) {
        String campo = e instanceof MethodArgumentTypeMismatchException m ? " «" + m.getName() + "»" : "";
        return respuesta(HttpStatus.BAD_REQUEST, "Valor inválido en el parámetro" + campo, "Validación");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> tipoContenido(HttpMediaTypeNotSupportedException e) {
        return respuesta(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de contenido no admitido: use application/json", "Validación");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> metodo(HttpRequestMethodNotSupportedException e) {
        return respuesta(HttpStatus.METHOD_NOT_ALLOWED, "Método HTTP no permitido en esta ruta", "Validación");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> general(Exception e, HttpServletRequest req) {
        // El navegador cerró el flujo de eventos (SSE): no es un error del sistema ni hay a quién responder
        boolean flujo = "/api/eventos".equals(req.getRequestURI())
                || String.valueOf(req.getHeader("Accept")).contains("text/event-stream");
        if (e instanceof AsyncRequestNotUsableException || (flujo && (e instanceof IOException || e.getCause() instanceof IOException))) {
            return null;
        }
        PSQLException pg = buscarPsql(e);
        if (pg != null) return desdeBaseDeDatos(pg);
        if (e instanceof IllegalArgumentException) {
            return respuesta(HttpStatus.BAD_REQUEST, e.getMessage(), "Validación");
        }
        String referencia = Long.toString(System.nanoTime() % 1_000_000_000L, 36).toUpperCase();
        log.error("Error no controlado [{}] en {} {}", referencia, req.getMethod(), req.getRequestURI(), e);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor (referencia " + referencia + ")", "Error");
    }

    /** Mensaje de la base de datos según el código SQLSTATE. */
    public static ResponseEntity<Map<String, Object>> desdeBaseDeDatos(PSQLException pg) {
        String estadoSql = pg.getSQLState() == null ? "" : pg.getSQLState();
        String mensaje = pg.getServerErrorMessage() != null ? pg.getServerErrorMessage().getMessage() : pg.getMessage();
        String restriccion = pg.getServerErrorMessage() != null ? pg.getServerErrorMessage().getConstraint() : null;
        return switch (estadoSql) {
            case "P0001" -> respuesta(HttpStatus.UNPROCESSABLE_ENTITY, mensaje, "Regla de negocio (trigger)");
            case "23505" -> respuesta(HttpStatus.CONFLICT, "Registro duplicado" + (restriccion != null ? " (" + restriccion + ")" : ""), "Duplicado");
            case "23503" -> respuesta(HttpStatus.CONFLICT, "El registro está relacionado con otros datos (" + restriccion + ")", "Integridad");
            case "23514" -> respuesta(HttpStatus.UNPROCESSABLE_ENTITY, "Valor fuera del dominio permitido (" + restriccion + ")", "Dominio");
            case "23502" -> respuesta(HttpStatus.BAD_REQUEST, "Falta un dato obligatorio: " + mensaje, "Validación");
            case "42501" -> respuesta(HttpStatus.FORBIDDEN, "Permiso denegado en la base de datos: " + mensaje, "Permiso");
            case "22P02", "22003" -> respuesta(HttpStatus.BAD_REQUEST, "Formato de dato inválido: " + mensaje, "Validación");
            default -> respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "Error de base de datos: " + mensaje, "Base de datos");
        };
    }

    public static PSQLException buscarPsql(Throwable e) {
        Throwable t = e;
        int guardia = 0;
        while (t != null && guardia++ < 15) {
            if (t instanceof PSQLException p) return p;
            if (t instanceof SQLException s && s.getNextException() instanceof PSQLException p) return p;
            t = t.getCause();
        }
        return null;
    }

    public static ResponseEntity<Map<String, Object>> respuesta(HttpStatus estado, String mensaje, String tipo) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("estado", estado.value());
        cuerpo.put("tipo", tipo);
        cuerpo.put("regla", ReglaNegocioException.regla(mensaje));
        cuerpo.put("mensaje", ReglaNegocioException.texto(mensaje));
        cuerpo.put("mensajeCompleto", mensaje);
        return ResponseEntity.status(estado).body(cuerpo);
    }
}

package bo.umsa.sigre.seguridad;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import bo.umsa.sigre.comun.ReglaNegocioException;

/**
 * Protección contra adivinación de claves y de códigos TOTP (RNF01): bloquea temporalmente
 * tras varios fallos, hace que cada desafío de doble factor sea de un solo uso y rechaza
 * que un mismo código TOTP se use dos veces.
 */
@Component
public class ControlIntentos {

    static final int MAXIMO_FALLOS = 5;
    static final long BLOQUEO_SEGUNDOS = 15 * 60;

    private record Contador(int fallos, Instant hasta) { }

    private final Map<String, Contador> fallos = new ConcurrentHashMap<>();
    private final Map<String, Instant> desafiosUsados = new ConcurrentHashMap<>();
    private final Map<Integer, Long> ultimoPasoTotp = new ConcurrentHashMap<>();

    /** Lanza 429 si la clave (usuario o desafío) está bloqueada. */
    public void verificarBloqueo(String clave) {
        Contador c = fallos.get(clave);
        if (c != null && c.hasta() != null && c.hasta().isAfter(Instant.now())) {
            long minutos = Math.max(1, (c.hasta().getEpochSecond() - Instant.now().getEpochSecond() + 59) / 60);
            throw new ReglaNegocioException("RNF01: Demasiados intentos fallidos. Espere " + minutos + " min e intente de nuevo",
                    HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    public void registrarFallo(String clave) {
        fallos.compute(clave, (k, c) -> {
            int n = (c == null || (c.hasta() != null && c.hasta().isBefore(Instant.now()))) ? 1 : c.fallos() + 1;
            return new Contador(n, n >= MAXIMO_FALLOS ? Instant.now().plusSeconds(BLOQUEO_SEGUNDOS) : null);
        });
    }

    public void registrarExito(String clave) {
        fallos.remove(clave);
    }

    /** Un desafío de doble factor solo se puede canjear una vez. */
    public void consumirDesafio(String jti, long expiraEnSegundos) {
        desafiosUsados.entrySet().removeIf(e -> e.getValue().isBefore(Instant.now()));
        if (desafiosUsados.putIfAbsent(jti, Instant.now().plusSeconds(expiraEnSegundos)) != null) {
            throw new ReglaNegocioException("El desafío de doble factor ya fue usado; vuelva a iniciar sesión", HttpStatus.UNAUTHORIZED);
        }
    }

    /** Rechaza reutilizar el mismo paso TOTP (ventana de 30 s) para un usuario. */
    public void consumirPasoTotp(int idUsuario, long paso) {
        Long previo = ultimoPasoTotp.get(idUsuario);
        if (previo != null && paso <= previo) {
            throw new ReglaNegocioException("RNF01: Ese código ya fue usado; espere el siguiente", HttpStatus.UNAUTHORIZED);
        }
        ultimoPasoTotp.put(idUsuario, paso);
    }
}

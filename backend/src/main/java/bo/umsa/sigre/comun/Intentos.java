package bo.umsa.sigre.comun;

import java.util.Map;
import java.util.function.Supplier;

import org.postgresql.util.PSQLException;
import org.springframework.stereotype.Component;

import bo.umsa.sigre.administracion.RegistroBitacora;
import bo.umsa.sigre.seguridad.UsuarioSesion;

/**
 * Ejecuta una operación sensible y, si una regla de negocio la rechaza, deja constancia del
 * intento en la bitácora (resultado RECHAZADO) en una transacción aparte (RN13).
 */
@Component
public class Intentos {

    private final RegistroBitacora bitacora;

    public Intentos(RegistroBitacora bitacora) {
        this.bitacora = bitacora;
    }

    public <T> T registrar(UsuarioSesion u, String operacion, String entidad, Number id, Supplier<T> accion) {
        try {
            return accion.get();
        } catch (ReglaNegocioException e) {
            bitacora.registrar(u.id(), operacion, entidad, id, "RECHAZADO", Map.of("motivo", e.getMessage()));
            throw e;
        } catch (RuntimeException e) {
            PSQLException pg = ManejadorErrores.buscarPsql(e);
            if (pg != null && pg.getServerErrorMessage() != null) {
                bitacora.registrar(u.id(), operacion, entidad, id, "RECHAZADO",
                        Map.of("motivo", String.valueOf(pg.getServerErrorMessage().getMessage())));
            }
            throw e;
        }
    }
}

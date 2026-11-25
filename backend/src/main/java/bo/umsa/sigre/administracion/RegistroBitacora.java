package bo.umsa.sigre.administracion;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import bo.umsa.sigre.comun.Consultas;

/**
 * Registros de bitácora que hace la aplicación (inicios de sesión y operaciones críticas
 * rechazadas). Los cambios de datos los registra la base de datos con el trigger fn_auditar.
 * Se guarda en una transacción propia para que quede aunque la operación se revierta.
 */
@Service
public class RegistroBitacora {

    private final Consultas bd;
    private final ObjectMapper mapper;

    public RegistroBitacora(Consultas bd, ObjectMapper mapper) {
        this.bd = bd;
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(Integer idUsuario, String operacion, String entidad, Number idEntidad, String resultado,
                          Map<String, ?> detalle) {
        String json;
        try {
            json = mapper.writeValueAsString(detalle == null ? Map.of() : detalle);
        } catch (Exception e) {
            json = "{}";
        }
        bd.ejecutar("""
                INSERT INTO bitacora (id_usuario, operacion, entidad, id_entidad, resultado, detalle, origen)
                VALUES (:u, :op, :ent, :id, :res, CAST(:det AS jsonb), 'APLICACION')""",
                Consultas.p("u", idUsuario, "op", operacion, "ent", entidad,
                        "id", idEntidad == null ? null : idEntidad.longValue(), "res", resultado, "det", json));
    }
}

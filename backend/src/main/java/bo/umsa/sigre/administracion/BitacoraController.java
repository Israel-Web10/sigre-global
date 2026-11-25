package bo.umsa.sigre.administracion;

import static bo.umsa.sigre.comun.Consultas.p;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import bo.umsa.sigre.comun.Condiciones;
import bo.umsa.sigre.comun.Consultas;
import bo.umsa.sigre.comun.ReglaNegocioException;
import bo.umsa.sigre.seguridad.UsuarioSesion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/bitacora")
@Tag(name = "Bitácora", description = "CU16: consulta de la bitácora inalterable (RN13)")
public class BitacoraController {

    private static final String[] ROLES = {UsuarioSesion.AUDITOR_INTERNO, UsuarioSesion.AUDITOR_FINANCIERO,
            UsuarioSesion.DIRECTOR, UsuarioSesion.ADMIN};

    private final Consultas bd;
    private final RegistroBitacora registro;
    private final String url;
    private final String dueno;
    private final String claveDueno;
    private final String usuarioApp;
    private final String claveApp;

    public BitacoraController(Consultas bd, RegistroBitacora registro, @Value("${spring.flyway.url}") String url,
                              @Value("${spring.flyway.user}") String dueno, @Value("${spring.flyway.password}") String claveDueno,
                              @Value("${spring.datasource.username}") String usuarioApp,
                              @Value("${spring.datasource.password}") String claveApp) {
        this.bd = bd;
        this.registro = registro;
        this.url = url;
        this.dueno = dueno;
        this.claveDueno = claveDueno;
        this.usuarioApp = usuarioApp;
        this.claveApp = claveApp;
    }

    @GetMapping
    @Operation(summary = "CU16: consultar la bitácora con filtros")
    public Map<String, Object> consultar(@RequestParam(required = false) String entidad,
                                         @RequestParam(required = false) String operacion,
                                         @RequestParam(required = false) Integer usuario,
                                         @RequestParam(required = false) String resultado,
                                         @RequestParam(required = false) LocalDate desde,
                                         @RequestParam(required = false) LocalDate hasta,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(defaultValue = "1") int pagina,
                                         @RequestParam(defaultValue = "50") int tamano) {
        UsuarioSesion.actual().exigir("consultar la bitácora", ROLES);
        int t = Math.max(10, Math.min(tamano, 200));
        Condiciones c = new Condiciones()
                .si(entidad, "b.entidad = :entidad", "entidad")
                .si(operacion, "b.operacion = :operacion", "operacion")
                .si(usuario, "b.id_usuario = :usuario", "usuario")
                .si(resultado, "b.resultado = :resultado", "resultado")
                .si(desde, "b.fecha_hora >= :desde", "desde")
                .si(hasta == null ? null : hasta.plusDays(1), "b.fecha_hora < :hasta", "hasta")
                .si(q == null ? null : "%" + q.trim().toLowerCase() + "%",
                        "(lower(b.detalle::text) LIKE :q OR lower(b.operacion) LIKE :q OR lower(coalesce(u.nombre_completo, 'sistema')) LIKE :q)", "q")
                .param("lim", t).param("off", (Math.max(1, pagina) - 1) * t);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", bd.valor("SELECT count(*) FROM bitacora b LEFT JOIN usuario u USING (id_usuario) WHERE TRUE" + c.sql(),
                c.params(), Long.class));
        m.put("pagina", pagina);
        m.put("tamano", t);
        m.put("registros", bd.lista("""
                SELECT b.*, coalesce(u.nombre_completo, 'Sistema') AS usuario, u.login
                FROM bitacora b LEFT JOIN usuario u USING (id_usuario) WHERE TRUE""" + c.sql() + " " + """
                 ORDER BY b.fecha_hora DESC, b.id_bitacora DESC LIMIT :lim OFFSET :off""", c.params()));
        return m;
    }

    @GetMapping("/resumen")
    public Map<String, Object> resumen() {
        UsuarioSesion.actual().exigir("consultar la bitácora", ROLES);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totales", bd.uno("""
                SELECT count(*) AS registros, count(*) FILTER (WHERE origen = 'TRIGGER') AS por_trigger,
                       count(*) FILTER (WHERE origen = 'APLICACION') AS por_aplicacion,
                       count(*) FILTER (WHERE resultado <> 'OK') AS rechazados,
                       count(*) FILTER (WHERE fecha_hora >= CURRENT_DATE) AS hoy, min(fecha_hora) AS desde
                FROM bitacora""", Map.of()));
        m.put("por_dia", bd.lista("""
                SELECT d::date AS dia, count(b.id_bitacora) AS total
                FROM generate_series(CURRENT_DATE - 13, CURRENT_DATE, interval '1 day') d
                LEFT JOIN bitacora b ON b.fecha_hora::date = d::date GROUP BY d ORDER BY d"""));
        m.put("por_operacion", bd.lista("SELECT operacion, count(*) AS total FROM bitacora GROUP BY operacion ORDER BY total DESC LIMIT 15"));
        m.put("por_entidad", bd.lista("SELECT entidad, count(*) AS total FROM bitacora GROUP BY entidad ORDER BY total DESC"));
        m.put("operaciones", bd.jdbc().queryForList("SELECT DISTINCT operacion FROM bitacora ORDER BY 1", Map.of(), String.class));
        m.put("entidades", bd.jdbc().queryForList("SELECT DISTINCT entidad FROM bitacora ORDER BY 1", Map.of(), String.class));
        return m;
    }

    @GetMapping("/{id}")
    public Map<String, Object> detalle(@PathVariable long id) {
        UsuarioSesion.actual().exigir("consultar la bitácora", ROLES);
        Map<String, Object> b = bd.uno("""
                SELECT b.*, coalesce(u.nombre_completo, 'Sistema') AS usuario, u.login, u.cargo
                FROM bitacora b LEFT JOIN usuario u USING (id_usuario) WHERE b.id_bitacora = :id""", p("id", id));
        if (b == null) throw ReglaNegocioException.noEncontrado("El registro de bitácora");
        return b;
    }

    /**
     * Demuestra RN13: intenta modificar y borrar el último registro con dos identidades.
     * La aplicación (rol app_rw) no tiene el permiso UPDATE/DELETE; el dueño del esquema sí,
     * pero el trigger trg_bitacora lo impide. Todo se revierte y el intento queda registrado.
     */
    @PostMapping("/prueba-inalterable")
    @Operation(summary = "RN13: demostrar que la bitácora no admite cambios ni borrados")
    public Map<String, Object> pruebaInalterable() {
        UsuarioSesion u = UsuarioSesion.actual();
        u.exigir("probar la bitácora", ROLES);
        Long ultimo = bd.valor("SELECT max(id_bitacora) FROM bitacora", Map.of(), Long.class);
        List<Map<String, Object>> capas = new ArrayList<>();
        capas.add(intentar("Permisos de la base de datos", "Rol de la aplicación (" + usuarioApp + ")", usuarioApp, claveApp,
                "UPDATE bitacora SET operacion = 'ALTERADO' WHERE id_bitacora = " + ultimo));
        capas.add(intentar("Trigger trg_bitacora", "Dueño del esquema (" + dueno + ")", dueno, claveDueno,
                "UPDATE bitacora SET operacion = 'ALTERADO' WHERE id_bitacora = " + ultimo));
        capas.add(intentar("Trigger trg_bitacora", "Dueño del esquema (" + dueno + ")", dueno, claveDueno,
                "DELETE FROM bitacora WHERE id_bitacora = " + ultimo));
        capas.add(intentar("Trigger trg_bitacora_truncate", "Dueño del esquema (" + dueno + ")", dueno, claveDueno,
                "TRUNCATE bitacora"));
        boolean protegida = capas.stream().allMatch(c -> Boolean.TRUE.equals(c.get("bloqueado")));
        registro.registrar(u.id(), "PRUEBA_INALTERABILIDAD", "BITACORA", ultimo, protegida ? "RECHAZADO" : "ALERTA",
                Map.of("intentos", capas.size(), "protegida", protegida));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("registro_probado", ultimo);
        m.put("protegida", protegida);
        m.put("capas", capas);
        return m;
    }

    private Map<String, Object> intentar(String capa, String identidad, String usuario, String clave, String sql) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("capa", capa);
        r.put("identidad", identidad);
        r.put("sentencia", sql);
        try (Connection c = DriverManager.getConnection(url, usuario, clave)) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute(sql);
                r.put("bloqueado", false);
                r.put("mensaje", "La sentencia se ejecutó (se revierte)");
            } catch (SQLException e) {
                r.put("bloqueado", true);
                r.put("sqlstate", e.getSQLState());
                r.put("mensaje", e.getMessage().replaceAll("(?s)\\s*(Where|Dónde|Donde):.*$", "").replace("ERROR: ", ""));
            } finally {
                c.rollback();
            }
        } catch (SQLException e) {
            r.put("bloqueado", true);
            r.put("mensaje", "Sin conexión: " + e.getMessage());
        }
        return r;
    }
}

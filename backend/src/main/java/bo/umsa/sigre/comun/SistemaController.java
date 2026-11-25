package bo.umsa.sigre.comun;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import bo.umsa.sigre.seguridad.UsuarioSesion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Sistema", description = "Información general y catálogos")
public class SistemaController {

    private final Consultas bd;

    public SistemaController(Consultas bd) {
        this.bd = bd;
    }

    @GetMapping("/api/sistema/info")
    @Operation(summary = "Versión, estado de la base de datos y cifras generales (público)")
    public Map<String, Object> info() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sistema", "SIGRE Global");
        m.put("descripcion", "Sistema de gestión de riesgos empresariales globales");
        m.put("caso", "Grupo Andino Global S.A.");
        m.put("version", "1.0.0");
        m.put("hora_servidor", OffsetDateTime.now().toString());
        m.put("activo_desde", Duration.ofMillis(ManagementFactory.getRuntimeMXBean().getUptime()).toSeconds());
        m.put("java", System.getProperty("java.version"));
        try {
            m.put("base_de_datos", bd.valor("SELECT split_part(version(), ',', 1)", Map.of(), String.class));
            m.put("cifras", bd.uno("""
                    SELECT (SELECT count(*) FROM filial) AS filiales,
                           (SELECT count(DISTINCT pais) FROM filial) AS paises,
                           (SELECT count(*) FROM riesgo WHERE estado <> 'CERRADO') AS riesgos,
                           (SELECT count(*) FROM indicador_kri WHERE activo) AS indicadores,
                           (SELECT count(*) FROM normativa) AS normativas,
                           (SELECT count(*) FROM usuario WHERE activo) AS usuarios,
                           (SELECT count(*) FROM bitacora) AS registros_bitacora""", Map.of()));
            m.put("filiales", bd.lista("""
                    SELECT id_filial, codigo, nombre, ciudad, pais, codigo_pais, latitud, longitud, casa_matriz
                    FROM filial WHERE activa ORDER BY casa_matriz DESC, nombre"""));
            m.put("estado", "OPERATIVO");
        } catch (RuntimeException e) {
            m.put("estado", "SIN_BASE_DE_DATOS");
        }
        return m;
    }

    @GetMapping("/api/catalogos")
    @Operation(summary = "Filiales, usuarios asignables, categorías y monedas")
    public Map<String, Object> catalogos() {
        UsuarioSesion u = UsuarioSesion.actual();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("filiales", bd.lista("""
                SELECT id_filial, codigo, nombre, pais, codigo_pais, ciudad, region, moneda, apetito_riesgo, latitud, longitud,
                       zona_horaria, casa_matriz
                FROM filial WHERE activa AND (:corp OR id_filial = :f) ORDER BY casa_matriz DESC, nombre""",
                Consultas.p("corp", u.corporativo(), "f", u.idFilial())));
        m.put("usuarios", bd.lista("""
                SELECT u.id_usuario, u.nombre_completo, u.cargo, u.id_filial, f.codigo AS filial_codigo, f.codigo_pais,
                       array_agg(r.codigo ORDER BY r.codigo) AS roles, bool_or(r.corporativo) AS corporativo
                FROM usuario u JOIN filial f USING (id_filial) JOIN usuario_rol ur USING (id_usuario) JOIN rol r USING (id_rol)
                WHERE u.activo GROUP BY u.id_usuario, f.codigo, f.codigo_pais ORDER BY u.nombre_completo"""));
        m.put("categorias", List.of("FINANCIERO", "TECNOLOGICO", "LEGAL", "OPERATIVO", "REPUTACIONAL"));
        m.put("monedas", bd.jdbc().queryForList("SELECT DISTINCT moneda FROM filial UNION SELECT 'USD' ORDER BY 1", Map.of(), String.class));
        m.put("parametros", bd.lista("SELECT clave, valor, descripcion, regla FROM parametro ORDER BY clave"));
        return m;
    }
}

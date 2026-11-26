package bo.umsa.sigre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import bo.umsa.sigre.monitoreo.ServicioAlertas;

/**
 * Casos de prueba CP01 a CP26 del Anexo B del informe, ejecutados de punta a punta: petición HTTP
 * con JWT → controlador → servicio y dominio → triggers de PostgreSQL. Usa la base sigre_test,
 * que se vacía y se vuelve a crear con las migraciones de Flyway (V1 a V5) al comenzar.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://${sigre.bd.host}:${sigre.bd.puerto}/${sigre.bd.nombre-pruebas}",
        "spring.flyway.url=jdbc:postgresql://${sigre.bd.host}:${sigre.bd.puerto}/${sigre.bd.nombre-pruebas}",
        "spring.flyway.clean-disabled=false",
        "sigre.simulador.activo=false",
        "sigre.demo.habilitado=true",
        "sigre.tareas.notificacion-ms=1000",
        "sigre.tareas.escalamiento-ms=3600000",
        "sigre.tareas.alertas-programadas-ms=3600000",
        "sigre.evidencias.carpeta=${java.io.tmpdir}/sigre-pruebas/evidencias",
        "logging.level.bo.umsa.sigre=WARN" })
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CasosDePruebaTest {

    @TestConfiguration
    static class BaseLimpia {
        /** Base de pruebas limpia: borra el esquema y aplica las migraciones V1 a V5. */
        @Bean
        FlywayMigrationStrategy limpiarYMigrar() {
            return flyway -> {
                flyway.clean();
                flyway.migrate();
            };
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired ServicioAlertas alertas;
    @Value("${sigre.demo.clave-usuarios}") String claveDemo;

    private static final Map<String, String> TOKENS = new HashMap<>();
    private static final Map<String, Object> COMPARTIDO = new HashMap<>();

    // ------------------------------------------------------------------ ayudas

    private String token(String login) throws Exception {
        if (!TOKENS.containsKey(login)) {
            MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("login", login, "clave", claveDemo)))).andReturn();
            assertThat(r.getResponse().getStatus()).as("ingreso de %s", login).isEqualTo(200);
            TOKENS.put(login, json.readTree(r.getResponse().getContentAsString()).get("token").asText());
        }
        return TOKENS.get(login);
    }

    private record Respuesta(int estado, JsonNode cuerpo) {
        String regla() {
            return cuerpo.path("regla").asText(null);
        }
    }

    private Respuesta llamar(String login, MockHttpServletRequestBuilder p, Object cuerpo) throws Exception {
        p.header("Authorization", "Bearer " + token(login));
        if (cuerpo != null) p.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo));
        MvcResult r = mvc.perform(p).andReturn();
        String texto = r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return new Respuesta(r.getResponse().getStatus(), texto.isBlank() ? json.createObjectNode() : json.readTree(texto));
    }

    private Respuesta get_(String login, String url) throws Exception {
        return llamar(login, get(url), null);
    }

    private Respuesta post_(String login, String url, Object cuerpo) throws Exception {
        return llamar(login, post(url), cuerpo == null ? Map.of() : cuerpo);
    }

    private int idRiesgo(String codigo) {
        return jdbc.queryForObject("SELECT id_riesgo FROM riesgo WHERE codigo = ?", Integer.class, codigo);
    }

    private int idUsuario(String login) {
        return jdbc.queryForObject("SELECT id_usuario FROM usuario WHERE login = ?", Integer.class, login);
    }

    private boolean enBitacora(String operacion, String entidad, Number id, String resultado) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM bitacora WHERE operacion = ? AND entidad = ? AND id_entidad = ? AND resultado = ?)",
                Boolean.class, operacion, entidad, id.longValue(), resultado));
    }

    // ------------------------------------------------------------------ CU01 a CU04

    @Test
    @Order(1)
    @DisplayName("CP01 · CU01: registrar un riesgo con un título que ya existe en la filial se rechaza")
    void cp01() throws Exception {
        Respuesta r = post_("mquispe", "/api/riesgos", Map.of("idFilial", 1, "idResponsable", idUsuario("jcondori"),
                "titulo", "exposición cambiaria por deuda en USD", "descripcion", "Riesgo duplicado para la prueba CP01",
                "categoria", "FINANCIERO", "proceso", "Tesorería", "causa", "Prueba", "consecuencia", "Prueba"));
        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.regla()).isEqualTo("CU01");
    }

    @Test
    @Order(2)
    @DisplayName("CP02 · CU02: evaluar con probabilidad 4 e impacto 5 da nivel 20 crítico y el riesgo pasa a EVALUADO")
    void cp02() throws Exception {
        Respuesta nuevo = post_("mquispe", "/api/riesgos", Map.of("idFilial", 1, "idResponsable", idUsuario("jcondori"),
                "titulo", "Caída del centro de datos principal (prueba CP02)", "descripcion", "Interrupción total del centro de datos de La Paz",
                "categoria", "TECNOLOGICO", "proceso", "Infraestructura", "causa", "Falla eléctrica prolongada", "consecuencia", "Sistemas detenidos"));
        assertThat(nuevo.estado()).isEqualTo(200);
        assertThat(nuevo.cuerpo().get("codigo").asText()).matches("RSK-TEC-\\d{4}");
        int id = nuevo.cuerpo().get("id_riesgo").asInt();
        COMPARTIDO.put("riesgoNuevo", id);
        Respuesta ev = post_("mquispe", "/api/riesgos/" + id + "/evaluaciones", Map.of("tipo", "INHERENTE", "probabilidad", 4, "impacto", 5));
        assertThat(ev.estado()).isEqualTo(200);
        assertThat(ev.cuerpo().get("valor").asInt()).isEqualTo(20);
        assertThat(ev.cuerpo().get("nivel").asText()).isEqualTo("CRITICO");
        assertThat(ev.cuerpo().get("estado_riesgo").asText()).isEqualTo("EVALUADO");
        assertThat(enBitacora("EVALUAR_RIESGO", "EVALUACION_RIESGO", ev.cuerpo().get("id_evaluacion").asInt(), "OK")).isTrue();
    }

    @Test
    @Order(3)
    @DisplayName("CP03 · CU02: una probabilidad de 7 se rechaza por estar fuera de 1 a 5")
    void cp03() throws Exception {
        Respuesta r = post_("mquispe", "/api/riesgos/" + COMPARTIDO.get("riesgoNuevo") + "/evaluaciones",
                Map.of("tipo", "INHERENTE", "probabilidad", 7, "impacto", 3));
        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.regla()).isEqualTo("RN02");
    }

    @Test
    @Order(4)
    @DisplayName("CP04 · CU03: proponer un plan sin acciones exige al menos una acción (RN05)")
    void cp04() throws Exception {
        Object id = COMPARTIDO.get("riesgoNuevo");
        assertThat(llamar("mquispe", put("/api/riesgos/" + id + "/plan"), Map.of("estrategia", "REDUCIR", "justificacion", "Plan de prueba")).estado()).isEqualTo(200);
        Respuesta r = post_("mquispe", "/api/riesgos/" + id + "/plan/proponer", null);
        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.regla()).isEqualTo("RN05");
    }

    @Test
    @Order(5)
    @DisplayName("CP05 · CU04: un Gerente de Filial no puede aprobar un riesgo crítico; se deriva al Director (RN04)")
    void cp05() throws Exception {
        int id = idRiesgo("RSK-TEC-0021");
        Respuesta r = post_("rgutierrez", "/api/riesgos/" + id + "/plan/aprobar", null);
        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.regla()).isEqualTo("RN04");
        assertThat(enBitacora("APROBAR_TRATAMIENTO", "RIESGO", id, "RECHAZADO")).isTrue();
    }

    @Test
    @Order(6)
    @DisplayName("CP06 · CU04: aprobar con una evaluación de hace 14 meses exige reevaluar (RN03)")
    void cp06() throws Exception {
        Respuesta r = post_("crojas", "/api/riesgos/" + idRiesgo("RSK-LEG-0018") + "/plan/aprobar", null);
        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.regla()).isEqualTo("RN03");
    }

    @Test
    @Order(7)
    @DisplayName("CP07 · CU04: el Director aprueba un plan completo; riesgo EN_TRATAMIENTO, avisos y bitácora")
    void cp07() throws Exception {
        int id = idRiesgo("RSK-TEC-0021");
        Respuesta r = post_("crojas", "/api/riesgos/" + id + "/plan/aprobar", null);
        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("estado").asText()).isEqualTo("APROBADO");
        assertThat(r.cuerpo().get("estado_riesgo").asText()).isEqualTo("EN_TRATAMIENTO");
        Integer avisos = jdbc.queryForObject("""
                SELECT count(*) FROM notificacion n JOIN accion_mitigacion a ON a.id_responsable = n.id_usuario
                JOIN plan_mitigacion p USING (id_plan) WHERE p.id_riesgo = ? AND n.titulo = 'Acción de mitigación asignada'""", Integer.class, id);
        assertThat(avisos).isPositive();
        int plan = jdbc.queryForObject("SELECT id_plan FROM plan_mitigacion WHERE id_riesgo = ?", Integer.class, id);
        assertThat(enBitacora("APROBAR_TRATAMIENTO", "PLAN_MITIGACION", plan, "OK")).isTrue();
    }

    // ------------------------------------------------------------------ CU05 (RN11)

    @Test
    @Order(80)
    @DisplayName("CP09 · CU05: con todas las acciones cerradas y residual 12 sobre el apetito, sigue EN_TRATAMIENTO (RN11)")
    void cp09() throws Exception {
        int id = idRiesgo("RSK-TEC-0030");
        int apetito = jdbc.queryForObject("SELECT f.apetito_riesgo FROM riesgo r JOIN filial f USING (id_filial) WHERE r.id_riesgo = ?", Integer.class, id);
        assertThat(12).isGreaterThan(apetito);
        Respuesta r = post_("mquispe", "/api/riesgos/" + id + "/evaluaciones", Map.of("tipo", "RESIDUAL", "probabilidad", 3, "impacto", 4));
        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("estado_riesgo").asText()).isEqualTo("EN_TRATAMIENTO");
    }

    @Test
    @Order(81)
    @DisplayName("CP08 · CU05: con residual 6 dentro del apetito el riesgo pasa a MITIGADO (RN11)")
    void cp08() throws Exception {
        int id = idRiesgo("RSK-TEC-0030");
        Respuesta r = post_("mquispe", "/api/riesgos/" + id + "/evaluaciones", Map.of("tipo", "RESIDUAL", "probabilidad", 2, "impacto", 3));
        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("valor").asInt()).isEqualTo(6);
        assertThat(r.cuerpo().get("estado_riesgo").asText()).isEqualTo("MITIGADO");
        assertThat(jdbc.queryForObject("SELECT estado FROM plan_mitigacion WHERE id_riesgo = ?", String.class, id)).isEqualTo("CERRADO");
    }

    // ------------------------------------------------------------------ CU06 a CU09

    @Test
    @Order(100)
    @DisplayName("CP10 · CU06: una medición de 5,8 con umbral rojo 5,0 da semáforo ROJO y alerta crítica de 24 h")
    void cp10() throws Exception {
        int kri = jdbc.queryForObject("SELECT id_kri FROM indicador_kri WHERE nombre = 'Variación mensual del tipo de cambio paralelo'", Integer.class);
        jdbc.queryForList("SELECT fn_cerrar_alerta_auto(id_alerta, 'Preparación de la prueba CP10') FROM alerta WHERE id_kri = ? AND estado NOT IN ('RESUELTA', 'CERRADA')", kri);
        Respuesta r = post_("mquispe", "/api/kri/" + kri + "/mediciones", Map.of("valor", 5.8));
        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("semaforo").asText()).isEqualTo("ROJO");
        JsonNode alerta = r.cuerpo().get("alerta");
        assertThat(alerta.isNull()).isFalse();
        assertThat(alerta.get("severidad").asText()).isEqualTo("CRITICA");
        int idAlerta = alerta.get("id_alerta").asInt();
        assertThat(jdbc.queryForObject("SELECT sla_horas FROM alerta WHERE id_alerta = ?", Integer.class, idAlerta)).isEqualTo(24);
        COMPARTIDO.put("kri", kri);
        COMPARTIDO.put("alerta", idAlerta);
    }

    @Test
    @Order(110)
    @DisplayName("CP11 · CU06: una medición de 2,5 con umbral amarillo 3,0 queda en VERDE y no genera alerta")
    void cp11() throws Exception {
        Respuesta r = post_("mquispe", "/api/kri/" + COMPARTIDO.get("kri") + "/mediciones", Map.of("valor", 2.5));
        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("semaforo").asText()).isEqualTo("VERDE");
        assertThat(r.cuerpo().get("alerta").isNull()).isTrue();
    }

    @Test
    @Order(120)
    @DisplayName("CP12 · CU09: una alerta crítica con 25 horas sin atención se escala al superior (RN07)")
    void cp12() throws Exception {
        int id = (Integer) COMPARTIDO.get("alerta");
        for (int i = 0; i < 40 && !"NOTIFICADA".equals(jdbc.queryForObject("SELECT estado FROM alerta WHERE id_alerta = ?", String.class, id)); i++) {
            Thread.sleep(500);
        }
        assertThat(jdbc.queryForObject("SELECT estado FROM alerta WHERE id_alerta = ?", String.class, id)).isEqualTo("NOTIFICADA");
        jdbc.update("UPDATE alerta SET fecha_generacion = now() - interval '25 hours', fecha_limite = now() - interval '1 hour' WHERE id_alerta = ?", id);
        int asignado = jdbc.queryForObject("SELECT id_asignado FROM alerta WHERE id_alerta = ?", Integer.class, id);
        int superior = jdbc.queryForObject("SELECT id_superior FROM usuario WHERE id_usuario = ?", Integer.class, asignado);
        assertThat(alertas.escalarVencidas()).isGreaterThanOrEqualTo(1);
        Map<String, Object> a = jdbc.queryForMap("SELECT estado, id_asignado, nivel_escalamiento FROM alerta WHERE id_alerta = ?", id);
        assertThat(a.get("estado")).isEqualTo("ESCALADA");
        assertThat(a.get("id_asignado")).isEqualTo(superior);
        assertThat(((Number) a.get("nivel_escalamiento")).intValue()).isEqualTo(1);
    }

    @Test
    @Order(130)
    @DisplayName("CP13 · CU08: el responsable atiende y resuelve la alerta con su acción correctiva; luego se cierra")
    void cp13() throws Exception {
        int id = (Integer) COMPARTIDO.get("alerta");
        String responsable = jdbc.queryForObject("SELECT u.login FROM alerta a JOIN usuario u ON u.id_usuario = a.id_asignado WHERE a.id_alerta = ?", String.class, id);
        assertThat(post_(responsable, "/api/alertas/" + id + "/atender", null).estado()).isEqualTo(200);
        Respuesta sinAccion = post_(responsable, "/api/alertas/" + id + "/resolver", Map.of("accionCorrectiva", ""));
        assertThat(sinAccion.regla()).isEqualTo("CU08");
        Respuesta resuelta = post_(responsable, "/api/alertas/" + id + "/resolver", Map.of("accionCorrectiva", "Se activó la cobertura cambiaria con el banco"));
        assertThat(resuelta.cuerpo().get("estado").asText()).isEqualTo("RESUELTA");
        assertThat(post_(responsable, "/api/alertas/" + id + "/cerrar", null).cuerpo().get("estado").asText()).isEqualTo("CERRADA");
    }

    // ------------------------------------------------------------------ CU11 a CU16

    @Test
    @Order(140)
    @DisplayName("CP14 · CU11: un hallazgo de severidad Alta sin riesgo vinculado se rechaza (RN08)")
    void cp14() throws Exception {
        int auditoria = jdbc.queryForObject("SELECT id_auditoria FROM auditoria WHERE estado = 'EN_EJECUCION' ORDER BY id_auditoria LIMIT 1", Integer.class);
        Respuesta r = post_("pvargas", "/api/auditorias/" + auditoria + "/hallazgos", Map.of("descripcion", "Conciliaciones bancarias sin revisión desde hace tres meses",
                "severidad", "ALTA", "recomendacion", "Revisión semanal firmada", "fechaLimite", LocalDate.now().plusDays(20).toString(),
                "idResponsable", idUsuario("sgomez")));
        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.regla()).isEqualTo("RN08");
    }

    @Test
    @Order(150)
    @DisplayName("CP15 · CU12: una obligación que vence en 25 días genera el aviso de vencimiento (RN09)")
    void cp15() throws Exception {
        int normativa = jdbc.queryForObject("SELECT min(id_normativa) FROM normativa", Integer.class);
        Respuesta r = post_("dflores", "/api/cumplimiento/normativas/" + normativa + "/obligaciones", Map.of("idFilial", 1,
                "descripcion", "Reporte trimestral de prueba CP15", "periodicidad", "TRIMESTRAL", "fechaVencimiento", LocalDate.now().plusDays(25).toString()));
        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("alerta_generada").asBoolean()).isTrue();
        COMPARTIDO.put("obligacion", r.cuerpo().get("id_obligacion").asInt());
    }

    @Test
    @Order(160)
    @DisplayName("CP16 · CU13: una obligación que no se cumple crea un riesgo Legal y su alerta (RN10)")
    void cp16() throws Exception {
        int obligacion = (Integer) COMPARTIDO.get("obligacion");
        Respuesta r = post_("dflores", "/api/cumplimiento/obligaciones/" + obligacion + "/evaluaciones",
                Map.of("resultado", "NO_CUMPLE", "observacion", "El reporte no se presentó al organismo regulador"));
        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("riesgo_generado").asText()).matches("RSK-LEG-\\d{4}");
        int riesgo = r.cuerpo().get("id_riesgo_generado").asInt();
        assertThat(jdbc.queryForObject("SELECT categoria FROM riesgo WHERE id_riesgo = ?", String.class, riesgo)).isEqualTo("LEGAL");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM alerta WHERE id_riesgo = ? AND tipo = 'RIESGO_LEGAL'", Integer.class, riesgo)).isEqualTo(1);
    }

    @Test
    @Order(170)
    @DisplayName("CP17 · CU14: el Gerente de la filial Perú solo ve la información de Perú (RN14)")
    void cp17() throws Exception {
        int filial = get_("lparedes", "/api/auth/yo").cuerpo().get("idFilial").asInt();
        JsonNode riesgos = get_("lparedes", "/api/riesgos").cuerpo();
        assertThat(riesgos.size()).isPositive();
        riesgos.forEach(r -> assertThat(r.get("id_filial").asInt()).isEqualTo(filial));
        JsonNode tablero = get_("lparedes", "/api/tablero").cuerpo();
        assertThat(tablero.get("por_filial").size()).isEqualTo(1);
        assertThat(tablero.get("por_filial").get(0).get("id_filial").asInt()).isEqualTo(filial);
        int otro = idRiesgo("RSK-FIN-0042");
        assertThat(get_("lparedes", "/api/riesgos/" + otro).estado()).isEqualTo(403);
    }

    @Test
    @Order(180)
    @DisplayName("CP18 · CU14: los impactos en bolivianos y en reales se consolidan en dólares (RN15)")
    void cp18() throws Exception {
        for (String codigo : List.of("RSK-FIN-0042", "RSK-FIN-0058")) {
            Map<String, Object> e = jdbc.queryForMap("SELECT impacto_financiero, tipo_cambio, impacto_usd, moneda FROM v_riesgo WHERE codigo = ?", codigo);
            BigDecimal esperado = ((BigDecimal) e.get("impacto_financiero")).divide((BigDecimal) e.get("tipo_cambio"), 2, RoundingMode.HALF_UP);
            assertThat((BigDecimal) e.get("impacto_usd")).as(codigo).isEqualByComparingTo(esperado);
            assertThat(e.get("moneda")).isIn("BOB", "BRL");
        }
        BigDecimal total = jdbc.queryForObject("SELECT coalesce(sum(impacto_usd), 0) FROM v_riesgo WHERE estado <> 'CERRADO'", BigDecimal.class);
        JsonNode kpi = get_("crojas", "/api/tablero").cuerpo().get("kpi");
        assertThat(kpi.get("impacto_usd_total").decimalValue()).isEqualByComparingTo(total);
    }

    @Test
    @Order(190)
    @DisplayName("CP19 · CU15: el administrador cambia el umbral rojo; el cambio se aplica y queda en la bitácora (RN13)")
    void cp19() throws Exception {
        int kri = jdbc.queryForObject("SELECT id_kri FROM indicador_kri WHERE nombre = 'Tasa SELIC'", Integer.class);
        Respuesta r = llamar("admin", put("/api/kri/" + kri + "/umbrales"), Map.of("umbralAmarillo", 14.0, "umbralRojo", 16.0));
        assertThat(r.estado()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT umbral_rojo FROM indicador_kri WHERE id_kri = ?", BigDecimal.class, kri)).isEqualByComparingTo("16");
        Map<String, Object> b = jdbc.queryForMap("""
                SELECT b.detalle -> 'cambios' -> 'umbral_rojo' ->> 'antes' AS antes, b.detalle -> 'cambios' -> 'umbral_rojo' ->> 'despues' AS despues, u.login
                FROM bitacora b JOIN usuario u USING (id_usuario)
                WHERE b.operacion = 'MODIFICAR_UMBRAL' AND b.id_entidad = ? ORDER BY b.id_bitacora DESC LIMIT 1""", kri);
        assertThat(new BigDecimal((String) b.get("antes"))).isEqualByComparingTo("15.5");
        assertThat(new BigDecimal((String) b.get("despues"))).isEqualByComparingTo("16");
        assertThat(b.get("login")).isEqualTo("admin");
    }

    @Test
    @Order(200)
    @DisplayName("CP20 · CU16: no se puede borrar ni modificar un registro de la bitácora (RN13)")
    void cp20() throws Exception {
        Respuesta r = post_("pvargas", "/api/bitacora/prueba-inalterable", null);
        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("protegida").asBoolean()).isTrue();
        assertThatThrownBy(() -> jdbc.update("DELETE FROM bitacora WHERE id_bitacora = (SELECT min(id_bitacora) FROM bitacora)"))
                .hasMessageContaining("bitacora");
    }

    // ------------------------------------------------------------------ CU17 y CU18 (Auditor Financiero)

    private int evaluacionPendiente(String codigo) {
        return jdbc.queryForObject("""
                SELECT e.id_evaluacion FROM evaluacion_riesgo e JOIN riesgo r USING (id_riesgo)
                WHERE r.codigo = ? AND e.estado_validacion = 'PENDIENTE' ORDER BY e.id_evaluacion DESC LIMIT 1""", Integer.class, codigo);
    }

    @Test
    @Order(210)
    @DisplayName("CP21 · CU17: el Auditor Financiero valida el impacto; queda VALIDADA, se notifica y se registra (RN16, RN13)")
    void cp21() throws Exception {
        int ev = evaluacionPendiente("RSK-FIN-0077");
        int evaluador = jdbc.queryForObject("SELECT id_evaluador FROM evaluacion_riesgo WHERE id_evaluacion = ?", Integer.class, ev);
        Respuesta r = post_("lmamani", "/api/validacion-financiera/" + ev, Map.of("resultado", "VALIDADA", "observacion", "Coincide con el ERP"));
        assertThat(r.estado()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT estado_validacion::text FROM evaluacion_riesgo WHERE id_evaluacion = ?", String.class, ev)).isEqualTo("VALIDADA");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE id_usuario = ? AND titulo = 'Impacto financiero validado'", Integer.class, evaluador)).isPositive();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion n JOIN usuario u USING (id_usuario) WHERE u.login = 'crojas' AND n.titulo = 'Resultado de validación financiera'", Integer.class)).isPositive();
        assertThat(enBitacora("VALIDAR_IMPACTO", "VALIDACION_FINANCIERA", r.cuerpo().get("id_validacion").asInt(), "OK")).isTrue();
    }

    @Test
    @Order(220)
    @DisplayName("CP22 · CU17: el Auditor Financiero no puede validar un riesgo del que es responsable (RN17)")
    void cp22() throws Exception {
        Respuesta r = post_("lmamani", "/api/validacion-financiera/" + evaluacionPendiente("RSK-FIN-0093"), Map.of("resultado", "VALIDADA"));
        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.regla()).isEqualTo("RN17");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO validacion_financiera (id_evaluacion, id_auditor_fin, resultado, tipo_cambio, impacto_usd_validado) "
                + "VALUES (?, ?, 'VALIDADA', 1, 1)", evaluacionPendiente("RSK-FIN-0093"), idUsuario("lmamani")))
                .hasMessageContaining("RN17");
    }

    @Test
    @Order(230)
    @DisplayName("CP23 · CU17: observar la evaluación por un tipo de cambio incorrecto la deja OBSERVADA")
    void cp23() throws Exception {
        int ev = evaluacionPendiente("RSK-FIN-0058");
        Respuesta r = post_("lmamani", "/api/validacion-financiera/" + ev, Map.of("resultado", "OBSERVADA",
                "observacion", "El tipo de cambio usado no corresponde a la fecha de la evaluación"));
        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("resultado").asText()).isEqualTo("OBSERVADA");
        assertThat(jdbc.queryForObject("SELECT estado_validacion::text FROM evaluacion_riesgo WHERE id_evaluacion = ?", String.class, ev)).isEqualTo("OBSERVADA");
    }

    @Test
    @Order(240)
    @DisplayName("CP24 · CU04: el Director no puede aprobar un riesgo financiero sin la validación del impacto (RN16)")
    void cp24() throws Exception {
        Respuesta r = post_("crojas", "/api/riesgos/" + idRiesgo("RSK-FIN-0051") + "/plan/aprobar", null);
        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.regla()).isEqualTo("RN16");
    }

    @Test
    @Order(250)
    @DisplayName("CP25 · CU18: una diferencia de 3,4 % con el ERP queda CON_DIFERENCIA, con alerta y hallazgo (RN18)")
    void cp25() throws Exception {
        Respuesta e = post_("lmamani", "/api/conciliacion/ejecutar", Map.of());
        assertThat(e.estado()).isEqualTo(200);
        Map<String, Object> c = jdbc.queryForMap("""
                SELECT c.id_conciliacion, c.estado, c.diferencia_pct FROM conciliacion_kri c JOIN indicador_kri k USING (id_kri)
                WHERE k.nombre = 'Ratio de liquidez corriente' ORDER BY c.periodo DESC LIMIT 1""");
        assertThat(c.get("estado")).isEqualTo("CON_DIFERENCIA");
        assertThat(((BigDecimal) c.get("diferencia_pct")).setScale(1, RoundingMode.HALF_UP)).isEqualByComparingTo("3.4");
        int id = ((Number) c.get("id_conciliacion")).intValue();
        assertThat(jdbc.queryForObject("SELECT severidad FROM alerta WHERE id_conciliacion = ? AND tipo = 'CONCILIACION_DIFERENCIA'", String.class, id)).isEqualTo("ADVERTENCIA");
        Respuesta h = post_("lmamani", "/api/conciliacion/" + id + "/hallazgo", Map.of("descripcion", "El ratio reportado difiere del cierre contable del ERP",
                "severidad", "MEDIA", "recomendacion", "Conciliar la fuente del indicador", "fechaLimite", LocalDate.now().plusDays(30).toString()));
        assertThat(h.estado()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT hallazgo_registrado FROM conciliacion_kri WHERE id_conciliacion = ?", Boolean.class, id)).isTrue();
    }

    @Test
    @Order(260)
    @DisplayName("CP26 · CU18: una diferencia de 1,2 % queda CONCILIADA y sin alerta")
    void cp26() throws Exception {
        Map<String, Object> c = jdbc.queryForMap("""
                SELECT c.id_conciliacion, c.estado, c.diferencia_pct FROM conciliacion_kri c JOIN indicador_kri k USING (id_kri)
                WHERE k.nombre = 'Tasa SELIC' ORDER BY c.periodo DESC LIMIT 1""");
        assertThat(c.get("estado")).isEqualTo("CONCILIADA");
        assertThat(((BigDecimal) c.get("diferencia_pct")).setScale(1, RoundingMode.HALF_UP)).isEqualByComparingTo("1.2");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM alerta WHERE id_conciliacion = ?", Integer.class, c.get("id_conciliacion"))).isZero();
    }

    // ------------------------------------------------------------------ Revisión de seguridad (correcciones verificadas)

    @Test
    @Order(300)
    @DisplayName("S01 · Un cuerpo JSON mal formado responde 400 y no 200 vacío")
    void s01() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{mal")).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(json.readTree(r.getResponse().getContentAsString()).get("tipo").asText()).isEqualTo("Validación");
    }

    @Test
    @Order(310)
    @DisplayName("S02 · RN14: un gerente de filial no ve integraciones ni usuarios de otras filiales")
    void s02() throws Exception {
        assertThat(get_("lparedes", "/api/integraciones").estado()).isEqualTo(403);
        assertThat(get_("lparedes", "/api/erp/cierres").estado()).isEqualTo(403);
        int filial = get_("lparedes", "/api/auth/yo").cuerpo().get("idFilial").asInt();
        get_("lparedes", "/api/catalogos").cuerpo().get("usuarios")
                .forEach(u -> assertThat(u.get("id_filial").asInt() == filial || u.get("corporativo").asBoolean()).isTrue());
    }

    @Test
    @Order(320)
    @DisplayName("S03 · Tabla 47: solo el Gestor registra riesgos (CU01) y mediciones manuales (CU06)")
    void s03() throws Exception {
        Respuesta r = post_("rgutierrez", "/api/riesgos", Map.of("idFilial", 1, "titulo", "Riesgo de prueba S03", "categoria", "OPERATIVO"));
        assertThat(r.estado()).isEqualTo(403);
        int kri = jdbc.queryForObject("SELECT id_kri FROM indicador_kri WHERE nombre = 'Variación mensual del tipo de cambio paralelo'", Integer.class);
        assertThat(post_("jcondori", "/api/kri/" + kri + "/mediciones", Map.of("valor", 1.0)).estado()).isEqualTo(403);
    }

    @Test
    @Order(330)
    @DisplayName("S04 · Evidencias: un HTML disfrazado de PDF se rechaza y las evidencias se sirven con su tipo real")
    void s04() throws Exception {
        Map<String, Object> fila = jdbc.queryForMap("""
                SELECT a.id_accion, u.login FROM accion_mitigacion a JOIN plan_mitigacion p USING (id_plan) JOIN usuario u ON u.id_usuario = a.id_responsable
                WHERE a.estado <> 'CERRADA' AND p.estado = 'APROBADO' ORDER BY a.id_accion LIMIT 1""");
        int accion = ((Number) fila.get("id_accion")).intValue();
        String responsable = (String) fila.get("login");
        var falso = new org.springframework.mock.web.MockMultipartFile("archivo", "informe.pdf", "text/html",
                "<script>alert(1)</script>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        MvcResult r = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/acciones/" + accion + "/evidencias")
                .file(falso).header("Authorization", "Bearer " + token(responsable))).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(422);
        var real = new org.springframework.mock.web.MockMultipartFile("archivo", "acta.pdf", "text/html",
                "%PDF-1.4 prueba".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        MvcResult ok = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/acciones/" + accion + "/evidencias")
                .file(real).header("Authorization", "Bearer " + token(responsable))).andReturn();
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        int evidencia = json.readTree(ok.getResponse().getContentAsString()).get("id_evidencia").asInt();
        MvcResult archivo = mvc.perform(get("/api/evidencias/" + evidencia + "/archivo").header("Authorization", "Bearer " + token(responsable))).andReturn();
        assertThat(archivo.getResponse().getContentType()).isEqualTo("application/pdf");
        assertThat(archivo.getResponse().getHeader("Content-Security-Policy")).contains("sandbox");
    }

    @Test
    @Order(340)
    @DisplayName("S05 · Al desactivar un usuario su token deja de valer de inmediato")
    void s05() throws Exception {
        assertThat(get_("nacosta", "/api/auth/yo").estado()).isEqualTo(200);
        int id = idUsuario("nacosta");
        assertThat(llamar("admin", put("/api/admin/usuarios/" + id), Map.of("activo", false, "idSuperior", jdbc.queryForObject(
                "SELECT id_superior FROM usuario WHERE id_usuario = ?", Integer.class, id))).estado()).isEqualTo(200);
        assertThat(get_("nacosta", "/api/auth/yo").estado()).isEqualTo(401);
        llamar("admin", put("/api/admin/usuarios/" + id), Map.of("activo", true, "idSuperior", jdbc.queryForObject(
                "SELECT id_superior FROM usuario WHERE id_usuario = ?", Integer.class, id)));
        TOKENS.remove("nacosta");
    }

    @Test
    @Order(350)
    @DisplayName("S06 · RF18: los reportes se exportan en PDF y CSV además de Excel")
    void s06() throws Exception {
        MvcResult pdf = mvc.perform(get("/api/reportes/riesgos.pdf").header("Authorization", "Bearer " + token("crojas"))).andReturn();
        assertThat(pdf.getResponse().getStatus()).isEqualTo(200);
        assertThat(new String(pdf.getResponse().getContentAsByteArray(), 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        MvcResult csv = mvc.perform(get("/api/reportes/planes.csv").header("Authorization", "Bearer " + token("crojas"))).andReturn();
        assertThat(csv.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).startsWith("\uFEFF\"Riesgo\"");
    }
}

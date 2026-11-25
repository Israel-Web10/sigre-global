package bo.umsa.sigre.comun;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Prepara los datos de demostración al arrancar: asigna la clave inicial (hash Argon2id) a los
 * usuarios que aún no la tienen y genera los archivos PDF de las evidencias precargadas.
 */
@Component
@Order(1)
public class InicializadorDemo implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InicializadorDemo.class);

    private final Consultas bd;
    private final PasswordEncoder claves;
    private final AlmacenEvidencias almacen;
    private final boolean demo;
    private final String claveDemo;

    public InicializadorDemo(Consultas bd, PasswordEncoder claves, AlmacenEvidencias almacen,
                             @Value("${sigre.demo.habilitado:false}") boolean demo,
                             @Value("${sigre.demo.clave-usuarios:}") String claveDemo) {
        this.bd = bd;
        this.claves = claves;
        this.almacen = almacen;
        this.demo = demo;
        this.claveDemo = claveDemo;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (demo && claveDemo != null && claveDemo.length() >= 8) {
            List<Integer> sinClave = bd.jdbc().queryForList("SELECT id_usuario FROM usuario WHERE clave_hash IS NULL", Map.of(), Integer.class);
            for (Integer id : sinClave) {
                bd.ejecutar("UPDATE usuario SET clave_hash = :h WHERE id_usuario = :id", Consultas.p("h", claves.encode(claveDemo), "id", id));
            }
            if (!sinClave.isEmpty()) log.info("Demostración: clave inicial asignada a {} usuario(s)", sinClave.size());
        }
        List<Map<String, Object>> evidencias = bd.lista("""
                SELECT ev.id_evidencia, ev.archivo, ev.descripcion, ev.fecha_carga, coalesce(u.nombre_completo, 'Sistema') AS usuario,
                       coalesce(r.codigo || ' · ' || a.descripcion, n.nombre || ' · ' || o.descripcion) AS contexto
                FROM evidencia ev LEFT JOIN usuario u USING (id_usuario)
                LEFT JOIN accion_mitigacion a ON a.id_accion = ev.id_accion LEFT JOIN plan_mitigacion p ON p.id_plan = a.id_plan
                LEFT JOIN riesgo r ON r.id_riesgo = p.id_riesgo
                LEFT JOIN evaluacion_cumplimiento ec ON ec.id_eval_cump = ev.id_eval_cump
                LEFT JOIN obligacion o ON o.id_obligacion = ec.id_obligacion LEFT JOIN normativa n ON n.id_normativa = o.id_normativa
                WHERE ev.ruta IS NULL""");
        for (Map<String, Object> e : evidencias) {
            try {
                byte[] pdf = PdfSimple.documento("Evidencia de cumplimiento", String.valueOf(e.get("contexto")), List.of(
                        "Archivo: " + e.get("archivo"),
                        "Cargado por: " + e.get("usuario"),
                        "Fecha de carga: " + String.valueOf(e.get("fecha_carga")).replace('T', ' ').substring(0, 16),
                        "",
                        "Descripción: " + (e.get("descripcion") == null ? "Respaldo documental de la actividad." : e.get("descripcion")),
                        "",
                        "Este documento acredita la ejecución de la actividad indicada y forma parte del expediente",
                        "del riesgo u obligación en SIGRE Global. Su carga quedó registrada en la bitácora (RN13)."));
                String ruta = almacen.guardar(new ByteArrayInputStream(pdf), "precargadas", (String) e.get("archivo"));
                bd.ejecutar("UPDATE evidencia SET ruta = :r, tamano = :t, tipo_mime = 'application/pdf' WHERE id_evidencia = :id",
                        Consultas.p("r", ruta, "t", (long) pdf.length, "id", e.get("id_evidencia")));
            } catch (Exception ex) {
                log.warn("No se pudo generar la evidencia {}: {}", e.get("id_evidencia"), ex.getMessage());
            }
        }
        if (!evidencias.isEmpty()) log.info("Demostración: {} evidencia(s) precargadas generadas", evidencias.size());
    }
}

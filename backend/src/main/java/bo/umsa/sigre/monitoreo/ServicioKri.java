package bo.umsa.sigre.monitoreo;

import static bo.umsa.sigre.comun.Consultas.p;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bo.umsa.sigre.comun.Condiciones;
import bo.umsa.sigre.comun.Consultas;
import bo.umsa.sigre.comun.Dominio;
import bo.umsa.sigre.comun.ReglaNegocioException;
import bo.umsa.sigre.riesgos.Riesgo;
import bo.umsa.sigre.seguridad.UsuarioSesion;

/** Indicadores clave de riesgo y sus mediciones (CU06, RN06). */
@Service
public class ServicioKri {

    private final Consultas bd;
    private final Dominio dominio;

    public ServicioKri(Consultas bd, Dominio dominio) {
        this.bd = bd;
        this.dominio = dominio;
    }

    public record NuevoKri(Integer idRiesgo, String nombre, String unidad, BigDecimal umbralAmarillo, BigDecimal umbralRojo,
                           String direccion, String frecuencia, String fuente, Boolean financiero) { }

    public record Umbrales(BigDecimal umbralAmarillo, BigDecimal umbralRojo) { }

    public List<Map<String, Object>> listar(UsuarioSesion u, Integer filial, String fuente, String semaforo, Integer riesgo) {
        Condiciones c = new Condiciones().alcance(u, "r.id_filial")
                .si(filial, "r.id_filial = :filial", "filial")
                .si(fuente, "k.fuente = :fuente", "fuente")
                .si(semaforo, "m.semaforo = :semaforo", "semaforo")
                .si(riesgo, "k.id_riesgo = :riesgo", "riesgo");
        return bd.lista("""
                SELECT k.*, r.codigo AS riesgo_codigo, r.titulo AS riesgo_titulo, r.categoria, r.id_filial,
                       f.nombre AS filial, f.codigo_pais, m.valor AS ultimo_valor, m.semaforo, m.fecha AS fecha_medicion,
                       m.origen AS origen_medicion,
                       (SELECT array_agg(x.valor ORDER BY x.fecha) FROM (SELECT valor, fecha FROM medicion_kri mm
                         WHERE mm.id_kri = k.id_kri ORDER BY fecha DESC LIMIT 30) x) AS serie,
                       (SELECT count(*) FROM alerta a WHERE a.id_kri = k.id_kri AND a.estado NOT IN ('RESUELTA', 'CERRADA')) AS alertas_abiertas
                FROM indicador_kri k JOIN riesgo r USING (id_riesgo) JOIN filial f ON f.id_filial = r.id_filial
                LEFT JOIN LATERAL (SELECT valor, semaforo, fecha, origen FROM medicion_kri mk WHERE mk.id_kri = k.id_kri
                                   ORDER BY fecha DESC LIMIT 1) m ON TRUE
                WHERE TRUE""" + c.sql() + " " + """
                 ORDER BY CASE m.semaforo WHEN 'ROJO' THEN 0 WHEN 'AMARILLO' THEN 1 ELSE 2 END, k.nombre""", c.params());
    }

    private Map<String, Object> kri(UsuarioSesion u, int idKri) {
        Map<String, Object> k = bd.uno("""
                SELECT k.*, r.codigo AS riesgo_codigo, r.titulo AS riesgo_titulo, r.id_filial, r.id_responsable, f.nombre AS filial
                FROM indicador_kri k JOIN riesgo r USING (id_riesgo) JOIN filial f ON f.id_filial = r.id_filial
                WHERE k.id_kri = :k""", p("k", idKri));
        if (k == null) throw ReglaNegocioException.noEncontrado("El indicador");
        u.exigirFilial((Integer) k.get("id_filial"));
        return k;
    }

    public Map<String, Object> detalle(UsuarioSesion u, int idKri, int limite) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("kri", kri(u, idKri));
        d.put("mediciones", bd.lista("""
                SELECT * FROM (SELECT m.id_medicion, m.fecha, m.valor, m.origen, m.semaforo, coalesce(u.nombre_completo, 'Integración') AS usuario
                               FROM medicion_kri m LEFT JOIN usuario u USING (id_usuario)
                               WHERE m.id_kri = :k ORDER BY m.fecha DESC LIMIT :l) x ORDER BY fecha""",
                p("k", idKri, "l", Math.max(5, Math.min(limite, 500)))));
        d.put("alertas", bd.lista("SELECT * FROM v_alerta WHERE id_kri = :k ORDER BY fecha_generacion DESC LIMIT 20", p("k", idKri)));
        d.put("conciliaciones", bd.lista("SELECT * FROM conciliacion_kri WHERE id_kri = :k ORDER BY periodo DESC", p("k", idKri)));
        return d;
    }

    /** CU06: registrar una medición manual; el trigger asigna el semáforo y genera la alerta (CU07). */
    @Transactional
    public Map<String, Object> registrarMedicion(UsuarioSesion u, int idKri, BigDecimal valor) {
        u.exigir("registrar mediciones", UsuarioSesion.GESTOR);
        kri(u, idKri);
        if (valor == null) throw new ReglaNegocioException("CU06: Indique el valor medido");
        IndicadorKri k = dominio.buscar(IndicadorKri.class, idKri, "El indicador");
        if (!k.isActivo()) throw new ReglaNegocioException("CU06: El indicador " + k.getNombre() + " está inactivo");
        MedicionKri m = dominio.guardar(new MedicionKri(idKri, valor, "MANUAL", u.id()));
        Map<String, Object> alerta = bd.uno("SELECT id_alerta, severidad, mensaje FROM alerta WHERE id_medicion = :m", p("m", m.getId()));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id_medicion", m.getId());
        r.put("semaforo", m.getSemaforo().name());
        r.put("esperado", k.evaluar(valor).name());
        r.put("alerta", alerta);
        return r;
    }

    @Transactional
    public Map<String, Object> definir(UsuarioSesion u, NuevoKri n) {
        u.exigir("definir indicadores", UsuarioSesion.GESTOR);
        Riesgo r = dominio.buscar(Riesgo.class, n.idRiesgo(), "El riesgo");
        u.exigirFilial(r.getIdFilial());
        if (n.nombre() == null || n.nombre().isBlank()) throw new ReglaNegocioException("CU06: Indique el nombre del indicador");
        IndicadorKri k = dominio.guardar(new IndicadorKri(n.idRiesgo(), n.nombre().trim(), n.unidad() == null ? "" : n.unidad().trim(),
                n.umbralAmarillo(), n.umbralRojo(), n.direccion(), n.frecuencia() == null ? "MENSUAL" : n.frecuencia(),
                n.fuente() == null ? "MANUAL" : n.fuente(), Boolean.TRUE.equals(n.financiero())));
        return Map.of("id_kri", k.getId());
    }

    /** CP19: modificar los umbrales queda en la bitácora como MODIFICAR_UMBRAL. */
    @Transactional
    public Map<String, Object> cambiarUmbrales(UsuarioSesion u, int idKri, Umbrales n) {
        u.exigir("modificar umbrales", UsuarioSesion.GESTOR, UsuarioSesion.ADMIN);
        kri(u, idKri);
        IndicadorKri k = dominio.buscar(IndicadorKri.class, idKri, "El indicador");
        k.cambiarUmbrales(n.umbralAmarillo(), n.umbralRojo());
        dominio.sincronizar(k);
        return Map.of("id_kri", k.getId(), "umbral_amarillo", k.getUmbralAmarillo(), "umbral_rojo", k.getUmbralRojo());
    }

    @Transactional
    public Map<String, Object> activar(UsuarioSesion u, int idKri, boolean activo) {
        u.exigir("activar indicadores", UsuarioSesion.GESTOR, UsuarioSesion.ADMIN);
        kri(u, idKri);
        IndicadorKri k = dominio.buscar(IndicadorKri.class, idKri, "El indicador");
        k.activar(activo);
        dominio.sincronizar(k);
        return Map.of("id_kri", k.getId(), "activo", k.isActivo());
    }
}

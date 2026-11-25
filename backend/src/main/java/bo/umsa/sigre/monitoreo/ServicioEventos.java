package bo.umsa.sigre.monitoreo;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import bo.umsa.sigre.seguridad.UsuarioSesion;

/**
 * Tiempo real: escucha el canal sigre_eventos de PostgreSQL (LISTEN/NOTIFY), que alimentan
 * los triggers, y reenvía cada evento a los navegadores conectados por Server-Sent Events.
 * En la arquitectura del informe este papel lo cumple el bus de mensajes (sección 10.1).
 */
@Service
public class ServicioEventos implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ServicioEventos.class);

    private record Suscripcion(SseEmitter emisor, UsuarioSesion usuario) { }

    private final List<Suscripcion> suscripciones = new CopyOnWriteArrayList<>();
    private final ObjectMapper mapper;
    private final String url;
    private final String usuarioBd;
    private final String claveBd;
    private final AtomicLong eventos = new AtomicLong();
    private volatile boolean activo;
    private volatile Thread hilo;
    private volatile OffsetDateTime ultimoEvento;

    public ServicioEventos(ObjectMapper mapper, @Value("${spring.datasource.url}") String url,
                           @Value("${spring.datasource.username}") String usuarioBd,
                           @Value("${spring.datasource.password}") String claveBd) {
        this.mapper = mapper;
        this.url = url;
        this.usuarioBd = usuarioBd;
        this.claveBd = claveBd;
    }

    public SseEmitter suscribir(UsuarioSesion u) {
        SseEmitter emisor = new SseEmitter(0L);
        Suscripcion s = new Suscripcion(emisor, u);
        suscripciones.add(s);
        emisor.onCompletion(() -> suscripciones.remove(s));
        emisor.onTimeout(() -> suscripciones.remove(s));
        emisor.onError(e -> suscripciones.remove(s));
        enviar(s, "CONECTADO", Map.of("usuario", u.login(), "conectados", suscripciones.size()));
        return emisor;
    }

    public int conectados() {
        return suscripciones.size();
    }

    public Map<String, Object> estado() {
        return Map.of("escuchando", hilo != null && hilo.isAlive(), "conectados", suscripciones.size(),
                "eventos", eventos.get(), "ultimo", ultimoEvento == null ? "" : ultimoEvento.toString());
    }

    /** Difunde un evento respetando el alcance por filial (RN14) y los destinatarios. */
    public void difundir(Map<String, Object> evento) {
        eventos.incrementAndGet();
        ultimoEvento = OffsetDateTime.now();
        String tipo = String.valueOf(evento.getOrDefault("evento", "EVENTO"));
        Object destinatario = "NOTIFICACION".equals(tipo) ? evento.get("id_usuario") : null;
        Object filial = evento.get("id_filial");
        for (Suscripcion s : suscripciones) {
            if (destinatario != null && ((Number) destinatario).intValue() != s.usuario().id()) continue;
            if (destinatario == null && filial instanceof Number f && !s.usuario().accedeA(f.intValue())
                    && !(evento.get("id_asignado") instanceof Number a && a.intValue() == s.usuario().id())) continue;
            enviar(s, tipo, evento);
        }
    }

    private void enviar(Suscripcion s, String tipo, Object datos) {
        try {
            s.emisor().send(SseEmitter.event().name(tipo).data(datos, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            suscripciones.remove(s);
        }
    }

    /** Mantiene viva la conexión de los navegadores. */
    @Scheduled(fixedRate = 20000)
    public void latido() {
        for (Suscripcion s : suscripciones) {
            try {
                s.emisor().send(SseEmitter.event().comment("latido"));
            } catch (IOException | IllegalStateException e) {
                suscripciones.remove(s);
            }
        }
    }

    private void escuchar() {
        int espera = 1000;
        while (activo) {
            try (Connection c = DriverManager.getConnection(url, usuarioBd, claveBd)) {
                try (Statement st = c.createStatement()) {
                    st.execute("LISTEN sigre_eventos");
                }
                log.info("Escuchando el canal sigre_eventos de PostgreSQL");
                espera = 1000;
                PGConnection pg = c.unwrap(PGConnection.class);
                while (activo) {
                    PGNotification[] avisos = pg.getNotifications(5000);
                    if (avisos == null) continue;
                    for (PGNotification n : avisos) {
                        try {
                            Map<String, Object> evento = mapper.readValue(n.getParameter(), new TypeReference<>() { });
                            difundir(evento);
                        } catch (Exception e) {
                            log.debug("Evento no válido: {}", n.getParameter());
                        }
                    }
                }
            } catch (Exception e) {
                if (!activo) break;
                log.warn("Se perdió la escucha de eventos ({}); reintento en {} ms", e.getMessage(), espera);
                try {
                    Thread.sleep(espera);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
                espera = Math.min(espera * 2, 30000);
            }
        }
    }

    @Override
    public void start() {
        activo = true;
        hilo = Thread.ofPlatform().daemon().name("sigre-eventos").start(this::escuchar);
    }

    @Override
    public void stop() {
        activo = false;
        if (hilo != null) hilo.interrupt();
        for (Suscripcion s : suscripciones) s.emisor().complete();
        suscripciones.clear();
    }

    @Override
    public boolean isRunning() {
        return activo;
    }
}

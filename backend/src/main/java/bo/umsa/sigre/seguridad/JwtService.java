package bo.umsa.sigre.seguridad;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Emisión y verificación de tokens JWT firmados con HMAC-SHA256. */
@Service
public class JwtService {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder D64 = Base64.getUrlDecoder();

    private final byte[] clave;
    private final long minutosSesion;
    private final ObjectMapper mapper;

    public JwtService(@Value("${sigre.jwt.secreto}") String secreto,
                      @Value("${sigre.jwt.minutos:480}") long minutosSesion, ObjectMapper mapper) {
        if (secreto == null || secreto.length() < 32) {
            throw new IllegalStateException("La clave JWT (sigre.jwt.secreto) debe tener al menos 32 caracteres");
        }
        this.clave = secreto.getBytes(StandardCharsets.UTF_8);
        this.minutosSesion = minutosSesion;
        this.mapper = mapper;
    }

    public long minutosSesion() {
        return minutosSesion;
    }

    public String emitir(Map<String, Object> datos, long minutos) {
        try {
            Map<String, Object> cuerpo = new LinkedHashMap<>(datos);
            long ahora = Instant.now().getEpochSecond();
            cuerpo.put("iat", ahora);
            cuerpo.put("exp", ahora + minutos * 60);
            String cabecera = B64.encodeToString(mapper.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
            String carga = B64.encodeToString(mapper.writeValueAsBytes(cuerpo));
            String firma = B64.encodeToString(firmar(cabecera + "." + carga));
            return cabecera + "." + carga + "." + firma;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo emitir el token", e);
        }
    }

    /** Devuelve los datos del token o null si es inválido o expiró. */
    public Map<String, Object> verificar(String token) {
        try {
            String[] partes = token.split("\\.");
            if (partes.length != 3) return null;
            byte[] esperada = firmar(partes[0] + "." + partes[1]);
            if (!MessageDigest.isEqual(esperada, D64.decode(partes[2]))) return null;
            Map<String, Object> datos = mapper.readValue(D64.decode(partes[1]), new TypeReference<>() { });
            Object exp = datos.get("exp");
            if (!(exp instanceof Number n) || n.longValue() < Instant.now().getEpochSecond()) return null;
            return datos;
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] firmar(String texto) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(clave, "HmacSHA256"));
        return mac.doFinal(texto.getBytes(StandardCharsets.US_ASCII));
    }
}

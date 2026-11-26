package bo.umsa.sigre.seguridad;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

/**
 * Doble factor de autenticación con códigos TOTP (RFC 6238), compatibles con
 * Google Authenticator o Microsoft Authenticator (RNF01).
 */
@Service
public class TotpService {

    private static final String ALFABETO = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom AZAR = new SecureRandom();

    public String nuevoSecreto() {
        byte[] b = new byte[20];
        AZAR.nextBytes(b);
        return base32(b);
    }

    public String urlOtpauth(String login, String secreto) {
        String etiqueta = URLEncoder.encode("SIGRE Global:" + login, StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + etiqueta + "?secret=" + secreto + "&issuer=SIGRE%20Global&algorithm=SHA1&digits=6&period=30";
    }

    /** Paso de 30 s que coincide con el código (±1 paso de tolerancia) o null si no coincide. */
    public Long pasoVerificado(String secreto, String codigo) {
        if (secreto == null || codigo == null || !codigo.matches("[0-9]{6}")) return null;
        long paso = Instant.now().getEpochSecond() / 30;
        for (int d = -1; d <= 1; d++) {
            if (codigo(secreto, paso + d).equals(codigo)) return paso + d;
        }
        return null;
    }

    /** Acepta el código del intervalo actual y de los intervalos vecinos (tolerancia de reloj). */
    public boolean verificar(String secreto, String codigo) {
        if (secreto == null || codigo == null || !codigo.matches("\\d{6}")) return false;
        long paso = Instant.now().getEpochSecond() / 30;
        for (int d = -1; d <= 1; d++) {
            if (codigo(secreto, paso + d).equals(codigo)) return true;
        }
        return false;
    }

    /** Código vigente (se usa solo en el modo demostración y en las pruebas). */
    public String codigoActual(String secreto) {
        return codigo(secreto, Instant.now().getEpochSecond() / 30);
    }

    private String codigo(String secreto, long paso) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(desdeBase32(secreto), "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(paso).array());
            int o = h[h.length - 1] & 0x0f;
            int bin = ((h[o] & 0x7f) << 24) | ((h[o + 1] & 0xff) << 16) | ((h[o + 2] & 0xff) << 8) | (h[o + 3] & 0xff);
            return String.format("%06d", bin % 1_000_000);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String base32(byte[] datos) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte b : datos) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                sb.append(ALFABETO.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) sb.append(ALFABETO.charAt((buffer << (5 - bits)) & 31));
        return sb.toString();
    }

    static byte[] desdeBase32(String texto) {
        String s = texto.replace("=", "").replace(" ", "").toUpperCase();
        ByteBuffer out = ByteBuffer.allocate(s.length() * 5 / 8);
        int buffer = 0, bits = 0;
        for (char c : s.toCharArray()) {
            int v = ALFABETO.indexOf(c);
            if (v < 0) throw new IllegalArgumentException("Secreto TOTP inválido");
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) ((buffer >> (bits - 8)) & 0xff));
                bits -= 8;
            }
        }
        byte[] r = new byte[out.position()];
        out.flip();
        out.get(r);
        return r;
    }
}

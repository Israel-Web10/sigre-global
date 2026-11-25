package bo.umsa.sigre.comun;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Generador mínimo de PDF (una página, Helvetica) para las evidencias de la demostración
 * y las constancias que emite el sistema. No requiere bibliotecas externas.
 */
public final class PdfSimple {

    private PdfSimple() {
    }

    private static String escapar(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '(' || c == ')' || c == '\\') b.append('\\').append(c);
            else if (c < 256) b.append(c);
            else b.append('?');
        }
        return b.toString();
    }

    /** Crea un PDF con un título, un subtítulo y líneas de texto. */
    public static byte[] documento(String titulo, String subtitulo, List<String> lineas) {
        StringBuilder c = new StringBuilder();
        // Franja superior con el color corporativo
        c.append("0.05 0.20 0.45 rg 0 792 612 -70 re f\n");
        c.append("0.13 0.71 0.62 rg 0 722 612 -4 re f\n");
        c.append("BT /F2 20 Tf 1 1 1 rg 50 750 Td (").append(escapar("SIGRE Global")).append(") Tj ET\n");
        c.append("BT /F1 10 Tf 0.85 0.9 1 rg 50 734 Td (")
                .append(escapar("Sistema de gestión de riesgos empresariales")).append(") Tj ET\n");
        c.append("BT /F2 16 Tf 0.1 0.1 0.15 rg 50 680 Td (").append(escapar(titulo)).append(") Tj ET\n");
        c.append("BT /F1 11 Tf 0.35 0.35 0.4 rg 50 660 Td (").append(escapar(subtitulo)).append(") Tj ET\n");
        int y = 625;
        for (String l : partir(lineas, 92)) {
            if (y < 70) break;
            c.append("BT /F1 11 Tf 0.15 0.15 0.2 rg 50 ").append(y).append(" Td (").append(escapar(l)).append(") Tj ET\n");
            y -= 17;
        }
        c.append("0.8 0.8 0.85 RG 50 50 m 562 50 l S\n");
        c.append("BT /F1 8 Tf 0.5 0.5 0.55 rg 50 38 Td (")
                .append(escapar("Documento generado por SIGRE Global - evidencia de demostración")).append(") Tj ET\n");

        byte[] contenido = c.toString().getBytes(StandardCharsets.ISO_8859_1);
        List<String> objetos = new ArrayList<>();
        objetos.add("<< /Type /Catalog /Pages 2 0 R >>");
        objetos.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        objetos.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R "
                + "/Resources << /Font << /F1 5 0 R /F2 6 0 R >> >> >>");
        objetos.add(null); // flujo de contenido
        objetos.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        objetos.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        escribir(out, "%PDF-1.4\n%âãÏÓ\n");
        long[] posiciones = new long[objetos.size()];
        for (int i = 0; i < objetos.size(); i++) {
            posiciones[i] = out.size();
            escribir(out, (i + 1) + " 0 obj\n");
            if (objetos.get(i) == null) {
                escribir(out, "<< /Length " + contenido.length + " >>\nstream\n");
                out.writeBytes(contenido);
                escribir(out, "\nendstream");
            } else {
                escribir(out, objetos.get(i));
            }
            escribir(out, "\nendobj\n");
        }
        long xref = out.size();
        escribir(out, "xref\n0 " + (objetos.size() + 1) + "\n0000000000 65535 f \n");
        for (long p : posiciones) escribir(out, String.format("%010d 00000 n \n", p));
        escribir(out, "trailer\n<< /Size " + (objetos.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        return out.toByteArray();
    }

    private static List<String> partir(List<String> lineas, int ancho) {
        List<String> r = new ArrayList<>();
        for (String l : lineas) {
            String resto = l;
            while (resto.length() > ancho) {
                int corte = resto.lastIndexOf(' ', ancho);
                if (corte <= 0) corte = ancho;
                r.add(resto.substring(0, corte));
                resto = resto.substring(corte).trim();
            }
            r.add(resto);
        }
        return r;
    }

    private static void escribir(ByteArrayOutputStream out, String s) {
        out.writeBytes(s.getBytes(StandardCharsets.ISO_8859_1));
    }
}

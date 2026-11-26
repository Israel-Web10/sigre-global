package bo.umsa.sigre.reportes;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import bo.umsa.sigre.reportes.XlsxEscritor.Columna;

/**
 * Generador de reportes PDF tabulares sin dependencias (RF18): hoja horizontal A4, franja
 * corporativa, encabezado de columnas repetido en cada página, filas alternadas y pie con el
 * número de página. Usa las fuentes estándar Helvetica (codificación WinAnsi).
 */
public final class PdfTabla {

    private static final float ANCHO = 842f;
    private static final float ALTO = 595f;
    private static final float MARGEN = 32f;
    private static final float FILA = 15f;
    private static final float FUENTE = 7.4f;
    private static final DecimalFormat NUMERO = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(new Locale("es", "BO")));

    private PdfTabla() {
    }

    public static byte[] documento(String titulo, String subtitulo, List<Columna> columnas, List<Map<String, Object>> filas) {
        float util = ANCHO - 2 * MARGEN;
        int total = columnas.stream().mapToInt(Columna::ancho).sum();
        float[] anchos = new float[columnas.size()];
        for (int i = 0; i < anchos.length; i++) anchos[i] = util * columnas.get(i).ancho() / total;

        int porPagina = (int) ((ALTO - 150) / FILA);
        int paginas = Math.max(1, (filas.size() + porPagina - 1) / porPagina);
        List<String> contenidos = new ArrayList<>();
        for (int p = 0; p < paginas; p++) {
            StringBuilder c = new StringBuilder();
            // Franja superior con el degradado corporativo (dos bandas)
            c.append("0.03 0.07 0.16 rg 0 ").append(ALTO - 58).append(" ").append(ANCHO).append(" 58 re f\n");
            c.append("0.13 0.71 0.86 rg 0 ").append(ALTO - 61).append(" ").append(ANCHO * 0.55f).append(" 3 re f\n");
            c.append("0.39 0.40 0.95 rg ").append(ANCHO * 0.55f).append(" ").append(ALTO - 61).append(" ").append(ANCHO * 0.45f).append(" 3 re f\n");
            texto(c, "F2", 15, 1, 1, 1, MARGEN, ALTO - 30, "SIGRE Global");
            texto(c, "F1", 8.5f, 0.78f, 0.85f, 0.95f, MARGEN, ALTO - 45, "Sistema de gestión de riesgos empresariales globales");
            texto(c, "F2", 13, 0.07f, 0.09f, 0.15f, MARGEN, ALTO - 86, titulo);
            texto(c, "F1", 8, 0.35f, 0.38f, 0.45f, MARGEN, ALTO - 100, subtitulo);

            float y = ALTO - 128;
            // Encabezado de la tabla
            c.append("0.05 0.23 0.40 rg ").append(MARGEN).append(" ").append(y - 4).append(" ").append(util).append(" ").append(FILA + 2).append(" re f\n");
            float x = MARGEN;
            for (int i = 0; i < columnas.size(); i++) {
                texto(c, "F2", FUENTE, 1, 1, 1, x + 3, y + 1.5f, recortar(columnas.get(i).titulo(), anchos[i] - 6, FUENTE));
                x += anchos[i];
            }
            y -= FILA + 2;
            int desde = p * porPagina;
            int hasta = Math.min(filas.size(), desde + porPagina);
            for (int f = desde; f < hasta; f++) {
                if ((f - desde) % 2 == 1) {
                    c.append("0.95 0.96 0.98 rg ").append(MARGEN).append(" ").append(y - 4).append(" ").append(util).append(" ").append(FILA).append(" re f\n");
                }
                x = MARGEN;
                Map<String, Object> fila = filas.get(f);
                for (int i = 0; i < columnas.size(); i++) {
                    String v = valor(fila.get(columnas.get(i).clave()));
                    texto(c, "F1", FUENTE, 0.12f, 0.14f, 0.2f, x + 3, y + 1, recortar(v, anchos[i] - 6, FUENTE));
                    x += anchos[i];
                }
                y -= FILA;
            }
            if (filas.isEmpty()) texto(c, "F1", 9, 0.4f, 0.4f, 0.45f, MARGEN + 4, y, "Sin registros para el alcance seleccionado.");
            // Pie
            c.append("0.82 0.84 0.88 RG 0.5 w ").append(MARGEN).append(" 30 m ").append(ANCHO - MARGEN).append(" 30 l S\n");
            texto(c, "F1", 7, 0.45f, 0.47f, 0.52f, MARGEN, 18, filas.size() + " registros · Documento generado por SIGRE Global (RF18)");
            texto(c, "F1", 7, 0.45f, 0.47f, 0.52f, ANCHO - MARGEN - 70, 18, "Página " + (p + 1) + " de " + paginas);
            contenidos.add(c.toString());
        }
        return ensamblar(contenidos);
    }

    private static String valor(Object v) {
        if (v == null) return "";
        if (v instanceof BigDecimal b) return NUMERO.format(b);
        if (v instanceof Double d) return NUMERO.format(d);
        if (v instanceof Boolean b) return b ? "Sí" : "No";
        if (v instanceof List<?> l) return String.join(", ", l.stream().map(String::valueOf).toList());
        String t = String.valueOf(v);
        if (t.length() > 19 && t.charAt(10) == 'T') t = t.substring(0, 16).replace('T', ' ');
        return t.replace('\n', ' ');
    }

    /** Recorta el texto al ancho de la columna (ancho medio de Helvetica ≈ 0.52 em). */
    private static String recortar(String t, float ancho, float fuente) {
        int max = Math.max(3, (int) (ancho / (fuente * 0.52f)));
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }

    private static void texto(StringBuilder c, String fuente, float tam, float r, float g, float b, float x, float y, String t) {
        c.append("BT /").append(fuente).append(' ').append(tam).append(" Tf ").append(r).append(' ').append(g).append(' ').append(b)
         .append(" rg ").append(x).append(' ').append(y).append(" Td (").append(escapar(t)).append(") Tj ET\n");
    }

    private static String escapar(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch == '(' || ch == ')' || ch == '\\') b.append('\\').append(ch);
            else if (ch == '…') b.append("\\205");
            else if (ch == '·') b.append("\\267");
            else if (ch < 256) b.append(ch);
            else b.append('?');
        }
        return b.toString();
    }

    private static byte[] ensamblar(List<String> contenidos) {
        int n = contenidos.size();
        // Objetos: 1 catálogo, 2 páginas, 3 y 4 fuentes, luego (página, contenido) por cada hoja
        List<byte[]> objetos = new ArrayList<>();
        objetos.add(bytes("<< /Type /Catalog /Pages 2 0 R >>"));
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < n; i++) kids.append(5 + 2 * i).append(" 0 R ");
        objetos.add(bytes("<< /Type /Pages /Kids [" + kids + "] /Count " + n + " >>"));
        objetos.add(bytes("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"));
        objetos.add(bytes("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>"));
        for (int i = 0; i < n; i++) {
            objetos.add(bytes("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + (int) ANCHO + " " + (int) ALTO + "] /Contents " + (6 + 2 * i)
                    + " 0 R /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> >>"));
            byte[] flujo = contenidos.get(i).getBytes(StandardCharsets.ISO_8859_1);
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            o.writeBytes(bytes("<< /Length " + flujo.length + " >>\nstream\n"));
            o.writeBytes(flujo);
            o.writeBytes(bytes("\nendstream"));
            objetos.add(o.toByteArray());
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(bytes("%PDF-1.4\n%âãÏÓ\n"));
        long[] posiciones = new long[objetos.size()];
        for (int i = 0; i < objetos.size(); i++) {
            posiciones[i] = out.size();
            out.writeBytes(bytes((i + 1) + " 0 obj\n"));
            out.writeBytes(objetos.get(i));
            out.writeBytes(bytes("\nendobj\n"));
        }
        long xref = out.size();
        out.writeBytes(bytes("xref\n0 " + (objetos.size() + 1) + "\n0000000000 65535 f \n"));
        for (long p : posiciones) out.writeBytes(bytes(String.format("%010d 00000 n \n", p)));
        out.writeBytes(bytes("trailer\n<< /Size " + (objetos.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n"));
        return out.toByteArray();
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }
}

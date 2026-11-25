package bo.umsa.sigre.reportes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Escritor mínimo de libros Excel (Office Open XML, .xlsx) sin dependencias: una hoja con
 * título, encabezados con estilo, filtros, columnas ajustadas y la primera fila fija.
 */
public final class XlsxEscritor {

    public record Columna(String clave, String titulo, int ancho) { }

    private XlsxEscritor() {
    }

    public static byte[] libro(String hoja, String titulo, String subtitulo, List<Columna> columnas, List<Map<String, Object>> filas) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(bytes)) {
            poner(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                    <Default Extension="xml" ContentType="application/xml"/>
                    <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                    <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                    <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                    <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
                    </Types>""");
            poner(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                    <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
                    </Relationships>""");
            poner(zip, "docProps/core.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
                     xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>%s</dc:title><dc:creator>SIGRE Global</dc:creator></cp:coreProperties>"""
                    .formatted(esc(titulo)));
            poner(zip, "xl/workbook.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                     xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                    <sheets><sheet name="%s" sheetId="1" r:id="rId1"/></sheets>
                    <definedNames><definedName name="_xlnm._FilterDatabase" localSheetId="0" hidden="1">'%s'!$A$4:$%s$%d</definedName></definedNames>
                    </workbook>""".formatted(esc(hoja), esc(hoja), letra(columnas.size() - 1), filas.size() + 4));
            poner(zip, "xl/_rels/workbook.xml.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                    <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                    </Relationships>""");
            poner(zip, "xl/styles.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                    <numFmts count="1"><numFmt numFmtId="164" formatCode="#,##0.00"/></numFmts>
                    <fonts count="4">
                     <font><sz val="10"/><name val="Calibri"/></font>
                     <font><b/><sz val="10"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
                     <font><b/><sz val="15"/><color rgb="FF0D3B66"/><name val="Calibri"/></font>
                     <font><i/><sz val="10"/><color rgb="FF5B6475"/><name val="Calibri"/></font>
                    </fonts>
                    <fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>
                     <fill><patternFill patternType="solid"><fgColor rgb="FF0D3B66"/><bgColor indexed="64"/></patternFill></fill></fills>
                    <borders count="2"><border/><border><bottom style="thin"><color rgb="FFD5DAE3"/></bottom></border></borders>
                    <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
                    <cellXfs count="6">
                     <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
                     <xf numFmtId="0" fontId="1" fillId="2" borderId="0" applyFont="1" applyFill="1"/>
                     <xf numFmtId="0" fontId="2" fillId="0" borderId="0" applyFont="1"/>
                     <xf numFmtId="0" fontId="3" fillId="0" borderId="0" applyFont="1"/>
                     <xf numFmtId="0" fontId="0" fillId="0" borderId="1" applyBorder="1"/>
                     <xf numFmtId="164" fontId="0" fillId="0" borderId="1" applyNumberFormat="1" applyBorder="1"/>
                    </cellXfs>
                    </styleSheet>""");
            poner(zip, "xl/worksheets/sheet1.xml", hojaXml(titulo, subtitulo, columnas, filas));
            zip.finish();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar el libro", e);
        }
    }

    private static String hojaXml(String titulo, String subtitulo, List<Columna> columnas, List<Map<String, Object>> filas) {
        StringBuilder s = new StringBuilder(4096 + filas.size() * 200);
        s.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
         .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
         .append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"4\" topLeftCell=\"A5\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
         .append("<cols>");
        for (int i = 0; i < columnas.size(); i++) {
            s.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1).append("\" width=\"").append(columnas.get(i).ancho())
             .append("\" customWidth=\"1\"/>");
        }
        s.append("</cols><sheetData>");
        s.append("<row r=\"1\" ht=\"22\" customHeight=\"1\">").append(texto("A1", titulo, 2)).append("</row>");
        s.append("<row r=\"2\">").append(texto("A2", subtitulo, 3)).append("</row>");
        s.append("<row r=\"4\" ht=\"18\" customHeight=\"1\">");
        for (int i = 0; i < columnas.size(); i++) s.append(texto(letra(i) + "4", columnas.get(i).titulo(), 1));
        s.append("</row>");
        int r = 5;
        for (Map<String, Object> f : filas) {
            s.append("<row r=\"").append(r).append("\">");
            for (int i = 0; i < columnas.size(); i++) {
                Object v = f.get(columnas.get(i).clave());
                String ref = letra(i) + r;
                if (v == null) continue;
                if (v instanceof Number n) {
                    s.append("<c r=\"").append(ref).append("\" s=\"").append(v instanceof java.math.BigDecimal || v instanceof Double ? 5 : 4)
                     .append("\"><v>").append(n).append("</v></c>");
                } else if (v instanceof Boolean b) {
                    s.append(texto(ref, b ? "Sí" : "No", 4));
                } else if (v instanceof List<?> l) {
                    s.append(texto(ref, String.join(", ", l.stream().map(String::valueOf).toList()), 4));
                } else {
                    String t = String.valueOf(v);
                    if (t.length() > 19 && t.charAt(10) == 'T') t = t.substring(0, 16).replace('T', ' ');
                    s.append(texto(ref, t, 4));
                }
            }
            s.append("</row>");
            r++;
        }
        s.append("</sheetData>");
        s.append("<autoFilter ref=\"A4:").append(letra(columnas.size() - 1)).append(Math.max(4, r - 1)).append("\"/>");
        s.append("<pageMargins left=\"0.5\" right=\"0.5\" top=\"0.6\" bottom=\"0.6\" header=\"0.3\" footer=\"0.3\"/>");
        s.append("<pageSetup orientation=\"landscape\" fitToWidth=\"1\"/>");
        s.append("</worksheet>");
        return s.toString();
    }

    private static String texto(String ref, String valor, int estilo) {
        return "<c r=\"" + ref + "\" t=\"inlineStr\" s=\"" + estilo + "\"><is><t xml:space=\"preserve\">" + esc(valor) + "</t></is></c>";
    }

    static String letra(int i) {
        StringBuilder b = new StringBuilder();
        int n = i + 1;
        while (n > 0) {
            int m = (n - 1) % 26;
            b.insert(0, (char) ('A' + m));
            n = (n - 1) / 26;
        }
        return b.toString();
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '"' -> b.append("&quot;");
                default -> {
                    if (c >= 0x20 || c == '\t' || c == '\n' || c == '\r') b.append(c);
                }
            }
        }
        return b.toString();
    }

    private static void poner(ZipOutputStream zip, String nombre, String contenido) throws IOException {
        zip.putNextEntry(new ZipEntry(nombre));
        zip.write(contenido.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}

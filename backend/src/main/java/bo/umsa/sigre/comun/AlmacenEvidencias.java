package bo.umsa.sigre.comun;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Guarda los archivos de evidencia fuera de la base de datos (sección 10.2: almacenamiento
 * de objetos). En la base solo queda la ruta, el tipo y el tamaño.
 */
@Component
public class AlmacenEvidencias {

    private static final Set<String> PERMITIDAS = Set.of("pdf", "png", "jpg", "jpeg", "webp", "gif", "docx", "xlsx",
            "pptx", "txt", "csv", "zip", "mp4");

    private final Path raiz;

    public AlmacenEvidencias(@Value("${sigre.evidencias.carpeta}") String carpeta) throws IOException {
        this.raiz = Path.of(carpeta).toAbsolutePath().normalize();
        Files.createDirectories(raiz);
    }

    public static String limpiarNombre(String nombre) {
        String n = nombre == null ? "archivo" : Path.of(nombre).getFileName().toString();
        n = Normalizer.normalize(n, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        n = n.replaceAll("[^A-Za-z0-9._-]", "_");
        if (n.length() > 120) n = n.substring(n.length() - 120);
        return n.isBlank() ? "archivo" : n;
    }

    private static final java.util.Map<String, String> TIPOS = java.util.Map.ofEntries(
            java.util.Map.entry("pdf", "application/pdf"), java.util.Map.entry("png", "image/png"),
            java.util.Map.entry("jpg", "image/jpeg"), java.util.Map.entry("jpeg", "image/jpeg"),
            java.util.Map.entry("webp", "image/webp"), java.util.Map.entry("gif", "image/gif"),
            java.util.Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            java.util.Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            java.util.Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            java.util.Map.entry("txt", "text/plain"), java.util.Map.entry("csv", "text/csv"),
            java.util.Map.entry("zip", "application/zip"), java.util.Map.entry("mp4", "video/mp4"));

    /** Tipos que el navegador puede mostrar sin riesgo de ejecutar código. */
    private static final Set<String> SEGUROS_EN_LINEA = Set.of("pdf", "png", "jpg", "jpeg", "webp", "gif", "mp4");

    /** Tipo MIME según la extensión validada (nunca el que declara el navegador que sube el archivo). */
    public static String tipoMime(String nombre) {
        return TIPOS.getOrDefault(extension(nombre), "application/octet-stream");
    }

    public static boolean seguroEnLinea(String nombre) {
        return SEGUROS_EN_LINEA.contains(extension(nombre));
    }

    /** Comprueba la firma del archivo (primeros bytes) según su extensión. */
    static boolean firmaValida(String ext, byte[] b) {
        java.util.function.BiPredicate<Integer, int[]> empieza = (desde, firma) -> {
            if (b.length < desde + firma.length) return false;
            for (int i = 0; i < firma.length; i++) if ((b[desde + i] & 0xFF) != firma[i]) return false;
            return true;
        };
        return switch (ext) {
            case "pdf" -> empieza.test(0, new int[]{0x25, 0x50, 0x44, 0x46});
            case "png" -> empieza.test(0, new int[]{0x89, 0x50, 0x4E, 0x47});
            case "jpg", "jpeg" -> empieza.test(0, new int[]{0xFF, 0xD8, 0xFF});
            case "gif" -> empieza.test(0, new int[]{0x47, 0x49, 0x46, 0x38});
            case "webp" -> empieza.test(0, new int[]{0x52, 0x49, 0x46, 0x46}) && empieza.test(8, new int[]{0x57, 0x45, 0x42, 0x50});
            case "docx", "xlsx", "pptx", "zip" -> empieza.test(0, new int[]{0x50, 0x4B});
            case "mp4" -> empieza.test(4, new int[]{0x66, 0x74, 0x79, 0x70});
            default -> true; // txt y csv: texto plano, se sirven siempre como descarga
        };
    }

    public static String extension(String nombre) {
        int i = nombre.lastIndexOf('.');
        return i < 0 ? "" : nombre.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    /** Valida y guarda el archivo; devuelve la ruta relativa a la carpeta de evidencias. */
    public String guardar(MultipartFile archivo, String subcarpeta) {
        if (archivo == null || archivo.isEmpty()) throw new ReglaNegocioException("CU05: Adjunte un archivo de evidencia");
        String nombre = limpiarNombre(archivo.getOriginalFilename());
        if (!PERMITIDAS.contains(extension(nombre))) {
            throw new ReglaNegocioException("RNF02: Tipo de archivo no permitido (" + extension(nombre) + "). Use PDF, imagen u Office");
        }
        try (InputStream in = archivo.getInputStream()) {
            byte[] inicio = archivo.getInputStream().readNBytes(16);
            if (!firmaValida(extension(nombre), inicio)) {
                throw new ReglaNegocioException("RNF02: El contenido del archivo no corresponde a su extensión (" + extension(nombre) + ")");
            }
            return guardar(in, subcarpeta, nombre);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo guardar la evidencia", e);
        }
    }

    public String guardar(InputStream contenido, String subcarpeta, String nombre) throws IOException {
        Path carpeta = raiz.resolve(subcarpeta).normalize();
        if (!carpeta.startsWith(raiz)) throw new IllegalArgumentException("Ruta no válida");
        Files.createDirectories(carpeta);
        Path destino = carpeta.resolve(UUID.randomUUID().toString().substring(0, 8) + "_" + limpiarNombre(nombre));
        Files.copy(contenido, destino, StandardCopyOption.REPLACE_EXISTING);
        return raiz.relativize(destino).toString().replace('\\', '/');
    }

    public Path abrir(String rutaRelativa) {
        if (rutaRelativa == null) throw ReglaNegocioException.noEncontrado("El archivo de la evidencia");
        Path p = raiz.resolve(rutaRelativa).normalize();
        if (!p.startsWith(raiz) || !Files.exists(p)) throw ReglaNegocioException.noEncontrado("El archivo de la evidencia");
        return p;
    }
}

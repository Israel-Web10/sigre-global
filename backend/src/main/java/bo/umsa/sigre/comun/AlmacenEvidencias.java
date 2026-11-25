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

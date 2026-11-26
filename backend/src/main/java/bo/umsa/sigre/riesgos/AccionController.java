package bo.umsa.sigre.riesgos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import bo.umsa.sigre.seguridad.UsuarioSesion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Acciones de mitigación", description = "CU05: avance de acciones y evidencias")
public class AccionController {

    private final ServicioAcciones acciones;

    public AccionController(ServicioAcciones acciones) {
        this.acciones = acciones;
    }

    public record Avance(Integer avance, String comentario) { }

    @GetMapping("/api/acciones/mias")
    @Operation(summary = "Acciones asignadas al usuario")
    public List<Map<String, Object>> mias() {
        return acciones.misAcciones(UsuarioSesion.actual());
    }

    @PostMapping("/api/acciones/{id}/avance")
    @Operation(summary = "CU05: registrar el avance (no disminuye; al 100 % exige evidencia)")
    public Map<String, Object> avance(@PathVariable int id, @RequestBody Avance a) {
        return acciones.registrarAvance(UsuarioSesion.actual(), id, a.avance() == null ? 0 : a.avance(), a.comentario());
    }

    @PostMapping(value = "/api/acciones/{id}/evidencias", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "CU05: adjuntar una evidencia a la acción")
    public Map<String, Object> evidencia(@PathVariable int id, @RequestParam("archivo") MultipartFile archivo,
                                         @RequestParam(value = "descripcion", required = false) String descripcion) {
        return acciones.cargarEvidencia(UsuarioSesion.actual(), id, archivo, descripcion);
    }

    @GetMapping("/api/evidencias/{id}/archivo")
    @Operation(summary = "Descargar o ver una evidencia")
    public ResponseEntity<Resource> archivo(@PathVariable int id, @RequestParam(defaultValue = "false") boolean descargar)
            throws IOException {
        var par = acciones.archivo(UsuarioSesion.actual(), id);
        Path ruta = par.getValue();
        String nombre = par.getKey().getArchivo();
        // El tipo se deduce de la extensión validada; solo PDF, imágenes y video se muestran en el navegador
        String tipo = bo.umsa.sigre.comun.AlmacenEvidencias.tipoMime(nombre);
        boolean enLinea = !descargar && bo.umsa.sigre.comun.AlmacenEvidencias.seguroEnLinea(nombre);
        ContentDisposition disp = (enLinea ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(nombre, java.nio.charset.StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(tipo))
                .header(HttpHeaders.CONTENT_DISPOSITION, disp.toString())
                .header("Content-Security-Policy", "sandbox; default-src 'none'; img-src 'self' data:; media-src 'self'; style-src 'unsafe-inline'")
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(ruta));
    }
}

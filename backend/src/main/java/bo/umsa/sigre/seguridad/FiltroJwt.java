package bo.umsa.sigre.seguridad;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Lee el token JWT de la cabecera Authorization (o del parámetro "token" en las
 * descargas y en el canal de eventos en tiempo real) y autentica la petición.
 */
public class FiltroJwt extends OncePerRequestFilter {

    private final JwtService jwt;

    public FiltroJwt(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain cadena)
            throws ServletException, IOException {
        try {
            String token = null;
            String cabecera = req.getHeader("Authorization");
            if (cabecera != null && cabecera.startsWith("Bearer ")) token = cabecera.substring(7);
            else if ("GET".equals(req.getMethod())) token = req.getParameter("token");

            if (token != null) {
                Map<String, Object> d = jwt.verificar(token);
                if (d != null && "acceso".equals(d.get("tipo"))) {
                    UsuarioSesion u = desdeToken(d);
                    var autoridades = u.roles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, autoridades));
                    ContextoUsuario.establecer(u.id());
                }
            }
            cadena.doFilter(req, res);
        } finally {
            ContextoUsuario.limpiar();
            SecurityContextHolder.clearContext();
        }
    }

    @SuppressWarnings("unchecked")
    static UsuarioSesion desdeToken(Map<String, Object> d) {
        Set<String> roles = new LinkedHashSet<>((Collection<String>) d.getOrDefault("roles", List.of()));
        return new UsuarioSesion(
                ((Number) d.get("sub")).intValue(), (String) d.get("login"), (String) d.get("nombre"), (String) d.get("cargo"),
                ((Number) d.get("idFilial")).intValue(), (String) d.get("filial"), (String) d.get("filialCodigo"),
                roles, Boolean.TRUE.equals(d.get("corporativo")), (String) d.getOrDefault("idioma", "es"));
    }
}

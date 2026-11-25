package bo.umsa.sigre.comun;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Sirve la aplicación web (PWA) compilada en /static. Las rutas del cliente
 * (/riesgos, /tablero, /movil...) devuelven index.html para que las resuelva el enrutador.
 */
@Configuration
public class ConfiguracionWeb implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registro) {
        registro.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(java.time.Duration.ofDays(30)).cachePublic());
        registro.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noCache())
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String ruta, Resource ubicacion) throws IOException {
                        Resource r = ubicacion.createRelative(ruta);
                        if (r.exists() && r.isReadable()) return r;
                        if (ruta.startsWith("api") || ruta.startsWith("actuator") || ruta.contains(".")) return null;
                        Resource indice = new ClassPathResource("/static/index.html");
                        return indice.exists() ? indice : null;
                    }
                });
    }
}

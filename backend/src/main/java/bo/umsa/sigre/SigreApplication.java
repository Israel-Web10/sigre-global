package bo.umsa.sigre;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SIGRE Global - Sistema Integrado de Gestión de Riesgos Empresariales Globales.
 *
 * <p>Capa de aplicación de la arquitectura en capas (D19): API REST con JSON,
 * documentada con OpenAPI. Los paquetes siguen el diagrama de paquetes (D12):
 * seguridad, riesgos, monitoreo, auditorias, cumplimiento, reportes,
 * integraciones y administracion.</p>
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
@EnableAsync
public class SigreApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("America/La_Paz"));
        SpringApplication.run(SigreApplication.class, args);
    }
}

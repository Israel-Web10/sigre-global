package bo.umsa.sigre.seguridad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Seguridad de la API (sección 10.5): sesiones sin estado con JWT, autorización por rol
 * y por filial, claves con hash Argon2id.
 */
@Configuration
@EnableMethodSecurity
public class ConfiguracionSeguridad {

    @Bean
    SecurityFilterChain cadenaSeguridad(HttpSecurity http, JwtService jwt) throws Exception {
        http.csrf(c -> c.disable())
            .httpBasic(b -> b.disable())
            .formLogin(f -> f.disable())
            .logout(l -> l.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(401);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"estado\":401,\"tipo\":\"Sesión\",\"mensaje\":\"Sesión no válida o expirada. Inicie sesión nuevamente.\"}");
                })
                .accessDeniedHandler((req, res, ex) -> {
                    res.setStatus(403);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"estado\":403,\"tipo\":\"Acceso denegado\",\"mensaje\":\"Su rol no tiene permiso para esta operación.\"}");
                }))
            .authorizeHttpRequests(a -> a
                .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ASYNC, jakarta.servlet.DispatcherType.ERROR).permitAll()
                .requestMatchers("/api/auth/login", "/api/auth/2fa", "/api/auth/demo-usuarios", "/api/sistema/info").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
            .headers(h -> h.frameOptions(f -> f.sameOrigin()))
            .addFilterBefore(new FiltroJwt(jwt), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder codificadorClaves() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}

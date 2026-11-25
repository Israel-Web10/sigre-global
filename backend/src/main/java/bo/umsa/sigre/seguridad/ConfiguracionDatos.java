package bo.umsa.sigre.seguridad;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Cada vez que la aplicación toma una conexión del pool, publica el usuario de la
 * petición en la variable de sesión app.id_usuario. Los triggers fn_auditar y las
 * reglas de negocio la leen con fn_usuario_actual() (bitácora, RN13).
 */
@Configuration
public class ConfiguracionDatos {

    @Bean
    static BeanPostProcessor contextoUsuarioEnConexiones() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String nombre) {
                if ("dataSource".equals(nombre) && bean instanceof DataSource ds && !(bean instanceof DataSourceConContexto)) {
                    return new DataSourceConContexto(ds);
                }
                return bean;
            }
        };
    }

    static class DataSourceConContexto extends DelegatingDataSource {

        DataSourceConContexto(DataSource destino) {
            super(destino);
        }

        @Override
        public Connection getConnection() throws SQLException {
            return publicar(super.getConnection());
        }

        @Override
        public Connection getConnection(String usuario, String clave) throws SQLException {
            return publicar(super.getConnection(usuario, clave));
        }

        private Connection publicar(Connection c) throws SQLException {
            Integer id = ContextoUsuario.actual();
            try (PreparedStatement ps = c.prepareStatement("select set_config('app.id_usuario', ?, false)")) {
                ps.setString(1, id == null ? "" : id.toString());
                ps.execute();
            }
            return c;
        }
    }
}

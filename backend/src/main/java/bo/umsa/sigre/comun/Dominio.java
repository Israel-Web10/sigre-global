package bo.umsa.sigre.comun;

import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Acceso a las entidades del dominio (JPA/Hibernate). Después de guardar se relee la fila
 * porque los triggers completan columnas (código, nivel, vigencia, semáforo, SLA...).
 */
@Component
public class Dominio {

    @PersistenceContext
    private EntityManager em;

    public <T> T buscar(Class<T> clase, Object id, String nombre) {
        T t = id == null ? null : em.find(clase, id);
        if (t == null) throw ReglaNegocioException.noEncontrado(nombre);
        return t;
    }

    /** Inserta la entidad, ejecuta los triggers y trae los valores calculados por la base de datos. */
    public <T> T guardar(T entidad) {
        em.persist(entidad);
        em.flush();
        em.refresh(entidad);
        return entidad;
    }

    /** Envía los cambios pendientes y relee la entidad. */
    public <T> T sincronizar(T entidad) {
        em.flush();
        em.refresh(entidad);
        return entidad;
    }

    public void eliminar(Object entidad) {
        em.remove(entidad);
        em.flush();
    }

    public EntityManager em() {
        return em;
    }
}

package bo.umsa.sigre.monitoreo;

import java.util.Set;

/** Estados de la alerta y sus transiciones (diagrama de estados D09). */
public enum EstadoAlerta {
    GENERADA, NOTIFICADA, EN_ATENCION, ESCALADA, RESUELTA, CERRADA;

    public boolean puedePasarA(EstadoAlerta destino) {
        return switch (this) {
            case GENERADA -> destino == NOTIFICADA;
            case NOTIFICADA -> Set.of(EN_ATENCION, ESCALADA).contains(destino);
            case EN_ATENCION -> Set.of(ESCALADA, RESUELTA).contains(destino);
            case ESCALADA -> destino == EN_ATENCION;
            case RESUELTA -> Set.of(CERRADA, EN_ATENCION).contains(destino);
            case CERRADA -> false;
        };
    }

    public boolean abierta() {
        return this != RESUELTA && this != CERRADA;
    }
}

package bo.umsa.sigre.riesgos;

/** Estados del riesgo y sus transiciones permitidas (diagrama de estados D08). */
public enum EstadoRiesgo {
    IDENTIFICADO, EVALUADO, EN_TRATAMIENTO, MITIGADO, ACEPTADO, CERRADO;

    public boolean puedePasarA(EstadoRiesgo destino) {
        if (destino == this) return true;
        return switch (this) {
            case IDENTIFICADO -> destino == EVALUADO;                                   // CU02
            case EVALUADO -> destino == EN_TRATAMIENTO || destino == ACEPTADO;          // CU04, RN12
            case EN_TRATAMIENTO -> destino == MITIGADO;                                 // CU05, RN11
            case MITIGADO -> destino == EVALUADO || destino == CERRADO;                 // reevaluación o cierre
            case ACEPTADO -> destino == CERRADO;
            case CERRADO -> false;
        };
    }
}

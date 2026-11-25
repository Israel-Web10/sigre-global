package bo.umsa.sigre.riesgos;

/** Nivel del riesgo según la matriz de probabilidad por impacto (RN02). */
public enum NivelRiesgo {
    BAJO, MEDIO, ALTO, CRITICO;

    /** RN02: 1-4 Bajo, 5-9 Medio, 10-14 Alto, 15-25 Crítico. */
    public static NivelRiesgo desde(int valor) {
        if (valor <= 4) return BAJO;
        if (valor <= 9) return MEDIO;
        if (valor <= 14) return ALTO;
        return CRITICO;
    }

    public boolean altoOCritico() {
        return this == ALTO || this == CRITICO;
    }
}

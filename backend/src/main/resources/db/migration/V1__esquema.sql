-- =====================================================================================
-- SIGRE Global - V1: esquema físico (PostgreSQL 16+)
-- Implementa el modelo relacional de la sección 7.11 (D18) y el diseño físico de la 10.2.
-- Tablas del informe: 21. Tablas de soporte: parámetros, datos del ERP, notificaciones e
-- incidencias de integración.
-- =====================================================================================

CREATE TYPE estado_validacion AS ENUM ('NO_REQUERIDA', 'PENDIENTE', 'VALIDADA', 'OBSERVADA');

-- ------------------------------------------------------------------ Seguridad
CREATE TABLE filial (
    id_filial      SERIAL PRIMARY KEY,
    codigo         VARCHAR(10)  NOT NULL UNIQUE,
    nombre         VARCHAR(120) NOT NULL,
    pais           VARCHAR(60)  NOT NULL,
    codigo_pais    CHAR(2)      NOT NULL,
    ciudad         VARCHAR(60)  NOT NULL,
    region         VARCHAR(40)  NOT NULL,
    moneda         CHAR(3)      NOT NULL CHECK (moneda ~ '^[A-Z]{3}$'),
    apetito_riesgo SMALLINT     NOT NULL CHECK (apetito_riesgo BETWEEN 1 AND 25),
    latitud        NUMERIC(9,5) NOT NULL,
    longitud       NUMERIC(9,5) NOT NULL,
    zona_horaria   VARCHAR(40)  NOT NULL,
    casa_matriz    BOOLEAN      NOT NULL DEFAULT FALSE,
    activa         BOOLEAN      NOT NULL DEFAULT TRUE
);
COMMENT ON TABLE filial IS 'Unidad de la empresa en un país, con su moneda y su apetito de riesgo (RN11).';

CREATE TABLE rol (
    id_rol      SERIAL PRIMARY KEY,
    codigo      VARCHAR(30) NOT NULL UNIQUE,
    nombre      VARCHAR(60) NOT NULL UNIQUE,
    corporativo BOOLEAN     NOT NULL DEFAULT FALSE,
    descripcion TEXT
);
COMMENT ON COLUMN rol.corporativo IS 'Los roles corporativos ven todas las filiales (RN14).';

CREATE TABLE usuario (
    id_usuario      SERIAL PRIMARY KEY,
    id_filial       INT          NOT NULL REFERENCES filial (id_filial),
    id_superior     INT          REFERENCES usuario (id_usuario),
    nombre_completo VARCHAR(120) NOT NULL,
    login           VARCHAR(40)  NOT NULL UNIQUE CHECK (login = lower(login)),
    email           VARCHAR(120) NOT NULL UNIQUE,
    cargo           VARCHAR(100),
    clave_hash      VARCHAR(255),
    idioma          CHAR(2)      NOT NULL DEFAULT 'es' CHECK (idioma IN ('es', 'en', 'pt')),
    activo          BOOLEAN      NOT NULL DEFAULT TRUE,
    totp_secreto    VARCHAR(64),
    totp_activo     BOOLEAN      NOT NULL DEFAULT FALSE,
    ultimo_acceso   TIMESTAMPTZ,
    creado_en       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (id_superior IS DISTINCT FROM id_usuario)
);

CREATE TABLE usuario_rol (
    id_usuario INT NOT NULL REFERENCES usuario (id_usuario) ON DELETE CASCADE,
    id_rol     INT NOT NULL REFERENCES rol (id_rol),
    PRIMARY KEY (id_usuario, id_rol)
);

-- ------------------------------------------------------------------ Riesgos
CREATE SEQUENCE seq_riesgo_fin;
CREATE SEQUENCE seq_riesgo_tec;
CREATE SEQUENCE seq_riesgo_leg;
CREATE SEQUENCE seq_riesgo_ope;
CREATE SEQUENCE seq_riesgo_rep;

CREATE TABLE riesgo (
    id_riesgo      SERIAL PRIMARY KEY,
    id_filial      INT          NOT NULL REFERENCES filial (id_filial),
    id_responsable INT          NOT NULL REFERENCES usuario (id_usuario),
    id_registrador INT          REFERENCES usuario (id_usuario),
    codigo         VARCHAR(20)  NOT NULL UNIQUE CHECK (codigo ~ '^RSK-[A-Z]{3}-[0-9]{4}$'),
    titulo         VARCHAR(200) NOT NULL CHECK (length(trim(titulo)) >= 5),
    descripcion    TEXT         NOT NULL,
    categoria      VARCHAR(15)  NOT NULL CHECK (categoria IN ('FINANCIERO', 'TECNOLOGICO', 'LEGAL', 'OPERATIVO', 'REPUTACIONAL')),
    proceso        VARCHAR(100) NOT NULL,
    causa          TEXT         NOT NULL,
    consecuencia   TEXT         NOT NULL,
    fecha_registro DATE         NOT NULL DEFAULT CURRENT_DATE,
    estado         VARCHAR(15)  NOT NULL DEFAULT 'IDENTIFICADO'
                   CHECK (estado IN ('IDENTIFICADO', 'EVALUADO', 'EN_TRATAMIENTO', 'MITIGADO', 'ACEPTADO', 'CERRADO')),
    origen         VARCHAR(15)  NOT NULL DEFAULT 'MANUAL' CHECK (origen IN ('MANUAL', 'HALLAZGO', 'CUMPLIMIENTO')),
    actualizado_en TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- RN01 y CU01: un título no se repite dentro de la misma filial
CREATE UNIQUE INDEX ux_riesgo_titulo_filial ON riesgo (id_filial, lower(titulo));
CREATE INDEX idx_riesgo_filial_estado ON riesgo (id_filial, estado);

CREATE TABLE evaluacion_riesgo (
    id_evaluacion      SERIAL PRIMARY KEY,
    id_riesgo          INT           NOT NULL REFERENCES riesgo (id_riesgo) ON DELETE CASCADE,
    id_evaluador       INT           NOT NULL REFERENCES usuario (id_usuario),
    fecha              DATE          NOT NULL DEFAULT CURRENT_DATE,
    tipo               VARCHAR(10)   NOT NULL CHECK (tipo IN ('INHERENTE', 'RESIDUAL')),
    probabilidad       SMALLINT      NOT NULL CHECK (probabilidad BETWEEN 1 AND 5),
    impacto            SMALLINT      NOT NULL CHECK (impacto BETWEEN 1 AND 5),
    -- RN02: nivel = probabilidad × impacto, calculado por la base de datos
    valor              SMALLINT      GENERATED ALWAYS AS (probabilidad * impacto) STORED,
    nivel              VARCHAR(8)    GENERATED ALWAYS AS (CASE
                           WHEN probabilidad * impacto < 5  THEN 'BAJO'
                           WHEN probabilidad * impacto < 10 THEN 'MEDIO'
                           WHEN probabilidad * impacto < 15 THEN 'ALTO'
                           ELSE 'CRITICO' END) STORED,
    impacto_financiero NUMERIC(16,2) CHECK (impacto_financiero >= 0),
    moneda             CHAR(3),
    tipo_cambio        NUMERIC(12,6) CHECK (tipo_cambio > 0),
    impacto_usd        NUMERIC(16,2) CHECK (impacto_usd >= 0),
    vigente_hasta      DATE          NOT NULL,
    estado_validacion  estado_validacion NOT NULL DEFAULT 'NO_REQUERIDA',
    comentario         TEXT
);
CREATE INDEX idx_eval_riesgo_vig ON evaluacion_riesgo (id_riesgo, vigente_hasta DESC);
CREATE INDEX idx_eval_validacion ON evaluacion_riesgo (estado_validacion) WHERE estado_validacion = 'PENDIENTE';

CREATE TABLE control (
    id_control  SERIAL PRIMARY KEY,
    id_riesgo   INT          NOT NULL REFERENCES riesgo (id_riesgo) ON DELETE CASCADE,
    descripcion VARCHAR(300) NOT NULL,
    tipo        VARCHAR(15)  NOT NULL CHECK (tipo IN ('PREVENTIVO', 'DETECTIVO', 'CORRECTIVO')),
    efectividad SMALLINT     NOT NULL CHECK (efectividad BETWEEN 1 AND 5)
);

CREATE TABLE plan_mitigacion (
    id_plan          SERIAL PRIMARY KEY,
    id_riesgo        INT           NOT NULL UNIQUE REFERENCES riesgo (id_riesgo) ON DELETE CASCADE,
    id_proponente    INT           REFERENCES usuario (id_usuario),
    id_aprobador     INT           REFERENCES usuario (id_usuario),
    estrategia       VARCHAR(10)   NOT NULL CHECK (estrategia IN ('EVITAR', 'REDUCIR', 'TRANSFERIR', 'ACEPTAR')),
    costo_estimado   NUMERIC(16,2) NOT NULL DEFAULT 0 CHECK (costo_estimado >= 0),
    fecha_inicio     DATE          NOT NULL DEFAULT CURRENT_DATE,
    fecha_fin        DATE,
    estado           VARCHAR(10)   NOT NULL DEFAULT 'BORRADOR' CHECK (estado IN ('BORRADOR', 'PROPUESTO', 'APROBADO', 'CERRADO')),
    justificacion    TEXT,
    motivo_rechazo   TEXT,
    fecha_propuesta  TIMESTAMPTZ,
    fecha_aprobacion TIMESTAMPTZ
);

CREATE TABLE accion_mitigacion (
    id_accion      SERIAL PRIMARY KEY,
    id_plan        INT           NOT NULL REFERENCES plan_mitigacion (id_plan) ON DELETE CASCADE,
    id_responsable INT           NOT NULL REFERENCES usuario (id_usuario),
    descripcion    VARCHAR(300)  NOT NULL,
    fecha_limite   DATE          NOT NULL,
    costo          NUMERIC(16,2) NOT NULL DEFAULT 0 CHECK (costo >= 0),
    avance         SMALLINT      NOT NULL DEFAULT 0 CHECK (avance BETWEEN 0 AND 100),
    estado         VARCHAR(10)   NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE', 'EN_CURSO', 'CERRADA')),
    comentario     TEXT,
    fecha_cierre   TIMESTAMPTZ
);

-- ------------------------------------------------------------------ Monitoreo
CREATE TABLE indicador_kri (
    id_kri          SERIAL PRIMARY KEY,
    id_riesgo       INT           NOT NULL REFERENCES riesgo (id_riesgo) ON DELETE CASCADE,
    nombre          VARCHAR(120)  NOT NULL,
    unidad          VARCHAR(20)   NOT NULL,
    umbral_amarillo NUMERIC(18,4) NOT NULL,
    umbral_rojo     NUMERIC(18,4) NOT NULL,
    -- MAYOR: alerta cuando el valor supera el umbral; MENOR: cuando baja del umbral
    direccion       VARCHAR(5)    NOT NULL DEFAULT 'MAYOR' CHECK (direccion IN ('MAYOR', 'MENOR')),
    frecuencia      VARCHAR(10)   NOT NULL CHECK (frecuencia IN ('DIARIA', 'SEMANAL', 'MENSUAL')),
    fuente          VARCHAR(10)   NOT NULL CHECK (fuente IN ('MANUAL', 'ERP', 'SIEM')),
    financiero      BOOLEAN       NOT NULL DEFAULT FALSE,
    activo          BOOLEAN       NOT NULL DEFAULT TRUE,
    CHECK ((direccion = 'MAYOR' AND umbral_rojo > umbral_amarillo) OR (direccion = 'MENOR' AND umbral_rojo < umbral_amarillo))
);

CREATE TABLE medicion_kri (
    id_medicion BIGSERIAL PRIMARY KEY,
    id_kri      INT           NOT NULL REFERENCES indicador_kri (id_kri) ON DELETE CASCADE,
    fecha       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    valor       NUMERIC(18,4) NOT NULL,
    origen      VARCHAR(10)   NOT NULL CHECK (origen IN ('MANUAL', 'ERP', 'SIEM')),
    semaforo    VARCHAR(8)    NOT NULL DEFAULT 'VERDE' CHECK (semaforo IN ('VERDE', 'AMARILLO', 'ROJO')),
    id_usuario  INT           REFERENCES usuario (id_usuario)
);
CREATE INDEX idx_medicion_kri ON medicion_kri (id_kri, fecha DESC);

-- ------------------------------------------------------------------ Auditorías
CREATE SEQUENCE seq_auditoria;

CREATE TABLE auditoria (
    id_auditoria SERIAL PRIMARY KEY,
    id_filial    INT          NOT NULL REFERENCES filial (id_filial),
    id_auditor   INT          NOT NULL REFERENCES usuario (id_usuario),
    codigo       VARCHAR(20)  NOT NULL UNIQUE,
    anio         SMALLINT     NOT NULL,
    tipo         VARCHAR(20)  NOT NULL CHECK (tipo IN ('FINANCIERA', 'OPERATIVA', 'CUMPLIMIENTO', 'TECNOLOGICA')),
    alcance      TEXT         NOT NULL,
    fecha_inicio DATE         NOT NULL,
    fecha_fin    DATE         NOT NULL,
    estado       VARCHAR(15)  NOT NULL DEFAULT 'PLANIFICADA' CHECK (estado IN ('PLANIFICADA', 'EN_EJECUCION', 'CERRADA')),
    CHECK (fecha_fin >= fecha_inicio)
);

CREATE TABLE conciliacion_kri (
    id_conciliacion     SERIAL PRIMARY KEY,
    id_kri              INT           NOT NULL REFERENCES indicador_kri (id_kri),
    id_auditor_fin      INT           NOT NULL REFERENCES usuario (id_usuario),
    periodo             CHAR(7)       NOT NULL CHECK (periodo ~ '^[0-9]{4}-[0-9]{2}$'),
    valor_sistema       NUMERIC(18,4) NOT NULL,
    valor_erp           NUMERIC(18,4) NOT NULL,
    -- RN18: diferencia relativa, calculada por la base de datos
    diferencia_pct      NUMERIC(8,3)  GENERATED ALWAYS AS (CASE
                            WHEN valor_erp = 0 THEN CASE WHEN valor_sistema = 0 THEN 0 ELSE 100 END
                            ELSE round(abs(valor_sistema - valor_erp) / abs(valor_erp) * 100, 3) END) STORED,
    estado              VARCHAR(14)   NOT NULL CHECK (estado IN ('CONCILIADA', 'CON_DIFERENCIA')),
    hallazgo_registrado BOOLEAN       NOT NULL DEFAULT FALSE,
    fecha               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (id_kri, periodo)
);

CREATE TABLE hallazgo (
    id_hallazgo     SERIAL PRIMARY KEY,
    id_auditoria    INT          REFERENCES auditoria (id_auditoria),
    id_riesgo       INT          REFERENCES riesgo (id_riesgo),
    id_conciliacion INT          REFERENCES conciliacion_kri (id_conciliacion),
    id_responsable  INT          NOT NULL REFERENCES usuario (id_usuario),
    id_registrador  INT          REFERENCES usuario (id_usuario),
    descripcion     TEXT         NOT NULL,
    severidad       VARCHAR(5)   NOT NULL CHECK (severidad IN ('ALTA', 'MEDIA', 'BAJA')),
    recomendacion   TEXT         NOT NULL,
    plan_accion     TEXT,
    fecha_limite    DATE         NOT NULL,
    estado          VARCHAR(10)  NOT NULL DEFAULT 'ABIERTO' CHECK (estado IN ('ABIERTO', 'EN_PROCESO', 'CERRADO')),
    financiero      BOOLEAN      NOT NULL DEFAULT FALSE,
    fecha_registro  DATE         NOT NULL DEFAULT CURRENT_DATE,
    fecha_cierre    TIMESTAMPTZ,
    CHECK (id_auditoria IS NOT NULL OR id_conciliacion IS NOT NULL)
);

CREATE TABLE validacion_financiera (
    id_validacion        SERIAL PRIMARY KEY,
    id_evaluacion        INT           NOT NULL UNIQUE REFERENCES evaluacion_riesgo (id_evaluacion),
    id_auditor_fin       INT           NOT NULL REFERENCES usuario (id_usuario),
    fecha                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    resultado            estado_validacion NOT NULL CHECK (resultado IN ('VALIDADA', 'OBSERVADA')),
    tipo_cambio          NUMERIC(12,6) NOT NULL CHECK (tipo_cambio > 0),
    impacto_usd_validado NUMERIC(16,2) NOT NULL CHECK (impacto_usd_validado >= 0),
    observacion          TEXT,
    CHECK (resultado = 'VALIDADA' OR observacion IS NOT NULL)
);

-- ------------------------------------------------------------------ Cumplimiento
CREATE TABLE normativa (
    id_normativa   SERIAL PRIMARY KEY,
    nombre         VARCHAR(160) NOT NULL,
    jurisdiccion   VARCHAR(60)  NOT NULL,
    organismo      VARCHAR(120) NOT NULL,
    vigencia_desde DATE         NOT NULL,
    descripcion    TEXT,
    UNIQUE (nombre, jurisdiccion)
);

CREATE TABLE obligacion (
    id_obligacion       SERIAL PRIMARY KEY,
    id_normativa        INT          NOT NULL REFERENCES normativa (id_normativa) ON DELETE CASCADE,
    id_filial           INT          NOT NULL REFERENCES filial (id_filial),
    descripcion         VARCHAR(300) NOT NULL,
    periodicidad        VARCHAR(12)  NOT NULL CHECK (periodicidad IN ('UNICA', 'MENSUAL', 'TRIMESTRAL', 'SEMESTRAL', 'ANUAL')),
    fecha_vencimiento   DATE         NOT NULL,
    estado_cumplimiento VARCHAR(10)  NOT NULL DEFAULT 'PENDIENTE' CHECK (estado_cumplimiento IN ('PENDIENTE', 'CUMPLE', 'PARCIAL', 'NO_CUMPLE')),
    id_riesgo_generado  INT          REFERENCES riesgo (id_riesgo)
);

CREATE TABLE evaluacion_cumplimiento (
    id_eval_cump SERIAL PRIMARY KEY,
    id_obligacion INT          NOT NULL REFERENCES obligacion (id_obligacion) ON DELETE CASCADE,
    id_evaluador  INT          NOT NULL REFERENCES usuario (id_usuario),
    fecha         DATE         NOT NULL DEFAULT CURRENT_DATE,
    resultado     VARCHAR(10)  NOT NULL CHECK (resultado IN ('CUMPLE', 'PARCIAL', 'NO_CUMPLE')),
    porcentaje    NUMERIC(5,2) NOT NULL CHECK (porcentaje BETWEEN 0 AND 100),
    observacion   TEXT,
    lista_verificacion JSONB
);

CREATE TABLE evidencia (
    id_evidencia SERIAL PRIMARY KEY,
    id_eval_cump INT          REFERENCES evaluacion_cumplimiento (id_eval_cump) ON DELETE CASCADE,
    id_accion    INT          REFERENCES accion_mitigacion (id_accion) ON DELETE CASCADE,
    id_usuario   INT          REFERENCES usuario (id_usuario),
    archivo      VARCHAR(200) NOT NULL,
    ruta         VARCHAR(400),
    tipo_mime    VARCHAR(100),
    tamano       BIGINT,
    fecha_carga  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    descripcion  VARCHAR(300),
    CHECK (id_eval_cump IS NOT NULL OR id_accion IS NOT NULL)
);

-- ------------------------------------------------------------------ Alertas
CREATE TABLE alerta (
    id_alerta            SERIAL PRIMARY KEY,
    id_riesgo            INT          REFERENCES riesgo (id_riesgo) ON DELETE CASCADE,
    id_kri               INT          REFERENCES indicador_kri (id_kri),
    id_medicion          BIGINT       REFERENCES medicion_kri (id_medicion),
    id_accion            INT          REFERENCES accion_mitigacion (id_accion),
    id_obligacion        INT          REFERENCES obligacion (id_obligacion),
    id_conciliacion      INT          REFERENCES conciliacion_kri (id_conciliacion),
    id_hallazgo          INT          REFERENCES hallazgo (id_hallazgo),
    id_evaluacion        INT          REFERENCES evaluacion_riesgo (id_evaluacion),
    id_asignado          INT          NOT NULL REFERENCES usuario (id_usuario),
    id_asignado_original INT          REFERENCES usuario (id_usuario),
    tipo                 VARCHAR(25)  NOT NULL CHECK (tipo IN ('KRI_UMBRAL', 'ACCION_VENCIDA', 'OBLIGACION_POR_VENCER',
                             'CONCILIACION_DIFERENCIA', 'VALIDACION_FUERA_PLAZO', 'HALLAZGO_VENCIDO', 'RIESGO_LEGAL')),
    severidad            VARCHAR(12)  NOT NULL CHECK (severidad IN ('CRITICA', 'ADVERTENCIA', 'PREVENTIVA')),
    mensaje              VARCHAR(400) NOT NULL,
    fecha_generacion     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sla_horas            SMALLINT     NOT NULL,
    fecha_limite         TIMESTAMPTZ  NOT NULL,
    estado               VARCHAR(12)  NOT NULL DEFAULT 'GENERADA'
                         CHECK (estado IN ('GENERADA', 'NOTIFICADA', 'EN_ATENCION', 'ESCALADA', 'RESUELTA', 'CERRADA')),
    nivel_escalamiento   SMALLINT     NOT NULL DEFAULT 0,
    fecha_notificacion   TIMESTAMPTZ,
    fecha_atencion       TIMESTAMPTZ,
    fecha_escalamiento   TIMESTAMPTZ,
    fecha_resolucion     TIMESTAMPTZ,
    fecha_cierre         TIMESTAMPTZ,
    accion_correctiva    TEXT
);
CREATE INDEX idx_alerta_sla ON alerta (estado, fecha_limite);
CREATE INDEX idx_alerta_asignado ON alerta (id_asignado, estado);

-- ------------------------------------------------------------------ Bitácora (inalterable)
CREATE TABLE bitacora (
    id_bitacora BIGSERIAL PRIMARY KEY,
    id_usuario  INT          REFERENCES usuario (id_usuario),
    fecha_hora  TIMESTAMPTZ  NOT NULL DEFAULT clock_timestamp(),
    operacion   VARCHAR(60)  NOT NULL,
    entidad     VARCHAR(40)  NOT NULL,
    id_entidad  BIGINT,
    resultado   VARCHAR(20)  NOT NULL DEFAULT 'OK',
    detalle     JSONB,
    origen      VARCHAR(12)  NOT NULL DEFAULT 'TRIGGER' CHECK (origen IN ('TRIGGER', 'APLICACION'))
);
CREATE INDEX idx_bitacora_fecha ON bitacora (fecha_hora DESC);
CREATE INDEX idx_bitacora_entidad ON bitacora (entidad, id_entidad);

-- ------------------------------------------------------------------ Soporte
CREATE TABLE parametro (
    clave       VARCHAR(50)  PRIMARY KEY,
    valor       VARCHAR(100) NOT NULL,
    tipo        VARCHAR(10)  NOT NULL DEFAULT 'NUMERO' CHECK (tipo IN ('NUMERO', 'TEXTO', 'BOOLEANO')),
    descripcion VARCHAR(200) NOT NULL,
    regla       VARCHAR(20),
    minimo      NUMERIC,
    maximo      NUMERIC,
    CHECK (tipo <> 'NUMERO' OR (valor ~ '^-?[0-9]+(\.[0-9]+)?$'
           AND (minimo IS NULL OR valor::numeric >= minimo) AND (maximo IS NULL OR valor::numeric <= maximo)))
);

-- Datos que publica el ERP corporativo (conector simulado en la demostración)
CREATE TABLE tipo_cambio (
    fecha            DATE          NOT NULL,
    moneda           CHAR(3)       NOT NULL,
    unidades_por_usd NUMERIC(14,6) NOT NULL CHECK (unidades_por_usd > 0),
    PRIMARY KEY (fecha, moneda)
);

CREATE TABLE cierre_erp_kri (
    id_kri  INT           NOT NULL REFERENCES indicador_kri (id_kri) ON DELETE CASCADE,
    periodo CHAR(7)       NOT NULL,
    valor   NUMERIC(18,4) NOT NULL,
    PRIMARY KEY (id_kri, periodo)
);

CREATE TABLE notificacion (
    id_notificacion BIGSERIAL PRIMARY KEY,
    id_usuario      INT          NOT NULL REFERENCES usuario (id_usuario),
    titulo          VARCHAR(150) NOT NULL,
    mensaje         VARCHAR(500) NOT NULL,
    tipo            VARCHAR(20)  NOT NULL DEFAULT 'INFO' CHECK (tipo IN ('INFO', 'ALERTA', 'APROBACION', 'VALIDACION', 'TAREA', 'ESCALAMIENTO')),
    canal           VARCHAR(10)  NOT NULL DEFAULT 'APP' CHECK (canal IN ('APP', 'CORREO', 'SMS')),
    enlace          VARCHAR(200),
    leida           BOOLEAN      NOT NULL DEFAULT FALSE,
    fecha           TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_notificacion_usuario ON notificacion (id_usuario, leida, fecha DESC);

CREATE TABLE incidencia_integracion (
    id_incidencia SERIAL PRIMARY KEY,
    sistema       VARCHAR(20)  NOT NULL CHECK (sistema IN ('ERP', 'SIEM', 'NORMATIVO', 'NOTIFICACIONES')),
    operacion     VARCHAR(60)  NOT NULL,
    mensaje       VARCHAR(400) NOT NULL,
    fecha         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    resuelta      BOOLEAN      NOT NULL DEFAULT FALSE
);

-- =====================================================================================
-- SIGRE Global - V2: funciones y triggers (PL/pgSQL)
-- La base de datos garantiza las reglas de negocio aunque una aplicación las omita:
--   * Bitácora automática de toda operación crítica e inalterable (RN13, RNF03).
--   * Máquinas de estados del riesgo (D08) y de la alerta (D09).
--   * Reglas RN02, RN03, RN04, RN05, RN06, RN07, RN08, RN09, RN10, RN11, RN12, RN15,
--     RN16, RN17 y RN18.
-- Los mensajes de error empiezan con el código de la regla para mostrarlos al usuario.
-- =====================================================================================

-- ------------------------------------------------------------------ Contexto y utilidades

-- Usuario de la sesión: la API lo fija con set_config('app.id_usuario', ...) en cada conexión.
CREATE FUNCTION fn_usuario_actual() RETURNS INT
LANGUAGE sql STABLE AS $$
    SELECT NULLIF(current_setting('app.id_usuario', true), '')::int
$$;

-- Carga inicial de datos de demostración: desactiva validaciones de fechas y avisos.
CREATE FUNCTION fn_carga_inicial() RETURNS BOOLEAN
LANGUAGE sql STABLE AS $$
    SELECT coalesce(current_setting('app.carga_inicial', true), '') = 'on'
$$;

CREATE FUNCTION fn_ahora(p_valor TIMESTAMPTZ) RETURNS TIMESTAMPTZ
LANGUAGE sql STABLE AS $$
    SELECT CASE WHEN fn_carga_inicial() THEN coalesce(p_valor, now()) ELSE now() END
$$;

CREATE FUNCTION fn_param(p_clave TEXT) RETURNS NUMERIC
LANGUAGE sql STABLE AS $$
    SELECT valor::numeric FROM parametro WHERE clave = p_clave
$$;

-- RN02: clasificación del nivel de riesgo
CREATE FUNCTION fn_nivel(p_valor INT) RETURNS VARCHAR
LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE WHEN p_valor <= 4 THEN 'BAJO' WHEN p_valor <= 9 THEN 'MEDIO'
                WHEN p_valor <= 14 THEN 'ALTO' ELSE 'CRITICO' END
$$;

CREATE FUNCTION fn_tiene_rol(p_usuario INT, p_rol TEXT) RETURNS BOOLEAN
LANGUAGE sql STABLE AS $$
    SELECT EXISTS (SELECT 1 FROM usuario_rol ur JOIN rol r USING (id_rol)
                   WHERE ur.id_usuario = p_usuario AND r.codigo = p_rol)
$$;

CREATE FUNCTION fn_es_corporativo(p_usuario INT) RETURNS BOOLEAN
LANGUAGE sql STABLE AS $$
    SELECT EXISTS (SELECT 1 FROM usuario_rol ur JOIN rol r USING (id_rol)
                   WHERE ur.id_usuario = p_usuario AND r.corporativo)
$$;

-- RN14: acceso de un usuario a una filial
CREATE FUNCTION fn_acceso_filial(p_usuario INT, p_filial INT) RETURNS BOOLEAN
LANGUAGE sql STABLE AS $$
    SELECT fn_es_corporativo(p_usuario)
        OR EXISTS (SELECT 1 FROM usuario WHERE id_usuario = p_usuario AND id_filial = p_filial)
$$;

-- RN15: tipo de cambio publicado por el ERP (unidades de moneda local por 1 USD)
CREATE FUNCTION fn_tipo_cambio(p_moneda CHAR(3), p_fecha DATE) RETURNS NUMERIC
LANGUAGE sql STABLE AS $$
    SELECT CASE WHEN p_moneda = 'USD' THEN 1::numeric
                ELSE (SELECT unidades_por_usd FROM tipo_cambio
                      WHERE moneda = p_moneda AND fecha <= p_fecha
                      ORDER BY fecha DESC LIMIT 1) END
$$;

-- RN03: evaluación inherente vigente (12 meses) más reciente de un riesgo
CREATE FUNCTION fn_evaluacion_vigente(p_riesgo INT) RETURNS evaluacion_riesgo
LANGUAGE sql STABLE AS $$
    SELECT * FROM evaluacion_riesgo
    WHERE id_riesgo = p_riesgo AND tipo = 'INHERENTE' AND vigente_hasta >= CURRENT_DATE
    ORDER BY fecha DESC, id_evaluacion DESC LIMIT 1
$$;

CREATE FUNCTION fn_sumar_dias_habiles(p_fecha DATE, p_dias INT) RETURNS DATE
LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE
    v_fecha DATE := p_fecha;
    v_n     INT  := 0;
BEGIN
    WHILE v_n < p_dias LOOP
        v_fecha := v_fecha + 1;
        IF extract(isodow FROM v_fecha) < 6 THEN v_n := v_n + 1; END IF;
    END LOOP;
    RETURN v_fecha;
END $$;

-- ------------------------------------------------------------------ Eventos y notificaciones
-- Los eventos viajan por LISTEN/NOTIFY al servidor de aplicaciones, que los envía en tiempo
-- real a los navegadores (Server-Sent Events). Se entregan al confirmar la transacción.
CREATE FUNCTION fn_evento(p_evento TEXT, p_datos JSONB) RETURNS VOID
LANGUAGE plpgsql AS $$
BEGIN
    IF fn_carga_inicial() THEN RETURN; END IF;
    PERFORM pg_notify('sigre_eventos', (jsonb_build_object('evento', p_evento) || coalesce(p_datos, '{}'::jsonb))::text);
END $$;

CREATE FUNCTION fn_notificar(p_usuario INT, p_titulo TEXT, p_mensaje TEXT,
                             p_tipo TEXT DEFAULT 'INFO', p_enlace TEXT DEFAULT NULL) RETURNS VOID
LANGUAGE plpgsql AS $$
DECLARE
    v_id BIGINT;
BEGIN
    IF p_usuario IS NULL OR fn_carga_inicial() THEN RETURN; END IF;
    INSERT INTO notificacion (id_usuario, titulo, mensaje, tipo, enlace)
    VALUES (p_usuario, left(p_titulo, 150), left(p_mensaje, 500), p_tipo, p_enlace)
    RETURNING id_notificacion INTO v_id;
    PERFORM fn_evento('NOTIFICACION', jsonb_build_object('id_notificacion', v_id, 'id_usuario', p_usuario,
        'titulo', p_titulo, 'mensaje', p_mensaje, 'tipo', p_tipo, 'enlace', p_enlace));
END $$;

-- Notifica a todos los usuarios activos de un rol (si p_filial no es nulo, solo a los de esa filial)
CREATE FUNCTION fn_notificar_rol(p_rol TEXT, p_filial INT, p_titulo TEXT, p_mensaje TEXT,
                                 p_tipo TEXT DEFAULT 'INFO', p_enlace TEXT DEFAULT NULL) RETURNS VOID
LANGUAGE plpgsql AS $$
DECLARE
    v_usuario INT;
BEGIN
    FOR v_usuario IN
        SELECT u.id_usuario FROM usuario u JOIN usuario_rol ur USING (id_usuario) JOIN rol r USING (id_rol)
        WHERE r.codigo = p_rol AND u.activo AND (p_filial IS NULL OR u.id_filial = p_filial)
    LOOP
        PERFORM fn_notificar(v_usuario, p_titulo, p_mensaje, p_tipo, p_enlace);
    END LOOP;
END $$;

-- ------------------------------------------------------------------ Bitácora automática (CU16)
CREATE FUNCTION fn_nombre_operacion(p_tabla TEXT, p_op TEXT, p_cambios JSONB, p_fila JSONB) RETURNS TEXT
LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE
    v_estado TEXT := p_cambios -> 'estado' ->> 'despues';
    v_antes  TEXT := p_cambios -> 'estado' ->> 'antes';
BEGIN
    CASE p_tabla
        WHEN 'riesgo' THEN RETURN CASE
            WHEN p_op = 'INSERT' THEN 'REGISTRAR_RIESGO'
            WHEN p_op = 'DELETE' THEN 'ELIMINAR_RIESGO'
            WHEN v_estado = 'CERRADO' THEN 'CERRAR_RIESGO'
            WHEN v_estado IS NOT NULL THEN 'CAMBIAR_ESTADO_RIESGO'
            ELSE 'MODIFICAR_RIESGO' END;
        WHEN 'evaluacion_riesgo' THEN RETURN CASE WHEN p_op = 'INSERT' THEN 'EVALUAR_RIESGO' ELSE 'ACTUALIZAR_VALIDACION' END;
        WHEN 'plan_mitigacion' THEN RETURN CASE
            WHEN p_op = 'INSERT' THEN 'REGISTRAR_PLAN'
            WHEN v_estado = 'PROPUESTO' THEN 'PROPONER_PLAN'
            WHEN v_estado = 'APROBADO' THEN 'APROBAR_TRATAMIENTO'
            WHEN v_estado = 'BORRADOR' AND v_antes = 'PROPUESTO' THEN 'RECHAZAR_TRATAMIENTO'
            WHEN v_estado = 'CERRADO' THEN 'CERRAR_PLAN'
            ELSE 'MODIFICAR_PLAN' END;
        WHEN 'accion_mitigacion' THEN RETURN CASE
            WHEN p_op = 'INSERT' THEN 'REGISTRAR_ACCION'
            WHEN p_op = 'DELETE' THEN 'ELIMINAR_ACCION'
            WHEN v_estado = 'CERRADA' THEN 'CERRAR_ACCION'
            WHEN p_cambios ? 'avance' THEN 'REGISTRAR_AVANCE'
            ELSE 'MODIFICAR_ACCION' END;
        WHEN 'alerta' THEN RETURN CASE
            WHEN p_op = 'INSERT' THEN 'GENERAR_ALERTA'
            WHEN v_estado = 'NOTIFICADA' THEN 'NOTIFICAR_ALERTA'
            WHEN v_estado = 'EN_ATENCION' THEN 'ATENDER_ALERTA'
            WHEN v_estado = 'ESCALADA' THEN 'ESCALAR_ALERTA'
            WHEN v_estado = 'RESUELTA' THEN 'RESOLVER_ALERTA'
            WHEN v_estado = 'CERRADA' THEN 'CERRAR_ALERTA'
            ELSE 'MODIFICAR_ALERTA' END;
        WHEN 'validacion_financiera' THEN RETURN CASE WHEN p_fila ->> 'resultado' = 'VALIDADA'
            THEN 'VALIDAR_IMPACTO' ELSE 'OBSERVAR_IMPACTO' END;
        WHEN 'conciliacion_kri' THEN RETURN CASE WHEN p_op = 'INSERT' THEN 'CONCILIAR_KRI' ELSE 'ACTUALIZAR_CONCILIACION' END;
        WHEN 'indicador_kri' THEN RETURN CASE
            WHEN p_op = 'INSERT' THEN 'DEFINIR_KRI'
            WHEN p_cambios ?| ARRAY['umbral_amarillo', 'umbral_rojo'] THEN 'MODIFICAR_UMBRAL'
            ELSE 'MODIFICAR_KRI' END;
        WHEN 'parametro' THEN RETURN 'MODIFICAR_PARAMETRO';
        WHEN 'usuario' THEN RETURN CASE
            WHEN p_op = 'INSERT' THEN 'CREAR_USUARIO'
            WHEN p_cambios ? 'activo' THEN CASE WHEN (p_cambios -> 'activo' ->> 'despues')::boolean
                                                THEN 'ACTIVAR_USUARIO' ELSE 'DESACTIVAR_USUARIO' END
            WHEN p_cambios ? 'totp_activo' THEN 'CONFIGURAR_DOBLE_FACTOR'
            ELSE 'MODIFICAR_USUARIO' END;
        WHEN 'usuario_rol' THEN RETURN CASE WHEN p_op = 'INSERT' THEN 'ASIGNAR_ROL' ELSE 'QUITAR_ROL' END;
        WHEN 'filial' THEN RETURN CASE
            WHEN p_op = 'INSERT' THEN 'CREAR_FILIAL'
            WHEN p_cambios ? 'apetito_riesgo' THEN 'MODIFICAR_APETITO'
            ELSE 'MODIFICAR_FILIAL' END;
        WHEN 'auditoria' THEN RETURN CASE
            WHEN p_op = 'INSERT' THEN 'PLANIFICAR_AUDITORIA'
            WHEN v_estado = 'EN_EJECUCION' THEN 'INICIAR_AUDITORIA'
            WHEN v_estado = 'CERRADA' THEN 'CERRAR_AUDITORIA'
            ELSE 'MODIFICAR_AUDITORIA' END;
        WHEN 'hallazgo' THEN RETURN CASE
            WHEN p_op = 'INSERT' THEN 'REGISTRAR_HALLAZGO'
            WHEN v_estado = 'CERRADO' THEN 'CERRAR_HALLAZGO'
            ELSE 'MODIFICAR_HALLAZGO' END;
        WHEN 'normativa' THEN RETURN CASE WHEN p_op = 'INSERT' THEN 'REGISTRAR_NORMATIVA' ELSE 'MODIFICAR_NORMATIVA' END;
        WHEN 'obligacion' THEN RETURN CASE
            WHEN p_op = 'INSERT' THEN 'REGISTRAR_OBLIGACION'
            WHEN p_cambios ? 'estado_cumplimiento' THEN 'ACTUALIZAR_CUMPLIMIENTO'
            ELSE 'MODIFICAR_OBLIGACION' END;
        WHEN 'evaluacion_cumplimiento' THEN RETURN 'EVALUAR_CUMPLIMIENTO';
        WHEN 'control' THEN RETURN CASE WHEN p_op = 'INSERT' THEN 'REGISTRAR_CONTROL'
                                         WHEN p_op = 'DELETE' THEN 'ELIMINAR_CONTROL' ELSE 'MODIFICAR_CONTROL' END;
        WHEN 'evidencia' THEN RETURN CASE WHEN p_op = 'INSERT' THEN 'CARGAR_EVIDENCIA' ELSE 'ELIMINAR_EVIDENCIA' END;
        ELSE RETURN p_op || '_' || upper(p_tabla);
    END CASE;
END $$;

-- Trigger genérico: registra quién, cuándo, qué operación y qué cambió (antes y después).
-- TG_ARGV[0] es el nombre de la clave primaria de la tabla.
CREATE FUNCTION fn_auditar() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    v_ignorar TEXT[] := ARRAY['actualizado_en', 'clave_hash', 'totp_secreto', 'ultimo_acceso'];
    v_nuevo   JSONB;
    v_viejo   JSONB;
    v_id      TEXT;
    v_cambios JSONB := '{}'::jsonb;
    v_clave   TEXT;
    v_detalle JSONB;
BEGIN
    IF fn_carga_inicial() THEN RETURN NULL; END IF;
    IF TG_OP <> 'DELETE' THEN v_nuevo := to_jsonb(NEW) - v_ignorar; END IF;
    IF TG_OP <> 'INSERT' THEN v_viejo := to_jsonb(OLD) - v_ignorar; END IF;
    v_id := coalesce(v_nuevo, v_viejo) ->> TG_ARGV[0];

    IF TG_OP = 'UPDATE' THEN
        FOR v_clave IN SELECT jsonb_object_keys(v_nuevo) LOOP
            IF (v_nuevo -> v_clave) IS DISTINCT FROM (v_viejo -> v_clave) THEN
                v_cambios := v_cambios || jsonb_build_object(v_clave,
                    jsonb_build_object('antes', v_viejo -> v_clave, 'despues', v_nuevo -> v_clave));
            END IF;
        END LOOP;
        IF v_cambios = '{}'::jsonb THEN RETURN NULL; END IF;   -- sin cambios reales
        v_detalle := jsonb_build_object('cambios', v_cambios);
    ELSE
        v_detalle := jsonb_build_object('registro', coalesce(v_nuevo, v_viejo));
    END IF;
    IF v_id !~ '^[0-9]+$' THEN v_detalle := v_detalle || jsonb_build_object('clave', v_id); END IF;

    INSERT INTO bitacora (id_usuario, operacion, entidad, id_entidad, detalle)
    VALUES (fn_usuario_actual(),
            fn_nombre_operacion(TG_TABLE_NAME, TG_OP, v_cambios, coalesce(v_nuevo, v_viejo)),
            upper(TG_TABLE_NAME),
            CASE WHEN v_id ~ '^[0-9]+$' THEN v_id::bigint END,
            v_detalle);
    RETURN NULL;
END $$;

-- Bitácora inalterable (RNF03, RN13): mismo nombre que en el diseño físico (sección 10.2)
CREATE FUNCTION bitacora_solo_insert() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'RN13: La bitácora solo admite inserciones; no se puede % un registro',
        CASE TG_OP WHEN 'UPDATE' THEN 'modificar' WHEN 'DELETE' THEN 'eliminar' ELSE 'vaciar' END;
END $$;

CREATE TRIGGER trg_bitacora BEFORE UPDATE OR DELETE ON bitacora
    FOR EACH ROW EXECUTE FUNCTION bitacora_solo_insert();
CREATE TRIGGER trg_bitacora_truncate BEFORE TRUNCATE ON bitacora
    FOR EACH STATEMENT EXECUTE FUNCTION bitacora_solo_insert();

-- ------------------------------------------------------------------ Riesgo (CU01, D08)
CREATE FUNCTION fn_riesgo_antes_insertar() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    v_abrev TEXT;
BEGIN
    NEW.titulo := trim(NEW.titulo);
    NEW.id_registrador := coalesce(NEW.id_registrador, fn_usuario_actual());
    IF EXISTS (SELECT 1 FROM riesgo WHERE id_filial = NEW.id_filial AND lower(titulo) = lower(NEW.titulo)) THEN
        RAISE EXCEPTION 'CU01: Ya existe un riesgo con el título "%" en esta filial', NEW.titulo;
    END IF;
    IF NOT fn_acceso_filial(NEW.id_responsable, NEW.id_filial) THEN
        RAISE EXCEPTION 'RN14: El responsable no tiene acceso a la filial del riesgo';
    END IF;
    IF NEW.codigo IS NULL THEN
        v_abrev := CASE NEW.categoria WHEN 'FINANCIERO' THEN 'FIN' WHEN 'TECNOLOGICO' THEN 'TEC'
                                      WHEN 'LEGAL' THEN 'LEG' WHEN 'OPERATIVO' THEN 'OPE' ELSE 'REP' END;
        NEW.codigo := 'RSK-' || v_abrev || '-'
                   || lpad(nextval(('seq_riesgo_' || lower(v_abrev))::regclass)::text, 4, '0');
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_riesgo_antes_insertar BEFORE INSERT ON riesgo
    FOR EACH ROW EXECUTE FUNCTION fn_riesgo_antes_insertar();

-- Máquina de estados del riesgo (D08) y regla RN11 para pasar a MITIGADO
CREATE FUNCTION fn_riesgo_transicion() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    v_pendientes INT;
    v_residual   evaluacion_riesgo;
    v_apetito    SMALLINT;
BEGIN
    NEW.actualizado_en := now();
    IF NEW.estado = OLD.estado THEN RETURN NEW; END IF;

    IF (OLD.estado, NEW.estado) NOT IN (
           ('IDENTIFICADO', 'EVALUADO'), ('EVALUADO', 'EN_TRATAMIENTO'), ('EVALUADO', 'ACEPTADO'),
           ('EN_TRATAMIENTO', 'MITIGADO'), ('MITIGADO', 'EVALUADO'), ('MITIGADO', 'CERRADO'),
           ('ACEPTADO', 'CERRADO')) THEN
        RAISE EXCEPTION 'D08: Transición de estado no permitida para el riesgo %: % → %',
            NEW.codigo, OLD.estado, NEW.estado;
    END IF;

    IF NEW.estado = 'MITIGADO' THEN
        SELECT count(*) INTO v_pendientes
        FROM accion_mitigacion a JOIN plan_mitigacion p USING (id_plan)
        WHERE p.id_riesgo = NEW.id_riesgo AND a.estado <> 'CERRADA';
        IF v_pendientes > 0 THEN
            RAISE EXCEPTION 'RN11: El riesgo % tiene % acción(es) sin cerrar', NEW.codigo, v_pendientes;
        END IF;
        SELECT * INTO v_residual FROM evaluacion_riesgo
        WHERE id_riesgo = NEW.id_riesgo AND tipo = 'RESIDUAL'
        ORDER BY fecha DESC, id_evaluacion DESC LIMIT 1;
        SELECT apetito_riesgo INTO v_apetito FROM filial WHERE id_filial = NEW.id_filial;
        IF v_residual.id_evaluacion IS NULL THEN
            RAISE EXCEPTION 'RN11: Falta la evaluación residual del riesgo %', NEW.codigo;
        END IF;
        IF v_residual.valor > v_apetito THEN
            RAISE EXCEPTION 'RN11: El nivel residual (%) supera el apetito de riesgo de la filial (%)',
                v_residual.valor, v_apetito;
        END IF;
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_riesgo_transicion BEFORE UPDATE ON riesgo
    FOR EACH ROW EXECUTE FUNCTION fn_riesgo_transicion();

-- ------------------------------------------------------------------ Evaluación (CU02, RN02, RN03, RN15, RN16)
CREATE FUNCTION fn_evaluacion_antes_insertar() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    r       riesgo;
    v_valor INT := NEW.probabilidad * NEW.impacto;
BEGIN
    SELECT * INTO r FROM riesgo WHERE id_riesgo = NEW.id_riesgo;
    IF NOT fn_carga_inicial() THEN
        IF NEW.tipo = 'INHERENTE' AND r.estado NOT IN ('IDENTIFICADO', 'EVALUADO', 'MITIGADO') THEN
            RAISE EXCEPTION 'CU02: Solo se evalúan riesgos IDENTIFICADO, EVALUADO o MITIGADO (el riesgo % está %)',
                r.codigo, r.estado;
        END IF;
        IF NEW.tipo = 'RESIDUAL' AND r.estado NOT IN ('EN_TRATAMIENTO', 'MITIGADO') THEN
            RAISE EXCEPTION 'CU05: La evaluación residual corresponde a riesgos EN_TRATAMIENTO o MITIGADO (el riesgo % está %)',
                r.codigo, r.estado;
        END IF;
        NEW.fecha := CURRENT_DATE;
    END IF;

    NEW.id_evaluador  := coalesce(NEW.id_evaluador, fn_usuario_actual());
    -- RN03: vigencia de 12 meses
    NEW.vigente_hasta := (NEW.fecha + make_interval(months => fn_param('vigencia_evaluacion_meses')::int))::date;

    -- RN15: conversión a la moneda corporativa con el tipo de cambio del ERP en la fecha
    IF NEW.impacto_financiero IS NOT NULL THEN
        NEW.moneda := coalesce(NEW.moneda, (SELECT moneda FROM filial WHERE id_filial = r.id_filial));
        NEW.tipo_cambio := coalesce(NEW.tipo_cambio, fn_tipo_cambio(NEW.moneda, NEW.fecha));
        IF NEW.tipo_cambio IS NULL THEN
            RAISE EXCEPTION 'RN15: El ERP no tiene tipo de cambio de % para el %', NEW.moneda, to_char(NEW.fecha, 'DD/MM/YYYY');
        END IF;
        NEW.impacto_usd := round(NEW.impacto_financiero / NEW.tipo_cambio, 2);
    END IF;

    -- RN16: riesgo financiero Alto/Crítico o de 100 000 USD o más requiere validación financiera
    IF NEW.tipo = 'INHERENTE' AND r.categoria = 'FINANCIERO'
       AND (v_valor >= 10 OR coalesce(NEW.impacto_usd, 0) >= fn_param('umbral_validacion_usd')) THEN
        NEW.estado_validacion := 'PENDIENTE';
    ELSE
        NEW.estado_validacion := 'NO_REQUERIDA';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_evaluacion_antes_insertar BEFORE INSERT ON evaluacion_riesgo
    FOR EACH ROW EXECUTE FUNCTION fn_evaluacion_antes_insertar();

CREATE FUNCTION fn_evaluacion_despues_insertar() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    r            riesgo;
    v_pendientes INT;
    v_apetito    SMALLINT;
BEGIN
    SELECT * INTO r FROM riesgo WHERE id_riesgo = NEW.id_riesgo;
    IF NEW.tipo = 'INHERENTE' THEN
        IF r.estado IN ('IDENTIFICADO', 'MITIGADO') THEN
            UPDATE riesgo SET estado = 'EVALUADO' WHERE id_riesgo = r.id_riesgo;
        END IF;
        IF NEW.estado_validacion = 'PENDIENTE' THEN
            PERFORM fn_notificar_rol('AUDITOR_FINANCIERO', NULL, 'Validación financiera pendiente',
                format('%s · %s: nivel %s (%s), impacto %s USD. Plazo: %s días hábiles (RN16).',
                       r.codigo, r.titulo, NEW.nivel, NEW.valor, coalesce(to_char(NEW.impacto_usd, 'FM999G999G999D00'), '-'),
                       fn_param('plazo_validacion_dias_habiles')),
                'VALIDACION', '/validacion-financiera');
        END IF;
    ELSIF r.estado = 'EN_TRATAMIENTO' THEN
        -- RN11: si todas las acciones están cerradas y el residual no supera el apetito, queda MITIGADO
        SELECT count(*) INTO v_pendientes
        FROM accion_mitigacion a JOIN plan_mitigacion p USING (id_plan)
        WHERE p.id_riesgo = r.id_riesgo AND a.estado <> 'CERRADA';
        SELECT apetito_riesgo INTO v_apetito FROM filial WHERE id_filial = r.id_filial;
        IF v_pendientes = 0 AND NEW.valor <= v_apetito THEN
            UPDATE riesgo SET estado = 'MITIGADO' WHERE id_riesgo = r.id_riesgo;
            UPDATE plan_mitigacion SET estado = 'CERRADO' WHERE id_riesgo = r.id_riesgo AND estado = 'APROBADO';
            PERFORM fn_notificar(r.id_registrador, 'Riesgo mitigado',
                format('%s quedó MITIGADO: nivel residual %s dentro del apetito %s (RN11).', r.codigo, NEW.valor, v_apetito),
                'INFO', '/riesgos/' || r.id_riesgo);
        ELSE
            PERFORM fn_notificar(r.id_registrador, 'El riesgo sigue en tratamiento',
                format('%s: nivel residual %s, apetito %s, acciones pendientes %s (RN11).', r.codigo, NEW.valor, v_apetito, v_pendientes),
                'INFO', '/riesgos/' || r.id_riesgo);
        END IF;
    END IF;
    PERFORM fn_evento('EVALUACION', jsonb_build_object('id_riesgo', r.id_riesgo, 'codigo', r.codigo,
        'nivel', NEW.nivel, 'valor', NEW.valor, 'id_filial', r.id_filial));
    RETURN NULL;
END $$;

CREATE TRIGGER trg_evaluacion_despues_insertar AFTER INSERT ON evaluacion_riesgo
    FOR EACH ROW EXECUTE FUNCTION fn_evaluacion_despues_insertar();

-- ------------------------------------------------------------------ Plan de mitigación (CU03, CU04)
CREATE FUNCTION fn_plan_antes_insertar() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    r riesgo;
BEGIN
    SELECT * INTO r FROM riesgo WHERE id_riesgo = NEW.id_riesgo;
    IF r.estado <> 'EVALUADO' AND NOT fn_carga_inicial() THEN
        RAISE EXCEPTION 'CU03: El plan de mitigación se define para riesgos EVALUADO (el riesgo % está %)', r.codigo, r.estado;
    END IF;
    NEW.id_proponente := coalesce(NEW.id_proponente, fn_usuario_actual());
    NEW.estado := 'BORRADOR';
    RETURN NEW;
END $$;

CREATE TRIGGER trg_plan_antes_insertar BEFORE INSERT ON plan_mitigacion
    FOR EACH ROW EXECUTE FUNCTION fn_plan_antes_insertar();

CREATE FUNCTION fn_plan_antes_actualizar() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    r           riesgo;
    e           evaluacion_riesgo;
    v_acciones  INT;
    v_aprobador INT;
BEGIN
    IF NEW.estado = OLD.estado THEN
        IF OLD.estado IN ('APROBADO', 'CERRADO') AND NEW.estrategia <> OLD.estrategia THEN
            RAISE EXCEPTION 'CU03: Un plan aprobado no se puede modificar';
        END IF;
        RETURN NEW;
    END IF;

    SELECT * INTO r FROM riesgo WHERE id_riesgo = NEW.id_riesgo;
    IF (OLD.estado, NEW.estado) NOT IN (('BORRADOR', 'PROPUESTO'), ('PROPUESTO', 'APROBADO'),
                                         ('PROPUESTO', 'BORRADOR'), ('APROBADO', 'CERRADO')) THEN
        RAISE EXCEPTION 'CU04: Transición de plan no permitida: % → %', OLD.estado, NEW.estado;
    END IF;

    IF NEW.estado = 'PROPUESTO' THEN
        SELECT count(*) INTO v_acciones FROM accion_mitigacion WHERE id_plan = NEW.id_plan;
        IF NEW.estrategia <> 'ACEPTAR' AND v_acciones = 0 THEN
            RAISE EXCEPTION 'RN05: El plan debe tener al menos una acción con responsable y fecha límite';
        END IF;
        IF NEW.estrategia = 'ACEPTAR' AND coalesce(length(trim(NEW.justificacion)), 0) < 10 THEN
            RAISE EXCEPTION 'RN12: Aceptar un riesgo requiere una justificación escrita';
        END IF;
        -- CU03 paso 4: costo total y fecha fin del plan
        SELECT coalesce(sum(costo), 0), max(fecha_limite) INTO NEW.costo_estimado, NEW.fecha_fin
        FROM accion_mitigacion WHERE id_plan = NEW.id_plan;
        NEW.fecha_propuesta := fn_ahora(NEW.fecha_propuesta);
        NEW.motivo_rechazo := NULL;

    ELSIF NEW.estado = 'APROBADO' THEN
        v_aprobador := coalesce(NEW.id_aprobador, fn_usuario_actual());
        NEW.id_aprobador := v_aprobador;
        e := fn_evaluacion_vigente(NEW.id_riesgo);
        -- RN03
        IF e.id_evaluacion IS NULL THEN
            RAISE EXCEPTION 'RN03: El riesgo % no tiene una evaluación vigente (máximo % meses); se devuelve para reevaluación',
                r.codigo, fn_param('vigencia_evaluacion_meses');
        END IF;
        -- RN16
        IF e.estado_validacion IN ('PENDIENTE', 'OBSERVADA') THEN
            RAISE EXCEPTION 'RN16: La validación del impacto financiero está %; se requiere la validación del Auditor Financiero (CU17)',
                e.estado_validacion;
        END IF;
        IF v_aprobador IS NULL THEN
            RAISE EXCEPTION 'CU04: No se identificó al usuario que aprueba';
        END IF;
        -- RN12
        IF NEW.estrategia = 'ACEPTAR' AND NOT fn_tiene_rol(v_aprobador, 'DIRECTOR') THEN
            RAISE EXCEPTION 'RN12: Aceptar un riesgo requiere la aprobación del Director de Riesgos';
        END IF;
        -- RN04: atribución del aprobador según el nivel
        IF e.nivel IN ('ALTO', 'CRITICO') AND NOT fn_tiene_rol(v_aprobador, 'DIRECTOR') THEN
            RAISE EXCEPTION 'RN04: Un riesgo de nivel % debe aprobarlo el Director de Riesgos; se deriva al Director', e.nivel;
        END IF;
        IF e.nivel IN ('BAJO', 'MEDIO') AND NOT (
               fn_tiene_rol(v_aprobador, 'DIRECTOR')
               OR (fn_tiene_rol(v_aprobador, 'GERENTE_FILIAL')
                   AND (SELECT id_filial FROM usuario WHERE id_usuario = v_aprobador) = r.id_filial)) THEN
            RAISE EXCEPTION 'RN04: Un riesgo de nivel % lo aprueba el Gerente de su filial o el Director de Riesgos', e.nivel;
        END IF;
        -- Separación de funciones: quien propone no aprueba (sección 10.5)
        IF v_aprobador = NEW.id_proponente THEN
            RAISE EXCEPTION 'RN04: Separación de funciones: quien propone el plan no puede aprobarlo';
        END IF;
        NEW.fecha_aprobacion := fn_ahora(NEW.fecha_aprobacion);

    ELSIF NEW.estado = 'BORRADOR' THEN
        IF coalesce(length(trim(NEW.motivo_rechazo)), 0) < 5 THEN
            RAISE EXCEPTION 'CU04: El rechazo debe registrar el motivo';
        END IF;
        NEW.id_aprobador := coalesce(fn_usuario_actual(), NEW.id_aprobador);
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_plan_antes_actualizar BEFORE UPDATE ON plan_mitigacion
    FOR EACH ROW EXECUTE FUNCTION fn_plan_antes_actualizar();

CREATE FUNCTION fn_plan_despues_actualizar() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    r riesgo;
    e evaluacion_riesgo;
    a accion_mitigacion;
BEGIN
    IF NEW.estado = OLD.estado THEN RETURN NULL; END IF;
    SELECT * INTO r FROM riesgo WHERE id_riesgo = NEW.id_riesgo;

    IF NEW.estado = 'APROBADO' THEN
        UPDATE riesgo SET estado = CASE WHEN NEW.estrategia = 'ACEPTAR' THEN 'ACEPTADO' ELSE 'EN_TRATAMIENTO' END
        WHERE id_riesgo = NEW.id_riesgo;
        FOR a IN SELECT * FROM accion_mitigacion WHERE id_plan = NEW.id_plan LOOP
            PERFORM fn_notificar(a.id_responsable, 'Acción de mitigación asignada',
                format('%s · %s (vence el %s)', r.codigo, a.descripcion, to_char(a.fecha_limite, 'DD/MM/YYYY')),
                'TAREA', '/mis-acciones');
        END LOOP;
        PERFORM fn_notificar(NEW.id_proponente, 'Tratamiento aprobado',
            format('%s · %s: el plan (%s) fue aprobado.', r.codigo, r.titulo, NEW.estrategia), 'APROBACION', '/riesgos/' || r.id_riesgo);
    ELSIF NEW.estado = 'PROPUESTO' THEN
        e := fn_evaluacion_vigente(NEW.id_riesgo);
        IF e.nivel IN ('ALTO', 'CRITICO') OR NEW.estrategia = 'ACEPTAR' OR e.id_evaluacion IS NULL THEN
            PERFORM fn_notificar_rol('DIRECTOR', NULL, 'Plan pendiente de aprobación',
                format('%s · %s (%s) espera su aprobación.', r.codigo, r.titulo, coalesce(e.nivel, 'sin evaluación vigente')),
                'APROBACION', '/riesgos/' || r.id_riesgo);
        ELSE
            PERFORM fn_notificar_rol('GERENTE_FILIAL', r.id_filial, 'Plan pendiente de aprobación',
                format('%s · %s (%s) espera su aprobación.', r.codigo, r.titulo, e.nivel), 'APROBACION', '/riesgos/' || r.id_riesgo);
        END IF;
    ELSIF NEW.estado = 'BORRADOR' THEN
        PERFORM fn_notificar(NEW.id_proponente, 'Tratamiento rechazado',
            format('%s · %s: %s', r.codigo, r.titulo, NEW.motivo_rechazo), 'APROBACION', '/riesgos/' || r.id_riesgo);
    END IF;
    PERFORM fn_evento('PLAN', jsonb_build_object('id_riesgo', r.id_riesgo, 'codigo', r.codigo, 'estado', NEW.estado));
    RETURN NULL;
END $$;

CREATE TRIGGER trg_plan_despues_actualizar AFTER UPDATE ON plan_mitigacion
    FOR EACH ROW EXECUTE FUNCTION fn_plan_despues_actualizar();

-- ------------------------------------------------------------------ Acciones de mitigación (CU05)
CREATE FUNCTION fn_accion_antes() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    p            plan_mitigacion;
    r            riesgo;
    v_evidencias INT;
BEGIN
    SELECT * INTO p FROM plan_mitigacion WHERE id_plan = NEW.id_plan;
    SELECT * INTO r FROM riesgo WHERE id_riesgo = p.id_riesgo;

    IF TG_OP = 'INSERT' THEN
        IF NOT fn_carga_inicial() THEN
            IF p.estado <> 'BORRADOR' THEN
                RAISE EXCEPTION 'CU03: Solo se agregan acciones a un plan en BORRADOR (el plan está %)', p.estado;
            END IF;
            IF NEW.fecha_limite < CURRENT_DATE THEN
                RAISE EXCEPTION 'CU03: La fecha límite de la acción no puede ser anterior a hoy';
            END IF;
        END IF;
        IF NOT fn_acceso_filial(NEW.id_responsable, r.id_filial) THEN
            RAISE EXCEPTION 'CU03: El responsable de la acción no tiene acceso a la filial del riesgo (RN14)';
        END IF;
        RETURN NEW;
    END IF;

    IF NEW.avance < OLD.avance THEN
        RAISE EXCEPTION 'CU05: El avance no puede disminuir (de % %% a % %%)', OLD.avance, NEW.avance;
    END IF;
    IF OLD.estado = 'CERRADA' AND NEW.avance IS DISTINCT FROM OLD.avance THEN
        RAISE EXCEPTION 'CU05: La acción ya está cerrada';
    END IF;
    IF NEW.avance > OLD.avance AND p.estado <> 'APROBADO' AND NOT fn_carga_inicial() THEN
        RAISE EXCEPTION 'CU05: Solo se registran avances de planes aprobados (el plan está %)', p.estado;
    END IF;
    IF NEW.avance = 100 AND OLD.estado <> 'CERRADA' THEN
        SELECT count(*) INTO v_evidencias FROM evidencia WHERE id_accion = NEW.id_accion;
        IF v_evidencias = 0 AND NOT fn_carga_inicial() THEN
            RAISE EXCEPTION 'CU05: Para cerrar la acción se requiere al menos una evidencia';
        END IF;
        NEW.estado := 'CERRADA';
        NEW.fecha_cierre := fn_ahora(NEW.fecha_cierre);
    ELSIF NEW.avance > 0 AND NEW.avance < 100 THEN
        NEW.estado := 'EN_CURSO';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_accion_antes BEFORE INSERT OR UPDATE ON accion_mitigacion
    FOR EACH ROW EXECUTE FUNCTION fn_accion_antes();

CREATE FUNCTION fn_accion_despues() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    r            riesgo;
    v_pendientes INT;
BEGIN
    IF NEW.estado = 'CERRADA' AND OLD.estado <> 'CERRADA' THEN
        SELECT ri.* INTO r FROM riesgo ri JOIN plan_mitigacion p USING (id_riesgo) WHERE p.id_plan = NEW.id_plan;
        SELECT count(*) INTO v_pendientes FROM accion_mitigacion WHERE id_plan = NEW.id_plan AND estado <> 'CERRADA';
        IF v_pendientes = 0 THEN
            PERFORM fn_notificar(r.id_registrador, 'Acciones completadas',
                format('%s: todas las acciones están cerradas. Registre la evaluación residual (RN11).', r.codigo),
                'TAREA', '/riesgos/' || r.id_riesgo);
        END IF;
        PERFORM fn_evento('ACCION', jsonb_build_object('id_riesgo', r.id_riesgo, 'id_accion', NEW.id_accion, 'estado', NEW.estado));
    END IF;
    RETURN NULL;
END $$;

CREATE TRIGGER trg_accion_despues AFTER UPDATE ON accion_mitigacion
    FOR EACH ROW EXECUTE FUNCTION fn_accion_despues();

-- ------------------------------------------------------------------ Mediciones de KRI (CU06, RN06)
CREATE FUNCTION fn_medicion_antes() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    k indicador_kri;
BEGIN
    SELECT * INTO k FROM indicador_kri WHERE id_kri = NEW.id_kri;
    IF NOT k.activo THEN
        RAISE EXCEPTION 'CU06: El indicador % está inactivo', k.nombre;
    END IF;
    NEW.id_usuario := coalesce(NEW.id_usuario, fn_usuario_actual());
    NEW.fecha := fn_ahora(NEW.fecha);
    -- RN06: semáforo según los umbrales amarillo y rojo
    NEW.semaforo := CASE WHEN k.direccion = 'MAYOR' THEN
                        CASE WHEN NEW.valor > k.umbral_rojo THEN 'ROJO'
                             WHEN NEW.valor > k.umbral_amarillo THEN 'AMARILLO' ELSE 'VERDE' END
                    ELSE
                        CASE WHEN NEW.valor < k.umbral_rojo THEN 'ROJO'
                             WHEN NEW.valor < k.umbral_amarillo THEN 'AMARILLO' ELSE 'VERDE' END
                    END;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_medicion_antes BEFORE INSERT ON medicion_kri
    FOR EACH ROW EXECUTE FUNCTION fn_medicion_antes();

-- CU07: una medición fuera de umbral genera la alerta automáticamente
CREATE FUNCTION fn_medicion_despues() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    k     indicador_kri;
    r     riesgo;
    v_sev TEXT;
BEGIN
    IF fn_carga_inicial() THEN RETURN NULL; END IF;
    SELECT * INTO k FROM indicador_kri WHERE id_kri = NEW.id_kri;
    SELECT * INTO r FROM riesgo WHERE id_riesgo = k.id_riesgo;
    PERFORM fn_evento('MEDICION', jsonb_build_object('id_kri', k.id_kri, 'kri', k.nombre, 'valor', NEW.valor,
        'unidad', k.unidad, 'semaforo', NEW.semaforo, 'origen', NEW.origen, 'id_riesgo', r.id_riesgo,
        'codigo', r.codigo, 'id_filial', r.id_filial));
    IF NEW.semaforo = 'VERDE' THEN RETURN NULL; END IF;

    v_sev := CASE NEW.semaforo WHEN 'ROJO' THEN 'CRITICA' ELSE 'ADVERTENCIA' END;
    -- Evita duplicar: ya existe una alerta abierta del mismo indicador con igual o mayor severidad
    IF EXISTS (SELECT 1 FROM alerta WHERE id_kri = k.id_kri AND estado NOT IN ('RESUELTA', 'CERRADA')
               AND (severidad = 'CRITICA' OR severidad = v_sev)) THEN
        RETURN NULL;
    END IF;

    INSERT INTO alerta (id_riesgo, id_kri, id_medicion, id_asignado, tipo, severidad, mensaje)
    VALUES (r.id_riesgo, k.id_kri, NEW.id_medicion, r.id_responsable, 'KRI_UMBRAL', v_sev,
            format('%s · %s = %s %s %s el umbral %s (%s %s)', r.codigo, k.nombre, trim_scale(NEW.valor), k.unidad,
                   CASE k.direccion WHEN 'MAYOR' THEN 'supera' ELSE 'está por debajo de' END,
                   lower(NEW.semaforo),
                   trim_scale(CASE NEW.semaforo WHEN 'ROJO' THEN k.umbral_rojo ELSE k.umbral_amarillo END), k.unidad));
    RETURN NULL;
END $$;

CREATE TRIGGER trg_medicion_despues AFTER INSERT ON medicion_kri
    FOR EACH ROW EXECUTE FUNCTION fn_medicion_despues();

-- ------------------------------------------------------------------ Alertas (CU07, CU08, CU09, D09, RN07)
CREATE FUNCTION fn_alerta_antes_insertar() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    NEW.fecha_generacion := fn_ahora(NEW.fecha_generacion);
    -- RN07: plazo de atención según la severidad
    NEW.sla_horas := coalesce(NEW.sla_horas, CASE NEW.severidad
                        WHEN 'CRITICA' THEN fn_param('sla_critica_horas')
                        WHEN 'ADVERTENCIA' THEN fn_param('sla_advertencia_horas')
                        ELSE fn_param('sla_preventiva_horas') END);
    NEW.fecha_limite := NEW.fecha_generacion + make_interval(hours => NEW.sla_horas);
    NEW.id_asignado_original := coalesce(NEW.id_asignado_original, NEW.id_asignado);
    IF NOT fn_carga_inicial() THEN
        NEW.estado := 'GENERADA';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_alerta_antes_insertar BEFORE INSERT ON alerta
    FOR EACH ROW EXECUTE FUNCTION fn_alerta_antes_insertar();

CREATE FUNCTION fn_alerta_despues_insertar() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    -- El servicio de notificaciones de la API la entrega y la pasa a NOTIFICADA (CU07 pasos 4 y 5)
    PERFORM fn_evento('ALERTA_GENERADA', jsonb_build_object('id_alerta', NEW.id_alerta, 'severidad', NEW.severidad,
        'tipo', NEW.tipo, 'mensaje', NEW.mensaje, 'id_asignado', NEW.id_asignado,
        'id_filial', (SELECT id_filial FROM riesgo WHERE id_riesgo = NEW.id_riesgo)));
    RETURN NULL;
END $$;

CREATE TRIGGER trg_alerta_despues_insertar AFTER INSERT ON alerta
    FOR EACH ROW EXECUTE FUNCTION fn_alerta_despues_insertar();

-- Máquina de estados de la alerta (D09)
CREATE FUNCTION fn_alerta_transicion() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.estado = OLD.estado THEN RETURN NEW; END IF;
    IF (OLD.estado, NEW.estado) NOT IN (
           ('GENERADA', 'NOTIFICADA'), ('NOTIFICADA', 'EN_ATENCION'), ('NOTIFICADA', 'ESCALADA'),
           ('EN_ATENCION', 'ESCALADA'), ('ESCALADA', 'EN_ATENCION'), ('EN_ATENCION', 'RESUELTA'),
           ('RESUELTA', 'CERRADA'), ('RESUELTA', 'EN_ATENCION')) THEN
        RAISE EXCEPTION 'D09: Transición de alerta no permitida: % → %', OLD.estado, NEW.estado;
    END IF;
    CASE NEW.estado
        WHEN 'NOTIFICADA' THEN
            NEW.fecha_notificacion := fn_ahora(NEW.fecha_notificacion);
        WHEN 'EN_ATENCION' THEN
            NEW.fecha_atencion := coalesce(OLD.fecha_atencion, fn_ahora(NEW.fecha_atencion));
        WHEN 'ESCALADA' THEN
            NEW.nivel_escalamiento := OLD.nivel_escalamiento + 1;
            NEW.fecha_escalamiento := fn_ahora(NEW.fecha_escalamiento);
        WHEN 'RESUELTA' THEN
            IF coalesce(length(trim(NEW.accion_correctiva)), 0) < 5 THEN
                RAISE EXCEPTION 'CU08: Para resolver la alerta registre la acción correctiva';
            END IF;
            NEW.fecha_resolucion := fn_ahora(NEW.fecha_resolucion);
        WHEN 'CERRADA' THEN
            NEW.fecha_cierre := fn_ahora(NEW.fecha_cierre);
        ELSE
            NULL;
    END CASE;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_alerta_transicion BEFORE UPDATE ON alerta
    FOR EACH ROW EXECUTE FUNCTION fn_alerta_transicion();

CREATE FUNCTION fn_alerta_despues_actualizar() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.estado IS DISTINCT FROM OLD.estado OR NEW.id_asignado IS DISTINCT FROM OLD.id_asignado THEN
        PERFORM fn_evento('ALERTA_ACTUALIZADA', jsonb_build_object('id_alerta', NEW.id_alerta,
            'estado', NEW.estado, 'severidad', NEW.severidad, 'id_asignado', NEW.id_asignado));
    END IF;
    RETURN NULL;
END $$;

CREATE TRIGGER trg_alerta_despues_actualizar AFTER UPDATE ON alerta
    FOR EACH ROW EXECUTE FUNCTION fn_alerta_despues_actualizar();

-- Cierra en cadena (EN_ATENCION → RESUELTA → CERRADA) una alerta que ya no aplica
CREATE FUNCTION fn_cerrar_alerta_auto(p_alerta INT, p_motivo TEXT) RETURNS VOID
LANGUAGE plpgsql AS $$
DECLARE
    v_estado TEXT;
BEGIN
    SELECT estado INTO v_estado FROM alerta WHERE id_alerta = p_alerta;
    IF v_estado = 'GENERADA' THEN UPDATE alerta SET estado = 'NOTIFICADA' WHERE id_alerta = p_alerta; v_estado := 'NOTIFICADA'; END IF;
    IF v_estado IN ('NOTIFICADA', 'ESCALADA') THEN UPDATE alerta SET estado = 'EN_ATENCION' WHERE id_alerta = p_alerta; v_estado := 'EN_ATENCION'; END IF;
    IF v_estado = 'EN_ATENCION' THEN
        UPDATE alerta SET estado = 'RESUELTA', accion_correctiva = p_motivo WHERE id_alerta = p_alerta;
        v_estado := 'RESUELTA';
    END IF;
    IF v_estado = 'RESUELTA' THEN UPDATE alerta SET estado = 'CERRADA' WHERE id_alerta = p_alerta; END IF;
END $$;

-- ------------------------------------------------------------------ Auditorías (CU10, CU11, RN08)
CREATE FUNCTION fn_auditoria_antes() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    v_otra TEXT;
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.anio := coalesce(NEW.anio, extract(year FROM NEW.fecha_inicio)::smallint);
        NEW.codigo := coalesce(NEW.codigo, 'AUD-' || NEW.anio || '-' || lpad(nextval('seq_auditoria')::text, 3, '0'));
        IF NOT (fn_tiene_rol(NEW.id_auditor, 'AUDITOR_INTERNO') OR fn_tiene_rol(NEW.id_auditor, 'AUDITOR_FINANCIERO')) THEN
            RAISE EXCEPTION 'CU10: El auditor asignado debe tener rol de Auditor Interno o Auditor Financiero';
        END IF;
        SELECT codigo INTO v_otra FROM auditoria
        WHERE id_auditor = NEW.id_auditor AND estado <> 'CERRADA'
          AND daterange(fecha_inicio, fecha_fin, '[]') && daterange(NEW.fecha_inicio, NEW.fecha_fin, '[]')
        LIMIT 1;
        IF v_otra IS NOT NULL AND NOT fn_carga_inicial() THEN
            RAISE EXCEPTION 'CU10: El auditor no tiene disponibilidad: ya tiene la auditoría % en esas fechas', v_otra;
        END IF;
        RETURN NEW;
    END IF;
    IF NEW.estado <> OLD.estado AND (OLD.estado, NEW.estado) NOT IN (('PLANIFICADA', 'EN_EJECUCION'), ('EN_EJECUCION', 'CERRADA')) THEN
        RAISE EXCEPTION 'CU10: Transición de auditoría no permitida: % → %', OLD.estado, NEW.estado;
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_auditoria_antes BEFORE INSERT OR UPDATE ON auditoria
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria_antes();

CREATE FUNCTION fn_hallazgo_antes() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.id_registrador := coalesce(NEW.id_registrador, fn_usuario_actual());
        IF NOT fn_carga_inicial() THEN
            NEW.fecha_registro := CURRENT_DATE;
            IF NEW.fecha_limite < CURRENT_DATE THEN
                RAISE EXCEPTION 'CU11: La fecha límite no puede ser anterior a la fecha actual';
            END IF;
            IF NEW.id_auditoria IS NOT NULL
               AND (SELECT estado FROM auditoria WHERE id_auditoria = NEW.id_auditoria) <> 'EN_EJECUCION' THEN
                RAISE EXCEPTION 'CU11: Solo se registran hallazgos en auditorías EN_EJECUCION';
            END IF;
        END IF;
        IF NEW.id_conciliacion IS NOT NULL THEN
            IF (SELECT estado FROM conciliacion_kri WHERE id_conciliacion = NEW.id_conciliacion) <> 'CON_DIFERENCIA' THEN
                RAISE EXCEPTION 'RN18: Solo una conciliación CON_DIFERENCIA origina un hallazgo financiero';
            END IF;
            NEW.financiero := TRUE;
        END IF;
    END IF;
    -- RN08: hallazgo de severidad Alta vinculado a un riesgo y con plan de acción
    IF NEW.severidad = 'ALTA' AND (NEW.id_riesgo IS NULL OR coalesce(length(trim(NEW.plan_accion)), 0) < 5) THEN
        RAISE EXCEPTION 'RN08: Un hallazgo de severidad Alta debe vincularse a un riesgo y tener un plan de acción';
    END IF;
    IF TG_OP = 'UPDATE' AND NEW.estado = 'CERRADO' AND OLD.estado <> 'CERRADO' THEN
        NEW.fecha_cierre := fn_ahora(NEW.fecha_cierre);
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_hallazgo_antes BEFORE INSERT OR UPDATE ON hallazgo
    FOR EACH ROW EXECUTE FUNCTION fn_hallazgo_antes();

CREATE FUNCTION fn_hallazgo_despues_insertar() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.id_conciliacion IS NOT NULL THEN
        UPDATE conciliacion_kri SET hallazgo_registrado = TRUE WHERE id_conciliacion = NEW.id_conciliacion;
    END IF;
    PERFORM fn_notificar(NEW.id_responsable, 'Hallazgo asignado',
        format('Hallazgo de severidad %s: %s (fecha límite %s).', NEW.severidad, left(NEW.descripcion, 160),
               to_char(NEW.fecha_limite, 'DD/MM/YYYY')), 'TAREA', '/auditorias');
    RETURN NULL;
END $$;

CREATE TRIGGER trg_hallazgo_despues_insertar AFTER INSERT ON hallazgo
    FOR EACH ROW EXECUTE FUNCTION fn_hallazgo_despues_insertar();

-- ------------------------------------------------------------------ Validación financiera (CU17, RN16, RN17)
-- Mismo nombre y regla que el extracto del diseño físico (sección 10.2)
CREATE FUNCTION chk_independencia() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    e evaluacion_riesgo;
    r riesgo;
BEGIN
    SELECT * INTO e FROM evaluacion_riesgo WHERE id_evaluacion = NEW.id_evaluacion;
    SELECT * INTO r FROM riesgo WHERE id_riesgo = e.id_riesgo;
    IF NOT fn_tiene_rol(NEW.id_auditor_fin, 'AUDITOR_FINANCIERO') THEN
        RAISE EXCEPTION 'RN17: Solo un usuario con rol Auditor Financiero puede validar el impacto financiero';
    END IF;
    IF r.id_responsable = NEW.id_auditor_fin OR e.id_evaluador = NEW.id_auditor_fin THEN
        RAISE EXCEPTION 'RN17: conflicto de funciones: el auditor es responsable o evaluador del riesgo %', r.codigo;
    END IF;
    IF e.estado_validacion <> 'PENDIENTE' THEN
        RAISE EXCEPTION 'CU17: La evaluación no está pendiente de validación (estado %)', e.estado_validacion;
    END IF;
    -- Contraste con el tipo de cambio del ERP en la fecha de la evaluación
    NEW.tipo_cambio := coalesce(NEW.tipo_cambio, fn_tipo_cambio(e.moneda, e.fecha), e.tipo_cambio, 1);
    NEW.impacto_usd_validado := coalesce(NEW.impacto_usd_validado,
                                         round(coalesce(e.impacto_financiero, 0) / NEW.tipo_cambio, 2));
    NEW.fecha := fn_ahora(NEW.fecha);
    RETURN NEW;
END $$;

CREATE TRIGGER trg_independencia BEFORE INSERT ON validacion_financiera
    FOR EACH ROW EXECUTE FUNCTION chk_independencia();

CREATE FUNCTION fn_validacion_despues_insertar() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    e evaluacion_riesgo;
    r riesgo;
BEGIN
    UPDATE evaluacion_riesgo SET estado_validacion = NEW.resultado WHERE id_evaluacion = NEW.id_evaluacion;
    SELECT * INTO e FROM evaluacion_riesgo WHERE id_evaluacion = NEW.id_evaluacion;
    SELECT * INTO r FROM riesgo WHERE id_riesgo = e.id_riesgo;
    -- S05: aviso de validación al Gestor y al Director
    PERFORM fn_notificar(e.id_evaluador,
        CASE NEW.resultado WHEN 'VALIDADA' THEN 'Impacto financiero validado' ELSE 'Impacto financiero observado' END,
        format('%s · %s: %s%s', r.codigo, r.titulo, NEW.resultado,
               CASE WHEN NEW.resultado = 'OBSERVADA' THEN '. Debe reevaluar el riesgo (CU02): ' || NEW.observacion ELSE '' END),
        'VALIDACION', '/riesgos/' || r.id_riesgo);
    PERFORM fn_notificar_rol('DIRECTOR', NULL, 'Resultado de validación financiera',
        format('%s · %s quedó %s.', r.codigo, r.titulo, NEW.resultado), 'VALIDACION', '/riesgos/' || r.id_riesgo);
    PERFORM fn_evento('VALIDACION', jsonb_build_object('id_riesgo', r.id_riesgo, 'codigo', r.codigo, 'resultado', NEW.resultado));
    RETURN NULL;
END $$;

CREATE TRIGGER trg_validacion_despues_insertar AFTER INSERT ON validacion_financiera
    FOR EACH ROW EXECUTE FUNCTION fn_validacion_despues_insertar();

-- ------------------------------------------------------------------ Conciliación de KRI (CU18, RN18)
CREATE FUNCTION fn_conciliacion_antes() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    k     indicador_kri;
    v_dif NUMERIC;
BEGIN
    SELECT * INTO k FROM indicador_kri WHERE id_kri = NEW.id_kri;
    IF NOT k.financiero THEN
        RAISE EXCEPTION 'CU18: Solo se concilian indicadores financieros (% no lo es)', k.nombre;
    END IF;
    IF NOT fn_tiene_rol(NEW.id_auditor_fin, 'AUDITOR_FINANCIERO') THEN
        RAISE EXCEPTION 'CU18: La conciliación la realiza un usuario con rol Auditor Financiero';
    END IF;
    v_dif := CASE WHEN NEW.valor_erp = 0 THEN CASE WHEN NEW.valor_sistema = 0 THEN 0 ELSE 100 END
                  ELSE abs(NEW.valor_sistema - NEW.valor_erp) / abs(NEW.valor_erp) * 100 END;
    NEW.estado := CASE WHEN v_dif <= fn_param('tolerancia_conciliacion_pct') THEN 'CONCILIADA' ELSE 'CON_DIFERENCIA' END;
    NEW.fecha := fn_ahora(NEW.fecha);
    RETURN NEW;
END $$;

CREATE TRIGGER trg_conciliacion_antes BEFORE INSERT ON conciliacion_kri
    FOR EACH ROW EXECUTE FUNCTION fn_conciliacion_antes();

CREATE FUNCTION fn_conciliacion_despues() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    k indicador_kri;
    r riesgo;
    f filial;
BEGIN
    IF NEW.estado <> 'CON_DIFERENCIA' OR fn_carga_inicial() THEN RETURN NULL; END IF;
    SELECT * INTO k FROM indicador_kri WHERE id_kri = NEW.id_kri;
    SELECT * INTO r FROM riesgo WHERE id_riesgo = k.id_riesgo;
    SELECT * INTO f FROM filial WHERE id_filial = r.id_filial;
    INSERT INTO alerta (id_riesgo, id_kri, id_conciliacion, id_asignado, tipo, severidad, mensaje)
    VALUES (r.id_riesgo, k.id_kri, NEW.id_conciliacion, NEW.id_auditor_fin, 'CONCILIACION_DIFERENCIA', 'ADVERTENCIA',
            format('Conciliación %s %s: %s difiere %s %% del ERP (tolerancia %s %%). Registre el hallazgo financiero (RN18).',
                   f.nombre, NEW.periodo, k.nombre, trim_scale(NEW.diferencia_pct), trim_scale(fn_param('tolerancia_conciliacion_pct'))));
    RETURN NULL;
END $$;

CREATE TRIGGER trg_conciliacion_despues AFTER INSERT ON conciliacion_kri
    FOR EACH ROW EXECUTE FUNCTION fn_conciliacion_despues();

-- ------------------------------------------------------------------ Cumplimiento (CU12, CU13, RN09, RN10)
CREATE FUNCTION fn_alerta_obligacion(o obligacion) RETURNS BOOLEAN
LANGUAGE plpgsql AS $$
DECLARE
    v_oficial INT;
    n         normativa;
    f         filial;
    v_dias    INT := o.fecha_vencimiento - CURRENT_DATE;
BEGIN
    IF o.estado_cumplimiento <> 'PENDIENTE' OR v_dias > fn_param('aviso_obligacion_dias') THEN RETURN FALSE; END IF;
    IF EXISTS (SELECT 1 FROM alerta WHERE id_obligacion = o.id_obligacion AND estado NOT IN ('RESUELTA', 'CERRADA')) THEN
        RETURN FALSE;
    END IF;
    SELECT * INTO n FROM normativa WHERE id_normativa = o.id_normativa;
    SELECT * INTO f FROM filial WHERE id_filial = o.id_filial;
    SELECT u.id_usuario INTO v_oficial
    FROM usuario u JOIN usuario_rol ur USING (id_usuario) JOIN rol r USING (id_rol)
    WHERE r.codigo = 'OFICIAL_CUMPLIMIENTO' AND u.activo
    ORDER BY (u.id_filial = o.id_filial) DESC, u.id_usuario LIMIT 1;
    IF v_oficial IS NULL THEN RETURN FALSE; END IF;
    INSERT INTO alerta (id_obligacion, id_asignado, tipo, severidad, mensaje)
    VALUES (o.id_obligacion, v_oficial, 'OBLIGACION_POR_VENCER',
            CASE WHEN v_dias < 0 THEN 'ADVERTENCIA' ELSE 'PREVENTIVA' END,
            format('%s (%s): "%s" %s el %s (%s días) — RN09', n.nombre, f.nombre, o.descripcion,
                   CASE WHEN v_dias < 0 THEN 'venció' ELSE 'vence' END,
                   to_char(o.fecha_vencimiento, 'DD/MM/YYYY'), v_dias));
    RETURN TRUE;
END $$;

CREATE FUNCTION fn_obligacion_despues() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NOT fn_carga_inicial() THEN
        PERFORM fn_alerta_obligacion(NEW);
    END IF;
    RETURN NULL;
END $$;

CREATE TRIGGER trg_obligacion_despues AFTER INSERT OR UPDATE OF fecha_vencimiento ON obligacion
    FOR EACH ROW EXECUTE FUNCTION fn_obligacion_despues();

CREATE FUNCTION fn_eval_cumplimiento_antes() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    NEW.id_evaluador := coalesce(NEW.id_evaluador, fn_usuario_actual());
    NEW.porcentaje := coalesce(NEW.porcentaje, CASE NEW.resultado WHEN 'CUMPLE' THEN 100 WHEN 'PARCIAL' THEN 50 ELSE 0 END);
    IF NOT fn_carga_inicial() THEN NEW.fecha := CURRENT_DATE; END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_eval_cumplimiento_antes BEFORE INSERT ON evaluacion_cumplimiento
    FOR EACH ROW EXECUTE FUNCTION fn_eval_cumplimiento_antes();

-- RN10: una obligación que "No cumple" se registra automáticamente como riesgo Legal
CREATE FUNCTION fn_eval_cumplimiento_despues() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    o          obligacion;
    n          normativa;
    v_resp     INT;
    v_riesgo   INT;
    v_titulo   TEXT;
    v_alerta   INT;
BEGIN
    UPDATE obligacion SET estado_cumplimiento = NEW.resultado WHERE id_obligacion = NEW.id_obligacion;
    SELECT * INTO o FROM obligacion WHERE id_obligacion = NEW.id_obligacion;
    SELECT * INTO n FROM normativa WHERE id_normativa = o.id_normativa;

    -- Si se cumple, se cierran los avisos de vencimiento abiertos
    IF NEW.resultado = 'CUMPLE' AND NOT fn_carga_inicial() THEN
        FOR v_alerta IN SELECT id_alerta FROM alerta
                        WHERE id_obligacion = o.id_obligacion AND tipo = 'OBLIGACION_POR_VENCER'
                          AND estado NOT IN ('RESUELTA', 'CERRADA') LOOP
            PERFORM fn_cerrar_alerta_auto(v_alerta, 'Obligación evaluada como CUMPLE');
        END LOOP;
    END IF;

    IF NEW.resultado = 'NO_CUMPLE' AND o.id_riesgo_generado IS NULL THEN
        SELECT u.id_usuario INTO v_resp
        FROM usuario u JOIN usuario_rol ur USING (id_usuario) JOIN rol r USING (id_rol)
        WHERE u.activo AND ((r.codigo = 'GERENTE_FILIAL' AND u.id_filial = o.id_filial) OR r.codigo = 'OFICIAL_CUMPLIMIENTO')
        ORDER BY (r.codigo = 'GERENTE_FILIAL') DESC, u.id_usuario LIMIT 1;
        v_titulo := left('Incumplimiento: ' || o.descripcion, 200);
        IF EXISTS (SELECT 1 FROM riesgo WHERE id_filial = o.id_filial AND lower(titulo) = lower(v_titulo)) THEN
            v_titulo := left('Incumplimiento ' || n.nombre || ': ' || o.descripcion, 200);
        END IF;
        INSERT INTO riesgo (id_filial, id_responsable, id_registrador, titulo, descripcion, categoria, proceso,
                            causa, consecuencia, origen, fecha_registro)
        VALUES (o.id_filial, v_resp, NEW.id_evaluador, v_titulo,
                format('Brecha detectada al evaluar el cumplimiento de %s (%s, %s).', n.nombre, n.organismo, n.jurisdiccion),
                'LEGAL', 'Cumplimiento normativo',
                coalesce(NULLIF(trim(NEW.observacion), ''), 'La obligación fue evaluada como No cumple'),
                'Multas, sanciones y daño reputacional ante ' || n.organismo, 'CUMPLIMIENTO', NEW.fecha)
        RETURNING id_riesgo INTO v_riesgo;
        UPDATE obligacion SET id_riesgo_generado = v_riesgo WHERE id_obligacion = o.id_obligacion;
        IF NOT fn_carga_inicial() THEN
            INSERT INTO alerta (id_riesgo, id_obligacion, id_asignado, tipo, severidad, mensaje)
            SELECT v_riesgo, o.id_obligacion, v_resp, 'RIESGO_LEGAL', 'CRITICA',
                   format('%s · Riesgo legal registrado por incumplimiento de %s: %s (RN10)', codigo, n.nombre, o.descripcion)
            FROM riesgo WHERE id_riesgo = v_riesgo;
        END IF;
    END IF;
    PERFORM fn_evento('CUMPLIMIENTO', jsonb_build_object('id_obligacion', o.id_obligacion, 'resultado', NEW.resultado,
        'id_filial', o.id_filial, 'id_riesgo_generado', v_riesgo));
    RETURN NULL;
END $$;

CREATE TRIGGER trg_eval_cumplimiento_despues AFTER INSERT ON evaluacion_cumplimiento
    FOR EACH ROW EXECUTE FUNCTION fn_eval_cumplimiento_despues();

-- ------------------------------------------------------------------ Usuarios y roles (CU15, RN17)
CREATE FUNCTION fn_usuario_rol_sod() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    v_codigo TEXT := (SELECT codigo FROM rol WHERE id_rol = NEW.id_rol);
BEGIN
    IF (v_codigo = 'AUDITOR_FINANCIERO' AND fn_tiene_rol(NEW.id_usuario, 'GESTOR'))
       OR (v_codigo = 'GESTOR' AND fn_tiene_rol(NEW.id_usuario, 'AUDITOR_FINANCIERO')) THEN
        RAISE EXCEPTION 'RN17: El rol Auditor Financiero no se combina con Gestor de Riesgos (separación de funciones)';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_usuario_rol_sod BEFORE INSERT ON usuario_rol
    FOR EACH ROW EXECUTE FUNCTION fn_usuario_rol_sod();

-- ------------------------------------------------------------------ Tareas programadas (servidor de trabajos)
-- Genera los avisos que dependen del paso del tiempo. La API la invoca periódicamente.
CREATE FUNCTION fn_generar_alertas_programadas() RETURNS INT
LANGUAGE plpgsql AS $$
DECLARE
    v_total   INT := 0;
    a         RECORD;
    o         obligacion;
    v_auditor INT;
BEGIN
    -- CU05: acciones vencidas
    FOR a IN
        SELECT ac.id_accion, ac.id_responsable, ac.descripcion, ac.fecha_limite, ac.avance, r.id_riesgo, r.codigo
        FROM accion_mitigacion ac JOIN plan_mitigacion p USING (id_plan) JOIN riesgo r USING (id_riesgo)
        WHERE ac.estado <> 'CERRADA' AND p.estado = 'APROBADO' AND ac.fecha_limite < CURRENT_DATE
          AND NOT EXISTS (SELECT 1 FROM alerta al WHERE al.id_accion = ac.id_accion AND al.estado NOT IN ('RESUELTA', 'CERRADA'))
    LOOP
        INSERT INTO alerta (id_riesgo, id_accion, id_asignado, tipo, severidad, mensaje)
        VALUES (a.id_riesgo, a.id_accion, a.id_responsable, 'ACCION_VENCIDA', 'ADVERTENCIA',
                format('%s · La acción "%s" venció el %s con %s %% de avance', a.codigo, a.descripcion,
                       to_char(a.fecha_limite, 'DD/MM/YYYY'), a.avance));
        v_total := v_total + 1;
    END LOOP;

    -- RN09: obligaciones que vencen en 30 días o menos
    FOR o IN SELECT * FROM obligacion WHERE estado_cumplimiento = 'PENDIENTE'
             AND fecha_vencimiento <= CURRENT_DATE + fn_param('aviso_obligacion_dias')::int LOOP
        IF fn_alerta_obligacion(o) THEN v_total := v_total + 1; END IF;
    END LOOP;

    -- RN16: validación financiera fuera del plazo de 3 días hábiles
    SELECT u.id_usuario INTO v_auditor FROM usuario u JOIN usuario_rol ur USING (id_usuario) JOIN rol r USING (id_rol)
    WHERE r.codigo = 'AUDITOR_FINANCIERO' AND u.activo ORDER BY u.id_usuario LIMIT 1;
    FOR a IN
        SELECT e.id_evaluacion, e.fecha, r.id_riesgo, r.codigo, r.titulo
        FROM evaluacion_riesgo e JOIN riesgo r USING (id_riesgo)
        WHERE e.estado_validacion = 'PENDIENTE'
          AND fn_sumar_dias_habiles(e.fecha, fn_param('plazo_validacion_dias_habiles')::int) < CURRENT_DATE
          AND NOT EXISTS (SELECT 1 FROM alerta al WHERE al.id_evaluacion = e.id_evaluacion
                          AND al.tipo = 'VALIDACION_FUERA_PLAZO' AND al.estado NOT IN ('RESUELTA', 'CERRADA'))
    LOOP
        IF v_auditor IS NOT NULL THEN
            INSERT INTO alerta (id_riesgo, id_evaluacion, id_asignado, tipo, severidad, mensaje)
            VALUES (a.id_riesgo, a.id_evaluacion, v_auditor, 'VALIDACION_FUERA_PLAZO', 'ADVERTENCIA',
                    format('%s · La validación financiera de "%s" superó los %s días hábiles (RN16)',
                           a.codigo, a.titulo, fn_param('plazo_validacion_dias_habiles')));
            v_total := v_total + 1;
        END IF;
    END LOOP;

    -- RF14: hallazgos vencidos
    FOR a IN
        SELECT h.id_hallazgo, h.id_responsable, h.id_riesgo, h.descripcion, h.fecha_limite
        FROM hallazgo h
        WHERE h.estado <> 'CERRADO' AND h.fecha_limite < CURRENT_DATE
          AND NOT EXISTS (SELECT 1 FROM alerta al WHERE al.id_hallazgo = h.id_hallazgo AND al.estado NOT IN ('RESUELTA', 'CERRADA'))
    LOOP
        INSERT INTO alerta (id_riesgo, id_hallazgo, id_asignado, tipo, severidad, mensaje)
        VALUES (a.id_riesgo, a.id_hallazgo, a.id_responsable, 'HALLAZGO_VENCIDO', 'ADVERTENCIA',
                format('Hallazgo vencido el %s: %s (RF14)', to_char(a.fecha_limite, 'DD/MM/YYYY'), left(a.descripcion, 200)));
        v_total := v_total + 1;
    END LOOP;
    RETURN v_total;
END $$;

-- ------------------------------------------------------------------ Bitácora: tablas auditadas (RN13)
CREATE TRIGGER trg_auditar AFTER INSERT OR UPDATE OR DELETE ON riesgo
    FOR EACH ROW EXECUTE FUNCTION fn_auditar('id_riesgo');
CREATE TRIGGER trg_auditar AFTER INSERT OR UPDATE ON evaluacion_riesgo
    FOR EACH ROW EXECUTE FUNCTION fn_auditar('id_evaluacion');
CREATE TRIGGER trg_auditar AFTER INSERT OR UPDATE OR DELETE ON control
    FOR EACH ROW EXECUTE FUNCTION fn_auditar('id_control');
CREATE TRIGGER trg_auditar AFTER INSERT OR UPDATE ON plan_mitigacion
    FOR EACH ROW EXECUTE FUNCTION fn_auditar('id_plan');
CREATE TRIGGER trg_auditar AFTER INSERT OR UPDATE OR DELETE ON accion_mitigacion
    FOR EACH ROW EXECUTE FUNCTION fn_auditar('id_accion');
CREATE TRIGGER trg_auditar AFTER INSERT OR UPDATE ON indicador_kri
    FOR EACH ROW EXECUTE FUNCTION fn_auditar('id_kri');
CREATE TRIGGER trg_auditar AFTER INSERT OR UPDATE ON alerta
    FOR EACH ROW EXECUTE FUNCTION fn_auditar('id_alerta');
CREATE TRIGGER trg_auditar AFTER INSERT OR UPDATE ON auditoria
    FOR EACH ROW EXECUTE FUNCTION fn_auditar('id_auditoria');
CREATE TRIGGER trg_auditar AFTER INSERT OR UPDATE ON hallazgo
    FOR EACH ROW EXECUTE FUNCTION fn_auditar('id_hallazgo');
CREATE TRIGGER trg_auditar AFTER INSERT ON validacion_financiera
    FOR EACH ROW EXECUTE FUNCTION fn_auditar('id_validacion');
CREATE TRIGGER trg_auditar AFTER INSERT OR UPDATE ON conciliacion_kri
    FOR EACH ROW EXECUTE FUNCTION fn_auditar('id_conciliacion');
CREATE TRIGGER trg_auditar AFTER INSERT OR UPDATE ON normativa
    FOR EACH ROW EXECUTE FUNCTION fn_auditar('id_normativa');
CREATE TRIGGER trg_auditar AFTER INSERT OR UPDATE ON obligacion
    FOR EACH ROW EXECUTE FUNCTION fn_auditar('id_obligacion');
CREATE TRIGGER trg_auditar AFTER INSERT ON evaluacion_cumplimiento
    FOR EACH ROW EXECUTE FUNCTION fn_auditar('id_eval_cump');
CREATE TRIGGER trg_auditar AFTER INSERT OR DELETE ON evidencia
    FOR EACH ROW EXECUTE FUNCTION fn_auditar('id_evidencia');
CREATE TRIGGER trg_auditar AFTER INSERT OR UPDATE ON usuario
    FOR EACH ROW EXECUTE FUNCTION fn_auditar('id_usuario');
CREATE TRIGGER trg_auditar AFTER INSERT OR DELETE ON usuario_rol
    FOR EACH ROW EXECUTE FUNCTION fn_auditar('id_usuario');
CREATE TRIGGER trg_auditar AFTER INSERT OR UPDATE ON filial
    FOR EACH ROW EXECUTE FUNCTION fn_auditar('id_filial');
CREATE TRIGGER trg_auditar AFTER UPDATE ON parametro
    FOR EACH ROW EXECUTE FUNCTION fn_auditar('clave');

-- ------------------------------------------------------------------ Vistas de consulta
CREATE VIEW v_riesgo AS
SELECT r.id_riesgo, r.codigo, r.titulo, r.descripcion, r.categoria, r.proceso, r.causa, r.consecuencia,
       r.fecha_registro, r.estado, r.origen, r.id_filial, r.id_responsable, r.id_registrador,
       f.codigo AS filial_codigo, f.nombre AS filial, f.pais, f.codigo_pais, f.region, f.moneda AS moneda_filial,
       f.apetito_riesgo,
       u.nombre_completo AS responsable, rg.nombre_completo AS registrador,
       e.id_evaluacion, e.fecha AS fecha_evaluacion, e.tipo AS tipo_evaluacion, e.probabilidad, e.impacto,
       e.valor, e.nivel, e.impacto_financiero, e.moneda, e.tipo_cambio, e.impacto_usd, e.vigente_hasta,
       e.estado_validacion::text AS estado_validacion,
       (e.vigente_hasta >= CURRENT_DATE) AS evaluacion_vigente,
       p.id_plan, p.estrategia, p.estado AS estado_plan,
       (SELECT round(avg(ac.avance)) FROM accion_mitigacion ac WHERE ac.id_plan = p.id_plan) AS avance_plan,
       (SELECT count(*) FROM alerta a WHERE a.id_riesgo = r.id_riesgo AND a.estado NOT IN ('RESUELTA', 'CERRADA')) AS alertas_abiertas
FROM riesgo r
JOIN filial f ON f.id_filial = r.id_filial
JOIN usuario u ON u.id_usuario = r.id_responsable
LEFT JOIN usuario rg ON rg.id_usuario = r.id_registrador
LEFT JOIN LATERAL (SELECT * FROM evaluacion_riesgo ev WHERE ev.id_riesgo = r.id_riesgo
                   ORDER BY ev.fecha DESC, ev.id_evaluacion DESC LIMIT 1) e ON TRUE
LEFT JOIN plan_mitigacion p ON p.id_riesgo = r.id_riesgo;

CREATE VIEW v_alerta AS
SELECT a.*, r.codigo AS riesgo_codigo, r.titulo AS riesgo_titulo, r.categoria,
       coalesce(r.id_filial, o.id_filial) AS id_filial, f.nombre AS filial, f.codigo AS filial_codigo,
       u.nombre_completo AS asignado, uo.nombre_completo AS asignado_original,
       k.nombre AS kri, k.unidad,
       extract(epoch FROM (a.fecha_limite - now()))::bigint AS segundos_restantes,
       (a.estado NOT IN ('RESUELTA', 'CERRADA') AND a.fecha_limite < now()) AS sla_vencido
FROM alerta a
LEFT JOIN riesgo r ON r.id_riesgo = a.id_riesgo
LEFT JOIN obligacion o ON o.id_obligacion = a.id_obligacion
LEFT JOIN filial f ON f.id_filial = coalesce(r.id_filial, o.id_filial)
JOIN usuario u ON u.id_usuario = a.id_asignado
LEFT JOIN usuario uo ON uo.id_usuario = a.id_asignado_original
LEFT JOIN indicador_kri k ON k.id_kri = a.id_kri;

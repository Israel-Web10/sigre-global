-- =====================================================================================
-- SIGRE Global · V7: CU17, excepción «ERP no disponible»
-- El informe (CU17, Excepciones) dice: «ERP no disponible: se permite posponer la validación
-- sin vencer el plazo de 3 días hábiles». Mientras la validación está pospuesta, el plazo de
-- RN16 queda en pausa. Se reanuda cuando:
--   * se resuelve la incidencia del ERP que abrió la posposición (Integraciones),
--   * el Auditor Financiero pulsa «Reanudar plazo», o
--   * se registra la validación (VALIDADA u OBSERVADA).
-- =====================================================================================

CREATE TABLE posposicion_validacion (
    id_posposicion SERIAL PRIMARY KEY,
    id_evaluacion  INT          NOT NULL REFERENCES evaluacion_riesgo (id_evaluacion),
    id_auditor_fin INT          NOT NULL REFERENCES usuario (id_usuario),
    id_incidencia  INT          REFERENCES incidencia_integracion (id_incidencia),
    motivo         VARCHAR(300) NOT NULL CHECK (length(btrim(motivo)) >= 10),
    desde          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    hasta          TIMESTAMPTZ,
    CHECK (hasta IS NULL OR hasta >= desde)
);
-- Una evaluación tiene a lo sumo una posposición abierta
CREATE UNIQUE INDEX uq_posposicion_abierta ON posposicion_validacion (id_evaluacion) WHERE hasta IS NULL;
CREATE INDEX idx_posposicion_incidencia ON posposicion_validacion (id_incidencia);

-- Días hábiles en pausa: los que pasan después del día en que se pospuso y hasta el día en que
-- se reanuda (si sigue pospuesta, hasta hoy). Así los días que le quedaban al auditor no se pierden.
CREATE FUNCTION fn_dias_pospuestos(p_evaluacion INT) RETURNS INT
LANGUAGE sql STABLE AS $$
    SELECT coalesce(sum((SELECT count(*) FROM generate_series(p.desde::date + 1, coalesce(p.hasta, now())::date, interval '1 day') d
                         WHERE extract(isodow FROM d) < 6)), 0)::int
    FROM posposicion_validacion p WHERE p.id_evaluacion = p_evaluacion
$$;

-- RN16: fecha límite de la validación = fecha de evaluación + 3 días hábiles + días en pausa
CREATE FUNCTION fn_plazo_validacion(p_evaluacion INT) RETURNS DATE
LANGUAGE sql STABLE AS $$
    SELECT fn_sumar_dias_habiles(e.fecha::date,
                                 fn_param('plazo_validacion_dias_habiles')::int + fn_dias_pospuestos(e.id_evaluacion))
    FROM evaluacion_riesgo e WHERE e.id_evaluacion = p_evaluacion
$$;

CREATE FUNCTION fn_posposicion_antes_insertar() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    e evaluacion_riesgo;
    r riesgo;
BEGIN
    SELECT * INTO e FROM evaluacion_riesgo WHERE id_evaluacion = NEW.id_evaluacion;
    SELECT * INTO r FROM riesgo WHERE id_riesgo = e.id_riesgo;
    IF NOT fn_tiene_rol(NEW.id_auditor_fin, 'AUDITOR_FINANCIERO') THEN
        RAISE EXCEPTION 'RN17: Solo un usuario con rol Auditor Financiero puede posponer la validación financiera';
    END IF;
    IF r.id_responsable = NEW.id_auditor_fin OR e.id_evaluador = NEW.id_auditor_fin THEN
        RAISE EXCEPTION 'RN17: conflicto de funciones: el auditor es responsable o evaluador del riesgo %', r.codigo;
    END IF;
    IF e.estado_validacion <> 'PENDIENTE' THEN
        RAISE EXCEPTION 'CU17: La evaluación no está pendiente de validación (estado %)', e.estado_validacion;
    END IF;
    IF EXISTS (SELECT 1 FROM posposicion_validacion WHERE id_evaluacion = NEW.id_evaluacion AND hasta IS NULL) THEN
        RAISE EXCEPTION 'CU17: La validación de % ya está pospuesta por ERP no disponible', r.codigo;
    END IF;
    IF NOT fn_carga_inicial() AND fn_plazo_validacion(NEW.id_evaluacion) < CURRENT_DATE THEN
        RAISE EXCEPTION 'RN16: La validación de % ya superó el plazo de % días hábiles; solo se puede posponer dentro del plazo',
            r.codigo, fn_param('plazo_validacion_dias_habiles');
    END IF;
    NEW.motivo := btrim(NEW.motivo);
    NEW.desde := fn_ahora(NEW.desde);
    NEW.hasta := NULL;
    -- Como en CU18, la falla del ERP queda registrada como incidencia de integración
    IF NEW.id_incidencia IS NULL THEN
        INSERT INTO incidencia_integracion (sistema, operacion, mensaje)
        VALUES ('ERP', 'TIPO_CAMBIO_VALIDACION',
                left(format('%s · ERP no disponible para contrastar el tipo de cambio (CU17): %s', r.codigo, NEW.motivo), 400))
        RETURNING id_incidencia INTO NEW.id_incidencia;
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_posposicion_antes_insertar BEFORE INSERT ON posposicion_validacion
    FOR EACH ROW EXECUTE FUNCTION fn_posposicion_antes_insertar();

-- De una posposición solo se registra el momento en que se reanuda (una vez)
CREATE FUNCTION fn_posposicion_antes_actualizar() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF fn_carga_inicial() THEN RETURN NEW; END IF;
    IF OLD.hasta IS NOT NULL THEN
        RAISE EXCEPTION 'CU17: La posposición ya fue cerrada y no se puede modificar';
    END IF;
    IF (NEW.id_evaluacion, NEW.id_auditor_fin, NEW.id_incidencia, NEW.motivo, NEW.desde)
       IS DISTINCT FROM (OLD.id_evaluacion, OLD.id_auditor_fin, OLD.id_incidencia, OLD.motivo, OLD.desde) THEN
        RAISE EXCEPTION 'CU17: De una posposición solo se registra el momento en que se reanuda el plazo';
    END IF;
    IF NEW.hasta IS NOT NULL THEN
        NEW.hasta := greatest(now(), OLD.desde);
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_posposicion_antes_actualizar BEFORE UPDATE ON posposicion_validacion
    FOR EACH ROW EXECUTE FUNCTION fn_posposicion_antes_actualizar();

CREATE FUNCTION fn_posposicion_despues() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    e evaluacion_riesgo;
    r riesgo;
BEGIN
    SELECT * INTO e FROM evaluacion_riesgo WHERE id_evaluacion = NEW.id_evaluacion;
    SELECT * INTO r FROM riesgo WHERE id_riesgo = e.id_riesgo;
    IF TG_OP = 'INSERT' THEN
        PERFORM fn_notificar(e.id_evaluador, 'Validación financiera pospuesta',
            format('%s · %s: el ERP no está disponible. El plazo de %s días hábiles queda en pausa (CU17).',
                   r.codigo, r.titulo, fn_param('plazo_validacion_dias_habiles')),
            'VALIDACION', '/riesgos/' || r.id_riesgo);
        PERFORM fn_notificar_rol('DIRECTOR', NULL, 'Validación financiera pospuesta',
            format('%s · %s: pospuesta por ERP no disponible. Motivo: %s', r.codigo, r.titulo, NEW.motivo),
            'VALIDACION', '/riesgos/' || r.id_riesgo);
        PERFORM fn_evento('VALIDACION', jsonb_build_object('id_riesgo', r.id_riesgo, 'codigo', r.codigo, 'resultado', 'POSPUESTA'));
    ELSIF OLD.hasta IS NULL AND NEW.hasta IS NOT NULL AND e.estado_validacion = 'PENDIENTE' THEN
        PERFORM fn_notificar(NEW.id_auditor_fin, 'Plazo de validación reanudado',
            format('%s · %s: el ERP volvió a estar disponible. Nueva fecha límite: %s (RN16).',
                   r.codigo, r.titulo, to_char(fn_plazo_validacion(NEW.id_evaluacion), 'DD/MM/YYYY')),
            'VALIDACION', '/validacion-financiera');
        PERFORM fn_evento('VALIDACION', jsonb_build_object('id_riesgo', r.id_riesgo, 'codigo', r.codigo, 'resultado', 'REANUDADA'));
    END IF;
    RETURN NULL;
END $$;

CREATE TRIGGER trg_posposicion_despues AFTER INSERT OR UPDATE ON posposicion_validacion
    FOR EACH ROW EXECUTE FUNCTION fn_posposicion_despues();

-- RN13: bitácora con valores antes y después (POSPONER_VALIDACION / REANUDAR_VALIDACION)
CREATE TRIGGER trg_auditar AFTER INSERT OR UPDATE ON posposicion_validacion
    FOR EACH ROW EXECUTE FUNCTION fn_auditar('id_posposicion');

-- Al resolverse la incidencia del ERP, el plazo se reanuda solo
CREATE FUNCTION fn_incidencia_resuelta() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    UPDATE posposicion_validacion SET hasta = now() WHERE id_incidencia = NEW.id_incidencia AND hasta IS NULL;
    RETURN NULL;
END $$;

CREATE TRIGGER trg_incidencia_resuelta AFTER UPDATE OF resuelta ON incidencia_integracion
    FOR EACH ROW WHEN (NEW.resuelta AND NOT OLD.resuelta) EXECUTE FUNCTION fn_incidencia_resuelta();

-- Registrar la validación cierra la posposición y su incidencia. El nombre del trigger lo hace
-- correr después de trg_validacion_despues_insertar (los AFTER se disparan en orden alfabético),
-- cuando la evaluación ya no está PENDIENTE y no corresponde avisar «plazo reanudado».
CREATE FUNCTION fn_validacion_cierra_posposicion() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    UPDATE incidencia_integracion SET resuelta = TRUE
    WHERE NOT resuelta AND id_incidencia IN (SELECT id_incidencia FROM posposicion_validacion
                                             WHERE id_evaluacion = NEW.id_evaluacion AND hasta IS NULL);
    UPDATE posposicion_validacion SET hasta = now() WHERE id_evaluacion = NEW.id_evaluacion AND hasta IS NULL;
    RETURN NULL;
END $$;

CREATE TRIGGER trg_validacion_posposicion AFTER INSERT ON validacion_financiera
    FOR EACH ROW EXECUTE FUNCTION fn_validacion_cierra_posposicion();

-- Nombre de la operación en la bitácora
CREATE OR REPLACE FUNCTION fn_nombre_operacion(p_tabla TEXT, p_op TEXT, p_cambios JSONB, p_fila JSONB) RETURNS TEXT
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
        WHEN 'posposicion_validacion' THEN RETURN CASE WHEN p_op = 'INSERT'
            THEN 'POSPONER_VALIDACION' ELSE 'REANUDAR_VALIDACION' END;
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

-- Alertas programadas: la validación vence según fn_plazo_validacion (descuenta la pausa)
CREATE OR REPLACE FUNCTION fn_generar_alertas_programadas() RETURNS INT
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

    -- RN16: validación financiera fuera del plazo de 3 días hábiles (sin contar los días en pausa, CU17)
    SELECT u.id_usuario INTO v_auditor FROM usuario u JOIN usuario_rol ur USING (id_usuario) JOIN rol r USING (id_rol)
    WHERE r.codigo = 'AUDITOR_FINANCIERO' AND u.activo ORDER BY u.id_usuario LIMIT 1;
    FOR a IN
        SELECT e.id_evaluacion, e.fecha, r.id_riesgo, r.codigo, r.titulo
        FROM evaluacion_riesgo e JOIN riesgo r USING (id_riesgo)
        WHERE e.estado_validacion = 'PENDIENTE'
          AND fn_plazo_validacion(e.id_evaluacion) < CURRENT_DATE
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

-- Permisos (las tablas nuevas no heredan los GRANT de V3 y V6)
GRANT SELECT, INSERT, UPDATE ON posposicion_validacion TO app_rw;
GRANT USAGE, SELECT ON SEQUENCE posposicion_validacion_id_posposicion_seq TO app_rw;
GRANT SELECT ON posposicion_validacion TO app_ro, backup;
GRANT SELECT ON SEQUENCE posposicion_validacion_id_posposicion_seq TO backup;

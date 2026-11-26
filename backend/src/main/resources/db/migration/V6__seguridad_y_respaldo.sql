-- =====================================================================================
-- SIGRE Global - V6: refuerzos de seguridad y roles de respaldo (revisión de seguridad)
--   * usuario.sesion_desde: los tokens emitidos antes de esta fecha dejan de valer
--     (al desactivar un usuario, cambiar sus roles o su clave).
--   * usuario.totp_pendiente: el secreto TOTP nuevo no reemplaza al vigente hasta confirmarlo.
--   * Roles auditor_ro y backup de la Tabla 43 (sin inicio de sesión: se asignan a personas).
-- =====================================================================================

ALTER TABLE usuario ADD COLUMN sesion_desde   TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE usuario ADD COLUMN totp_pendiente VARCHAR(64);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'auditor_ro') THEN
        CREATE ROLE auditor_ro NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'backup') THEN
        CREATE ROLE backup NOLOGIN;
    END IF;
END $$;

-- Auditoría externa: solo lectura de la bitácora y de las vistas de consulta
GRANT USAGE ON SCHEMA public TO auditor_ro;
GRANT SELECT ON bitacora, v_riesgo, v_alerta TO auditor_ro;

-- Respaldo: lectura de todas las tablas (pg_dump)
GRANT USAGE ON SCHEMA public TO backup;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO backup;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO backup;

-- La bitácora no debe registrar datos de sesión ni secretos
CREATE OR REPLACE FUNCTION fn_auditar() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    v_ignorar TEXT[] := ARRAY['actualizado_en', 'clave_hash', 'totp_secreto', 'totp_pendiente', 'ultimo_acceso', 'sesion_desde'];
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
        IF v_cambios = '{}'::jsonb THEN RETURN NULL; END IF;
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

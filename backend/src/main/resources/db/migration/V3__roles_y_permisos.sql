-- =====================================================================================
-- SIGRE Global - V3: permisos de los roles de base de datos (sección 10.2, Tabla 43)
--   app_rw : usuario de la aplicación. SELECT, INSERT y UPDATE en las tablas de negocio;
--            en la bitácora solo INSERT.
--   app_ro : solo lectura (réplica, reportes y consultas de auditoría).
-- Los roles se crean al inicializar la instancia (scripts/bd.ps1).
-- =====================================================================================

GRANT USAGE ON SCHEMA public TO app_rw, app_ro;

GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA public TO app_rw;
-- Borrado solo donde el proceso lo necesita (acciones y controles de un plan en borrador,
-- evidencias cargadas por error, asignación de roles y notificaciones leídas)
GRANT DELETE ON accion_mitigacion, control, evidencia, usuario_rol, notificacion TO app_rw;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_rw;

-- Bitácora inalterable: además del trigger, la aplicación no tiene permiso de modificarla
REVOKE UPDATE, DELETE, TRUNCATE ON bitacora FROM app_rw;

GRANT SELECT ON ALL TABLES IN SCHEMA public TO app_ro;

-- =====================================================================================
-- SIGRE Global - V5: datos de demostración (caso de estudio Grupo Andino Global S.A.)
-- Las fechas son relativas al día de la carga, para que el sistema siempre parezca vivo.
-- Los datos pasan por los triggers de V2, así que cumplen las reglas de negocio:
-- los planes se aprueban con la atribución correcta, las validaciones respetan RN17, etc.
-- Incluye los escenarios de los casos de prueba CP01 a CP26 (Anexo B del informe).
-- =====================================================================================
SELECT set_config('app.carga_inicial', 'on', true);

-- Los códigos explícitos del informe (RSK-FIN-0042, ...) conviven con los automáticos
SELECT setval('seq_riesgo_fin', 93), setval('seq_riesgo_tec', 41), setval('seq_riesgo_leg', 20),
       setval('seq_riesgo_ope', 47), setval('seq_riesgo_rep', 11);

-- ------------------------------------------------------------------ Funciones auxiliares de carga
CREATE FUNCTION seed_u(p TEXT) RETURNS INT LANGUAGE sql STABLE AS $$ SELECT id_usuario FROM usuario WHERE login = p $$;
CREATE FUNCTION seed_f(p TEXT) RETURNS INT LANGUAGE sql STABLE AS $$ SELECT id_filial FROM filial WHERE codigo = p $$;
CREATE FUNCTION seed_r(p TEXT) RETURNS INT LANGUAGE sql STABLE AS $$ SELECT id_riesgo FROM riesgo WHERE codigo = p $$;

CREATE FUNCTION seed_como(p_login TEXT) RETURNS VOID LANGUAGE plpgsql AS $$
BEGIN
    PERFORM set_config('app.id_usuario', coalesce((SELECT id_usuario::text FROM usuario WHERE login = p_login), ''), true);
END $$;

CREATE FUNCTION seed_riesgo(p_codigo TEXT, p_filial TEXT, p_resp TEXT, p_reg TEXT, p_cat TEXT, p_dias INT,
                            p_titulo TEXT, p_proceso TEXT, p_desc TEXT, p_causa TEXT, p_consec TEXT,
                            p_origen TEXT DEFAULT 'MANUAL') RETURNS INT
LANGUAGE plpgsql AS $$
DECLARE v INT;
BEGIN
    PERFORM seed_como(p_reg);
    INSERT INTO riesgo (codigo, id_filial, id_responsable, id_registrador, categoria, fecha_registro,
                        titulo, proceso, descripcion, causa, consecuencia, origen)
    VALUES (p_codigo, seed_f(p_filial), seed_u(p_resp), seed_u(p_reg), p_cat, CURRENT_DATE - p_dias,
            p_titulo, p_proceso, p_desc, p_causa, p_consec, p_origen)
    RETURNING id_riesgo INTO v;
    RETURN v;
END $$;

CREATE FUNCTION seed_eval(p_codigo TEXT, p_evaluador TEXT, p_dias INT, p_tipo TEXT, p_p INT, p_i INT,
                          p_monto NUMERIC DEFAULT NULL, p_tc NUMERIC DEFAULT NULL, p_coment TEXT DEFAULT NULL) RETURNS INT
LANGUAGE plpgsql AS $$
DECLARE v INT;
BEGIN
    PERFORM seed_como(p_evaluador);
    INSERT INTO evaluacion_riesgo (id_riesgo, id_evaluador, fecha, tipo, probabilidad, impacto,
                                   impacto_financiero, tipo_cambio, comentario)
    VALUES (seed_r(p_codigo), seed_u(p_evaluador), CURRENT_DATE - p_dias, p_tipo, p_p, p_i, p_monto, p_tc, p_coment)
    RETURNING id_evaluacion INTO v;
    RETURN v;
END $$;

CREATE FUNCTION seed_validar(p_codigo TEXT, p_auditor TEXT, p_dias INT, p_resultado TEXT, p_obs TEXT) RETURNS VOID
LANGUAGE plpgsql AS $$
BEGIN
    PERFORM seed_como(p_auditor);
    INSERT INTO validacion_financiera (id_evaluacion, id_auditor_fin, fecha, resultado, observacion)
    SELECT id_evaluacion, seed_u(p_auditor), now() - make_interval(days => p_dias), p_resultado::estado_validacion, p_obs
    FROM evaluacion_riesgo WHERE id_riesgo = seed_r(p_codigo) AND tipo = 'INHERENTE'
    ORDER BY fecha DESC, id_evaluacion DESC LIMIT 1;
END $$;

CREATE FUNCTION seed_plan(p_codigo TEXT, p_proponente TEXT, p_estrategia TEXT, p_dias INT, p_justif TEXT DEFAULT NULL) RETURNS INT
LANGUAGE plpgsql AS $$
DECLARE v INT;
BEGIN
    PERFORM seed_como(p_proponente);
    INSERT INTO plan_mitigacion (id_riesgo, id_proponente, estrategia, fecha_inicio, justificacion)
    VALUES (seed_r(p_codigo), seed_u(p_proponente), p_estrategia, CURRENT_DATE - p_dias, p_justif)
    RETURNING id_plan INTO v;
    RETURN v;
END $$;

CREATE FUNCTION seed_accion(p_plan INT, p_resp TEXT, p_desc TEXT, p_dias_limite INT, p_costo NUMERIC) RETURNS INT
LANGUAGE plpgsql AS $$
DECLARE v INT;
BEGIN
    INSERT INTO accion_mitigacion (id_plan, id_responsable, descripcion, fecha_limite, costo)
    VALUES (p_plan, seed_u(p_resp), p_desc, CURRENT_DATE + p_dias_limite, p_costo)
    RETURNING id_accion INTO v;
    RETURN v;
END $$;

CREATE FUNCTION seed_proponer(p_codigo TEXT, p_proponente TEXT, p_dias INT) RETURNS VOID
LANGUAGE plpgsql AS $$
BEGIN
    PERFORM seed_como(p_proponente);
    UPDATE plan_mitigacion SET estado = 'PROPUESTO', fecha_propuesta = now() - make_interval(days => p_dias)
    WHERE id_riesgo = seed_r(p_codigo);
END $$;

CREATE FUNCTION seed_aprobar(p_codigo TEXT, p_aprobador TEXT, p_dias INT) RETURNS VOID
LANGUAGE plpgsql AS $$
BEGIN
    PERFORM seed_como(p_aprobador);
    UPDATE plan_mitigacion SET estado = 'APROBADO', id_aprobador = seed_u(p_aprobador),
                               fecha_aprobacion = now() - make_interval(days => p_dias)
    WHERE id_riesgo = seed_r(p_codigo);
END $$;

-- Registra avance; si llega a 100 % se adjunta una evidencia y la acción queda CERRADA
CREATE FUNCTION seed_avance(p_accion INT, p_avance INT, p_dias INT, p_coment TEXT) RETURNS VOID
LANGUAGE plpgsql AS $$
DECLARE v_resp INT;
BEGIN
    SELECT id_responsable INTO v_resp FROM accion_mitigacion WHERE id_accion = p_accion;
    IF p_avance = 100 THEN
        INSERT INTO evidencia (id_accion, id_usuario, archivo, tipo_mime, tamano, fecha_carga, descripcion)
        VALUES (p_accion, v_resp, 'evidencia_accion_' || p_accion || '.pdf', 'application/pdf', 48211,
                now() - make_interval(days => p_dias), 'Informe de cierre de la acción');
    END IF;
    UPDATE accion_mitigacion
    SET avance = p_avance, comentario = p_coment,
        fecha_cierre = CASE WHEN p_avance = 100 THEN now() - make_interval(days => p_dias) END
    WHERE id_accion = p_accion;
END $$;

CREATE FUNCTION seed_control(p_codigo TEXT, p_desc TEXT, p_tipo TEXT, p_efect INT) RETURNS VOID
LANGUAGE sql AS $$
    INSERT INTO control (id_riesgo, descripcion, tipo, efectividad) VALUES (seed_r(p_codigo), p_desc, p_tipo, p_efect)
$$;

CREATE FUNCTION seed_kri(p_codigo TEXT, p_nombre TEXT, p_unidad TEXT, p_am NUMERIC, p_rojo NUMERIC, p_dir TEXT,
                         p_frec TEXT, p_fuente TEXT, p_fin BOOLEAN) RETURNS INT
LANGUAGE plpgsql AS $$
DECLARE v INT;
BEGIN
    INSERT INTO indicador_kri (id_riesgo, nombre, unidad, umbral_amarillo, umbral_rojo, direccion, frecuencia, fuente, financiero)
    VALUES (seed_r(p_codigo), p_nombre, p_unidad, p_am, p_rojo, p_dir, p_frec, p_fuente, p_fin)
    RETURNING id_kri INTO v;
    RETURN v;
END $$;

-- Serie histórica: el último valor queda unas horas antes de la carga
CREATE FUNCTION seed_serie(p_kri INT, p_valores NUMERIC[], p_paso INTERVAL) RETURNS VOID
LANGUAGE plpgsql AS $$
DECLARE
    n      INT := array_length(p_valores, 1);
    i      INT;
    v_orig TEXT := (SELECT fuente FROM indicador_kri WHERE id_kri = p_kri);
BEGIN
    FOR i IN 1..n LOOP
        INSERT INTO medicion_kri (id_kri, fecha, valor, origen)
        VALUES (p_kri, now() - interval '3 hours' - (n - i) * p_paso, p_valores[i], v_orig);
    END LOOP;
END $$;

CREATE FUNCTION seed_ultima_medicion(p_kri INT) RETURNS BIGINT LANGUAGE sql STABLE AS $$
    SELECT id_medicion FROM medicion_kri WHERE id_kri = p_kri ORDER BY fecha DESC LIMIT 1
$$;

-- ==================================================================== RIESGOS FINANCIEROS
SELECT seed_riesgo('RSK-FIN-0042', 'LPZ', 'jcondori', 'mquispe', 'FINANCIERO', 82,
    'Exposición cambiaria por deuda en USD', 'Tesorería',
    'La filial mantiene deuda financiera en dólares mientras sus ingresos están en bolivianos; una devaluación o la escasez de divisas encarecería el servicio de la deuda.',
    'Descalce entre pasivos en USD e ingresos en BOB; restricciones de acceso a divisas.',
    'Pérdida cambiaria, mayor costo financiero y posible incumplimiento de covenants.');
SELECT seed_eval('RSK-FIN-0042', 'mquispe', 80, 'INHERENTE', 4, 5, 8350000);
SELECT seed_validar('RSK-FIN-0042', 'lmamani', 67, 'VALIDADA', 'Monto y tipo de cambio coinciden con el ERP.');
SELECT seed_control('RSK-FIN-0042', 'Monitoreo diario de la posición en moneda extranjera', 'DETECTIVO', 3);
SELECT seed_control('RSK-FIN-0042', 'Límite de endeudamiento en USD aprobado por el directorio', 'PREVENTIVO', 4);

SELECT seed_riesgo('RSK-FIN-0051', 'LIM', 'lparedes', 'mquispe', 'FINANCIERO', 20,
    'Riesgo de liquidez por concentración de vencimientos', 'Tesorería',
    'El 45 % de los depósitos a plazo de la filial vence en el mismo trimestre que las obligaciones con proveedores.',
    'Falta de escalonamiento de vencimientos y dependencia de pocos depositantes.',
    'Necesidad de financiamiento de emergencia a tasas altas o retraso en pagos.');
SELECT seed_eval('RSK-FIN-0051', 'mquispe', 6, 'INHERENTE', 3, 4, 1264800);

SELECT seed_riesgo('RSK-FIN-0058', 'SAO', 'rsouza', 'aperez', 'FINANCIERO', 15,
    'Incremento del costo de financiamiento por alza de la tasa SELIC', 'Finanzas',
    'Los préstamos de capital de trabajo de la filial están indexados a la tasa SELIC.',
    'Política monetaria restrictiva del Banco Central do Brasil.',
    'Mayor gasto financiero y menor margen operativo.');
SELECT seed_eval('RSK-FIN-0058', 'aperez', 5, 'INHERENTE', 3, 4, 817500);

SELECT seed_riesgo('RSK-FIN-0063', 'BUE', 'nacosta', 'mquispe', 'FINANCIERO', 140,
    'Pérdida de valor de los activos monetarios por inflación', 'Finanzas',
    'Los saldos en pesos argentinos pierden poder adquisitivo con una inflación mensual elevada.',
    'Inflación persistente y rezago en el ajuste de precios.',
    'Erosión del capital de trabajo y del resultado consolidado en USD.');
SELECT seed_eval('RSK-FIN-0063', 'mquispe', 130, 'INHERENTE', 5, 4, 145000000);
SELECT seed_validar('RSK-FIN-0063', 'lmamani', 126, 'VALIDADA', 'Tipo de cambio contrastado con el ERP.');

SELECT seed_riesgo('RSK-FIN-0071', 'SCL', 'tbravo', 'aperez', 'FINANCIERO', 30,
    'Riesgo de crédito de clientes del sector minero', 'Comercial',
    'Tres clientes mineros concentran el 38 % de las cuentas por cobrar de la filial.',
    'Caída del precio del cobre y plazos de pago extendidos.',
    'Incobrabilidad y necesidad de provisiones.');
SELECT seed_eval('RSK-FIN-0071', 'aperez', 28, 'INHERENTE', 2, 4, 47250000);

SELECT seed_riesgo('RSK-FIN-0077', 'BOG', 'sgomez', 'mquispe', 'FINANCIERO', 12,
    'Morosidad de la cartera comercial', 'Créditos y cobranzas',
    'La cartera vencida a más de 60 días creció del 4 % al 9 % en el último semestre.',
    'Desaceleración económica y criterios de crédito poco estrictos.',
    'Pérdidas por incobrabilidad y menor flujo de caja.');
SELECT seed_eval('RSK-FIN-0077', 'mquispe', 10, 'INHERENTE', 3, 3, 615000000);

SELECT seed_riesgo('RSK-FIN-0080', 'SCZ', 'ftorrez', 'aperez', 'FINANCIERO', 50,
    'Concentración de proveedores que facturan en dólares', 'Compras',
    'El 70 % de las compras de insumos se factura en USD a dos proveedores.',
    'Mercado local de insumos poco desarrollado.',
    'Mayor costo de compras ante variaciones del tipo de cambio.');
SELECT seed_eval('RSK-FIN-0080', 'aperez', 48, 'INHERENTE', 2, 3, 348000);

SELECT seed_riesgo('RSK-FIN-0084', 'MAD', 'jmartin', 'mquispe', 'FINANCIERO', 200,
    'Riesgo de tipo de interés en el préstamo sindicado en euros', 'Finanzas',
    'El préstamo sindicado de la filial europea tiene tasa variable referenciada al Euríbor.',
    'Subidas de tasas del Banco Central Europeo.',
    'Incremento de los intereses y presión sobre los covenants.');
SELECT seed_eval('RSK-FIN-0084', 'mquispe', 190, 'INHERENTE', 2, 5, 1380000);
SELECT seed_validar('RSK-FIN-0084', 'lmamani', 187, 'VALIDADA', 'Conforme con el cierre contable del ERP.');

SELECT seed_riesgo('RSK-FIN-0086', 'MDE', 'jrestrepo', 'aperez', 'FINANCIERO', 25,
    'Riesgo de contraparte en contratos de derivados', 'Tesorería',
    'La filial mantiene contratos forward con un único banco local de calificación media.',
    'Concentración de contrapartes y deterioro de la calificación del banco.',
    'Pérdida del valor de las coberturas si la contraparte incumple.');
SELECT seed_eval('RSK-FIN-0086', 'aperez', 22, 'INHERENTE', 4, 3, 860000000, 4300,
    'Tipo de cambio ingresado manualmente por el gestor');
SELECT seed_validar('RSK-FIN-0086', 'lmamani', 20, 'OBSERVADA',
    'El tipo de cambio registrado (4 300) no coincide con el del ERP para la fecha. Reevaluar con el tipo de cambio oficial.');

SELECT seed_riesgo('RSK-FIN-0093', 'LPZ', 'lmamani', 'mquispe', 'FINANCIERO', 9,
    'Errores en la valoración de la cartera de inversiones', 'Contabilidad',
    'La valoración a precio de mercado de las inversiones se hace en hojas de cálculo.',
    'Procesos manuales y fuentes de precios no verificadas.',
    'Estados financieros con errores materiales.');
SELECT seed_eval('RSK-FIN-0093', 'mquispe', 8, 'INHERENTE', 3, 4, 1044000);

-- ==================================================================== RIESGOS TECNOLÓGICOS
SELECT seed_riesgo('RSK-TEC-0017', 'BOG', 'dlopez', 'mquispe', 'TECNOLOGICO', 95,
    'Intrusiones en la red corporativa', 'Seguridad de la información',
    'El SIEM registra un aumento sostenido de intentos de acceso no autorizado a la red de la filial.',
    'Servicios expuestos a internet sin segmentación y credenciales débiles en la VPN.',
    'Robo de información, interrupción de servicios y sanciones por protección de datos.');
SELECT seed_eval('RSK-TEC-0017', 'mquispe', 93, 'INHERENTE', 4, 4);

SELECT seed_riesgo('RSK-TEC-0021', 'LPZ', 'omendoza', 'mquispe', 'TECNOLOGICO', 18,
    'Caída del ERP por falta de alta disponibilidad', 'Tecnología',
    'El ERP corporativo funciona en un único servidor sin réplica.',
    'Infraestructura sin redundancia y ausencia de un plan de recuperación probado.',
    'Paralización de la facturación, la tesorería y los reportes regulatorios.');
SELECT seed_eval('RSK-TEC-0021', 'mquispe', 16, 'INHERENTE', 3, 5);

SELECT seed_riesgo('RSK-TEC-0025', 'LIM', 'acastillo', 'aperez', 'TECNOLOGICO', 70,
    'Ransomware en estaciones de trabajo', 'Tecnología',
    'Equipos con parches críticos pendientes y usuarios con privilegios de administrador.',
    'Gestión de parches irregular y campañas de phishing dirigidas.',
    'Cifrado de información y paralización de operaciones.');
SELECT seed_eval('RSK-TEC-0025', 'aperez', 68, 'INHERENTE', 3, 4);

SELECT seed_riesgo('RSK-TEC-0030', 'SCL', 'tbravo', 'aperez', 'TECNOLOGICO', 110,
    'Pérdida de datos por respaldos deficientes', 'Tecnología',
    'Los respaldos se hacían manualmente y nunca se había probado una restauración.',
    'Ausencia de un procedimiento automatizado de respaldo.',
    'Pérdida irreversible de información operativa.');
SELECT seed_eval('RSK-TEC-0030', 'aperez', 108, 'INHERENTE', 3, 3);

SELECT seed_riesgo('RSK-TEC-0034', 'MAD', 'egarcia', 'mquispe', 'TECNOLOGICO', 240,
    'Fuga de información por correo electrónico', 'Tecnología',
    'Envío de datos de clientes a direcciones externas sin control.',
    'Falta de herramientas de prevención de fuga de datos (DLP).',
    'Sanciones del RGPD y pérdida de confianza.');
SELECT seed_eval('RSK-TEC-0034', 'mquispe', 235, 'INHERENTE', 2, 4);

SELECT seed_riesgo('RSK-TEC-0038', 'SAO', 'rsouza', 'aperez', 'TECNOLOGICO', 4,
    'Vulnerabilidades en la aplicación móvil de clientes', 'Canales digitales',
    'Una prueba de penetración externa detectó dos vulnerabilidades de severidad alta.',
    'Desarrollo sin revisión de seguridad del código.',
    'Acceso indebido a cuentas de clientes.');

SELECT seed_riesgo('RSK-TEC-0041', 'CBB', 'kvillca', 'mquispe', 'TECNOLOGICO', 60,
    'Obsolescencia de servidores de la sucursal', 'Tecnología',
    'Los servidores de la sucursal superaron su vida útil y no tienen soporte del fabricante.',
    'Postergación de inversiones en infraestructura.',
    'Fallas de hardware sin repuestos disponibles.');
SELECT seed_eval('RSK-TEC-0041', 'mquispe', 58, 'INHERENTE', 2, 2);

-- ==================================================================== RIESGOS LEGALES
SELECT seed_riesgo('RSK-LEG-0009', 'MAD', 'egarcia', 'mquispe', 'LEGAL', 160,
    'Sanciones por incumplimiento del RGPD', 'Protección de datos',
    'La filial trata datos personales de clientes europeos sin evaluaciones de impacto actualizadas.',
    'Crecimiento de los tratamientos sin revisión del delegado de protección de datos.',
    'Multas de hasta el 4 % de la facturación anual.');
SELECT seed_eval('RSK-LEG-0009', 'mquispe', 155, 'INHERENTE', 3, 5);

SELECT seed_riesgo('RSK-LEG-0012', 'LIM', 'lparedes', 'aperez', 'LEGAL', 35,
    'Cambios en la normativa tributaria del Perú', 'Tributación',
    'Se anunció una reforma del régimen de detracciones y percepciones del IGV.',
    'Cambios normativos frecuentes de la SUNAT.',
    'Contingencias tributarias y multas.');
SELECT seed_eval('RSK-LEG-0012', 'aperez', 33, 'INHERENTE', 4, 3);

SELECT seed_riesgo('RSK-LEG-0015', 'BUE', 'nacosta', 'aperez', 'LEGAL', 45,
    'Litigio laboral colectivo', 'Recursos humanos',
    'El sindicato presentó una demanda por diferencias salariales.',
    'Desactualización de las escalas salariales frente a la inflación.',
    'Indemnizaciones y costas judiciales.');
SELECT seed_eval('RSK-LEG-0015', 'aperez', 44, 'INHERENTE', 2, 4);

-- CP06: evaluación con 14 meses de antigüedad (RN03)
SELECT seed_riesgo('RSK-LEG-0018', 'LPZ', 'jcondori', 'mquispe', 'LEGAL', 440,
    'Retraso en reportes regulatorios a la ASFI', 'Cumplimiento regulatorio',
    'Los reportes regulatorios se consolidan manualmente y en dos ocasiones se enviaron fuera de plazo.',
    'Dependencia de planillas y de una sola persona.',
    'Multas de la ASFI y observaciones de los auditores externos.');
SELECT seed_eval('RSK-LEG-0018', 'mquispe', 425, 'INHERENTE', 3, 4);

SELECT seed_riesgo('RSK-LEG-0020', 'ANF', 'isoto', 'mquispe', 'LEGAL', 300,
    'Sanciones ambientales en la operación minera', 'Medio ambiente',
    'La operación de transporte de concentrados atraviesa zonas con regulación ambiental estricta.',
    'Derrames menores durante la carga y descarga.',
    'Multas y suspensión temporal de operaciones.');
SELECT seed_eval('RSK-LEG-0020', 'mquispe', 290, 'INHERENTE', 2, 5);

-- ==================================================================== RIESGOS OPERATIVOS
SELECT seed_riesgo('RSK-OPE-0031', 'SCZ', 'ftorrez', 'mquispe', 'OPERATIVO', 75,
    'Interrupción de la cadena de suministro', 'Logística',
    'Un único proveedor abastece los insumos críticos de la planta.',
    'Bloqueos de carreteras y falta de proveedores alternativos.',
    'Paralización de la producción y pérdida de ventas.');
SELECT seed_eval('RSK-OPE-0031', 'mquispe', 73, 'INHERENTE', 4, 3);

SELECT seed_riesgo('RSK-OPE-0035', 'LPZ', 'rgutierrez', 'aperez', 'OPERATIVO', 40,
    'Rotación del personal clave', 'Recursos humanos',
    'En el último año renunció el 30 % de los analistas de riesgos y tesorería.',
    'Salarios por debajo del mercado y falta de plan de carrera.',
    'Pérdida de conocimiento y errores operativos.');
SELECT seed_eval('RSK-OPE-0035', 'aperez', 38, 'INHERENTE', 3, 2);

SELECT seed_riesgo('RSK-OPE-0039', 'MDE', 'jrestrepo', 'aperez', 'OPERATIVO', 85,
    'Accidentes en los almacenes', 'Seguridad industrial',
    'Se registraron cuatro incidentes con montacargas en el último trimestre.',
    'Señalización insuficiente y falta de capacitación.',
    'Lesiones al personal y sanciones laborales.');
SELECT seed_eval('RSK-OPE-0039', 'aperez', 83, 'INHERENTE', 2, 3);

SELECT seed_riesgo('RSK-OPE-0044', 'AQP', 'privas', 'aperez', 'OPERATIVO', 3,
    'Fallas en el transporte de mercadería', 'Logística',
    'Retrasos frecuentes del transportista contratado en la ruta Arequipa-Puno.',
    'Flota del transportista con antigüedad elevada.',
    'Incumplimiento de entregas y penalidades contractuales.');

SELECT seed_riesgo('RSK-OPE-0047', 'BOG', 'dlopez', 'pvargas', 'OPERATIVO', 14,
    'Fraude interno en el proceso de compras', 'Compras',
    'La auditoría AUD-2026-002 detectó que un mismo usuario solicita y aprueba órdenes de compra.',
    'Falta de segregación de funciones en el sistema de compras.',
    'Pagos indebidos a proveedores ficticios.', 'HALLAZGO');
SELECT seed_eval('RSK-OPE-0047', 'mquispe', 12, 'INHERENTE', 2, 5);

-- ==================================================================== RIESGOS REPUTACIONALES
SELECT seed_riesgo('RSK-REP-0005', 'SAO', 'vsilva', 'aperez', 'REPUTACIONAL', 55,
    'Crisis en redes sociales por reclamos de clientes', 'Comunicación',
    'Los reclamos por demoras en las entregas se viralizan en redes sociales.',
    'Tiempo de respuesta del servicio al cliente mayor a 72 horas.',
    'Pérdida de clientes y de imagen de marca.');
SELECT seed_eval('RSK-REP-0005', 'aperez', 53, 'INHERENTE', 3, 3);

SELECT seed_riesgo('RSK-REP-0008', 'SCL', 'cmunoz', 'aperez', 'REPUTACIONAL', 330,
    'Daño reputacional por un proveedor con malas prácticas', 'Compras',
    'Un proveedor de transporte fue denunciado por trabajo informal.',
    'Debida diligencia de proveedores incompleta.',
    'Asociación de la marca con prácticas ilegales.');
SELECT seed_eval('RSK-REP-0008', 'aperez', 325, 'INHERENTE', 2, 4);

SELECT seed_riesgo('RSK-REP-0011', 'LIM', 'lparedes', 'aperez', 'REPUTACIONAL', 180,
    'Publicidad engañosa denunciada ante INDECOPI', 'Marketing',
    'Una campaña publicitaria fue denunciada por no informar las condiciones de una promoción.',
    'Ausencia de revisión legal de las campañas.',
    'Multas de INDECOPI y desconfianza de los clientes.');
SELECT seed_eval('RSK-REP-0011', 'aperez', 178, 'INHERENTE', 1, 3);

-- ==================================================================== PLANES DE MITIGACIÓN
DO $$
DECLARE
    p  INT;
    a1 INT; a2 INT; a3 INT;
BEGIN
    -- RSK-FIN-0042: TRANSFERIR, aprobado por el Director tras la validación financiera (D13)
    p  := seed_plan('RSK-FIN-0042', 'mquispe', 'TRANSFERIR', 66);
    a1 := seed_accion(p, 'jcondori', 'Contratar cobertura forward USD/BOB por el 60 % de la deuda', 25, 45000);
    a2 := seed_accion(p, 'jcondori', 'Renegociar el 30 % de la deuda en moneda local', 60, 12000);
    a3 := seed_accion(p, 'rgutierrez', 'Aprobar en directorio la política de calce de monedas', -5, 3000);
    PERFORM seed_proponer('RSK-FIN-0042', 'mquispe', 66);
    PERFORM seed_aprobar('RSK-FIN-0042', 'crojas', 63);
    PERFORM seed_avance(a1, 60, 4, 'Cotizaciones recibidas de tres bancos; contrato en revisión legal');
    PERFORM seed_avance(a2, 20, 10, 'Primera reunión con el banco acreedor');
    PERFORM seed_avance(a3, 80, 8, 'Propuesta presentada; falta la firma del acta');

    -- RSK-FIN-0051: PROPUESTO, bloqueado por RN16 hasta la validación (CP24)
    p := seed_plan('RSK-FIN-0051', 'mquispe', 'REDUCIR', 4);
    PERFORM seed_accion(p, 'lparedes', 'Escalonar los vencimientos de los depósitos a plazo', 40, 8000);
    PERFORM seed_accion(p, 'lparedes', 'Abrir una línea de crédito contingente de 2 MM PEN', 30, 15000);
    PERFORM seed_proponer('RSK-FIN-0051', 'mquispe', 4);

    -- RSK-FIN-0058: BORRADOR
    p := seed_plan('RSK-FIN-0058', 'aperez', 'REDUCIR', 3);
    PERFORM seed_accion(p, 'rsouza', 'Fijar la tasa de los préstamos variables mediante un swap', 45, 20000);

    -- RSK-FIN-0063: REDUCIR, aprobado
    p  := seed_plan('RSK-FIN-0063', 'mquispe', 'REDUCIR', 125);
    a1 := seed_accion(p, 'nacosta', 'Invertir excedentes en instrumentos ajustados por inflación (CER)', -12, 5000);
    a2 := seed_accion(p, 'nacosta', 'Ajustar mensualmente las listas de precios', 20, 2000);
    PERFORM seed_proponer('RSK-FIN-0063', 'mquispe', 125);
    PERFORM seed_aprobar('RSK-FIN-0063', 'crojas', 122);
    PERFORM seed_avance(a1, 100, 14, 'Colocación completada en bonos CER');
    PERFORM seed_avance(a2, 70, 3, 'Listas actualizadas en 7 de 10 canales');

    -- RSK-FIN-0071: MEDIO, PROPUESTO para el Gerente de Chile (RN04)
    p := seed_plan('RSK-FIN-0071', 'aperez', 'REDUCIR', 3);
    PERFORM seed_accion(p, 'tbravo', 'Exigir garantías a clientes con más de 90 días de mora', 30, 2000);
    PERFORM seed_proponer('RSK-FIN-0071', 'aperez', 3);

    -- RSK-FIN-0084: TRANSFERIR, aprobado
    p  := seed_plan('RSK-FIN-0084', 'mquispe', 'TRANSFERIR', 182);
    a1 := seed_accion(p, 'jmartin', 'Contratar un swap de tasa de interés para el 80 % del préstamo', -150, 30000);
    a2 := seed_accion(p, 'egarcia', 'Revisión trimestral de covenants con el banco agente', 15, 1500);
    PERFORM seed_proponer('RSK-FIN-0084', 'mquispe', 182);
    PERFORM seed_aprobar('RSK-FIN-0084', 'crojas', 180);
    PERFORM seed_avance(a1, 100, 150, 'Swap firmado con vencimiento a 5 años');
    PERFORM seed_avance(a2, 75, 6, 'Tercera revisión trimestral completada');

    -- RSK-TEC-0017: REDUCIR, aprobado
    p  := seed_plan('RSK-TEC-0017', 'mquispe', 'REDUCIR', 90);
    a1 := seed_accion(p, 'dlopez', 'Segmentar la red e implementar doble factor en la VPN', 10, 18000);
    a2 := seed_accion(p, 'dlopez', 'Contratar un centro de operaciones de seguridad (SOC) 24/7', 45, 60000);
    PERFORM seed_proponer('RSK-TEC-0017', 'mquispe', 90);
    PERFORM seed_aprobar('RSK-TEC-0017', 'crojas', 88);
    PERFORM seed_avance(a1, 70, 2, 'Doble factor activo en el 100 % de las cuentas VPN');
    PERFORM seed_avance(a2, 30, 9, 'Proceso de licitación en curso');

    -- RSK-TEC-0021: CRÍTICO, PROPUESTO esperando al Director (CP07)
    p := seed_plan('RSK-TEC-0021', 'mquispe', 'REDUCIR', 2);
    PERFORM seed_accion(p, 'omendoza', 'Implementar un clúster activo-pasivo para el ERP', 50, 38000);
    PERFORM seed_accion(p, 'omendoza', 'Probar el plan de recuperación ante desastres', 70, 6000);
    PERFORM seed_proponer('RSK-TEC-0021', 'mquispe', 2);

    -- RSK-TEC-0025: REDUCIR, aprobado
    p  := seed_plan('RSK-TEC-0025', 'aperez', 'REDUCIR', 62);
    a1 := seed_accion(p, 'acastillo', 'Desplegar EDR en el 100 % de los equipos', 5, 25000);
    a2 := seed_accion(p, 'acastillo', 'Campaña de concientización contra el phishing', -20, 3000);
    PERFORM seed_proponer('RSK-TEC-0025', 'aperez', 62);
    PERFORM seed_aprobar('RSK-TEC-0025', 'crojas', 60);
    PERFORM seed_avance(a1, 85, 1, 'EDR instalado en 312 de 367 equipos');
    PERFORM seed_avance(a2, 100, 22, 'Campaña completada con 94 % de participación');

    -- RSK-TEC-0030: acciones cerradas, espera la evaluación residual (CP08)
    p  := seed_plan('RSK-TEC-0030', 'aperez', 'REDUCIR', 102);
    a1 := seed_accion(p, 'tbravo', 'Automatizar respaldos diarios con copia fuera de sitio', -30, 9000);
    a2 := seed_accion(p, 'tbravo', 'Prueba trimestral de restauración de respaldos', -8, 1500);
    PERFORM seed_proponer('RSK-TEC-0030', 'aperez', 102);
    PERFORM seed_aprobar('RSK-TEC-0030', 'cmunoz', 100);
    PERFORM seed_avance(a1, 100, 31, 'Respaldos automáticos operando desde hace un mes');
    PERFORM seed_avance(a2, 100, 8, 'Restauración completa probada en 2 horas');

    -- RSK-TEC-0034: aprobado por el Gerente de España, mitigado con evaluación residual (RN11)
    p  := seed_plan('RSK-TEC-0034', 'mquispe', 'REDUCIR', 232);
    a1 := seed_accion(p, 'egarcia', 'Implementar prevención de fuga de datos (DLP) en el correo', -60, 14000);
    PERFORM seed_proponer('RSK-TEC-0034', 'mquispe', 232);
    PERFORM seed_aprobar('RSK-TEC-0034', 'jmartin', 230);
    PERFORM seed_avance(a1, 100, 62, 'DLP en producción con reglas para datos personales');

    -- RSK-LEG-0009: REDUCIR, aprobado
    p  := seed_plan('RSK-LEG-0009', 'mquispe', 'REDUCIR', 152);
    a1 := seed_accion(p, 'egarcia', 'Actualizar el registro de actividades de tratamiento', -100, 4000);
    a2 := seed_accion(p, 'egarcia', 'Evaluaciones de impacto (DPIA) de los sistemas críticos', 30, 12000);
    PERFORM seed_proponer('RSK-LEG-0009', 'mquispe', 152);
    PERFORM seed_aprobar('RSK-LEG-0009', 'crojas', 150);
    PERFORM seed_avance(a1, 100, 101, 'Registro aprobado por el delegado de protección de datos');
    PERFORM seed_avance(a2, 50, 5, '3 de 6 evaluaciones completadas');

    -- RSK-LEG-0012: BORRADOR sin acciones
    PERFORM seed_plan('RSK-LEG-0012', 'aperez', 'REDUCIR', 2);

    -- RSK-LEG-0018: PROPUESTO con evaluación vencida (CP06)
    p := seed_plan('RSK-LEG-0018', 'mquispe', 'REDUCIR', 2);
    PERFORM seed_accion(p, 'jcondori', 'Automatizar la generación de reportes regulatorios', 40, 22000);
    PERFORM seed_proponer('RSK-LEG-0018', 'mquispe', 2);

    -- RSK-LEG-0020: ACEPTAR con justificación y aprobación del Director (RN12)
    PERFORM seed_plan('RSK-LEG-0020', 'mquispe', 'ACEPTAR', 288,
        'El costo de mitigación supera el impacto esperado; existe una póliza de responsabilidad ambiental vigente y el riesgo se revisa trimestralmente.');
    PERFORM seed_proponer('RSK-LEG-0020', 'mquispe', 288);
    PERFORM seed_aprobar('RSK-LEG-0020', 'crojas', 285);

    -- RSK-OPE-0031: REDUCIR, aprobado, con una acción vencida
    p  := seed_plan('RSK-OPE-0031', 'mquispe', 'REDUCIR', 70);
    a1 := seed_accion(p, 'ftorrez', 'Homologar un segundo proveedor de insumos críticos', -3, 7000);
    a2 := seed_accion(p, 'ftorrez', 'Mantener un stock de seguridad de 30 días', 20, 40000);
    PERFORM seed_proponer('RSK-OPE-0031', 'mquispe', 70);
    PERFORM seed_aprobar('RSK-OPE-0031', 'crojas', 68);
    PERFORM seed_avance(a1, 40, 12, 'Dos proveedores en evaluación técnica');
    PERFORM seed_avance(a2, 60, 4, 'Stock actual para 18 días');

    -- RSK-OPE-0039: MEDIO, aprobado por la Gerente de Medellín
    p  := seed_plan('RSK-OPE-0039', 'aperez', 'REDUCIR', 80);
    a1 := seed_accion(p, 'jrestrepo', 'Señalización y capacitación en seguridad industrial', 25, 6500);
    PERFORM seed_proponer('RSK-OPE-0039', 'aperez', 80);
    PERFORM seed_aprobar('RSK-OPE-0039', 'jrestrepo', 78);
    PERFORM seed_avance(a1, 45, 6, 'Señalización instalada en 2 de 4 almacenes');

    -- RSK-OPE-0047: PROPUESTO esperando al Director (nivel ALTO)
    p := seed_plan('RSK-OPE-0047', 'mquispe', 'REDUCIR', 5);
    PERFORM seed_accion(p, 'dlopez', 'Segregar las funciones de solicitud y aprobación de compras', 30, 4000);
    PERFORM seed_proponer('RSK-OPE-0047', 'mquispe', 5);

    -- RSK-REP-0008: EVITAR, mitigado y luego cerrado
    p  := seed_plan('RSK-REP-0008', 'aperez', 'EVITAR', 322);
    a1 := seed_accion(p, 'cmunoz', 'Reemplazar al proveedor y auditar a los demás transportistas', -250, 8000);
    PERFORM seed_proponer('RSK-REP-0008', 'aperez', 322);
    PERFORM seed_aprobar('RSK-REP-0008', 'cmunoz', 320);
    PERFORM seed_avance(a1, 100, 252, 'Proveedor reemplazado; 12 transportistas auditados');

    -- RSK-REP-0011: BAJO, aprobado por la Gerente de Perú y mitigado
    p  := seed_plan('RSK-REP-0011', 'aperez', 'REDUCIR', 176);
    a1 := seed_accion(p, 'lparedes', 'Revisión legal previa de todas las campañas', -120, 1500);
    PERFORM seed_proponer('RSK-REP-0011', 'aperez', 176);
    PERFORM seed_aprobar('RSK-REP-0011', 'lparedes', 174);
    PERFORM seed_avance(a1, 100, 121, 'Procedimiento de revisión legal vigente');
END $$;

-- Evaluaciones residuales (RN11: el trigger pasa el riesgo a MITIGADO)
SELECT seed_eval('RSK-TEC-0034', 'mquispe', 55, 'RESIDUAL', 1, 4);
SELECT seed_eval('RSK-REP-0008', 'aperez', 200, 'RESIDUAL', 1, 2);
SELECT seed_eval('RSK-REP-0011', 'aperez', 90, 'RESIDUAL', 1, 2);
UPDATE riesgo SET estado = 'CERRADO' WHERE codigo = 'RSK-REP-0008';

-- ==================================================================== INDICADORES KRI Y MEDICIONES
SELECT seed_como('sistema');   -- las mediciones llegan del ERP/SIEM, sin usuario
DO $$
DECLARE k INT;
BEGIN
    k := seed_kri('RSK-FIN-0042', 'Variación mensual del tipo de cambio paralelo', '%', 3.0, 5.0, 'MAYOR', 'MENSUAL', 'ERP', TRUE);
    PERFORM seed_serie(k, ARRAY[0.4,0.6,0.5,0.9,1.2,1.6,2.1,2.8,3.4,4.1,4.9,5.8]::numeric[], interval '1 month');
    k := seed_kri('RSK-FIN-0042', 'Cobertura de la deuda en USD', '%', 50, 30, 'MENOR', 'MENSUAL', 'ERP', TRUE);
    PERFORM seed_serie(k, ARRAY[10,12,15,18,20,25,30,34,38,42,51,55]::numeric[], interval '1 month');
    k := seed_kri('RSK-FIN-0051', 'Ratio de liquidez corriente', 'veces', 1.2, 1.0, 'MENOR', 'MENSUAL', 'ERP', TRUE);
    PERFORM seed_serie(k, ARRAY[1.60,1.55,1.50,1.45,1.42,1.38,1.33,1.30,1.28,1.25,1.22,1.18]::numeric[], interval '1 month');
    k := seed_kri('RSK-FIN-0058', 'Tasa SELIC', '%', 14.0, 15.5, 'MAYOR', 'MENSUAL', 'ERP', TRUE);
    PERFORM seed_serie(k, ARRAY[10.50,10.75,11.25,11.75,12.25,13.25,14.25,14.75,15.00,15.00,15.00,15.00]::numeric[], interval '1 month');
    k := seed_kri('RSK-FIN-0063', 'Inflación mensual', '%', 2.5, 4.0, 'MAYOR', 'MENSUAL', 'ERP', TRUE);
    PERFORM seed_serie(k, ARRAY[4.2,3.9,3.5,3.7,3.3,2.9,2.7,2.4,2.2,2.5,2.8,3.1]::numeric[], interval '1 month');
    k := seed_kri('RSK-FIN-0084', 'Euríbor a 12 meses', '%', 3.0, 3.5, 'MAYOR', 'MENSUAL', 'ERP', TRUE);
    PERFORM seed_serie(k, ARRAY[3.60,3.50,3.30,3.10,2.90,2.70,2.60,2.50,2.45,2.40,2.35,2.30]::numeric[], interval '1 month');
    k := seed_kri('RSK-FIN-0077', 'Cartera vencida a más de 60 días', '%', 6, 8, 'MAYOR', 'MENSUAL', 'ERP', TRUE);
    PERFORM seed_serie(k, ARRAY[4.0,4.2,4.5,4.9,5.4,5.9,6.3,6.8,7.4,7.9,8.4,9.1]::numeric[], interval '1 month');

    k := seed_kri('RSK-TEC-0017', 'Intentos de intrusión bloqueados por día', 'eventos', 150, 300, 'MAYOR', 'DIARIA', 'SIEM', FALSE);
    PERFORM seed_serie(k, ARRAY[88,95,102,97,110,121,118,130,126,140,151,149,162,171,168,180,192,205,199,214,228,241,236,252,266,281,277,295,318,342]::numeric[], interval '1 day');
    k := seed_kri('RSK-TEC-0021', 'Disponibilidad mensual del ERP', '%', 99.5, 98.0, 'MENOR', 'DIARIA', 'SIEM', FALSE);
    PERFORM seed_serie(k, ARRAY[99.95,99.97,99.92,99.98,99.90,99.94,99.96,99.88,99.91,99.93,99.97,99.85,99.80,99.86,99.90,99.70,99.62,99.75,99.58,99.66,99.49,99.41,99.30,99.12,98.95,98.70,98.44,98.12,97.80,97.40]::numeric[], interval '1 day');
    k := seed_kri('RSK-TEC-0025', 'Equipos sin parche crítico', '%', 5, 10, 'MAYOR', 'SEMANAL', 'SIEM', FALSE);
    PERFORM seed_serie(k, ARRAY[14.0,12.5,12.0,11.0,10.5,9.8,9.0,8.6,7.9,7.2]::numeric[], interval '7 days');
    k := seed_kri('RSK-TEC-0030', 'Respaldos fallidos en el mes', 'respaldos', 2, 5, 'MAYOR', 'MENSUAL', 'SIEM', FALSE);
    PERFORM seed_serie(k, ARRAY[7,6,6,5,4,3,1,0,0,0]::numeric[], interval '1 month');
    k := seed_kri('RSK-OPE-0031', 'Días de stock de insumos críticos', 'días', 20, 10, 'MENOR', 'SEMANAL', 'MANUAL', FALSE);
    PERFORM seed_serie(k, ARRAY[9,10,11,12,13,14,15,16,17,18]::numeric[], interval '7 days');
    k := seed_kri('RSK-LEG-0009', 'Solicitudes de derechos ARCO fuera de plazo', 'solicitudes', 2, 5, 'MAYOR', 'MENSUAL', 'MANUAL', FALSE);
    PERFORM seed_serie(k, ARRAY[6,5,4,4,3,2,2,1,1,0]::numeric[], interval '1 month');
    k := seed_kri('RSK-REP-0005', 'Menciones negativas en redes sociales por semana', 'menciones', 80, 150, 'MAYOR', 'SEMANAL', 'MANUAL', FALSE);
    PERFORM seed_serie(k, ARRAY[42,51,47,63,58,71,66,74,69,77]::numeric[], interval '7 days');
END $$;

-- Cierres mensuales publicados por el ERP para la conciliación (CU18, RN18).
-- Mes anterior: Perú difiere 3,4 % (CP25) y Brasil 1,2 % (CP26); los demás coinciden.
INSERT INTO cierre_erp_kri (id_kri, periodo, valor)
SELECT k.id_kri, to_char(m.fecha, 'YYYY-MM'),
       round(m.valor * CASE k.nombre
                           WHEN 'Ratio de liquidez corriente' THEN 0.967
                           WHEN 'Tasa SELIC' THEN 0.988
                           WHEN 'Inflación mensual' THEN 1.007
                           ELSE 1 END, 4)
FROM indicador_kri k
JOIN LATERAL (SELECT fecha, valor FROM medicion_kri mk WHERE mk.id_kri = k.id_kri
              AND date_trunc('month', mk.fecha) = date_trunc('month', now() - interval '1 month')
              ORDER BY fecha DESC LIMIT 1) m ON TRUE
WHERE k.financiero;

-- Hace dos meses: todos los cierres coinciden salvo Perú (3,4 %)
INSERT INTO cierre_erp_kri (id_kri, periodo, valor)
SELECT k.id_kri, to_char(m.fecha, 'YYYY-MM'),
       round(m.valor * CASE WHEN k.nombre = 'Ratio de liquidez corriente' THEN 0.967 ELSE 1 END, 4)
FROM indicador_kri k
JOIN LATERAL (SELECT fecha, valor FROM medicion_kri mk WHERE mk.id_kri = k.id_kri
              AND date_trunc('month', mk.fecha) = date_trunc('month', now() - interval '2 months')
              ORDER BY fecha DESC LIMIT 1) m ON TRUE
WHERE k.financiero;

-- Conciliaciones de hace dos meses realizadas por el Auditor Financiero
SELECT seed_como('lmamani');
INSERT INTO conciliacion_kri (id_kri, id_auditor_fin, periodo, valor_sistema, valor_erp, fecha)
SELECT c.id_kri, seed_u('lmamani'), c.periodo,
       (SELECT valor FROM medicion_kri mk WHERE mk.id_kri = c.id_kri AND to_char(mk.fecha, 'YYYY-MM') = c.periodo
        ORDER BY fecha DESC LIMIT 1),
       c.valor, date_trunc('month', now() - interval '1 month') + interval '1 day 9 hours'
FROM cierre_erp_kri c
WHERE c.periodo = to_char(now() - interval '2 months', 'YYYY-MM');

-- ==================================================================== AUDITORÍAS Y HALLAZGOS
SELECT seed_como('pvargas');
INSERT INTO auditoria (id_filial, id_auditor, codigo, anio, tipo, alcance, fecha_inicio, fecha_fin, estado) VALUES
(seed_f('LPZ'), seed_u('pvargas'), 'AUD-2026-001', extract(year FROM CURRENT_DATE), 'FINANCIERA',
 'Gestión de tesorería, inversiones y posición en moneda extranjera', CURRENT_DATE - 250, CURRENT_DATE - 230, 'CERRADA'),
(seed_f('BOG'), seed_u('pvargas'), 'AUD-2026-002', extract(year FROM CURRENT_DATE), 'OPERATIVA',
 'Proceso de compras y abastecimiento, segregación de funciones', CURRENT_DATE - 20, CURRENT_DATE + 10, 'EN_EJECUCION'),
(seed_f('LIM'), seed_u('pvargas'), 'AUD-2026-003', extract(year FROM CURRENT_DATE), 'CUMPLIMIENTO',
 'Cumplimiento tributario del IGV y libros electrónicos', CURRENT_DATE + 15, CURRENT_DATE + 35, 'PLANIFICADA'),
(seed_f('SCZ'), seed_u('pvargas'), 'AUD-2026-004', extract(year FROM CURRENT_DATE), 'OPERATIVA',
 'Inventarios y almacenes de la planta', CURRENT_DATE - 120, CURRENT_DATE - 100, 'CERRADA'),
(seed_f('MAD'), seed_u('pvargas'), 'AUD-2026-005', extract(year FROM CURRENT_DATE), 'TECNOLOGICA',
 'Protección de datos personales y seguridad del correo', CURRENT_DATE + 45, CURRENT_DATE + 60, 'PLANIFICADA'),
(seed_f('SAO'), seed_u('lmamani'), 'AUD-2026-006', extract(year FROM CURRENT_DATE), 'FINANCIERA',
 'Financiamiento, derivados y cuentas por cobrar', CURRENT_DATE - 7, CURRENT_DATE + 14, 'EN_EJECUCION');
SELECT setval('seq_auditoria', 6);

INSERT INTO hallazgo (id_auditoria, id_riesgo, id_responsable, id_registrador, descripcion, severidad, recomendacion,
                      plan_accion, fecha_limite, estado, fecha_registro, fecha_cierre)
VALUES
((SELECT id_auditoria FROM auditoria WHERE codigo = 'AUD-2026-001'), NULL, seed_u('jcondori'), seed_u('pvargas'),
 'Conciliaciones bancarias con más de 30 días de atraso', 'MEDIA', 'Conciliar las cuentas bancarias dentro de los 5 días hábiles del cierre.',
 'Asignar un analista dedicado a conciliaciones', CURRENT_DATE - 180, 'CERRADO', CURRENT_DATE - 232, now() - interval '185 days'),
((SELECT id_auditoria FROM auditoria WHERE codigo = 'AUD-2026-001'), seed_r('RSK-FIN-0042'), seed_u('jcondori'), seed_u('pvargas'),
 'Deuda en USD sin cobertura cambiaria', 'ALTA', 'Contratar instrumentos de cobertura y definir una política de calce.',
 'Plan de mitigación del riesgo RSK-FIN-0042', CURRENT_DATE + 25, 'EN_PROCESO', CURRENT_DATE - 231, NULL),
((SELECT id_auditoria FROM auditoria WHERE codigo = 'AUD-2026-002'), seed_r('RSK-OPE-0047'), seed_u('dlopez'), seed_u('pvargas'),
 'Un mismo usuario solicita y aprueba órdenes de compra', 'ALTA', 'Separar en el sistema los perfiles de solicitante y aprobador.',
 'Plan de mitigación del riesgo RSK-OPE-0047', CURRENT_DATE + 30, 'ABIERTO', CURRENT_DATE - 14, NULL),
((SELECT id_auditoria FROM auditoria WHERE codigo = 'AUD-2026-002'), NULL, seed_u('sgomez'), seed_u('pvargas'),
 'Proveedores sin evaluación anual de desempeño', 'MEDIA', 'Evaluar anualmente a los proveedores con compras mayores a 50 000 USD.',
 NULL, CURRENT_DATE + 20, 'ABIERTO', CURRENT_DATE - 9, NULL),
((SELECT id_auditoria FROM auditoria WHERE codigo = 'AUD-2026-004'), NULL, seed_u('ftorrez'), seed_u('pvargas'),
 'Diferencias de inventario no investigadas', 'MEDIA', 'Investigar y documentar toda diferencia mayor al 1 %.',
 'Procedimiento de inventarios cíclicos', CURRENT_DATE - 10, 'EN_PROCESO', CURRENT_DATE - 101, NULL),
((SELECT id_auditoria FROM auditoria WHERE codigo = 'AUD-2026-004'), NULL, seed_u('hsalazar'), seed_u('pvargas'),
 'Accesos al almacén sin registro', 'BAJA', 'Instalar control de acceso con tarjeta.',
 NULL, CURRENT_DATE - 40, 'CERRADO', CURRENT_DATE - 101, now() - interval '45 days');

-- ==================================================================== CUMPLIMIENTO NORMATIVO
SELECT seed_como('dflores');
INSERT INTO normativa (nombre, jurisdiccion, organismo, vigencia_desde, descripcion) VALUES
('Ley 393 de Servicios Financieros', 'Bolivia', 'ASFI', '2013-08-21', 'Regula las actividades de intermediación financiera y los reportes a la ASFI.'),
('Código Tributario Boliviano (Ley 2492)', 'Bolivia', 'Servicio de Impuestos Nacionales', '2003-08-02', 'Obligaciones tributarias formales y materiales.'),
('Ley General del Sistema Financiero (Ley 26702)', 'Perú', 'SBS', '1996-12-09', 'Requerimientos de liquidez y reportes a la SBS.'),
('Régimen del IGV y del impuesto a la renta', 'Perú', 'SUNAT', '1999-04-15', 'Declaraciones mensuales y libros electrónicos.'),
('Ley 21.521 Fintec', 'Chile', 'CMF', '2023-01-04', 'Registro y obligaciones de los prestadores de servicios financieros.'),
('Ley 19.628 sobre protección de la vida privada', 'Chile', 'Consejo para la Transparencia', '1999-08-28', 'Tratamiento de datos personales.'),
('Régimen informativo del BCRA', 'Argentina', 'Banco Central de la República Argentina', '2002-01-01', 'Posición global neta en moneda extranjera.'),
('Ley 25.326 de Protección de Datos Personales', 'Argentina', 'AAIP', '2000-11-02', 'Inscripción de bases de datos y derechos de los titulares.'),
('Circular Básica Jurídica: SARLAFT', 'Colombia', 'Superintendencia Financiera', '2014-11-28', 'Prevención del lavado de activos y reportes a la UIAF.'),
('Ley 1581 de 2012 (Habeas Data)', 'Colombia', 'Superintendencia de Industria y Comercio', '2012-10-17', 'Registro Nacional de Bases de Datos.'),
('Lei Geral de Proteção de Dados (LGPD)', 'Brasil', 'ANPD', '2020-09-18', 'Protección de datos personales y notificación de incidentes.'),
('Escrituração Fiscal Digital (SPED)', 'Brasil', 'Receita Federal', '2007-01-22', 'Obligaciones fiscales digitales mensuales.'),
('Reglamento General de Protección de Datos (RGPD)', 'Unión Europea', 'AEPD', '2018-05-25', 'Protección de datos personales en la Unión Europea.'),
('Ley 10/2010 de prevención del blanqueo de capitales', 'España', 'SEPBLAC', '2010-04-29', 'Medidas de diligencia debida y examen externo.'),
('ISO/IEC 27001:2022', 'Internacional', 'ISO', '2022-10-25', 'Sistema de gestión de seguridad de la información.'),
('Ley Sarbanes-Oxley, sección 404', 'Estados Unidos', 'SEC', '2002-07-30', 'Control interno sobre la información financiera.');

INSERT INTO obligacion (id_normativa, id_filial, descripcion, periodicidad, fecha_vencimiento)
SELECT n.id_normativa, seed_f(x.filial), x.descripcion, x.periodicidad, CURRENT_DATE + x.dias
FROM (VALUES
    ('Ley 393 de Servicios Financieros', 'LPZ', 'Reporte mensual de posición cambiaria', 'MENSUAL', 6),
    ('Ley 393 de Servicios Financieros', 'LPZ', 'Informe anual de gestión integral de riesgos', 'ANUAL', 75),
    ('Ley 393 de Servicios Financieros', 'SCZ', 'Reporte de operaciones en moneda extranjera', 'MENSUAL', -25),
    ('Código Tributario Boliviano (Ley 2492)', 'LPZ', 'Declaración jurada del IUE', 'ANUAL', -200),
    ('Código Tributario Boliviano (Ley 2492)', 'CBB', 'Libro de compras y ventas IVA en el SIAT', 'MENSUAL', 12),
    ('Ley General del Sistema Financiero (Ley 26702)', 'LIM', 'Reporte de liquidez a la SBS', 'MENSUAL', 3),
    ('Régimen del IGV y del impuesto a la renta', 'LIM', 'Declaración mensual del IGV (PDT 621)', 'MENSUAL', -20),
    ('Régimen del IGV y del impuesto a la renta', 'AQP', 'Libros electrónicos (PLE)', 'MENSUAL', 27),
    ('Ley 21.521 Fintec', 'SCL', 'Inscripción en el Registro de Prestadores de Servicios Financieros', 'UNICA', 90),
    ('Ley 19.628 sobre protección de la vida privada', 'SCL', 'Política de tratamiento de datos personales publicada', 'UNICA', -60),
    ('Régimen informativo del BCRA', 'BUE', 'Régimen informativo de posición global neta en moneda extranjera', 'MENSUAL', 9),
    ('Ley 25.326 de Protección de Datos Personales', 'BUE', 'Inscripción de bases de datos ante la AAIP', 'ANUAL', -40),
    ('Circular Básica Jurídica: SARLAFT', 'BOG', 'Reporte de operaciones sospechosas (ROS) a la UIAF', 'MENSUAL', 14),
    ('Circular Básica Jurídica: SARLAFT', 'MDE', 'Actualización anual de datos de clientes', 'ANUAL', -15),
    ('Ley 1581 de 2012 (Habeas Data)', 'BOG', 'Actualización del Registro Nacional de Bases de Datos', 'ANUAL', 45),
    ('Lei Geral de Proteção de Dados (LGPD)', 'SAO', 'Notificación de incidentes de seguridad a la ANPD', 'UNICA', -30),
    ('Escrituração Fiscal Digital (SPED)', 'SAO', 'Entrega del SPED Fiscal (EFD ICMS/IPI)', 'MENSUAL', 22),
    ('Reglamento General de Protección de Datos (RGPD)', 'MAD', 'Registro de actividades de tratamiento', 'ANUAL', -90),
    ('Reglamento General de Protección de Datos (RGPD)', 'MAD', 'Evaluación de impacto de protección de datos (DPIA)', 'ANUAL', 18),
    ('Ley 10/2010 de prevención del blanqueo de capitales', 'MAD', 'Informe anual del experto externo', 'ANUAL', 120),
    ('ISO/IEC 27001:2022', 'LPZ', 'Auditoría de seguimiento de la certificación', 'ANUAL', 150),
    ('Ley Sarbanes-Oxley, sección 404', 'LPZ', 'Certificación de controles internos sobre la información financiera', 'ANUAL', 40)
) AS x(normativa, filial, descripcion, periodicidad, dias)
JOIN normativa n ON n.nombre = x.normativa;

-- Evaluaciones de cumplimiento (las "No cumple" generan un riesgo Legal por RN10)
INSERT INTO evaluacion_cumplimiento (id_obligacion, id_evaluador, fecha, resultado, porcentaje, observacion, lista_verificacion)
SELECT o.id_obligacion, seed_u('dflores'), CURRENT_DATE - x.dias, x.resultado, x.porcentaje, x.observacion, x.lista::jsonb
FROM (VALUES
    ('Reporte de operaciones en moneda extranjera', 26, 'CUMPLE', 100, 'Enviado dentro del plazo', '[{"item":"Reporte generado","ok":true},{"item":"Enviado a la ASFI","ok":true},{"item":"Constancia archivada","ok":true}]'),
    ('Declaración jurada del IUE', 201, 'CUMPLE', 100, 'Presentada y pagada', '[{"item":"Estados financieros auditados","ok":true},{"item":"Formulario 500 presentado","ok":true}]'),
    ('Declaración mensual del IGV (PDT 621)', 19, 'PARCIAL', 70, 'Se declaró una factura fuera de plazo', '[{"item":"Declaración presentada","ok":true},{"item":"Todas las facturas incluidas","ok":false},{"item":"Pago realizado","ok":true}]'),
    ('Política de tratamiento de datos personales publicada', 61, 'CUMPLE', 100, 'Publicada en el sitio web', '[{"item":"Política aprobada","ok":true},{"item":"Publicada","ok":true}]'),
    ('Inscripción de bases de datos ante la AAIP', 38, 'NO_CUMPLE', 0, 'Las bases de datos de clientes y empleados no están inscritas', '[{"item":"Inventario de bases","ok":true},{"item":"Inscripción ante la AAIP","ok":false}]'),
    ('Actualización anual de datos de clientes', 14, 'PARCIAL', 60, 'Actualizado el 60 % de los clientes de riesgo alto', '[{"item":"Clientes de riesgo alto","ok":false},{"item":"Clientes de riesgo medio","ok":true}]'),
    ('Notificación de incidentes de seguridad a la ANPD', 25, 'NO_CUMPLE', 0, 'El incidente de agosto no se notificó en el plazo legal', '[{"item":"Procedimiento de notificación","ok":false},{"item":"Registro del incidente","ok":true},{"item":"Notificación a la ANPD","ok":false}]'),
    ('Registro de actividades de tratamiento', 91, 'CUMPLE', 100, 'Registro actualizado y aprobado por el DPO', '[{"item":"Registro actualizado","ok":true},{"item":"Aprobación del DPO","ok":true}]')
) AS x(descripcion, dias, resultado, porcentaje, observacion, lista)
JOIN obligacion o ON o.descripcion = x.descripcion;

-- ==================================================================== VALIDACIÓN, CONCILIACIÓN Y ALERTAS
-- Perú, hace dos meses: CON_DIFERENCIA y su alerta (prototipo P01), hallazgo aún pendiente (O7)
INSERT INTO alerta (id_riesgo, id_kri, id_conciliacion, id_asignado, tipo, severidad, mensaje, fecha_generacion, estado, fecha_notificacion)
SELECT k.id_riesgo, k.id_kri, c.id_conciliacion, seed_u('lmamani'), 'CONCILIACION_DIFERENCIA', 'ADVERTENCIA',
       format('Conciliación Andino Perú %s: %s difiere %s %% del ERP (tolerancia 2 %%). Registre el hallazgo financiero (RN18).',
              c.periodo, k.nombre, trim_scale(c.diferencia_pct)),
       c.fecha + interval '1 minute', 'NOTIFICADA', c.fecha + interval '2 minutes'
FROM conciliacion_kri c JOIN indicador_kri k USING (id_kri)
WHERE c.estado = 'CON_DIFERENCIA';

-- Alertas de KRI con distintos estados (D09)
INSERT INTO alerta (id_riesgo, id_kri, id_medicion, id_asignado, id_asignado_original, tipo, severidad, mensaje,
                    fecha_generacion, estado, fecha_notificacion, fecha_atencion, fecha_escalamiento, nivel_escalamiento,
                    fecha_resolucion, fecha_cierre, accion_correctiva)
SELECT r.id_riesgo, k.id_kri, seed_ultima_medicion(k.id_kri), seed_u(x.asignado), seed_u(x.original), 'KRI_UMBRAL', x.sev,
       format('%s · %s = %s %s %s el umbral %s (%s %s)', r.codigo, k.nombre, trim_scale(m.valor), k.unidad,
              CASE k.direccion WHEN 'MAYOR' THEN 'supera' ELSE 'está por debajo de' END,
              CASE x.sev WHEN 'CRITICA' THEN 'rojo' ELSE 'amarillo' END,
              trim_scale(CASE x.sev WHEN 'CRITICA' THEN k.umbral_rojo ELSE k.umbral_amarillo END), k.unidad),
       now() - x.hace, x.estado, now() - x.hace + interval '1 minute',
       CASE WHEN x.estado IN ('EN_ATENCION', 'RESUELTA', 'CERRADA') THEN now() - x.hace + interval '40 minutes' END,
       CASE WHEN x.estado = 'ESCALADA' THEN now() - interval '6 hours' END,
       CASE WHEN x.estado = 'ESCALADA' THEN 1 ELSE 0 END,
       CASE WHEN x.estado IN ('RESUELTA', 'CERRADA') THEN now() - x.hace + interval '20 hours' END,
       CASE WHEN x.estado = 'CERRADA' THEN now() - x.hace + interval '22 hours' END,
       CASE WHEN x.estado IN ('RESUELTA', 'CERRADA') THEN x.accion END
FROM (VALUES
    ('RSK-FIN-0042', 'Variación mensual del tipo de cambio paralelo', 'CRITICA', 'jcondori', 'jcondori', interval '6 hours', 'EN_ATENCION', NULL),
    ('RSK-TEC-0017', 'Intentos de intrusión bloqueados por día', 'CRITICA', 'sgomez', 'dlopez', interval '30 hours', 'ESCALADA', NULL),
    ('RSK-TEC-0021', 'Disponibilidad mensual del ERP', 'CRITICA', 'omendoza', 'omendoza', interval '23 hours 40 minutes', 'NOTIFICADA', NULL),
    ('RSK-FIN-0063', 'Inflación mensual', 'ADVERTENCIA', 'nacosta', 'nacosta', interval '20 hours', 'NOTIFICADA', NULL),
    ('RSK-TEC-0025', 'Equipos sin parche crítico', 'ADVERTENCIA', 'acastillo', 'acastillo', interval '50 hours', 'RESUELTA', 'Parches críticos aplicados en 55 equipos; quedan 26 en ventana de mantenimiento.'),
    ('RSK-FIN-0051', 'Ratio de liquidez corriente', 'ADVERTENCIA', 'lparedes', 'lparedes', interval '2 days 4 hours', 'NOTIFICADA', NULL),
    ('RSK-FIN-0058', 'Tasa SELIC', 'ADVERTENCIA', 'rsouza', 'rsouza', interval '9 days', 'CERRADA', 'Se renegoció el spread del préstamo principal.'),
    ('RSK-FIN-0077', 'Cartera vencida a más de 60 días', 'CRITICA', 'sgomez', 'sgomez', interval '15 hours', 'EN_ATENCION', NULL)
) AS x(codigo, kri, sev, asignado, original, hace, estado, accion)
JOIN riesgo r ON r.codigo = x.codigo
JOIN indicador_kri k ON k.id_riesgo = r.id_riesgo AND k.nombre = x.kri
JOIN LATERAL (SELECT valor FROM medicion_kri WHERE id_kri = k.id_kri ORDER BY fecha DESC LIMIT 1) m ON TRUE;

-- Alertas históricas cerradas (estadística de atención dentro del SLA)
INSERT INTO alerta (id_riesgo, id_kri, id_asignado, tipo, severidad, mensaje, fecha_generacion, estado,
                    fecha_notificacion, fecha_atencion, fecha_resolucion, fecha_cierre, accion_correctiva)
SELECT r.id_riesgo, k.id_kri, r.id_responsable, 'KRI_UMBRAL', s.sev,
       format('%s · %s fuera de umbral (registro histórico)', r.codigo, k.nombre),
       now() - make_interval(days => s.dias), 'CERRADA',
       now() - make_interval(days => s.dias) + interval '2 minutes',
       now() - make_interval(days => s.dias) + make_interval(hours => s.horas_atencion),
       now() - make_interval(days => s.dias) + make_interval(hours => s.horas_atencion + 6),
       now() - make_interval(days => s.dias) + make_interval(hours => s.horas_atencion + 9),
       'Acción correctiva registrada y verificada por el Gestor'
FROM (VALUES
    ('RSK-TEC-0017', 'CRITICA', 41, 3), ('RSK-TEC-0017', 'ADVERTENCIA', 63, 20), ('RSK-FIN-0042', 'ADVERTENCIA', 70, 12),
    ('RSK-TEC-0025', 'CRITICA', 55, 5), ('RSK-FIN-0063', 'CRITICA', 110, 30), ('RSK-TEC-0030', 'CRITICA', 150, 8),
    ('RSK-LEG-0009', 'ADVERTENCIA', 120, 40), ('RSK-FIN-0084', 'CRITICA', 175, 4), ('RSK-OPE-0031', 'ADVERTENCIA', 35, 18),
    ('RSK-FIN-0042', 'ADVERTENCIA', 32, 9), ('RSK-TEC-0021', 'ADVERTENCIA', 12, 15), ('RSK-REP-0005', 'ADVERTENCIA', 25, 50)
) AS s(codigo, sev, dias, horas_atencion)
JOIN riesgo r ON r.codigo = s.codigo
JOIN LATERAL (SELECT id_kri, nombre FROM indicador_kri WHERE id_riesgo = r.id_riesgo ORDER BY id_kri LIMIT 1) k ON TRUE;

-- Aviso legal por el riesgo generado desde cumplimiento en Brasil (RN10)
INSERT INTO alerta (id_riesgo, id_obligacion, id_asignado, tipo, severidad, mensaje, fecha_generacion, estado, fecha_notificacion)
SELECT o.id_riesgo_generado, o.id_obligacion, r.id_responsable, 'RIESGO_LEGAL', 'CRITICA',
       format('%s · Riesgo legal registrado por incumplimiento: %s (RN10)', r.codigo, o.descripcion),
       now() - interval '25 days', 'NOTIFICADA', now() - interval '25 days' + interval '1 minute'
FROM obligacion o JOIN riesgo r ON r.id_riesgo = o.id_riesgo_generado
WHERE o.descripcion = 'Notificación de incidentes de seguridad a la ANPD';

-- ==================================================================== NOTIFICACIONES INICIALES
INSERT INTO notificacion (id_usuario, titulo, mensaje, tipo, enlace, leida, fecha)
SELECT seed_u(x.login), x.titulo, x.mensaje, x.tipo, x.enlace, x.leida, now() - x.hace
FROM (VALUES
    ('crojas', 'Plan pendiente de aprobación', 'RSK-TEC-0021 · Caída del ERP por falta de alta disponibilidad (CRITICO) espera su aprobación.', 'APROBACION', '/riesgos', FALSE, interval '2 days'),
    ('crojas', 'Plan pendiente de aprobación', 'RSK-OPE-0047 · Fraude interno en el proceso de compras (ALTO) espera su aprobación.', 'APROBACION', '/riesgos', FALSE, interval '5 days'),
    ('crojas', 'Plan pendiente de aprobación', 'RSK-FIN-0051 · Riesgo de liquidez por concentración de vencimientos (ALTO) espera su aprobación.', 'APROBACION', '/riesgos', FALSE, interval '4 days'),
    ('crojas', 'Alerta escalada', 'RSK-TEC-0017 · Intrusiones en la red corporativa: la alerta crítica superó las 24 horas sin atención (RN07).', 'ESCALAMIENTO', '/alertas', FALSE, interval '6 hours'),
    ('lmamani', 'Validación financiera pendiente', 'RSK-FIN-0051 · Andino Perú: nivel ALTO, impacto 340 000 USD (RN16).', 'VALIDACION', '/validacion-financiera', FALSE, interval '6 days'),
    ('lmamani', 'Validación financiera pendiente', 'RSK-FIN-0058 · Andino Brasil: nivel ALTO, impacto 150 000 USD (RN16).', 'VALIDACION', '/validacion-financiera', FALSE, interval '5 days'),
    ('lmamani', 'Validación financiera pendiente', 'RSK-FIN-0077 · Andino Colombia: nivel MEDIO, impacto 150 000 USD (RN16).', 'VALIDACION', '/validacion-financiera', FALSE, interval '10 days'),
    ('lmamani', 'Conciliación con diferencia', 'Andino Perú: el ratio de liquidez difiere 3,4 % del ERP. Registre el hallazgo financiero (RN18).', 'ALERTA', '/conciliacion', FALSE, interval '20 days'),
    ('jcondori', 'Alerta crítica', 'RSK-FIN-0042 · La variación del tipo de cambio paralelo (5,8 %) supera el umbral rojo.', 'ALERTA', '/alertas', FALSE, interval '6 hours'),
    ('jcondori', 'Acción de mitigación asignada', 'RSK-FIN-0042 · Contratar cobertura forward USD/BOB por el 60 % de la deuda.', 'TAREA', '/mis-acciones', TRUE, interval '63 days'),
    ('mquispe', 'Impacto financiero observado', 'RSK-FIN-0086 · El Auditor Financiero observó la evaluación. Debe reevaluar el riesgo (CU02).', 'VALIDACION', '/riesgos', FALSE, interval '20 days'),
    ('aperez', 'Impacto financiero observado', 'RSK-FIN-0086 · El tipo de cambio registrado no coincide con el ERP.', 'VALIDACION', '/riesgos', FALSE, interval '20 days'),
    ('aperez', 'Acciones completadas', 'RSK-TEC-0030: todas las acciones están cerradas. Registre la evaluación residual (RN11).', 'TAREA', '/riesgos', FALSE, interval '8 days'),
    ('cmunoz', 'Plan pendiente de aprobación', 'RSK-FIN-0071 · Riesgo de crédito de clientes del sector minero (MEDIO) espera su aprobación.', 'APROBACION', '/riesgos', FALSE, interval '3 days'),
    ('dflores', 'Obligación por vencer', 'Andino Brasil: Entrega del SPED Fiscal (EFD ICMS/IPI) vence en 22 días (RN09).', 'ALERTA', '/cumplimiento', FALSE, interval '8 days'),
    ('sgomez', 'Alerta escalada a su cargo', 'RSK-TEC-0017 · Intrusiones en la red corporativa: alerta crítica escalada desde Diego López.', 'ESCALAMIENTO', '/alertas', FALSE, interval '6 hours'),
    ('omendoza', 'Alerta crítica', 'RSK-TEC-0021 · La disponibilidad del ERP (97,4 %) está por debajo del umbral rojo.', 'ALERTA', '/alertas', FALSE, interval '23 hours'),
    ('pvargas', 'Auditoría en ejecución', 'AUD-2026-002 · Compras Andino Colombia está en ejecución hasta dentro de 10 días.', 'INFO', '/auditorias', TRUE, interval '20 days'),
    ('admin', 'Sistema listo', 'Se cargaron los datos de demostración del caso de estudio Grupo Andino Global S.A.', 'INFO', '/administracion', FALSE, interval '1 minute')
) AS x(login, titulo, mensaje, tipo, enlace, leida, hace);

-- ==================================================================== BITÁCORA HISTÓRICA
-- Las operaciones de la carga se registran con su fecha real de negocio (los triggers de
-- auditoría no actúan durante la carga inicial).
INSERT INTO bitacora (id_usuario, fecha_hora, operacion, entidad, id_entidad, resultado, detalle, origen)
SELECT * FROM (
    SELECT r.id_registrador, r.fecha_registro + time '09:15', 'REGISTRAR_RIESGO', 'RIESGO', r.id_riesgo::bigint, 'OK',
           jsonb_build_object('registro', jsonb_build_object('codigo', r.codigo, 'titulo', r.titulo, 'categoria', r.categoria)), 'TRIGGER'
    FROM riesgo r
    UNION ALL
    SELECT e.id_evaluador, e.fecha + time '11:40', 'EVALUAR_RIESGO', 'EVALUACION_RIESGO', e.id_evaluacion::bigint, 'OK',
           jsonb_build_object('registro', jsonb_build_object('riesgo', r.codigo, 'tipo', e.tipo, 'probabilidad', e.probabilidad,
               'impacto', e.impacto, 'nivel', e.nivel, 'impacto_usd', e.impacto_usd, 'estado_validacion', e.estado_validacion)), 'TRIGGER'
    FROM evaluacion_riesgo e JOIN riesgo r USING (id_riesgo)
    UNION ALL
    SELECT v.id_auditor_fin, v.fecha, CASE v.resultado WHEN 'VALIDADA' THEN 'VALIDAR_IMPACTO' ELSE 'OBSERVAR_IMPACTO' END,
           'VALIDACION_FINANCIERA', v.id_validacion::bigint, 'OK',
           jsonb_build_object('registro', jsonb_build_object('riesgo', r.codigo, 'resultado', v.resultado, 'tipo_cambio', v.tipo_cambio,
               'impacto_usd_validado', v.impacto_usd_validado, 'observacion', v.observacion)), 'TRIGGER'
    FROM validacion_financiera v JOIN evaluacion_riesgo e USING (id_evaluacion) JOIN riesgo r USING (id_riesgo)
    UNION ALL
    SELECT p.id_proponente, p.fecha_propuesta, 'PROPONER_PLAN', 'PLAN_MITIGACION', p.id_plan::bigint, 'OK',
           jsonb_build_object('cambios', jsonb_build_object('estado', jsonb_build_object('antes', 'BORRADOR', 'despues', 'PROPUESTO')), 'riesgo', r.codigo), 'TRIGGER'
    FROM plan_mitigacion p JOIN riesgo r USING (id_riesgo) WHERE p.fecha_propuesta IS NOT NULL
    UNION ALL
    SELECT p.id_aprobador, p.fecha_aprobacion, 'APROBAR_TRATAMIENTO', 'PLAN_MITIGACION', p.id_plan::bigint, 'OK',
           jsonb_build_object('cambios', jsonb_build_object('estado', jsonb_build_object('antes', 'PROPUESTO', 'despues', 'APROBADO')), 'riesgo', r.codigo, 'estrategia', p.estrategia), 'TRIGGER'
    FROM plan_mitigacion p JOIN riesgo r USING (id_riesgo) WHERE p.fecha_aprobacion IS NOT NULL
    UNION ALL
    SELECT a.id_responsable, a.fecha_cierre, 'CERRAR_ACCION', 'ACCION_MITIGACION', a.id_accion::bigint, 'OK',
           jsonb_build_object('cambios', jsonb_build_object('avance', jsonb_build_object('despues', 100), 'estado', jsonb_build_object('despues', 'CERRADA'))), 'TRIGGER'
    FROM accion_mitigacion a WHERE a.fecha_cierre IS NOT NULL
    UNION ALL
    SELECT c.id_auditor_fin, c.fecha, 'CONCILIAR_KRI', 'CONCILIACION_KRI', c.id_conciliacion::bigint, 'OK',
           jsonb_build_object('registro', jsonb_build_object('periodo', c.periodo, 'valor_sistema', c.valor_sistema,
               'valor_erp', c.valor_erp, 'diferencia_pct', c.diferencia_pct, 'estado', c.estado)), 'TRIGGER'
    FROM conciliacion_kri c
    UNION ALL
    SELECT a.id_auditor, a.fecha_inicio - 30 + time '10:00', 'PLANIFICAR_AUDITORIA', 'AUDITORIA', a.id_auditoria::bigint, 'OK',
           jsonb_build_object('registro', jsonb_build_object('codigo', a.codigo, 'alcance', a.alcance)), 'TRIGGER'
    FROM auditoria a
    UNION ALL
    SELECT h.id_registrador, h.fecha_registro + time '16:20', 'REGISTRAR_HALLAZGO', 'HALLAZGO', h.id_hallazgo::bigint, 'OK',
           jsonb_build_object('registro', jsonb_build_object('severidad', h.severidad, 'descripcion', h.descripcion)), 'TRIGGER'
    FROM hallazgo h
    UNION ALL
    SELECT ec.id_evaluador, ec.fecha + time '15:05', 'EVALUAR_CUMPLIMIENTO', 'EVALUACION_CUMPLIMIENTO', ec.id_eval_cump::bigint, 'OK',
           jsonb_build_object('registro', jsonb_build_object('obligacion', o.descripcion, 'resultado', ec.resultado, 'porcentaje', ec.porcentaje)), 'TRIGGER'
    FROM evaluacion_cumplimiento ec JOIN obligacion o USING (id_obligacion)
    UNION ALL
    SELECT NULL::int, a.fecha_generacion, 'GENERAR_ALERTA', 'ALERTA', a.id_alerta::bigint, 'OK',
           jsonb_build_object('registro', jsonb_build_object('severidad', a.severidad, 'mensaje', a.mensaje)), 'TRIGGER'
    FROM alerta a
    UNION ALL
    SELECT NULL::int, a.fecha_escalamiento, 'ESCALAR_ALERTA', 'ALERTA', a.id_alerta::bigint, 'OK',
           jsonb_build_object('cambios', jsonb_build_object('estado', jsonb_build_object('antes', 'NOTIFICADA', 'despues', 'ESCALADA'),
               'id_asignado', jsonb_build_object('antes', a.id_asignado_original, 'despues', a.id_asignado))), 'TRIGGER'
    FROM alerta a WHERE a.fecha_escalamiento IS NOT NULL
    UNION ALL
    SELECT a.id_asignado, a.fecha_atencion, 'ATENDER_ALERTA', 'ALERTA', a.id_alerta::bigint, 'OK',
           jsonb_build_object('cambios', jsonb_build_object('estado', jsonb_build_object('despues', 'EN_ATENCION'))), 'TRIGGER'
    FROM alerta a WHERE a.fecha_atencion IS NOT NULL
    UNION ALL
    SELECT seed_u('admin'), now() - interval '3 days', 'MODIFICAR_PARAMETRO', 'PARAMETRO', NULL::bigint, 'OK',
           jsonb_build_object('clave', 'tolerancia_conciliacion_pct', 'cambios', jsonb_build_object('valor', jsonb_build_object('antes', '2.5', 'despues', '2'))), 'TRIGGER'
    UNION ALL
    SELECT seed_u('lmamani'), now() - interval '9 days', 'VALIDAR_IMPACTO', 'VALIDACION_FINANCIERA', NULL::bigint, 'RECHAZADO',
           jsonb_build_object('riesgo', 'RSK-FIN-0093', 'motivo', 'RN17: conflicto de funciones: el auditor es responsable del riesgo'), 'APLICACION'
) AS b(id_usuario, fecha_hora, operacion, entidad, id_entidad, resultado, detalle, origen)
WHERE b.fecha_hora IS NOT NULL AND b.fecha_hora <= now();

-- ==================================================================== Limpieza
DROP FUNCTION seed_ultima_medicion(INT);
DROP FUNCTION seed_serie(INT, NUMERIC[], INTERVAL);
DROP FUNCTION seed_kri(TEXT, TEXT, TEXT, NUMERIC, NUMERIC, TEXT, TEXT, TEXT, BOOLEAN);
DROP FUNCTION seed_control(TEXT, TEXT, TEXT, INT);
DROP FUNCTION seed_avance(INT, INT, INT, TEXT);
DROP FUNCTION seed_aprobar(TEXT, TEXT, INT);
DROP FUNCTION seed_proponer(TEXT, TEXT, INT);
DROP FUNCTION seed_accion(INT, TEXT, TEXT, INT, NUMERIC);
DROP FUNCTION seed_plan(TEXT, TEXT, TEXT, INT, TEXT);
DROP FUNCTION seed_validar(TEXT, TEXT, INT, TEXT, TEXT);
DROP FUNCTION seed_eval(TEXT, TEXT, INT, TEXT, INT, INT, NUMERIC, NUMERIC, TEXT);
DROP FUNCTION seed_riesgo(TEXT, TEXT, TEXT, TEXT, TEXT, INT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT);
DROP FUNCTION seed_como(TEXT);
DROP FUNCTION seed_r(TEXT);
DROP FUNCTION seed_f(TEXT);
DROP FUNCTION seed_u(TEXT);

SELECT set_config('app.id_usuario', '', true), set_config('app.carga_inicial', 'off', true);

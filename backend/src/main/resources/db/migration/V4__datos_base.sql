-- =====================================================================================
-- SIGRE Global - V4: datos base (catálogos, parámetros, filiales, roles y usuarios)
-- Caso de estudio: Grupo Andino Global S.A., 12 filiales en 7 países (datos de referencia).
-- Las claves de los usuarios las asigna la API al iniciar (hash Argon2id).
-- =====================================================================================
SELECT set_config('app.carga_inicial', 'on', true);

-- ------------------------------------------------------------------ Parámetros (Tabla 39)
INSERT INTO parametro (clave, valor, tipo, descripcion, regla, minimo, maximo) VALUES
('sla_critica_horas',            '24',     'NUMERO', 'Plazo de atención de una alerta crítica (horas)', 'RN07', 1, 168),
('sla_advertencia_horas',        '72',     'NUMERO', 'Plazo de atención de una alerta de advertencia (horas)', 'RN07', 1, 720),
('sla_preventiva_horas',         '168',    'NUMERO', 'Plazo de atención de un aviso preventivo (horas)', 'RN09', 1, 2160),
('aviso_obligacion_dias',        '30',     'NUMERO', 'Días de anticipación del aviso de vencimiento de una obligación', 'RN09', 1, 180),
('vigencia_evaluacion_meses',    '12',     'NUMERO', 'Vigencia de una evaluación de riesgo (meses)', 'RN03', 1, 36),
('plazo_validacion_dias_habiles','3',      'NUMERO', 'Plazo máximo de la validación del impacto financiero (días hábiles)', 'RN16', 1, 15),
('umbral_validacion_usd',        '100000', 'NUMERO', 'Impacto desde el cual se exige validación financiera (USD)', 'RN16', 0, NULL),
('tolerancia_conciliacion_pct',  '2',      'NUMERO', 'Tolerancia de la conciliación mensual con el ERP (%)', 'RN18', 0, 50),
('simulador_intervalo_seg',      '6',      'NUMERO', 'Intervalo del simulador de integraciones ERP/SIEM (segundos)', NULL, 2, 3600),
('simulador_prob_evento',        '0.12',   'NUMERO', 'Probabilidad de que el simulador envíe una medición fuera de umbral', NULL, 0, 1);

-- ------------------------------------------------------------------ Roles (actores A1 a A8)
INSERT INTO rol (codigo, nombre, corporativo, descripcion) VALUES
('GESTOR',               'Gestor de Riesgos',         TRUE,  'A1. Registra y evalúa riesgos, define planes e indicadores KRI.'),
('RESPONSABLE',          'Responsable de Riesgo',     FALSE, 'A2. Ejecuta las acciones de mitigación y atiende las alertas.'),
('AUDITOR_INTERNO',      'Auditor Interno',           TRUE,  'A3. Planifica auditorías y registra hallazgos.'),
('OFICIAL_CUMPLIMIENTO', 'Oficial de Cumplimiento',   TRUE,  'A4. Registra normativas y obligaciones y evalúa el cumplimiento.'),
('DIRECTOR',             'Director de Riesgos (CRO)', TRUE,  'A5. Define el apetito de riesgo y aprueba riesgos Altos y Críticos.'),
('GERENTE_FILIAL',       'Gerente de Filial',         FALSE, 'A6. Aprueba riesgos Bajos y Medios de su filial.'),
('ADMIN',                'Administrador del Sistema', TRUE,  'A7. Gestiona usuarios, roles, filiales y parámetros.'),
('AUDITOR_FINANCIERO',   'Auditor Financiero',        TRUE,  'A8. Valida el impacto financiero y concilia los KRI financieros con el ERP.');

-- ------------------------------------------------------------------ Filiales
INSERT INTO filial (codigo, nombre, pais, codigo_pais, ciudad, region, moneda, apetito_riesgo, latitud, longitud, zona_horaria, casa_matriz) VALUES
('LPZ', 'Casa Matriz La Paz',        'Bolivia',   'BO', 'La Paz',       'Andina',   'BOB',  9, -16.48970, -68.11930, 'America/La_Paz', TRUE),
('SCZ', 'Andino Santa Cruz',         'Bolivia',   'BO', 'Santa Cruz',   'Andina',   'BOB',  9, -17.78330, -63.18210, 'America/La_Paz', FALSE),
('CBB', 'Andino Cochabamba',         'Bolivia',   'BO', 'Cochabamba',   'Andina',   'BOB',  9, -17.38950, -66.15680, 'America/La_Paz', FALSE),
('LIM', 'Andino Perú',               'Perú',      'PE', 'Lima',         'Andina',   'PEN',  8, -12.04640, -77.04280, 'America/Lima', FALSE),
('AQP', 'Andino Arequipa',           'Perú',      'PE', 'Arequipa',     'Andina',   'PEN',  8, -16.40900, -71.53750, 'America/Lima', FALSE),
('SCL', 'Andino Chile',              'Chile',     'CL', 'Santiago',     'Cono Sur', 'CLP', 10, -33.44890, -70.66930, 'America/Santiago', FALSE),
('ANF', 'Andino Antofagasta',        'Chile',     'CL', 'Antofagasta',  'Cono Sur', 'CLP', 10, -23.65090, -70.39750, 'America/Santiago', FALSE),
('BUE', 'Andino Argentina',          'Argentina', 'AR', 'Buenos Aires', 'Cono Sur', 'ARS',  6, -34.60370, -58.38160, 'America/Argentina/Buenos_Aires', FALSE),
('BOG', 'Andino Colombia',           'Colombia',  'CO', 'Bogotá',       'Andina',   'COP',  9,   4.71100, -74.07210, 'America/Bogota', FALSE),
('MDE', 'Andino Medellín',           'Colombia',  'CO', 'Medellín',     'Andina',   'COP',  9,   6.24420, -75.58120, 'America/Bogota', FALSE),
('SAO', 'Andino Brasil',             'Brasil',    'BR', 'São Paulo',    'Brasil',   'BRL',  8, -23.55050, -46.63330, 'America/Sao_Paulo', FALSE),
('MAD', 'Andino Europa',             'España',    'ES', 'Madrid',       'Europa',   'EUR',  6,  40.41680,  -3.70380, 'Europe/Madrid', FALSE);

-- ------------------------------------------------------------------ Usuarios de demostración
INSERT INTO usuario (id_filial, nombre_completo, login, email, cargo, idioma)
SELECT f.id_filial, x.nombre, x.login, x.login || '@andinoglobal.example', x.cargo, x.idioma
FROM (VALUES
    ('admin',      'Fernando Chávez',   'Administrador del Sistema',            'LPZ', 'es'),
    ('crojas',     'Carlos Rojas',      'Director de Riesgos (CRO)',            'LPZ', 'es'),
    ('mquispe',    'María Quispe',      'Gestora Corporativa de Riesgos',       'LPZ', 'es'),
    ('aperez',     'Alejandro Pérez',   'Gestor de Riesgos',                    'LPZ', 'es'),
    ('lmamani',    'Luis Mamani',       'Auditor Financiero',                   'LPZ', 'es'),
    ('pvargas',    'Patricia Vargas',   'Auditora Interna',                     'LPZ', 'es'),
    ('dflores',    'Daniela Flores',    'Oficial de Cumplimiento',              'LPZ', 'es'),
    ('rgutierrez', 'Roberto Gutiérrez', 'Gerente de Filial La Paz',             'LPZ', 'es'),
    ('jcondori',   'Jorge Condori',     'Jefe de Tesorería',                    'LPZ', 'es'),
    ('omendoza',   'Óscar Mendoza',     'Jefe de Tecnología',                   'LPZ', 'es'),
    ('hsalazar',   'Hugo Salazar',      'Gerente de Filial Santa Cruz',         'SCZ', 'es'),
    ('ftorrez',    'Freddy Torrez',     'Jefe de Operaciones',                  'SCZ', 'es'),
    ('kvillca',    'Karina Villca',     'Gerente de Filial Cochabamba',         'CBB', 'es'),
    ('lparedes',   'Lucía Paredes',     'Gerente de Filial Perú',               'LIM', 'es'),
    ('acastillo',  'Andrés Castillo',   'Jefe de Tecnología',                   'LIM', 'es'),
    ('privas',     'Paolo Rivas',       'Gerente de Filial Arequipa',           'AQP', 'es'),
    ('cmunoz',     'Camila Muñoz',      'Gerente de Filial Chile',              'SCL', 'es'),
    ('tbravo',     'Tomás Bravo',       'Jefe de Operaciones',                  'SCL', 'es'),
    ('isoto',      'Ignacio Soto',      'Gerente de Filial Antofagasta',        'ANF', 'es'),
    ('mferreyra',  'Martín Ferreyra',   'Gerente de Filial Argentina',          'BUE', 'es'),
    ('nacosta',    'Natalia Acosta',    'Jefa de Finanzas',                     'BUE', 'es'),
    ('sgomez',     'Sofía Gómez',       'Gerente de Filial Colombia',           'BOG', 'es'),
    ('dlopez',     'Diego López',       'Jefe de Ciberseguridad',               'BOG', 'es'),
    ('jrestrepo',  'Juliana Restrepo',  'Gerente de Filial Medellín',           'MDE', 'es'),
    ('vsilva',     'Valentina Silva',   'Gerente de Filial Brasil',             'SAO', 'pt'),
    ('rsouza',     'Rafael Souza',      'Gerente Financiero',                   'SAO', 'pt'),
    ('jmartin',    'Javier Martín',     'Gerente de Filial España',             'MAD', 'es'),
    ('egarcia',    'Elena García',      'Responsable de Cumplimiento',          'MAD', 'es')
) AS x(login, nombre, cargo, filial, idioma)
JOIN filial f ON f.codigo = x.filial;

-- Superior jerárquico (para el escalamiento de alertas, CU09)
UPDATE usuario u SET id_superior = s.id_usuario
FROM (VALUES
    ('mquispe', 'crojas'), ('aperez', 'crojas'), ('rgutierrez', 'crojas'), ('jcondori', 'rgutierrez'),
    ('omendoza', 'rgutierrez'), ('hsalazar', 'crojas'), ('ftorrez', 'hsalazar'), ('kvillca', 'crojas'),
    ('lparedes', 'crojas'), ('acastillo', 'lparedes'), ('privas', 'lparedes'), ('cmunoz', 'crojas'),
    ('tbravo', 'cmunoz'), ('isoto', 'cmunoz'), ('mferreyra', 'crojas'), ('nacosta', 'mferreyra'),
    ('sgomez', 'crojas'), ('dlopez', 'sgomez'), ('jrestrepo', 'sgomez'), ('vsilva', 'crojas'),
    ('rsouza', 'vsilva'), ('jmartin', 'crojas'), ('egarcia', 'jmartin'), ('dflores', 'crojas')
) AS x(login, superior)
JOIN usuario s ON s.login = x.superior
WHERE u.login = x.login;

INSERT INTO usuario_rol (id_usuario, id_rol)
SELECT u.id_usuario, r.id_rol
FROM (VALUES
    ('admin', 'ADMIN'), ('crojas', 'DIRECTOR'), ('mquispe', 'GESTOR'), ('aperez', 'GESTOR'),
    ('lmamani', 'AUDITOR_FINANCIERO'), ('pvargas', 'AUDITOR_INTERNO'), ('dflores', 'OFICIAL_CUMPLIMIENTO'),
    ('rgutierrez', 'GERENTE_FILIAL'), ('rgutierrez', 'RESPONSABLE'), ('jcondori', 'RESPONSABLE'), ('omendoza', 'RESPONSABLE'),
    ('hsalazar', 'GERENTE_FILIAL'), ('ftorrez', 'RESPONSABLE'), ('kvillca', 'GERENTE_FILIAL'), ('kvillca', 'RESPONSABLE'),
    ('lparedes', 'GERENTE_FILIAL'), ('lparedes', 'RESPONSABLE'), ('acastillo', 'RESPONSABLE'),
    ('privas', 'GERENTE_FILIAL'), ('privas', 'RESPONSABLE'), ('cmunoz', 'GERENTE_FILIAL'), ('cmunoz', 'RESPONSABLE'),
    ('tbravo', 'RESPONSABLE'), ('isoto', 'GERENTE_FILIAL'), ('isoto', 'RESPONSABLE'),
    ('mferreyra', 'GERENTE_FILIAL'), ('nacosta', 'RESPONSABLE'), ('sgomez', 'GERENTE_FILIAL'), ('sgomez', 'RESPONSABLE'),
    ('dlopez', 'RESPONSABLE'), ('jrestrepo', 'GERENTE_FILIAL'), ('jrestrepo', 'RESPONSABLE'),
    ('vsilva', 'GERENTE_FILIAL'), ('vsilva', 'RESPONSABLE'), ('rsouza', 'RESPONSABLE'),
    ('jmartin', 'GERENTE_FILIAL'), ('jmartin', 'RESPONSABLE'), ('egarcia', 'RESPONSABLE')
) AS x(login, rol)
JOIN usuario u ON u.login = x.login
JOIN rol r ON r.codigo = x.rol;

-- ------------------------------------------------------------------ Tipos de cambio publicados por el ERP
-- Serie diaria de 500 días (unidades de moneda local por 1 USD), con tendencia y variación suave.
INSERT INTO tipo_cambio (fecha, moneda, unidades_por_usd)
SELECT d::date, m.moneda,
       round((m.base * (1 + m.tendencia * ((d::date - CURRENT_DATE) / 365.0))
                      * (1 + m.vol * sin(((d::date - CURRENT_DATE) + m.fase) / 9.0))
                      * (1 + m.vol * 0.4 * cos(((d::date - CURRENT_DATE) + m.fase) / 2.3)))::numeric, 6)
FROM generate_series(CURRENT_DATE - 500, CURRENT_DATE, interval '1 day') AS d
CROSS JOIN (VALUES
    ('BOB', 6.96,    0.00, 0.000,  0),
    ('PEN', 3.72,    0.02, 0.004,  3),
    ('CLP', 945.0,   0.03, 0.008,  7),
    ('ARS', 1450.0,  0.35, 0.006,  1),
    ('COP', 4100.0,  0.04, 0.010,  5),
    ('BRL', 5.45,    0.05, 0.009,  2),
    ('EUR', 0.92,   -0.02, 0.006,  4)
) AS m(moneda, base, tendencia, vol, fase);

# SIGRE Global · Sistema de gestión de riesgos empresariales globales

Software del proyecto final de **INF-241 Análisis y Diseño de Sistemas** (Universidad Mayor de San Andrés, carrera de Informática). Implementa el diseño
del informe *Informe_Analisis_y_Diseno_SIGRE_Global* (versión 3): 8 actores, casos de uso CU01 a CU18,
reglas de negocio RN01 a RN18, prototipos P01 a P03 y los 26 casos de prueba del Anexo B.

![Tablero ejecutivo](frontend/public/medios/capturas/02-tablero.webp)

## 1. Cómo iniciarlo

| Paso | Qué hacer |
|---|---|
| 1 | Doble clic en **`iniciar.bat`**. Levanta PostgreSQL (puerto 5434), la API y abre el navegador en **http://localhost:8090**. |
| 2 | En la pantalla de ingreso use el **acceso rápido de demostración**: un clic sobre una persona entra con su rol. |
| 3 | Para apagar todo: **`detener.bat`** (o cierre la ventana negra). |

Otros scripts:

* **`reiniciar-demo.bat`**: borra lo que se haya probado y vuelve a cargar el escenario de demostración. Úselo **unos 15 minutos antes de la defensa**: así la alerta crítica de RSK-TEC-0021 vence su SLA de 24 h durante la presentación y se ve el escalamiento automático (CU09).
* **`compilar.bat`**: vuelve a compilar la aplicación web y la API (solo si se cambió el código).
* **`probar.bat`**: ejecuta las pruebas automáticas CP01 a CP26 (y S01 a S06 de seguridad) sobre la base `sigre_test`.
* **`respaldar.bat`**: copia de seguridad completa de la base (`pg_dump`) en `%LOCALAPPDATA%\SIGRE-Global\respaldos`; guarda las 14 últimas y muestra el comando para restaurarla.
* `multimedia\generar_multimedia.py`: vuelve a grabar el video, los GIF y las capturas (con la aplicación encendida).

### Instalarlo en otro equipo (después de clonar el repositorio)

1. Instale **Java 21** o superior ([Adoptium](https://adoptium.net)), **PostgreSQL 16 o 17** (instalador oficial en `C:\Program Files\PostgreSQL`) y **Node.js 20** o superior. Maven ya viene en `herramientas/`.
2. Ejecute **`iniciar.bat`**. La primera vez:
   * `scripts\configurar.ps1` crea `config\bd.properties` y `config\seguridad.properties` con claves aleatorias, activa el modo demostración y genera la clave de los usuarios de prueba (nada de eso se sube al repositorio);
   * `scripts\bd.ps1` crea un clúster PostgreSQL propio en el puerto 5434 (no toca el servicio del puerto 5432);
   * como no existe el jar, se ejecuta `compilar.bat` (instala las dependencias de la web y compila la API);
   * al arrancar, Flyway crea las tablas, los triggers y el escenario de demostración.
3. Se abre **http://localhost:8090**.

### Trabajar con Visual Studio Code

El sistema tiene tres piezas que se encienden en este orden:

| Pieza | Qué es | Puerto | Cómo se inicia en VS Code |
|---|---|---|---|
| **Base de datos** | PostgreSQL con las tablas, los triggers y los datos | 5434 | Tarea *1. Base de datos: iniciar* (también la lanza sola la API) |
| **API (backend)** | Aplicación Spring Boot en Java: reglas, seguridad, reportes, tiempo real | 8090 | *Ejecutar y depurar* → **2. API SIGRE (Spring Boot)** (o F5) |
| **Web (frontend)** | Pantallas en React; en desarrollo se recargan al guardar | 5173 | *Ejecutar y depurar* → **3. Web React (Vite)** |

1. *Archivo → Abrir carpeta…* → elija la carpeta `software` y acepte instalar las extensiones recomendadas.
2. Si el sistema está abierto con `iniciar.bat`, ciérrelo antes (o ejecute la tarea *Liberar el puerto 8090*).
3. En *Ejecutar y depurar* elija **Sistema completo (base + API + web)** y pulse ▶. Abra **http://localhost:5173**.
4. Puede poner puntos de interrupción en cualquier clase Java (por ejemplo `ServicioTratamiento.aprobar`).
5. Las pruebas CP01–CP26 se ejecutan desde el panel *Pruebas* o con la tarea *Pruebas CP01–CP26*.

Las tareas están en *Terminal → Ejecutar tarea…*.

## 2. Usuarios de demostración

No hace falta escribir ninguna clave: en la pantalla de ingreso, el **acceso rápido de demostración** entra con un clic
como cualquiera de estas personas. Si quiere ingresar escribiendo usuario y clave (por ejemplo, para mostrar el bloqueo
tras 5 intentos fallidos), la clave común está en `config\seguridad.properties`, propiedad `sigre.demo.clave-usuarios`.
Ese archivo se genera en cada equipo con una clave aleatoria y no se publica. Las claves se guardan con hash Argon2id.

El modo demostración (`sigre.demo.habilitado`) viene **apagado** en `application.yml`: sin él no existen el acceso rápido,
el simulador del ERP/SIEM ni la prueba de inalterabilidad con el dueño del esquema. `configurar.ps1` lo enciende solo en
el equipo local.

| Usuario | Persona | Rol (actor) | Qué mostrar con este usuario |
|---|---|---|---|
| `crojas` | Carlos Rojas | Director de Riesgos (A5) | Tablero corporativo, **Aprobaciones** (CU04), riesgos críticos y aceptación (RN04, RN12) |
| `mquispe` | María Quispe | Gestora de Riesgos (A1) | Registrar (CU01), evaluar con P03 (CU02), plan (CU03), KRI y umbrales |
| `jcondori` | Jorge Condori | Responsable de Riesgo (A2) | **Mis acciones** (CU05): avance, evidencia; atiende y cierra sus alertas asignadas (CU08) |
| `omendoza` | Óscar Mendoza | Responsable de Riesgo (A2) | Alerta crítica de RSK-TEC-0021 que se escala sola (CU09) |
| `pvargas` | Patricia Vargas | Auditora Interna (A3) | Plan de auditorías (CU10), hallazgos (CU11), **bitácora** y prueba de inalterabilidad (CU16) |
| `dflores` | Daniela Flores | Oficial de Cumplimiento (A4) | Normativas y obligaciones (CU12), evaluar «No cumple» → riesgo Legal (CU13, RN10) |
| `rgutierrez` | Roberto Gutiérrez | Gerente de Filial La Paz (A6) | Aprueba tratamientos de su filial; un riesgo crítico lo deriva al Director (RN04) |
| `lparedes` | Lucía Paredes | Gerente de Filial Perú (A6) | Solo ve los datos de Perú (RN14) |
| `admin` | Fernando Chávez | Administrador (A7) | Usuarios, roles (RN17), apetito por filial, parámetros (CU15) |
| `lmamani` | Luis Mamani | Auditor Financiero (A8) | **Validación financiera** P02 (CU17, RN16, RN17), posponerla si el ERP no responde y **conciliación con el ERP** (CU18, RN18) |

## 3. Guion sugerido para la defensa (10 minutos)

1. **Ingreso** (`crojas`): globo 3D con las 12 filiales, acceso por rol, JWT + Argon2id, doble factor TOTP opcional (Mi perfil).
2. **Tablero P01**: indicadores, globo con columnas por nivel de riesgo, mapa de calor 5×5 (clic en una celda filtra el catálogo), filtros por región, categoría y periodo (7, 30, 90 días o 1 año), alertas abiertas, obligaciones por vencer y «Flujo en vivo». Botones **PDF / Excel / Financiero** para exportar (RF18, RF24).
3. **Ciclo del riesgo** (`mquispe`): *Riesgos → Registrar riesgo*; el asistente avisa duplicados (CU01). En la ficha, **Evaluar** (P03) calcula nivel, conversión a USD (RN15) y si requiere validación (RN16). Defina el plan y propóngalo.
4. **Aprobación** (`rgutierrez` y luego `crojas`): el Gerente intenta aprobar un riesgo crítico → rechazo RN04 (queda en la bitácora como RECHAZADO). El Director ve la lista de verificación y aprueba: el riesgo pasa a EN_TRATAMIENTO.
5. **Tiempo real** (`mquispe`, *Indicadores KRI*): abra un KRI del SIEM y pulse «Simular SIEM → Rojo». El trigger pone el semáforo, genera la alerta y llega al instante (campana, centro de alertas, consola de *Integraciones*).
6. **Alertas** (`omendoza`/`jcondori`): kanban por estado con cuenta regresiva del SLA; el responsable asignado atiende → resuelve (acción correctiva) → cierra. El escalamiento es automático al vencer el SLA (sube al superior y avisa al Director); con `crojas` también está el botón «Escalar ahora».
7. **Auditor Financiero** (`lmamani`): bandeja P02 con contraste del tipo de cambio del ERP; intentar validar RSK-FIN-0093 → RN17 (es responsable). **ERP no disponible (CU17)**: en el riesgo financiero que evaluó `mquispe` en el paso 3 (categoría Financiero, nivel Alto o impacto de 100 000 USD o más), pulse «Posponer: ERP no disponible»: el plazo queda en pausa y aparece la incidencia en *Integraciones*; luego «Reanudar plazo». Los riesgos de la demostración ya están fuera de plazo y por eso no muestran el botón. *Conciliación ERP → Ejecutar*: Perú queda CON_DIFERENCIA (3,4 %) y Brasil CONCILIADA (1,2 %); registrar el hallazgo financiero.
8. **Cumplimiento** (`dflores`): evaluar una obligación con la lista de verificación; si «No cumple», nace un riesgo Legal y su alerta (RN10).
9. **Bitácora** (`pvargas`): filtros, diferencias antes/después y **Probar que es inalterable**: el rol de la aplicación no tiene permiso y el trigger bloquea incluso al dueño del esquema (RN13).
10. **Arquitectura** y **Vista móvil**: capas en ejecución, triggers, los 32 diagramas del informe, la PWA dentro de un teléfono; **Presentación** tiene el video y los GIF.

Extras para lucirse: **Ctrl+K** (buscador de riesgos y pantallas), **ventanas flotantes** (botón «Ventana» en un riesgo, «Mapa en ventana», gráfico de un KRI), tema claro/oscuro, idiomas ES/EN/PT, *Reportes* en Excel, PDF y CSV, y la vista de teléfono con menú inferior y hoja «Más».

## 4. Arquitectura (sección 10 del informe)

```
Navegador / PWA (React 19 + TypeScript, Three.js)
        │  HTTPS · JSON (API REST, OpenAPI en /api-docs/ui.html)      ▲ Server-Sent Events
        ▼                                                             │
API Spring Boot 3.5 (Java 21): controladores → servicios → dominio JPA (D02) · JWT, Argon2id, TOTP
        │  JDBC (rol app_rw)                                          ▲ LISTEN sigre_eventos
        ▼                                                             │
PostgreSQL 17: 21 tablas + vistas · 30 triggers con las reglas RN01–RN18 · bitácora inalterable · NOTIFY
        │
Sistemas externos (simulados): ERP corporativo · SIEM · servicio normativo · notificaciones (correo/SMS)
```

| Carpeta | Contenido |
|---|---|
| `backend/src/main/java/bo/umsa/sigre/` | Paquetes del diagrama D12: `riesgos`, `monitoreo`, `auditorias`, `cumplimiento`, `reportes`, `administracion`, `seguridad`, `integraciones`, `comun` |
| `backend/src/main/resources/db/migration/` | Flyway: `V1` esquema, `V2` funciones y triggers, `V3` roles y permisos, `V4` datos base, `V5` escenario de demostración, `V6` revocación de sesiones y roles de solo lectura (`auditor_ro`, `backup`), `V7` posponer la validación por ERP no disponible (CU17) |
| `backend/src/test/` | `CasosDePruebaTest`: CP01 a CP26 de punta a punta (HTTP → servicio → trigger) y S01 a S06 de seguridad |
| `frontend/src/` | `paginas/` (una por pantalla), `componentes/` (globo 3D, carruseles, gráficos, ventanas), `nucleo/` (API, sesión, eventos, idiomas) |
| `frontend/public/diagramas/` | Las 32 figuras del informe (galería de *Arquitectura*) |
| `frontend/public/medios/` | Video MP4, GIF y capturas de la *Presentación* |
| `scripts/` | `bd.ps1` (clúster PostgreSQL: `iniciar`, `detener`, `estado`, `reiniciar-datos`, `respaldar`, `consola`), `configurar.ps1`, `java.ps1` |
| `multimedia/` | `generar_multimedia.py`: graba el video, los GIF y las capturas con Playwright |
| `config/` | Conexión a la base y clave de firma JWT: se generan en cada equipo y no se versionan |

### Reglas que garantiza la base de datos

| Trigger | Regla |
|---|---|
| `trg_riesgo_antes_insertar` | Código RSK-CAT-####, título único por filial (CU01), responsable con acceso (RN14) |
| `trg_riesgo_transicion` | Estados del riesgo (D08) y condición de MITIGADO (RN11) |
| `trg_evaluacion_antes_insertar` | Nivel P×I (RN02), vigencia 12 meses (RN03), conversión a USD (RN15), validación requerida (RN16) |
| `trg_plan_antes_actualizar` | RN03, RN04, RN05, RN12, RN16 y separación de funciones al aprobar (CU04) |
| `trg_accion_antes` | Avance que no disminuye y evidencia obligatoria (CU05) |
| `trg_medicion_antes/despues` | Semáforo (RN06) y alerta automática (CU07) |
| `trg_alerta_transicion` | Estados de la alerta (D09), SLA (RN07), acción correctiva (CU08) |
| `chk_independencia` | El auditor no valida riesgos propios (RN17) |
| `trg_posposicion_antes_insertar`, `trg_incidencia_resuelta` | ERP no disponible: posponer dentro del plazo, abrir la incidencia y reanudar al resolverla (CU17, RN16) |
| `trg_conciliacion_antes/despues` | Tolerancia de la conciliación y alerta de diferencia (RN18) |
| `trg_eval_cumplimiento_despues` | «No cumple» crea el riesgo Legal (RN10) |
| `trg_usuario_rol_sod` | Auditor Financiero y Gestor no se combinan (RN17) |
| `trg_auditar` (19 tablas) | Bitácora con valores antes y después (RN13) |
| `trg_bitacora`, `trg_bitacora_truncate` | La bitácora solo admite inserciones (RN13) |

### Permisos por rol (Tabla 47 del informe)

Cada servicio comprueba el rol antes de actuar y la web oculta lo que el usuario no puede hacer.

| Operación | Quién puede |
|---|---|
| Registrar, modificar y evaluar riesgos, definir controles (CU01, CU02) | Gestor de Riesgos |
| Definir el plan, sus acciones y proponerlo (CU03) | Gestor de Riesgos |
| Aprobar o rechazar el tratamiento (CU04) | Director; Gerente solo en su filial y nunca un riesgo crítico (RN04) |
| Ejecutar acciones y subir evidencias (CU05) | El responsable de esa acción |
| Registrar mediciones manuales de KRI (CU06) | Gestor de Riesgos |
| Umbrales y activación de KRI | Gestor de Riesgos y Administrador |
| Atender, resolver y cerrar una alerta (CU08) | El responsable asignado |
| Escalar una alerta a mano (CU09) | Director y Administrador (el automático no depende de nadie) |
| Planificar auditorías (CU10) | Auditor Interno |
| Validación financiera y conciliación (CU17, CU18) | Auditor Financiero |
| Usuarios, roles, apetito y parámetros (CU15) | Administrador |

Además, un usuario de filial solo ve los datos de su filial (RN14): catálogos, reportes, tablero y eventos en tiempo real.

### Seguridad

* **Ingreso**: JWT firmado (HS256), claves con Argon2id, bloqueo de 15 minutos tras 5 intentos fallidos y doble factor
  TOTP opcional (cada código vale una sola vez y el desafío también).
* **Revocación inmediata**: al cambiar la clave, desactivar al usuario o cambiarle los roles o la filial, los tokens
  anteriores dejan de valer (columna `usuario.sesion_desde`).
* **Evidencias**: se comprueba la firma real del archivo, se sirven con su tipo verdadero, `nosniff` y una política CSP
  `sandbox`; solo PDF, imágenes y video se abren en el navegador, el resto se descarga.
* **Errores**: los datos mal formados responden 400/405/415 con un mensaje claro; un error interno muestra solo un
  número de referencia, nunca detalles del servidor.
* **Base de datos**: además del rol de la aplicación, `auditor_ro` (solo lectura de la bitácora y las vistas) y `backup`
  (solo lectura de todo) para auditoría externa y respaldos.

### Decisiones y adaptaciones respecto al informe

El informe (sección 10) propone una plataforma de producción. Para una defensa que corre en una sola computadora se
tomaron estas decisiones, sin cambiar las reglas de negocio:

| Informe | En este software |
|---|---|
| Keycloak con OAuth 2.0 / OpenID Connect | Autenticación propia con JWT, Argon2id y TOTP dentro de la API (mismos roles y alcance por filial, RNF01) |
| Quartz para tareas programadas | `@Scheduled` de Spring: escalamiento cada minuto (CU09), entrega de notificaciones, sincronización diaria con el ERP y recordatorio mensual de conciliación (CU18, RF23) |
| RabbitMQ para la cola de eventos | `LISTEN/NOTIFY` de PostgreSQL + Server-Sent Events hacia el navegador |
| ERP, SIEM, servicio normativo y notificaciones reales | Conectores simulados que publican por el mismo camino que usaría el sistema real |
| Docker, Prometheus y Grafana | Scripts `.bat`/PowerShell y `/actuator/health` (los detalles solo para el Administrador) |
| Interfaz en el idioma de cada filial | El menú y los nombres de los módulos cambian entre español, inglés y portugués; el contenido de las pantallas, los datos y los reportes están en español |

### CU17: «ERP no disponible»

El informe permite «posponer la validación sin vencer el plazo de 3 días hábiles» cuando el ERP no responde. En
*Validación financiera*, el botón **Posponer: ERP no disponible** pide el motivo y:

* deja el plazo de RN16 **en pausa**: los días hábiles que pasan mientras está pospuesta no cuentan, así que el auditor
  conserva los días que le quedaban;
* registra una **incidencia del ERP** (como en CU18), visible en *Integraciones*, y avisa al Gestor y al Director;
* solo se puede pedir dentro del plazo, por un Auditor Financiero sin conflicto de funciones (RN16, RN17).

El plazo se reanuda al resolver la incidencia en *Integraciones*, con el botón **Reanudar plazo** o al validar. Todo
queda en la bitácora como `POSPONER_VALIDACION` y `REANUDAR_VALIDACION` (tabla `posposicion_validacion`, migración `V7`).

## 5. Pruebas

`probar.bat` ejecuta los 26 casos del Anexo B, 6 pruebas de seguridad y la excepción «ERP no disponible» de CU17 como
pruebas de integración (JUnit 5 + MockMvc) contra la base `sigre_test`, que se vacía y se recrea con Flyway en cada
corrida. Resultado esperado: `Tests run: 33, Failures: 0, Errors: 0`.

| Prueba | Qué comprueba |
|---|---|
| CP17-E | CU17 con el ERP caído: posponer abre la incidencia; tras 10 días en pausa el plazo no vence ni genera la alerta de RN16; al reanudar se conservan los 3 días |
| S01 | Un JSON mal formado responde 400 |
| S02 | Un gerente de filial no ve integraciones ni usuarios de otras filiales (RN14) |
| S03 | Solo el Gestor registra riesgos y mediciones manuales (Tabla 47) |
| S04 | Un HTML disfrazado de PDF se rechaza; las evidencias salen con su tipo real y CSP |
| S05 | Al desactivar un usuario su token deja de valer en el acto |
| S06 | Los reportes se exportan en PDF y CSV además de Excel (RF18) |

## 6. Problemas frecuentes

* **El puerto 8090 está ocupado**: ejecute `detener.bat` y vuelva a iniciar.
* **Copia de seguridad antes de la defensa**: doble clic en `respaldar.bat`; para volver a ese estado use el comando `pg_restore` que muestra al terminar.
* **La base no arranca**: `powershell -ExecutionPolicy Bypass -File scripts\bd.ps1 -Accion estado` muestra el estado; los datos están en `%LOCALAPPDATA%\SIGRE-Global\pgdata`.
* **Quiero ver la API**: http://localhost:8090/api-docs/ui.html (pida el token en `/api/auth/login` y péguelo en *Authorize*).
* **Abrirlo en el teléfono**: con el equipo y el teléfono en la misma red, abra `http://IP-DEL-EQUIPO:8090` y elija «Agregar a la pantalla de inicio».

import { useEffect, useMemo, useState } from 'react';
import { motion } from 'framer-motion';
import { AppWindow, BookOpen, Boxes, Braces, Cpu, Database, ExternalLink, Layers, Maximize2, Network, ShieldCheck, Workflow, Zap } from 'lucide-react';
import { useDatos } from '../nucleo/contexto';
import { Carrusel3D } from '../componentes/Carrusel';
import { Encabezado, Modal, Segmentado, Tarjeta } from '../componentes/Ui';
import { useVentanas } from '../componentes/Ventanas';

interface Figura { numero: number; titulo: string; archivo: string; pagina: number }

const PILA = [
  { t: 'Java 21 + Spring Boot 3.5', d: 'API REST por capas: controladores, servicios, dominio JPA (diagrama de clases D02) y seguridad.', i: Cpu, c: '#f97316' },
  { t: 'PostgreSQL 17', d: '21 tablas, 30 triggers y funciones PL/pgSQL que hacen cumplir las reglas RN01 a RN18 y la bitácora.', i: Database, c: '#22d3ee' },
  { t: 'Flyway', d: 'Migraciones versionadas V1 a V5: esquema, triggers, roles, datos base y demostración.', i: Layers, c: '#a855f7' },
  { t: 'Seguridad', d: 'JWT, claves Argon2id, doble factor TOTP, roles app_rw / app_ro y alcance por filial (RN14).', i: ShieldCheck, c: '#22c55e' },
  { t: 'Tiempo real', d: 'LISTEN/NOTIFY de PostgreSQL reenviado por Server-Sent Events a cada navegador.', i: Zap, c: '#eab308' },
  { t: 'React 19 + TypeScript', d: 'Aplicación web progresiva instalable en el teléfono, con tema claro y oscuro e idiomas ES/EN/PT.', i: Braces, c: '#38bdf8' },
  { t: 'Three.js', d: 'Globo 3D de las filiales con arcos animados desde la casa matriz.', i: Network, c: '#6366f1' },
  { t: 'OpenAPI', d: 'Documentación interactiva de la API en /api-docs/ui.html (Swagger UI).', i: BookOpen, c: '#ec4899' },
];

const TRIGGERS = [
  ['trg_riesgo_antes_insertar', 'riesgo', 'Código RSK-CAT-####, duplicados (CU01), responsable con acceso (RN14)'],
  ['trg_riesgo_transicion', 'riesgo', 'Máquina de estados D08 y condición de MITIGADO (RN11)'],
  ['trg_evaluacion_antes_insertar', 'evaluacion_riesgo', 'Vigencia 12 meses (RN03), conversión a USD (RN15), validación requerida (RN16)'],
  ['trg_plan_antes_actualizar', 'plan_mitigacion', 'RN03, RN04 (atribución), RN05, RN12, RN16 y separación de funciones'],
  ['trg_accion_antes', 'accion_mitigacion', 'Avance que no disminuye y evidencia obligatoria al cerrar (CU05)'],
  ['trg_medicion_antes / despues', 'medicion_kri', 'Semáforo (RN06) y alerta automática (CU07)'],
  ['trg_alerta_transicion', 'alerta', 'Máquina de estados D09, fechas de cada estado, acción correctiva (CU08)'],
  ['chk_independencia', 'validacion_financiera', 'El auditor no puede ser responsable ni evaluador del riesgo (RN17)'],
  ['trg_conciliacion_antes', 'conciliacion_kri', 'Diferencia y tolerancia de la conciliación con el ERP (RN18)'],
  ['trg_eval_cumplimiento_despues', 'evaluacion_cumplimiento', 'Si no cumple, crea el riesgo Legal y su alerta (RN10)'],
  ['trg_usuario_rol_sod', 'usuario_rol', 'Auditor Financiero y Gestor no se combinan (RN17)'],
  ['trg_auditar (fn_auditar)', '19 tablas', 'Bitácora con valores antes y después de cada cambio'],
  ['trg_bitacora', 'bitacora', 'Solo inserciones: ni UPDATE, ni DELETE, ni TRUNCATE (RN13)'],
];

const GRUPOS: Record<string, (n: number) => boolean> = {
  Todos: () => true,
  'Casos de uso y clases': (n) => n >= 5 && n <= 9,
  Comportamiento: (n) => n >= 10 && n <= 20,
  'Datos y procesos': (n) => (n >= 21 && n <= 24) || n <= 4 || n === 29,
  Arquitectura: (n) => n >= 25 && n <= 28,
  Prototipos: (n) => n >= 30,
};

export default function Arquitectura() {
  const { abrir } = useVentanas();
  const [figuras, setFiguras] = useState<Figura[]>([]);
  const [grupo, setGrupo] = useState('Todos');
  const [zoom, setZoom] = useState<Figura | null>(null);
  const { datos: info } = useDatos<any>('/api/sistema/info');
  useEffect(() => {
    fetch('/diagramas/indice.json').then((r) => r.json()).then(setFiguras).catch(() => setFiguras([]));
  }, []);
  const visibles = useMemo(() => figuras.filter((f) => GRUPOS[grupo](f.numero)), [figuras, grupo]);
  const ventana = (f: Figura) => abrir({ id: `fig-${f.numero}`, titulo: `Figura ${f.numero}. ${f.titulo}`, icono: <Workflow size={15} />, ancho: 820, alto: 600,
    contenido: <img src={f.archivo} alt={f.titulo} style={{ width: '100%', background: '#fff', borderRadius: 10 }} /> });

  return (
    <div className="columna" style={{ gap: 18 }}>
      <Encabezado icono={<Boxes size={22} />} titulo="Arquitectura y diseño" texto="Del informe al software: cada capa del diseño físico (sección 10) está implementada y cada diagrama tiene su correspondencia en el código.">
        <a className="boton" href="/api-docs/ui.html" target="_blank" rel="noreferrer"><ExternalLink size={16} /> API (Swagger)</a>
      </Encabezado>

      <Tarjeta brillante>
        <div className="tarjeta-cabecera"><h2><Layers size={18} /> Arquitectura en capas en ejecución</h2><p>Los puntos animados representan las peticiones y los eventos que circulan entre capas</p></div>
        <DiagramaVivo info={info} />
      </Tarjeta>

      <div className="rejilla r-3">
        <Tarjeta className="span-2">
          <h2 style={{ marginBottom: 6 }}><Cpu size={18} /> Pila tecnológica</h2>
          <Carrusel3D alto={330} ancho={300} elementos={PILA.map((p) => (
            <div className="tarjeta" style={{ height: '100%', background: `linear-gradient(160deg, ${p.c}33, var(--superficie-solida) 60%)`, borderColor: `${p.c}66`, display: 'flex', flexDirection: 'column', gap: 12, padding: 22 }}>
              <span className="icono-titulo" style={{ color: p.c, background: `${p.c}22`, width: 54, height: 54 }}><p.i size={26} /></span>
              <h2>{p.t}</h2>
              <p className="suave">{p.d}</p>
            </div>
          ))} />
        </Tarjeta>
        <Tarjeta>
          <h2 style={{ marginBottom: 10 }}><Database size={18} /> Base de datos</h2>
          <div className="columna pequeno" style={{ gap: 8 }}>
            {[['Motor', info?.base_de_datos ?? 'PostgreSQL'], ['Tablas del informe', '21 (+4 de soporte)'], ['Triggers y funciones', '30 triggers · 40 funciones'], ['Vistas', 'v_riesgo, v_alerta'], ['Roles', 'sigre_owner, app_rw, app_ro'], ['Migraciones', 'Flyway V1 a V5'], ['Registros de bitácora', info?.cifras?.registros_bitacora ?? '—']].map(([k, v]) => (
              <div key={k} className="fila entre"><span className="tenue">{k}</span><strong>{v}</strong></div>
            ))}
          </div>
        </Tarjeta>
      </div>

      <Tarjeta>
        <div className="tarjeta-cabecera"><h2><Zap size={18} /> Reglas de negocio garantizadas por triggers</h2><p>Aunque alguien ignore la interfaz y escriba directo en la base, la regla se cumple</p></div>
        <div className="tabla-contenedor">
          <table className="tabla">
            <thead><tr><th>Trigger</th><th>Tabla</th><th>Qué garantiza</th></tr></thead>
            <tbody>{TRIGGERS.map(([t, tb, d], k) => (
              <motion.tr key={t} initial={{ opacity: 0, x: -8 }} whileInView={{ opacity: 1, x: 0 }} viewport={{ once: true }} transition={{ delay: k * 0.03 }}>
                <td className="mono" style={{ color: 'var(--acento)' }}>{t}</td><td className="mono">{tb}</td><td>{d}</td>
              </motion.tr>
            ))}</tbody>
          </table>
        </div>
      </Tarjeta>

      <Tarjeta>
        <div className="tarjeta-cabecera">
          <h2><Workflow size={18} /> Diagramas del informe ({figuras.length})</h2>
          <div className="acciones"><Segmentado valor={grupo} cambiar={setGrupo} opciones={Object.keys(GRUPOS).map((g) => ({ valor: g, texto: g }))} /></div>
        </div>
        <div className="galeria">
          {visibles.map((f, k) => (
            <motion.figure key={f.numero} layout initial={{ opacity: 0, scale: 0.9 }} animate={{ opacity: 1, scale: 1 }} transition={{ delay: Math.min(k * 0.03, 0.4) }} onClick={() => setZoom(f)}>
              <img src={f.archivo} alt={f.titulo} loading="lazy" />
              <figcaption><strong>Figura {f.numero}.</strong> {f.titulo}</figcaption>
            </motion.figure>
          ))}
        </div>
      </Tarjeta>

      <Modal abierto={!!zoom} cerrar={() => setZoom(null)} ancho icono={<Maximize2 size={20} />} titulo={zoom ? `Figura ${zoom.numero}. ${zoom.titulo}` : ''} subtitulo={zoom ? `Informe, página ${zoom.pagina}` : ''}
        pie={zoom ? <><button className="boton" onClick={() => { ventana(zoom); setZoom(null); }}><AppWindow size={16} /> Abrir en ventana</button><a className="boton" href={zoom.archivo} target="_blank" rel="noreferrer"><ExternalLink size={16} /> Tamaño completo</a></> : undefined}>
        {zoom && <img src={zoom.archivo} alt={zoom.titulo} style={{ width: '100%', background: '#fff', borderRadius: 12 }} />}
      </Modal>
    </div>
  );
}

/** Diagrama de despliegue animado: navegador → API → base de datos → sistemas externos. */
function DiagramaVivo({ info }: { info: any }) {
  const cajas = [
    { x: 20, y: 70, w: 170, h: 120, t: 'Navegador / PWA', s: ['React 19 · TypeScript', 'Three.js · SSE', 'Instalable en el móvil'], c: '#38bdf8' },
    { x: 260, y: 30, w: 220, h: 200, t: 'API Spring Boot', s: ['Controladores REST (OpenAPI)', 'Servicios de dominio', 'Entidades JPA (D02)', 'JWT · Argon2id · TOTP', 'Tareas programadas (CU09)'], c: '#f97316' },
    { x: 550, y: 30, w: 210, h: 200, t: 'PostgreSQL', s: ['21 tablas + vistas', 'Triggers RN01–RN18', 'Bitácora inalterable', 'LISTEN / NOTIFY', 'Roles app_rw / app_ro'], c: '#22d3ee' },
    { x: 830, y: 10, w: 150, h: 52, t: 'ERP corporativo', s: [], c: '#a855f7' },
    { x: 830, y: 74, w: 150, h: 52, t: 'SIEM', s: [], c: '#a855f7' },
    { x: 830, y: 138, w: 150, h: 52, t: 'Servicio normativo', s: [], c: '#a855f7' },
    { x: 830, y: 202, w: 150, h: 52, t: 'Notificaciones', s: [], c: '#a855f7' },
  ];
  const rutas = [
    { d: 'M190,120 L260,120', c: '#38bdf8', dur: 1.6 },
    { d: 'M260,150 L190,150', c: '#22c55e', dur: 1.9 },
    { d: 'M480,110 L550,110', c: '#f97316', dur: 1.4 },
    { d: 'M550,160 L480,160', c: '#eab308', dur: 1.7 },
    { d: 'M830,36 C790,36 800,90 760,90', c: '#a855f7', dur: 2.2 },
    { d: 'M830,100 C800,100 790,120 760,120', c: '#a855f7', dur: 2.6 },
    { d: 'M480,200 C650,290 760,250 830,228', c: '#22c55e', dur: 3 },
    { d: 'M830,164 C700,180 600,250 480,215', c: '#eab308', dur: 3.4 },
  ];
  return (
    <div style={{ overflowX: 'auto' }}>
      <svg viewBox="0 0 1000 270" style={{ width: '100%', minWidth: 760 }}>
        <defs>
          <filter id="resplandor"><feGaussianBlur stdDeviation="3" result="b" /><feMerge><feMergeNode in="b" /><feMergeNode in="SourceGraphic" /></feMerge></filter>
        </defs>
        {rutas.map((r, k) => (
          <g key={k}>
            <path d={r.d} stroke={r.c} strokeOpacity="0.35" strokeWidth="2" fill="none" strokeDasharray="5 5" />
            {[0, 0.5].map((o) => (
              <circle key={o} r="4.5" fill={r.c} filter="url(#resplandor)">
                <animateMotion dur={`${r.dur}s`} begin={`${o * r.dur}s`} repeatCount="indefinite" path={r.d} />
              </circle>
            ))}
          </g>
        ))}
        {cajas.map((b, k) => (
          <motion.g key={b.t} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: k * 0.08 }}>
            <rect x={b.x} y={b.y} width={b.w} height={b.h} rx="14" fill="var(--superficie-solida)" stroke={b.c} strokeOpacity="0.7" strokeWidth="1.5" />
            <rect x={b.x} y={b.y} width={b.w} height="4" rx="2" fill={b.c} />
            <text x={b.x + 14} y={b.y + (b.s.length ? 30 : 31)} fill="var(--texto)" fontSize="14" fontWeight="700" fontFamily="var(--fuente-titulo)">{b.t}</text>
            {b.s.map((s, j) => <text key={s} x={b.x + 14} y={b.y + 56 + j * 26} fill="var(--texto-2)" fontSize="12">• {s}</text>)}
          </motion.g>
        ))}
        <text x="225" y="108" textAnchor="middle" fontSize="10" fill="var(--texto-3)">HTTPS · JSON</text>
        <text x="225" y="170" textAnchor="middle" fontSize="10" fill="var(--texto-3)">eventos SSE</text>
        <text x="515" y="100" textAnchor="middle" fontSize="10" fill="var(--texto-3)">JDBC</text>
        <text x="515" y="178" textAnchor="middle" fontSize="10" fill="var(--texto-3)">NOTIFY</text>
        <text x="20" y="250" fontSize="11" fill="var(--texto-3)">{info ? `Java ${info.java} · ${info.base_de_datos} · activo desde hace ${Math.round(info.activo_desde / 60)} min` : ''}</text>
      </svg>
    </div>
  );
}

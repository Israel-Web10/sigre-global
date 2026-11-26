import { useEffect, useId, useMemo, useRef, useState, type CSSProperties, type ReactNode } from 'react';
import { AnimatePresence, animate, motion, useInView } from 'framer-motion';
import { ArrowDown, ArrowUp, Clock, Inbox, Loader2, UploadCloud, X } from 'lucide-react';
import { banderaHtml, colorNivel, colorNivelSolido, colorSemaforo, colorSeveridad, etiqueta, iniciales, restante } from '../nucleo/formato';

// ---------------------------------------------------------------- Tarjeta con inclinación 3D

export function Tarjeta({
  children, className = '', inclinar = false, brillante = false, style, onClick, retraso = 0,
}: {
  children: ReactNode; className?: string; inclinar?: boolean; brillante?: boolean; style?: CSSProperties;
  onClick?: () => void; retraso?: number;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const mover = (e: React.MouseEvent) => {
    if (!inclinar || !ref.current) return;
    const r = ref.current.getBoundingClientRect();
    const x = (e.clientX - r.left) / r.width - 0.5;
    const y = (e.clientY - r.top) / r.height - 0.5;
    ref.current.style.transform = `perspective(900px) rotateX(${(-y * 7).toFixed(2)}deg) rotateY(${(x * 9).toFixed(2)}deg) translateZ(0)`;
  };
  const salir = () => {
    if (ref.current) ref.current.style.transform = '';
  };
  return (
    <motion.div
      ref={ref}
      initial={{ opacity: 0, y: 14 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.45, delay: retraso, ease: [0.2, 0.8, 0.2, 1] }}
      className={`tarjeta ${inclinar ? 'inclinable' : ''} ${brillante ? 'brillante' : ''} ${className}`}
      style={style}
      onMouseMove={mover}
      onMouseLeave={salir}
      onClick={onClick}
    >
      {children}
    </motion.div>
  );
}

// ---------------------------------------------------------------- Número animado

export function Contador({ valor, decimales = 0, sufijo = '' }: { valor: number; decimales?: number; sufijo?: string }) {
  const ref = useRef<HTMLSpanElement>(null);
  const visto = useInView(ref, { once: true });
  const previo = useRef(0);
  useEffect(() => {
    if (!visto || !ref.current) return;
    const control = animate(previo.current, valor, {
      duration: 1.2,
      ease: [0.2, 0.8, 0.2, 1],
      onUpdate: (v) => {
        if (ref.current) ref.current.textContent = v.toLocaleString('es-BO', { minimumFractionDigits: decimales, maximumFractionDigits: decimales }) + sufijo;
      },
    });
    previo.current = valor;
    return () => control.stop();
  }, [valor, visto, decimales, sufijo]);
  return <span ref={ref}>0{sufijo}</span>;
}

export function Kpi({
  titulo, valor, icono, color = 'var(--acento)', pie, decimales = 0, sufijo = '', retraso = 0, onClick, texto,
}: {
  titulo: string; valor?: number | null; icono: ReactNode; color?: string; pie?: ReactNode; decimales?: number; sufijo?: string;
  retraso?: number; onClick?: () => void; texto?: string;
}) {
  return (
    <Tarjeta inclinar className="kpi" retraso={retraso} onClick={onClick} style={onClick ? { cursor: 'pointer' } : undefined}>
      <div className="kpi-cab">
        <span className="kpi-icono" style={{ background: `color-mix(in srgb, ${color} 16%, transparent)`, color }}>{icono}</span>
        {titulo}
      </div>
      <div className="kpi-valor" style={{ color: 'var(--texto)', fontSize: texto ? 24 : undefined }}>
        {texto ?? <Contador valor={Number(valor ?? 0)} decimales={decimales} sufijo={sufijo} />}
      </div>
      {pie && <div className="kpi-pie">{pie}</div>}
      <div className="kpi-fondo" style={{ color }}>{icono && <span style={{ display: 'inline-block', transform: 'scale(4.2)', transformOrigin: 'bottom right' }}>{icono}</span>}</div>
    </Tarjeta>
  );
}

// ---------------------------------------------------------------- Distintivos

export function Chip({ color, children, solido = false, punto = true, title }: { color?: string; children: ReactNode; solido?: boolean; punto?: boolean; title?: string }) {
  const estilo: CSSProperties = solido
    ? { background: color }
    : { color, borderColor: color ? `color-mix(in srgb, ${color} 40%, transparent)` : undefined, background: color ? `color-mix(in srgb, ${color} 12%, transparent)` : undefined };
  return (
    <span className={`chip ${solido ? 'solido' : ''}`} style={estilo} title={title}>
      {punto && !solido && <i />}
      {children}
    </span>
  );
}

export const ChipNivel = ({ nivel, valor }: { nivel?: string | null; valor?: number | null }) =>
  nivel ? (
    <Chip color={colorNivelSolido(nivel)} solido>
      {etiqueta(nivel)}
      {valor != null && <span style={{ opacity: 0.85 }}>· {valor}</span>}
    </Chip>
  ) : (
    <Chip>Sin evaluar</Chip>
  );

const COLOR_ESTADO: Record<string, string> = {
  IDENTIFICADO: 'var(--texto-3)', EVALUADO: 'var(--info)', EN_TRATAMIENTO: 'var(--acento-2)', MITIGADO: 'var(--bajo)',
  ACEPTADO: 'var(--medio)', CERRADO: 'var(--texto-3)', BORRADOR: 'var(--texto-3)', PROPUESTO: 'var(--medio)', APROBADO: 'var(--bajo)',
  GENERADA: 'var(--texto-3)', NOTIFICADA: 'var(--info)', EN_ATENCION: 'var(--acento-2)', ESCALADA: 'var(--critico)', RESUELTA: 'var(--bajo)',
  CERRADA: 'var(--texto-3)', PENDIENTE: 'var(--medio)', VALIDADA: 'var(--bajo)', OBSERVADA: 'var(--critico)', NO_REQUERIDA: 'var(--texto-3)',
  CUMPLE: 'var(--bajo)', PARCIAL: 'var(--medio)', NO_CUMPLE: 'var(--critico)', EN_CURSO: 'var(--acento-2)',
  PLANIFICADA: 'var(--info)', EN_EJECUCION: 'var(--acento-2)', ABIERTO: 'var(--alto)', EN_PROCESO: 'var(--acento-2)', CERRADO_H: 'var(--bajo)',
  CONCILIADA: 'var(--bajo)', CON_DIFERENCIA: 'var(--critico)', OK: 'var(--bajo)', RECHAZADO: 'var(--critico)',
};

export const ChipEstado = ({ estado }: { estado?: string | null }) => <Chip color={COLOR_ESTADO[estado ?? ''] ?? 'var(--texto-3)'}>{etiqueta(estado)}</Chip>;

export const ChipSeveridad = ({ s }: { s?: string | null }) => <Chip color={colorSeveridad(s)}>{etiqueta(s)}</Chip>;

export function Semaforo({ valor }: { valor?: string | null }) {
  return (
    <span className="semaforo" title={etiqueta(valor)}>
      {['VERDE', 'AMARILLO', 'ROJO'].map((c) => (
        <i key={c} className={valor === c ? 'on' : ''} style={{ background: colorSemaforo(c), color: colorSemaforo(c) }} />
      ))}
    </span>
  );
}

export function BarraAvance({ valor, color, rayada = false }: { valor: number; color?: string; rayada?: boolean }) {
  return (
    <div className={`barra-avance ${rayada ? 'rayada' : ''}`}>
      <span style={{ width: `${Math.max(0, Math.min(100, valor))}%`, background: color }} />
    </div>
  );
}

const TONOS = ['#22d3ee,#6366f1', '#f97316,#ef4444', '#22c55e,#0ea5e9', '#a855f7,#ec4899', '#eab308,#f97316', '#6366f1,#a855f7'];

export function Avatar({ nombre, chico = false }: { nombre?: string | null; chico?: boolean }) {
  const tono = TONOS[(nombre ?? '').split('').reduce((a, c) => a + c.charCodeAt(0), 0) % TONOS.length];
  return (
    <span className={`avatar ${chico ? 'chico' : ''}`} style={{ background: `linear-gradient(135deg, ${tono})` }}>
      {iniciales(nombre)}
    </span>
  );
}

// ---------------------------------------------------------------- Reloj del SLA (RN07)

export function RelojSla({ segundos, abierta = true }: { segundos?: number | null; abierta?: boolean }) {
  const [base] = useState(() => Date.now());
  const [, setTic] = useState(0);
  useEffect(() => {
    if (!abierta) return;
    const t = setInterval(() => setTic((x) => x + 1), 1000);
    return () => clearInterval(t);
  }, [abierta]);
  if (segundos == null || !abierta) return null;
  const s = segundos - (Date.now() - base) / 1000;
  const clase = s < 0 ? 'vencido' : s < 3600 * 2 ? 'cerca' : '';
  return (
    <span className={`reloj-sla ${clase}`}>
      <Clock size={12} />
      {restante(s)}
    </span>
  );
}

// ---------------------------------------------------------------- Modal

export function Modal({
  abierto, cerrar, titulo, subtitulo, icono, children, pie, ancho = false,
}: {
  abierto: boolean; cerrar: () => void; titulo: ReactNode; subtitulo?: ReactNode; icono?: ReactNode; children: ReactNode;
  pie?: ReactNode; ancho?: boolean;
}) {
  const caja = useRef<HTMLDivElement>(null);
  const idTitulo = useId();
  const alCerrar = useRef(cerrar);
  alCerrar.current = cerrar;
  useEffect(() => {
    if (!abierto) return;
    // Accesibilidad: foco inicial dentro del diálogo, Tab que no sale de él y foco devuelto al cerrar
    const anterior = document.activeElement as HTMLElement | null;
    const enfocables = () =>
      Array.from(caja.current?.querySelectorAll<HTMLElement>('input:not([disabled]),select:not([disabled]),textarea:not([disabled]),button:not([disabled]),a[href],[tabindex]:not([tabindex="-1"])') ?? []);
    const t = setTimeout(() => {
      const cuerpo = enfocables().find((e) => !e.closest('.modal-cabecera'));
      (cuerpo ?? enfocables()[0] ?? caja.current)?.focus();
    }, 60);
    const k = (e: KeyboardEvent) => {
      if (e.key === 'Escape') alCerrar.current();
      if (e.key !== 'Tab') return;
      const lista = enfocables();
      if (!lista.length) return;
      const primero = lista[0];
      const ultimo = lista[lista.length - 1];
      if (e.shiftKey && (document.activeElement === primero || !caja.current?.contains(document.activeElement))) {
        e.preventDefault();
        ultimo.focus();
      } else if (!e.shiftKey && (document.activeElement === ultimo || !caja.current?.contains(document.activeElement))) {
        e.preventDefault();
        primero.focus();
      }
    };
    window.addEventListener('keydown', k);
    return () => {
      clearTimeout(t);
      window.removeEventListener('keydown', k);
      anterior?.focus?.();
    };
  }, [abierto]);
  return (
    <AnimatePresence>
      {abierto && (
        <motion.div className="velo" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onMouseDown={(e) => e.target === e.currentTarget && cerrar()}>
          <motion.div
            ref={caja}
            tabIndex={-1}
            className={`modal ${ancho ? 'ancho' : ''}`}
            initial={{ opacity: 0, y: 30, scale: 0.96, rotateX: 8 }}
            animate={{ opacity: 1, y: 0, scale: 1, rotateX: 0 }}
            exit={{ opacity: 0, y: 20, scale: 0.97 }}
            transition={{ type: 'spring', damping: 26, stiffness: 300 }}
            role="dialog"
            aria-modal="true"
            aria-labelledby={idTitulo}
          >
            <div className="modal-cabecera">
              {icono && <span className="icono-titulo" aria-hidden="true">{icono}</span>}
              <div className="crece">
                <h2 id={idTitulo}>{titulo}</h2>
                {subtitulo && <p>{subtitulo}</p>}
              </div>
              <button className="icono-btn" onClick={cerrar} aria-label="Cerrar">
                <X size={18} />
              </button>
            </div>
            <div className="modal-cuerpo">{children}</div>
            {pie && <div className="modal-pie">{pie}</div>}
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}

// ---------------------------------------------------------------- Pestañas animadas

export function Pestanas<T extends string>({
  valor, cambiar, opciones, id,
}: { valor: T; cambiar: (v: T) => void; opciones: { valor: T; texto: string; icono?: ReactNode; contador?: number }[]; id: string }) {
  return (
    <div className="pestanas" role="tablist">
      {opciones.map((o) => (
        <button key={o.valor} className={valor === o.valor ? 'activo' : ''} onClick={() => cambiar(o.valor)} role="tab" aria-selected={valor === o.valor}>
          {o.icono}
          {o.texto}
          {o.contador != null && o.contador > 0 && <span className="contador">{o.contador}</span>}
          {valor === o.valor && <motion.span layoutId={`subrayado-${id}`} className="subrayado" />}
        </button>
      ))}
    </div>
  );
}

export function Segmentado<T extends string>({ valor, cambiar, opciones }: { valor: T; cambiar: (v: T) => void; opciones: { valor: T; texto: ReactNode }[] }) {
  return (
    <div className="segmentado">
      {opciones.map((o) => (
        <button key={o.valor} className={valor === o.valor ? 'activo' : ''} onClick={() => cambiar(o.valor)} type="button">
          {o.texto}
        </button>
      ))}
    </div>
  );
}

// ---------------------------------------------------------------- Estados vacíos y de carga

export const Vacio = ({ texto, icono, children }: { texto: string; icono?: ReactNode; children?: ReactNode }) => (
  <div className="vacio">
    {icono ?? <Inbox size={36} />}
    <span>{texto}</span>
    {children}
  </div>
);

export const Cargando = ({ filas = 4, alto = 46 }: { filas?: number; alto?: number }) => (
  <div className="columna">
    {Array.from({ length: filas }).map((_, i) => (
      <div key={i} className="esqueleto" style={{ height: alto, opacity: 1 - i * 0.15 }} />
    ))}
  </div>
);

export const Girando = ({ size = 16 }: { size?: number }) => <Loader2 size={size} className="girando" />;

// ---------------------------------------------------------------- Formularios

export function Campo({ etiqueta: e, ayuda, children, className = '' }: { etiqueta: ReactNode; ayuda?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <div className={`campo ${className}`}>
      <label>{e}</label>
      {children}
      {ayuda && <span className="ayuda">{ayuda}</span>}
    </div>
  );
}

export function Interruptor({ valor, cambiar, titulo }: { valor: boolean; cambiar: (v: boolean) => void; titulo?: string }) {
  return (
    <label className="interruptor" title={titulo}>
      <input type="checkbox" checked={valor} onChange={(e) => cambiar(e.target.checked)} />
      <span />
    </label>
  );
}

export function ZonaArrastre({ alElegir, texto = 'Arrastre el archivo aquí o haga clic para elegirlo', acepta }: { alElegir: (f: File) => void; texto?: string; acepta?: string }) {
  const [activa, setActiva] = useState(false);
  const entrada = useRef<HTMLInputElement>(null);
  return (
    <div
      className={`zona-arrastre ${activa ? 'activa' : ''}`}
      onClick={() => entrada.current?.click()}
      onDragOver={(e) => {
        e.preventDefault();
        setActiva(true);
      }}
      onDragLeave={() => setActiva(false)}
      onDrop={(e) => {
        e.preventDefault();
        setActiva(false);
        const f = e.dataTransfer.files?.[0];
        if (f) alElegir(f);
      }}
    >
      <UploadCloud size={28} style={{ marginBottom: 6 }} />
      <div>{texto}</div>
      <div className="pequeno tenue">PDF, imágenes u Office · máximo 15 MB</div>
      <input ref={entrada} type="file" hidden accept={acepta} onChange={(e) => e.target.files?.[0] && alElegir(e.target.files[0])} />
    </div>
  );
}

// ---------------------------------------------------------------- Encabezado de página

export function Encabezado({ icono, titulo, texto, children }: { icono: ReactNode; titulo: ReactNode; texto?: ReactNode; children?: ReactNode }) {
  return (
    <motion.div className="encabezado-pagina" initial={{ opacity: 0, y: -8 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.4 }}>
      <div className="textos">
        <h1>
          <span className="icono-titulo">{icono}</span>
          {titulo}
        </h1>
        {texto && <p>{texto}</p>}
      </div>
      {children && <div className="acciones">{children}</div>}
    </motion.div>
  );
}

// ---------------------------------------------------------------- Tabla con orden por columna

export interface ColumnaTabla<T> {
  clave: string;
  titulo: ReactNode;
  render?: (f: T) => ReactNode;
  orden?: (f: T) => number | string;
  clase?: string;
  ancho?: number | string;
}

export function Tabla<T extends Record<string, any>>({
  filas, columnas, alClic, clave, vacio = 'Sin registros', maxAlto, etiqueta = 'Tabla de datos',
}: {
  filas: T[]; columnas: ColumnaTabla<T>[]; alClic?: (f: T) => void; clave: (f: T) => string | number; vacio?: string; maxAlto?: number; etiqueta?: string;
}) {
  const [orden, setOrden] = useState<{ c: string; asc: boolean } | null>(null);
  const ordenadas = useMemo(() => {
    if (!orden) return filas;
    const col = columnas.find((c) => c.clave === orden.c);
    const f = col?.orden ?? ((x: T) => x[orden.c]);
    return [...filas].sort((a, b) => {
      const va = f(a) ?? '';
      const vb = f(b) ?? '';
      const r = typeof va === 'number' && typeof vb === 'number' ? va - vb : String(va).localeCompare(String(vb), 'es');
      return orden.asc ? r : -r;
    });
  }, [filas, orden, columnas]);
  if (!filas.length) return <Vacio texto={vacio} />;
  return (
    <div className="tabla-contenedor" style={maxAlto ? { maxHeight: maxAlto } : undefined} tabIndex={0} role="region" aria-label={etiqueta}>
      <table className="tabla">
        <thead>
          <tr>
            {columnas.map((c) => (
              <th
                key={c.clave}
                className={`ordenable ${c.clase ?? ''}`}
                style={{ width: c.ancho }}
                onClick={() => setOrden((o) => (o?.c === c.clave ? { c: c.clave, asc: !o.asc } : { c: c.clave, asc: true }))}
              >
                {c.titulo}
                {orden?.c === c.clave && (orden.asc ? <ArrowUp size={11} style={{ marginLeft: 4 }} /> : <ArrowDown size={11} style={{ marginLeft: 4 }} />)}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {ordenadas.map((f, i) => (
            <motion.tr
              key={clave(f)}
              className={alClic ? 'clic' : ''}
              onClick={() => alClic?.(f)}
              tabIndex={alClic ? 0 : undefined}
              onKeyDown={alClic ? (e) => e.key === 'Enter' && alClic(f) : undefined}
              initial={{ opacity: 0, x: -6 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: Math.min(i * 0.015, 0.35) }}
            >
              {columnas.map((c) => (
                <td key={c.clave} className={c.clase}>
                  {c.render ? c.render(f) : (f[c.clave] ?? '—')}
                </td>
              ))}
            </motion.tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

// ---------------------------------------------------------------- Bandera del país

export const Bandera = ({ codigo }: { codigo?: string | null }) =>
  codigo ? <span style={{ display: 'inline-flex' }} title={codigo} dangerouslySetInnerHTML={{ __html: banderaHtml(codigo) }} /> : null;

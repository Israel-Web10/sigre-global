import { useEffect, useId, useMemo, useState, type ReactNode } from 'react';
import { motion } from 'framer-motion';
import { colorValor, etiqueta, numero } from '../nucleo/formato';

// ---------------------------------------------------------------- Línea mínima

export function Sparkline({ datos, color = 'var(--acento)', alto = 40, ancho = 140, umbral }: { datos: number[]; color?: string; alto?: number; ancho?: number; umbral?: number[] }) {
  const id = useId().replace(/:/g, '');
  if (!datos || datos.length < 2) return <svg width={ancho} height={alto} />;
  const todos = [...datos, ...(umbral ?? [])];
  const min = Math.min(...todos);
  const max = Math.max(...todos);
  const r = max - min || 1;
  const x = (i: number) => (i / (datos.length - 1)) * (ancho - 4) + 2;
  const y = (v: number) => alto - 4 - ((v - min) / r) * (alto - 8);
  const d = datos.map((v, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)},${y(v).toFixed(1)}`).join(' ');
  return (
    <svg width={ancho} height={alto} viewBox={`0 0 ${ancho} ${alto}`} style={{ overflow: 'visible' }}>
      <defs>
        <linearGradient id={`sp${id}`} x1="0" x2="0" y1="0" y2="1">
          <stop offset="0" stopColor={color} stopOpacity="0.35" />
          <stop offset="1" stopColor={color} stopOpacity="0" />
        </linearGradient>
      </defs>
      {umbral?.map((u, i) => (
        <line key={i} x1="0" x2={ancho} y1={y(u)} y2={y(u)} stroke={i ? 'var(--critico)' : 'var(--medio)'} strokeDasharray="3 3" strokeWidth="1" opacity="0.6" />
      ))}
      <motion.path d={`${d} L${x(datos.length - 1)},${alto} L${x(0)},${alto} Z`} fill={`url(#sp${id})`} initial={{ opacity: 0 }} animate={{ opacity: 1 }} />
      <motion.path d={d} fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" initial={{ pathLength: 0 }} animate={{ pathLength: 1 }} transition={{ duration: 1.1 }} />
      <circle cx={x(datos.length - 1)} cy={y(datos[datos.length - 1])} r="3.5" fill={color} stroke="var(--superficie-solida)" strokeWidth="2" />
    </svg>
  );
}

// ---------------------------------------------------------------- Áreas apiladas en el tiempo

export function GraficoArea({
  etiquetas, series, alto = 220, formato = (v: number) => numero(v, 0),
}: { etiquetas: string[]; series: { nombre: string; color: string; datos: number[] }[]; alto?: number; formato?: (v: number) => string }) {
  const id = useId().replace(/:/g, '');
  const [foco, setFoco] = useState<number | null>(null);
  const ancho = 640;
  const m = { i: 36, d: 10, s: 12, b: 26 };
  const max = Math.max(1, ...series.flatMap((s) => s.datos.map((v) => v ?? 0)));
  const n = etiquetas.length;
  const x = (i: number) => m.i + (i / Math.max(1, n - 1)) * (ancho - m.i - m.d);
  const y = (v: number) => alto - m.b - (v / max) * (alto - m.s - m.b);
  const curva = (d: number[]) => d.map((v, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)},${y(v ?? 0).toFixed(1)}`).join(' ');
  return (
    <div style={{ position: 'relative' }}>
      <svg viewBox={`0 0 ${ancho} ${alto}`} width="100%" height={alto} onMouseLeave={() => setFoco(null)}>
        <defs>
          {series.map((s, k) => (
            <linearGradient key={k} id={`ga${id}${k}`} x1="0" x2="0" y1="0" y2="1">
              <stop offset="0" stopColor={s.color} stopOpacity="0.32" />
              <stop offset="1" stopColor={s.color} stopOpacity="0" />
            </linearGradient>
          ))}
        </defs>
        {[0, 0.25, 0.5, 0.75, 1].map((p) => (
          <g key={p}>
            <line x1={m.i} x2={ancho - m.d} y1={y(max * p)} y2={y(max * p)} stroke="var(--borde)" />
            <text x={m.i - 6} y={y(max * p) + 4} textAnchor="end" fontSize="10" fill="var(--texto-3)">{formato(max * p)}</text>
          </g>
        ))}
        {etiquetas.map((e, i) =>
          i % Math.ceil(n / 8) === 0 || i === n - 1 ? (
            <text key={i} x={x(i)} y={alto - 8} textAnchor="middle" fontSize="10" fill="var(--texto-3)">{e}</text>
          ) : null
        )}
        {series.map((s, k) => (
          <g key={k}>
            <motion.path d={`${curva(s.datos)} L${x(n - 1)},${y(0)} L${x(0)},${y(0)} Z`} fill={`url(#ga${id}${k})`} initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.3 + k * 0.1 }} />
            <motion.path d={curva(s.datos)} fill="none" stroke={s.color} strokeWidth="2.2" strokeLinejoin="round" initial={{ pathLength: 0 }} animate={{ pathLength: 1 }} transition={{ duration: 1.3, delay: k * 0.15 }} />
          </g>
        ))}
        {etiquetas.map((_, i) => (
          <rect key={i} x={x(i) - (ancho / n) / 2} y={0} width={ancho / n} height={alto} fill="transparent" onMouseEnter={() => setFoco(i)} />
        ))}
        {foco != null && (
          <g pointerEvents="none">
            <line x1={x(foco)} x2={x(foco)} y1={m.s} y2={alto - m.b} stroke="var(--borde-fuerte)" strokeDasharray="3 3" />
            {series.map((s, k) => <circle key={k} cx={x(foco)} cy={y(s.datos[foco] ?? 0)} r="4" fill={s.color} stroke="var(--superficie-solida)" strokeWidth="2" />)}
          </g>
        )}
      </svg>
      {foco != null && (
        <div className="tarjeta" style={{ position: 'absolute', top: 6, left: `${Math.min(70, (x(foco) / ancho) * 100)}%`, padding: '8px 10px', pointerEvents: 'none', fontSize: 12, zIndex: 3 }}>
          <strong>{etiquetas[foco]}</strong>
          {series.map((s, k) => (
            <div key={k} className="fila" style={{ gap: 6 }}>
              <i style={{ width: 8, height: 8, borderRadius: 4, background: s.color, display: 'inline-block' }} />
              {s.nombre}: <strong>{formato(s.datos[foco] ?? 0)}</strong>
            </div>
          ))}
        </div>
      )}
      <div className="fila" style={{ justifyContent: 'center', gap: 16, marginTop: 4 }}>
        {series.map((s, k) => (
          <span key={k} className="fila pequeno suave" style={{ gap: 6 }}>
            <i style={{ width: 10, height: 10, borderRadius: 3, background: s.color, display: 'inline-block' }} />
            {s.nombre}
          </span>
        ))}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------- Dona

export function Dona({ datos, tamano = 170, centro, grosor = 22 }: { datos: { nombre: string; valor: number; color: string }[]; tamano?: number; centro?: ReactNode; grosor?: number }) {
  const total = datos.reduce((a, d) => a + d.valor, 0) || 1;
  const r = (tamano - grosor) / 2;
  const c = 2 * Math.PI * r;
  let acumulado = 0;
  return (
    <div style={{ position: 'relative', width: tamano, height: tamano, flexShrink: 0 }}>
      <svg width={tamano} height={tamano} style={{ transform: 'rotate(-90deg)' }}>
        <circle cx={tamano / 2} cy={tamano / 2} r={r} fill="none" stroke="var(--superficie-3)" strokeWidth={grosor} />
        {datos.map((d, i) => {
          const largo = (d.valor / total) * c;
          const desfase = acumulado;
          acumulado += largo;
          return (
            <motion.circle
              key={d.nombre}
              cx={tamano / 2}
              cy={tamano / 2}
              r={r}
              fill="none"
              stroke={d.color}
              strokeWidth={grosor}
              strokeDasharray={`${Math.max(0, largo - 2)} ${c}`}
              initial={{ strokeDashoffset: c }}
              animate={{ strokeDashoffset: -desfase }}
              transition={{ duration: 1.1, delay: i * 0.08, ease: [0.2, 0.8, 0.2, 1] }}
              strokeLinecap="butt"
            >
              <title>{`${d.nombre}: ${d.valor}`}</title>
            </motion.circle>
          );
        })}
      </svg>
      <div style={{ position: 'absolute', inset: 0, display: 'grid', placeItems: 'center', textAlign: 'center' }}>{centro}</div>
    </div>
  );
}

// ---------------------------------------------------------------- Barras horizontales

export function Barras({ datos, formato = (v: number) => numero(v, 0) }: { datos: { nombre: ReactNode; valor: number; color?: string; extra?: ReactNode }[]; formato?: (v: number) => string }) {
  const max = Math.max(1, ...datos.map((d) => d.valor));
  return (
    <div className="columna" style={{ gap: 12 }}>
      {datos.map((d, i) => (
        <div key={i}>
          <div className="fila entre pequeno" style={{ marginBottom: 5 }}>
            <span className="suave">{d.nombre}</span>
            <span className="fila" style={{ gap: 8 }}>
              {d.extra}
              <strong>{formato(d.valor)}</strong>
            </span>
          </div>
          <div className="barra-avance" style={{ height: 9 }}>
            <motion.span initial={{ width: 0 }} animate={{ width: `${(d.valor / max) * 100}%` }} transition={{ duration: 0.9, delay: i * 0.06 }} style={{ background: d.color ?? 'var(--degradado)' }} />
          </div>
        </div>
      ))}
    </div>
  );
}

// ---------------------------------------------------------------- Medidor de un KRI (RN06)

export function Medidor({ valor, amarillo, rojo, direccion, unidad, tamano = 190 }: { valor: number | null; amarillo: number; rojo: number; direccion: string; unidad: string; tamano?: number }) {
  const mayor = direccion === 'MAYOR';
  const span = Math.abs(rojo - amarillo) || Math.abs(amarillo) || 1;
  const min = mayor ? Math.min(0, amarillo - span * 2) : Math.min(rojo - span * 1.2, 0);
  const max = mayor ? rojo + span * 1.2 : amarillo + span * 2;
  const v = valor ?? min;
  const p = (x: number) => Math.max(0, Math.min(1, (x - min) / (max - min || 1)));
  const ang = (x: number) => Math.PI * (1 - p(x));
  const r = tamano / 2 - 14;
  const cx = tamano / 2;
  const cy = tamano / 2 + 6;
  const punto = (a: number, rr = r) => [cx + rr * Math.cos(a), cy - rr * Math.sin(a)];
  const arco = (a1: number, a2: number) => {
    const [x1, y1] = punto(a1);
    const [x2, y2] = punto(a2);
    return `M${x1},${y1} A${r},${r} 0 0 1 ${x2},${y2}`;
  };
  const tramos = mayor
    ? [[min, amarillo, 'var(--bajo)'], [amarillo, rojo, 'var(--medio)'], [rojo, max, 'var(--critico)']]
    : [[min, rojo, 'var(--critico)'], [rojo, amarillo, 'var(--medio)'], [amarillo, max, 'var(--bajo)']];
  const color = mayor ? (v > rojo ? 'var(--critico)' : v > amarillo ? 'var(--medio)' : 'var(--bajo)') : v < rojo ? 'var(--critico)' : v < amarillo ? 'var(--medio)' : 'var(--bajo)';
  const [montado, setMontado] = useState(false);
  useEffect(() => {
    const t = requestAnimationFrame(() => setMontado(true));
    return () => cancelAnimationFrame(t);
  }, []);
  const grados = montado ? (-ang(v) * 180) / Math.PI : -180;
  return (
    <svg width={tamano} height={tamano / 2 + 40} viewBox={`0 0 ${tamano} ${tamano / 2 + 40}`}>
      {tramos.map(([a, b, c], i) => (
        <path key={i} d={arco(ang(a as number), ang(b as number))} stroke={c as string} strokeWidth="12" fill="none" opacity="0.85" />
      ))}
      <g style={{ transform: `rotate(${grados}deg)`, transformOrigin: `${cx}px ${cy}px`, transition: 'transform 1.4s cubic-bezier(0.34, 1.4, 0.5, 1)' }}>
        <line x1={cx} y1={cy} x2={cx + r - 18} y2={cy} stroke="var(--texto)" strokeWidth="3" strokeLinecap="round" />
      </g>
      <circle cx={cx} cy={cy} r="7" fill="var(--texto)" />
      <circle cx={cx} cy={cy} r="3" fill={color} />
      <text x={cx} y={cy + 30} textAnchor="middle" fontSize="20" fontWeight="700" fill={color} fontFamily="var(--fuente-titulo)">
        {valor == null ? '—' : numero(valor)} <tspan fontSize="12" fill="var(--texto-3)">{unidad}</tspan>
      </text>
    </svg>
  );
}

// ---------------------------------------------------------------- Mapa de calor probabilidad × impacto

export function MapaCalor({
  celdas, marcar, alElegir, compacto = false,
}: {
  celdas: { probabilidad: number; impacto: number; total: number; codigos?: string[] }[];
  marcar?: { p: number; i: number } | null;
  alElegir?: (p: number, i: number) => void;
  compacto?: boolean;
}) {
  const mapa = useMemo(() => {
    const m = new Map<string, (typeof celdas)[number]>();
    celdas.forEach((c) => m.set(`${c.probabilidad}-${c.impacto}`, c));
    return m;
  }, [celdas]);
  return (
    <div>
      <div className="mapa-calor" style={compacto ? { aspectRatio: '1.3' } : undefined}>
        {[5, 4, 3, 2, 1].map((p) => (
          <FilaMapa key={p} p={p} mapa={mapa} marcar={marcar} alElegir={alElegir} />
        ))}
        <div />
        {[1, 2, 3, 4, 5].map((i) => (
          <div key={i} className="eje">{i}</div>
        ))}
      </div>
      <div className="fila entre pequeno tenue" style={{ marginTop: 6, paddingLeft: 30 }}>
        <span>↑ Probabilidad</span>
        <span>Impacto →</span>
      </div>
    </div>
  );
}

function FilaMapa({ p, mapa, marcar, alElegir }: { p: number; mapa: Map<string, any>; marcar?: { p: number; i: number } | null; alElegir?: (p: number, i: number) => void }) {
  return (
    <>
      <div className="eje">{p}</div>
      {[1, 2, 3, 4, 5].map((i) => {
        const c = mapa.get(`${p}-${i}`);
        const total = c?.total ?? 0;
        const valor = p * i;
        const marcada = marcar?.p === p && marcar?.i === i;
        return (
          <motion.div
            key={i}
            className={`celda ${total || marcada ? '' : 'vacia'} ${marcada ? 'marcada' : ''}`}
            style={{ background: `color-mix(in srgb, ${colorValor(valor)} ${total ? 78 + Math.min(total, 5) * 4 : 60}%, #0b1220)` }}
            title={`Probabilidad ${p} × Impacto ${i} = ${valor} (${etiqueta(valor <= 4 ? 'BAJO' : valor <= 9 ? 'MEDIO' : valor <= 14 ? 'ALTO' : 'CRITICO')})${c?.codigos ? '\n' + c.codigos.join(', ') : ''}`}
            onClick={() => alElegir?.(p, i)}
            initial={{ opacity: 0, scale: 0.6 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ delay: (5 - p + i) * 0.035, type: 'spring', damping: 14 }}
          >
            {marcada && !total ? valor : total || valor}
          </motion.div>
        );
      })}
    </>
  );
}

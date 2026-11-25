import { useEffect, useState, type ReactNode } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { ChevronLeft, ChevronRight, Pause, Play } from 'lucide-react';

/** Carrusel en perspectiva 3D (coverflow) con avance automático. */
export function Carrusel3D({ elementos, intervalo = 4200, alto = 360, ancho = 340 }: { elementos: ReactNode[]; intervalo?: number; alto?: number; ancho?: number }) {
  const [activo, setActivo] = useState(0);
  const [pausa, setPausa] = useState(false);
  const n = elementos.length;
  useEffect(() => {
    if (pausa || n < 2) return;
    const t = setInterval(() => setActivo((a) => (a + 1) % n), intervalo);
    return () => clearInterval(t);
  }, [pausa, n, intervalo]);
  const ir = (d: number) => setActivo((a) => (a + d + n) % n);
  return (
    <div className="carrusel" style={{ height: alto }} onMouseEnter={() => setPausa(true)} onMouseLeave={() => setPausa(false)}>
      <div className="carrusel-pista" style={{ width: ancho, height: alto - 60 }}>
        {elementos.map((e, i) => {
          let d = i - activo;
          if (d > n / 2) d -= n;
          if (d < -n / 2) d += n;
          const ad = Math.abs(d);
          return (
            <div
              key={i}
              className="carrusel-item"
              onClick={() => setActivo(i)}
              style={{
                transform: `translateX(${d * (ancho * 0.62)}px) translateZ(${-ad * 190}px) rotateY(${-d * 38}deg)`,
                opacity: ad > 2 ? 0 : 1 - ad * 0.28,
                filter: ad ? `blur(${ad * 0.6}px) brightness(${1 - ad * 0.18})` : 'none',
                zIndex: 10 - ad,
                pointerEvents: ad > 2 ? 'none' : 'auto',
              }}
            >
              {e}
            </div>
          );
        })}
      </div>
      <div className="carrusel-controles">
        <button className="icono-btn" onClick={() => ir(-1)} aria-label="Anterior"><ChevronLeft size={18} /></button>
        {elementos.map((_, i) => (
          <button key={i} className={`carrusel-punto ${i === activo ? 'activo' : ''}`} onClick={() => setActivo(i)} aria-label={`Elemento ${i + 1}`} />
        ))}
        <button className="icono-btn" onClick={() => ir(1)} aria-label="Siguiente"><ChevronRight size={18} /></button>
      </div>
    </div>
  );
}

/** Carrusel de diapositivas a pantalla completa con transición deslizante y barra de progreso. */
export function Diapositivas({ elementos, intervalo = 6000, alto = 340 }: { elementos: ReactNode[]; intervalo?: number; alto?: number }) {
  const [[activo, dir], setEstado] = useState<[number, number]>([0, 1]);
  const [pausa, setPausa] = useState(false);
  const n = elementos.length;
  const ir = (d: number) => setEstado(([a]) => [(a + d + n) % n, d]);
  useEffect(() => {
    if (pausa || n < 2) return;
    const t = setTimeout(() => ir(1), intervalo);
    return () => clearTimeout(t);
  }, [activo, pausa, n, intervalo]);
  return (
    <div className="diapositiva" style={{ height: alto }}>
      <AnimatePresence initial={false} custom={dir} mode="popLayout">
        <motion.div
          key={activo}
          custom={dir}
          initial={{ opacity: 0, x: dir * 120, rotateY: dir * 12 }}
          animate={{ opacity: 1, x: 0, rotateY: 0 }}
          exit={{ opacity: 0, x: -dir * 120, rotateY: -dir * 12 }}
          transition={{ duration: 0.7, ease: [0.2, 0.8, 0.2, 1] }}
          style={{ position: 'absolute', inset: 0 }}
        >
          {elementos[activo]}
        </motion.div>
      </AnimatePresence>
      <div className="fila" style={{ position: 'absolute', bottom: 14, right: 16, zIndex: 3, gap: 6 }}>
        <button className="icono-btn" onClick={() => ir(-1)}><ChevronLeft size={18} /></button>
        <button className="icono-btn" onClick={() => setPausa((p) => !p)}>{pausa ? <Play size={16} /> : <Pause size={16} />}</button>
        <button className="icono-btn" onClick={() => ir(1)}><ChevronRight size={18} /></button>
      </div>
      <div style={{ position: 'absolute', left: 0, right: 0, bottom: 0, height: 3, background: 'var(--superficie-3)', zIndex: 3 }}>
        <motion.div key={`${activo}-${pausa}`} initial={{ width: '0%' }} animate={{ width: pausa ? '0%' : '100%' }} transition={{ duration: intervalo / 1000, ease: 'linear' }} style={{ height: '100%', background: 'var(--degradado)' }} />
      </div>
    </div>
  );
}

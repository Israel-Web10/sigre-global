import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { AnimatePresence, motion } from 'framer-motion';
import { AppWindow } from 'lucide-react';

/** Ventana flotante del escritorio de trabajo (multiventana). */
interface Ventana {
  id: string;
  titulo: string;
  icono?: ReactNode;
  contenido: ReactNode;
  x: number;
  y: number;
  w: number;
  h: number;
  z: number;
  minimizada: boolean;
  maximizada: boolean;
}

interface Ventanas {
  abrir: (v: { id?: string; titulo: string; icono?: ReactNode; contenido: ReactNode; ancho?: number; alto?: number }) => void;
  cerrar: (id: string) => void;
  hay: boolean;
}

const Ctx = createContext<Ventanas>({ abrir: () => {}, cerrar: () => {}, hay: false });
export const useVentanas = () => useContext(Ctx);

export function ProveedorVentanas({ children }: { children: ReactNode }) {
  const [lista, setLista] = useState<Ventana[]>([]);
  const z = useRef(100);

  const enfocar = useCallback((id: string) => {
    setLista((l) => l.map((v) => (v.id === id ? { ...v, z: ++z.current, minimizada: false } : v)));
  }, []);

  const abrir: Ventanas['abrir'] = useCallback(({ id, titulo, icono, contenido, ancho = 560, alto = 460 }) => {
    const clave = id ?? `v${Date.now()}`;
    setLista((l) => {
      if (l.some((v) => v.id === clave)) return l.map((v) => (v.id === clave ? { ...v, contenido, titulo, z: ++z.current, minimizada: false } : v));
      const n = l.length;
      const w = Math.min(ancho, window.innerWidth - 40);
      const h = Math.min(alto, window.innerHeight - 140);
      return [...l, {
        id: clave, titulo, icono, contenido, w, h, z: ++z.current, minimizada: false, maximizada: false,
        x: Math.max(20, window.innerWidth / 2 - w / 2 + n * 34 - 60), y: Math.max(76, 96 + n * 30),
      }];
    });
  }, []);

  const cerrar = useCallback((id: string) => setLista((l) => l.filter((v) => v.id !== id)), []);
  const actualizar = (id: string, c: Partial<Ventana>) => setLista((l) => l.map((v) => (v.id === id ? { ...v, ...c } : v)));
  const superior = lista.reduce((m, v) => (!v.minimizada && v.z > m ? v.z : m), 0);

  return (
    <Ctx.Provider value={{ abrir, cerrar, hay: lista.length > 0 }}>
      {children}
      {createPortal(
        <>
          <AnimatePresence>
            {lista.filter((v) => !v.minimizada).map((v) => (
              <VentanaFlotante key={v.id} v={v} enfocada={v.z === superior} enfocar={() => enfocar(v.id)} cerrar={() => cerrar(v.id)}
                actualizar={(c) => actualizar(v.id, c)} />
            ))}
          </AnimatePresence>
          <AnimatePresence>
            {lista.length > 0 && (
              <motion.div className="barra-ventanas" initial={{ y: 80, opacity: 0 }} animate={{ y: 0, opacity: 1 }} exit={{ y: 80, opacity: 0 }}>
                <span className="fila tenue pequeno" style={{ padding: '0 8px', gap: 6 }}><AppWindow size={15} /> {lista.length}</span>
                {lista.map((v) => (
                  <button key={v.id} className={`${v.z === superior ? 'activa' : ''} ${v.minimizada ? 'minimizada' : ''}`}
                    onClick={() => (v.z === superior && !v.minimizada ? actualizar(v.id, { minimizada: true }) : enfocar(v.id))}>
                    {v.icono}
                    <span>{v.titulo}</span>
                  </button>
                ))}
              </motion.div>
            )}
          </AnimatePresence>
        </>,
        document.body
      )}
    </Ctx.Provider>
  );
}

function VentanaFlotante({ v, enfocada, enfocar, cerrar, actualizar }: { v: Ventana; enfocada: boolean; enfocar: () => void; cerrar: () => void; actualizar: (c: Partial<Ventana>) => void }) {
  const inicio = useRef<{ x: number; y: number; vx: number; vy: number; w: number; h: number; modo: 'mover' | 'tamano' } | null>(null);

  const comenzar = (e: React.PointerEvent, modo: 'mover' | 'tamano') => {
    if ((e.target as HTMLElement).closest('button') && modo === 'mover') return;
    enfocar();
    if (v.maximizada) return;
    inicio.current = { x: e.clientX, y: e.clientY, vx: v.x, vy: v.y, w: v.w, h: v.h, modo };
    (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
  };
  const mover = (e: React.PointerEvent) => {
    const s = inicio.current;
    if (!s) return;
    const dx = e.clientX - s.x;
    const dy = e.clientY - s.y;
    if (s.modo === 'mover') actualizar({ x: Math.max(-v.w + 120, Math.min(window.innerWidth - 120, s.vx + dx)), y: Math.max(64, Math.min(window.innerHeight - 50, s.vy + dy)) });
    else actualizar({ w: Math.max(320, s.w + dx), h: Math.max(200, s.h + dy) });
  };
  const soltar = () => {
    inicio.current = null;
  };
  const estilo = v.maximizada ? { left: 12, top: 74, width: window.innerWidth - 24, height: window.innerHeight - 160, zIndex: v.z } : { left: v.x, top: v.y, width: v.w, height: v.h, zIndex: v.z };

  return (
    <motion.div
      className={`ventana ${enfocada ? 'enfocada' : ''}`}
      style={estilo}
      initial={{ opacity: 0, scale: 0.85, y: 30, rotateX: 12 }}
      animate={{ opacity: 1, scale: 1, y: 0, rotateX: 0 }}
      exit={{ opacity: 0, scale: 0.8, y: 60 }}
      transition={{ type: 'spring', damping: 24, stiffness: 300 }}
      onPointerDown={enfocar}
    >
      <div className="ventana-barra" onPointerDown={(e) => comenzar(e, 'mover')} onPointerMove={mover} onPointerUp={soltar} onDoubleClick={() => actualizar({ maximizada: !v.maximizada })}>
        <div className="semaforo-ventana">
          <button style={{ background: '#ef4444' }} onClick={cerrar} title="Cerrar" />
          <button style={{ background: '#eab308' }} onClick={() => actualizar({ minimizada: true })} title="Minimizar" />
          <button style={{ background: '#22c55e' }} onClick={() => actualizar({ maximizada: !v.maximizada })} title="Maximizar" />
        </div>
        <strong>{v.icono}{v.titulo}</strong>
      </div>
      <div className="ventana-cuerpo">{v.contenido}</div>
      {!v.maximizada && <div className="ventana-redimensionar" onPointerDown={(e) => comenzar(e, 'tamano')} onPointerMove={mover} onPointerUp={soltar} />}
    </motion.div>
  );
}

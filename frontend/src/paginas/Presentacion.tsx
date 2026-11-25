import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Bell, Clapperboard, Database, Film, Gauge, Globe2, Image, PlayCircle, Scale, ShieldAlert, ShieldCheck, Smartphone } from 'lucide-react';
import { Carrusel3D, Diapositivas } from '../componentes/Carrusel';
import { Encabezado, Modal, Tarjeta, Vacio } from '../componentes/Ui';

interface Medio { archivo: string; titulo: string; tipo: 'video' | 'gif' | 'imagen'; descripcion?: string }

const DIAPOSITIVAS = [
  { i: Globe2, t: 'Una sola visión de 12 filiales', d: 'Tablero ejecutivo con globo 3D, mapa de calor 5×5 y tendencias que se actualizan solas.', c: '#22d3ee', ruta: '/' },
  { i: ShieldAlert, t: 'Ciclo completo del riesgo', d: 'Registro, evaluación, plan, aprobación con reglas RN03, RN04, RN05, RN12 y RN16, ejecución y cierre.', c: '#f97316', ruta: '/riesgos' },
  { i: Gauge, t: 'KRI conectados al ERP y al SIEM', d: 'Cada medición se clasifica con el semáforo y, si cruza un umbral, nace la alerta.', c: '#eab308', ruta: '/kri' },
  { i: Bell, t: 'Alertas con SLA y escalamiento', d: '24, 72 o 168 horas según la severidad. Si nadie atiende, sube al superior automáticamente.', c: '#ef4444', ruta: '/alertas' },
  { i: Scale, t: 'Control financiero independiente', d: 'El Auditor Financiero valida el impacto contra el ERP y concilia los KRI cada mes.', c: '#a855f7', ruta: '/validacion-financiera' },
  { i: Database, t: 'Reglas blindadas en la base de datos', d: 'Triggers que hacen cumplir las reglas y una bitácora que nadie puede alterar.', c: '#22c55e', ruta: '/bitacora' },
  { i: Smartphone, t: 'En el escritorio y en el bolsillo', d: 'Aplicación web progresiva instalable, multiventana, tema claro u oscuro y tres idiomas.', c: '#6366f1', ruta: '/movil' },
];

/** Vitrina de la solución para la defensa: diapositivas, video de demostración, GIF y capturas. */
export default function Presentacion() {
  const ir = useNavigate();
  const [medios, setMedios] = useState<Medio[]>([]);
  const [ver, setVer] = useState<Medio | null>(null);
  useEffect(() => {
    fetch('/medios/medios.json').then((r) => (r.ok ? r.json() : [])).then(setMedios).catch(() => setMedios([]));
  }, []);
  const videos = medios.filter((m) => m.tipo === 'video');
  const gifs = medios.filter((m) => m.tipo === 'gif');
  const imagenes = medios.filter((m) => m.tipo === 'imagen');

  return (
    <div className="columna" style={{ gap: 18 }}>
      <Encabezado icono={<Clapperboard size={22} />} titulo="Presentación de SIGRE Global" texto="Recorrido visual del sistema para la defensa del proyecto." />

      <Tarjeta brillante style={{ padding: 0, overflow: 'hidden' }}>
        <Diapositivas alto={360} elementos={DIAPOSITIVAS.map((s) => (
          <div style={{ height: '100%', display: 'grid', gridTemplateColumns: '1.1fr 0.9fr', alignItems: 'center', padding: '30px 44px', gap: 30,
            background: `radial-gradient(600px 300px at 85% 50%, ${s.c}33, transparent 70%), linear-gradient(135deg, var(--superficie-solida), var(--fondo-2))` }}>
            <div className="columna" style={{ gap: 14 }}>
              <motion.span initial={{ scale: 0, rotate: -30 }} animate={{ scale: 1, rotate: 0 }} transition={{ type: 'spring', delay: 0.2 }} className="icono-titulo" style={{ width: 58, height: 58, color: s.c, background: `${s.c}22` }}><s.i size={28} /></motion.span>
              <motion.h1 initial={{ opacity: 0, y: 14 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.3 }} style={{ fontSize: 34 }}>{s.t}</motion.h1>
              <motion.p initial={{ opacity: 0, y: 14 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.45 }} className="suave" style={{ fontSize: 16, maxWidth: 520 }}>{s.d}</motion.p>
              <motion.button initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.6 }} className="boton primario" style={{ alignSelf: 'flex-start' }} onClick={() => ir(s.ruta)}>Ver en el sistema</motion.button>
            </div>
            <motion.div className="ocultar-movil" initial={{ opacity: 0, scale: 0.8, rotateY: 30 }} animate={{ opacity: 1, scale: 1, rotateY: 0 }} transition={{ delay: 0.25, duration: 0.8 }}
              style={{ display: 'grid', placeItems: 'center' }}>
              <div className="flotando" style={{ width: 200, height: 200, borderRadius: 48, display: 'grid', placeItems: 'center', background: `linear-gradient(135deg, ${s.c}, ${s.c}55)`, boxShadow: `0 40px 80px -30px ${s.c}` }}>
                <s.i size={96} color="#fff" />
              </div>
            </motion.div>
          </div>
        ))} />
      </Tarjeta>

      <div className="rejilla r-3">
        <Tarjeta className="span-2">
          <h2 style={{ marginBottom: 12 }}><Film size={18} /> Video de demostración</h2>
          {videos.length ? (
            <video src={videos[0].archivo} controls autoPlay muted loop playsInline style={{ width: '100%', borderRadius: 12, background: '#000' }} />
          ) : <Vacio texto="El video se genera con el script de multimedia" icono={<Film size={36} />} />}
          {videos.length > 1 && (
            <div className="fila" style={{ marginTop: 10 }}>{videos.slice(1).map((v) => <button key={v.archivo} className="boton chico" onClick={() => setVer(v)}><PlayCircle size={14} /> {v.titulo}</button>)}</div>
          )}
        </Tarjeta>
        <Tarjeta>
          <h2 style={{ marginBottom: 12 }}><ShieldCheck size={18} /> Cómo se cumple el informe</h2>
          <div className="columna pequeno" style={{ gap: 8 }}>
            {[['Casos de uso', 'CU01 a CU18'], ['Reglas de negocio', 'RN01 a RN18'], ['Actores', 'A1 a A8 (8 roles)'], ['Prototipos', 'P01, P02 y P03'], ['Pruebas', 'CP01 a CP26 automatizadas'], ['Diagramas', '32 figuras en la galería']].map(([k, v], i) => (
              <motion.div key={k} className="fila entre item-verificacion ok" style={{ padding: 10 }} initial={{ opacity: 0, x: 12 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: i * 0.08 }}>
                <span>{k}</span><strong>{v}</strong>
              </motion.div>
            ))}
          </div>
        </Tarjeta>
      </div>

      {gifs.length > 0 && (
        <Tarjeta>
          <h2 style={{ marginBottom: 6 }}><Image size={18} /> Flujos animados</h2>
          <Carrusel3D alto={420} ancho={460} elementos={gifs.map((g) => (
            <div className="tarjeta" style={{ height: '100%', padding: 10, display: 'flex', flexDirection: 'column', gap: 8 }} onDoubleClick={() => setVer(g)}>
              <img src={g.archivo} alt={g.titulo} style={{ width: '100%', flex: 1, objectFit: 'contain', borderRadius: 10, background: '#050b17', minHeight: 0 }} />
              <strong className="pequeno">{g.titulo}</strong>
              {g.descripcion && <span className="pequeno tenue">{g.descripcion}</span>}
            </div>
          ))} />
        </Tarjeta>
      )}

      {imagenes.length > 0 && (
        <Tarjeta>
          <h2 style={{ marginBottom: 12 }}><Image size={18} /> Capturas</h2>
          <div className="galeria">
            {imagenes.map((m) => (
              <figure key={m.archivo} onClick={() => setVer(m)}>
                <img src={m.archivo} alt={m.titulo} loading="lazy" style={{ objectFit: 'cover' }} />
                <figcaption>{m.titulo}</figcaption>
              </figure>
            ))}
          </div>
        </Tarjeta>
      )}

      <Modal abierto={!!ver} cerrar={() => setVer(null)} ancho titulo={ver?.titulo ?? ''} subtitulo={ver?.descripcion}>
        {ver?.tipo === 'video' ? <video src={ver.archivo} controls autoPlay style={{ width: '100%', borderRadius: 12 }} /> : ver && <img src={ver.archivo} alt={ver.titulo} style={{ width: '100%', borderRadius: 12 }} />}
      </Modal>
    </div>
  );
}

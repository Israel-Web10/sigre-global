import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { AnimatePresence, motion } from 'framer-motion';
import { AlarmClock, ArrowUpRight, Bell, CheckCheck, CornerUpLeft, Hand, Siren, Timer, UserRound, Wrench } from 'lucide-react';
import { api, type Fila } from '../nucleo/api';
import { useAlEvento, useAvisos, useDatos } from '../nucleo/contexto';
import { colorSeveridad, etiqueta, fechaHora, haceCuanto, operacion } from '../nucleo/formato';
import { Avatar, Cargando, ChipEstado, ChipSeveridad, Encabezado, Girando, Kpi, Modal, RelojSla, Segmentado, Tarjeta, Vacio } from '../componentes/Ui';

const COLUMNAS = [
  { e: ['GENERADA', 'NOTIFICADA'], t: 'Notificadas', c: 'var(--info)' },
  { e: ['EN_ATENCION'], t: 'En atención', c: 'var(--acento-2)' },
  { e: ['ESCALADA'], t: 'Escaladas', c: 'var(--critico)' },
  { e: ['RESUELTA'], t: 'Resueltas', c: 'var(--bajo)' },
  { e: ['CERRADA'], t: 'Cerradas', c: 'var(--texto-3)' },
];

/** CU07 a CU09: alertas con su SLA (RN07), atención y escalamiento automático al superior. */
export default function Alertas() {
  const [params, setParams] = useSearchParams();
  const [mias, setMias] = useState<'todas' | 'mias'>('todas');
  const [severidad, setSeveridad] = useState('');
  const { datos, recargar } = useDatos<Fila[]>(`/api/alertas?mias=${mias === 'mias'}${severidad ? `&severidad=${severidad}` : ''}`);
  const { datos: res, recargar: recargarRes } = useDatos<Fila>('/api/alertas/resumen');
  const [nuevas, setNuevas] = useState<Set<number>>(new Set());
  const elegida = params.get('id');
  useAlEvento(['ALERTA_GENERADA', 'ALERTA_ACTUALIZADA'], (e) => {
    setNuevas((s) => new Set(s).add(Number(e.id_alerta)));
    recargar();
    recargarRes();
  });
  const grupos = useMemo(() => COLUMNAS.map((c) => ({ ...c, lista: (datos ?? []).filter((a) => c.e.includes(a.estado)) })), [datos]);

  return (
    <>
      <Encabezado icono={<Siren size={22} />} titulo="Centro de alertas (CU07 a CU09)"
        texto="Cada alerta nace de un trigger, la entrega el servicio de notificaciones y tiene un plazo según su severidad: crítica 24 h, advertencia 72 h, preventiva 168 h (RN07). Si nadie la atiende a tiempo, se escala al superior.">
        <Segmentado valor={mias} cambiar={setMias} opciones={[{ valor: 'todas', texto: 'Todas' }, { valor: 'mias', texto: <><UserRound size={14} /> Asignadas a mí</> }]} />
        <select className="entrada" style={{ width: 150 }} value={severidad} onChange={(e) => setSeveridad(e.target.value)}>
          <option value="">Toda severidad</option><option value="CRITICA">Crítica</option><option value="ADVERTENCIA">Advertencia</option><option value="PREVENTIVA">Preventiva</option>
        </select>
      </Encabezado>
      <div className="rejilla r-4" style={{ marginBottom: 16 }}>
        <Kpi titulo="Abiertas" valor={Number(res?.abiertas ?? 0)} icono={<Bell size={18} />} pie={<>{res?.mias ?? 0} asignadas a usted</>} />
        <Kpi titulo="Críticas abiertas" valor={Number(res?.criticas ?? 0)} icono={<Siren size={18} />} color="var(--critico)" retraso={0.05} pie="SLA de 24 horas" />
        <Kpi titulo="SLA vencido" valor={Number(res?.sla_vencidas ?? 0)} icono={<AlarmClock size={18} />} color="var(--alto)" retraso={0.1} pie={<>{res?.escaladas ?? 0} escaladas al superior</>} />
        <Kpi titulo="Atendidas en plazo" valor={res?.atendidas ? (100 * Number(res.en_plazo)) / Number(res.atendidas) : 0} decimales={1} sufijo=" %" icono={<CheckCheck size={18} />} color="var(--bajo)" retraso={0.15} pie={<>{res?.en_plazo ?? 0} de {res?.atendidas ?? 0}</>} />
      </div>
      {!datos ? <Cargando filas={3} alto={200} /> : (
        <div className="kanban">
          {grupos.map((g) => (
            <div key={g.t} className="kanban-columna">
              <h3><i style={{ width: 9, height: 9, borderRadius: 5, background: g.c, display: 'inline-block' }} /> {g.t} <span className="contador" style={{ marginLeft: 'auto' }}>{g.lista.length}</span></h3>
              <AnimatePresence initial={false}>
                {g.lista.slice(0, 40).map((a) => (
                  <motion.div layout key={a.id_alerta} layoutId={`al-${a.id_alerta}`} initial={{ opacity: 0, scale: 0.9 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: 0.9 }}
                    className={`tarjeta-alerta ${nuevas.has(a.id_alerta) ? 'pulso-rojo' : ''}`} style={{ ['--color' as any]: colorSeveridad(a.severidad) }} onClick={() => setParams({ id: String(a.id_alerta) })}>
                    <div className="fila entre"><ChipSeveridad s={a.severidad} />{a.nivel_escalamiento > 0 && <span className="chip" style={{ color: 'var(--critico)' }}><ArrowUpRight size={11} /> nivel {a.nivel_escalamiento}</span>}</div>
                    <p>{a.mensaje}</p>
                    <div className="fila entre pequeno">
                      <span className="fila tenue" style={{ gap: 6 }}><Avatar nombre={a.asignado} chico /> {a.asignado?.split(' ')[0]}</span>
                      {['RESUELTA', 'CERRADA'].includes(a.estado) ? <span className="tenue">{haceCuanto(a.fecha_resolucion ?? a.fecha_cierre)}</span> : <RelojSla segundos={a.segundos_restantes} />}
                    </div>
                  </motion.div>
                ))}
              </AnimatePresence>
              {g.lista.length === 0 && <Vacio texto="Sin alertas" />}
            </div>
          ))}
        </div>
      )}
      {elegida && <DetalleAlerta id={Number(elegida)} cerrar={() => setParams({})} alCambiar={() => { recargar(); recargarRes(); }} />}
    </>
  );
}

const FLUJO = ['GENERADA', 'NOTIFICADA', 'EN_ATENCION', 'RESUELTA', 'CERRADA'];

function DetalleAlerta({ id, cerrar, alCambiar }: { id: number; cerrar: () => void; alCambiar: () => void }) {
  const ir = useNavigate();
  const { avisar, error } = useAvisos();
  const { datos: d, recargar } = useDatos<Fila>(`/api/alertas/${id}`, [id]);
  const [accion, setAccion] = useState('');
  const [ocupado, setOcupado] = useState(false);
  useEffect(() => setAccion(''), [id]);
  useAlEvento(['ALERTA_ACTUALIZADA'], (e) => Number(e.id_alerta) === id && recargar());
  const hacer = async (ruta: string, cuerpo: unknown, texto: string) => {
    setOcupado(true);
    try {
      await api.post(`/api/alertas/${id}/${ruta}`, cuerpo);
      avisar('exito', texto);
      recargar();
      alCambiar();
    } catch (e) {
      error(e);
    } finally {
      setOcupado(false);
    }
  };
  const a = d?.alerta;
  const pm = d?.permisos ?? {};
  const pasoActual = a ? (a.estado === 'ESCALADA' ? 1.5 : FLUJO.indexOf(a.estado)) : 0;
  return (
    <Modal abierto cerrar={cerrar} ancho icono={<Siren size={20} />} titulo={a ? `Alerta #${a.id_alerta} · ${etiqueta(a.tipo)}` : 'Alerta'}
      subtitulo={a ? `${a.riesgo_codigo ?? ''} ${a.riesgo_titulo ?? ''} · ${a.filial ?? ''}` : ''}>
      {!a ? <Cargando /> : (
        <div className="rejilla r-3" style={{ gap: 20 }}>
          <div className="span-2 columna" style={{ gap: 16 }}>
            <div className="fila"><ChipSeveridad s={a.severidad} /><ChipEstado estado={a.estado} /><span className="chip">SLA {a.sla_horas} h</span>
              {!['RESUELTA', 'CERRADA'].includes(a.estado) && <span className="chip"><RelojSla segundos={a.segundos_restantes} /></span>}</div>
            <p style={{ fontSize: 15 }}>{a.mensaje}</p>
            <div className="fila" style={{ gap: 0, flexWrap: 'nowrap' }}>
              {FLUJO.map((e, k) => (
                <div key={e} className="crece columna" style={{ alignItems: 'center', gap: 4 }}>
                  <motion.div initial={{ scale: 0 }} animate={{ scale: 1 }} transition={{ delay: k * 0.08 }}
                    style={{ width: 14, height: 14, borderRadius: 7, background: k <= pasoActual ? 'var(--acento)' : 'var(--superficie-3)', boxShadow: k === Math.floor(pasoActual) ? '0 0 0 5px var(--acento-suave)' : undefined }} />
                  <span className="pequeno" style={{ color: k <= pasoActual ? 'var(--texto)' : 'var(--texto-3)' }}>{etiqueta(e)}</span>
                </div>
              ))}
            </div>
            {a.estado === 'ESCALADA' && <div className="item-verificacion no pequeno"><ArrowUpRight size={18} /> Escalada al nivel {a.nivel_escalamiento}: ahora la atiende {a.asignado} (originalmente {a.asignado_original}).</div>}
            {a.accion_correctiva && <div className="item-verificacion ok pequeno"><Wrench size={16} /><div><strong>Acción correctiva:</strong> {a.accion_correctiva}</div></div>}
            {pm.resolver && (
              <div className="columna">
                <textarea className="entrada" placeholder="Acción correctiva aplicada (obligatoria para resolver, CU08)" value={accion} onChange={(e) => setAccion(e.target.value)} />
              </div>
            )}
            <div className="fila">
              {pm.atender && <button className="boton primario" disabled={ocupado} onClick={() => hacer('atender', {}, 'Alerta en atención')}>{ocupado ? <Girando /> : <Hand size={16} />} {a.estado === 'RESUELTA' ? 'Reabrir' : 'Atender'}</button>}
              {pm.resolver && <button className="boton exito" disabled={ocupado || accion.trim().length < 5} onClick={() => hacer('resolver', { accionCorrectiva: accion }, 'Alerta resuelta')}><CheckCheck size={16} /> Resolver</button>}
              {pm.cerrar && <button className="boton" disabled={ocupado} onClick={() => hacer('cerrar', {}, 'Alerta cerrada')}><CheckCheck size={16} /> Verificar y cerrar</button>}
              {pm.escalar && <button className="boton peligro" disabled={ocupado} onClick={() => hacer('escalar', {}, 'Alerta escalada al superior (CU09)')}><ArrowUpRight size={16} /> Escalar ahora</button>}
              {a.id_riesgo && <button className="boton fantasma" onClick={() => ir(`/riesgos/${a.id_riesgo}`)}><CornerUpLeft size={16} /> Ver riesgo</button>}
            </div>
            <h3>Historial de la alerta</h3>
            <div className="linea-tiempo">
              {d.historial.map((h: Fila, k: number) => (
                <div key={k} className="hito" style={{ ['--c' as any]: h.resultado === 'OK' ? 'var(--acento)' : 'var(--critico)' }}>
                  <strong>{operacion(h.operacion)}</strong>
                  <p>{h.usuario} · {fechaHora(h.fecha_hora)}{h.resultado !== 'OK' ? ` · ${h.detalle?.motivo ?? h.resultado}` : ''}</p>
                </div>
              ))}
            </div>
          </div>
          <div className="columna" style={{ gap: 12 }}>
            <Tarjeta style={{ padding: 14 }}>
              <h3 style={{ marginBottom: 10 }}><Timer size={15} /> Tiempos</h3>
              {[['Generada', a.fecha_generacion], ['Notificada', a.fecha_notificacion], ['En atención', a.fecha_atencion], ['Escalada', a.fecha_escalamiento], ['Resuelta', a.fecha_resolucion], ['Cerrada', a.fecha_cierre], ['Límite SLA', a.fecha_limite]].map(([t, v]) => (
                <div key={t} className="fila entre pequeno" style={{ padding: '4px 0' }}><span className="tenue">{t}</span><span>{fechaHora(v)}</span></div>
              ))}
            </Tarjeta>
            <Tarjeta style={{ padding: 14 }}>
              <h3 style={{ marginBottom: 10 }}><ArrowUpRight size={15} /> Cadena de escalamiento</h3>
              {d.cadena.map((u: Fila) => (
                <div key={u.id_usuario} className="fila" style={{ gap: 10, padding: '6px 0', paddingLeft: u.nivel * 14 }}>
                  <Avatar nombre={u.nombre_completo} chico />
                  <div className="pequeno"><strong>{u.nombre_completo}</strong><div className="tenue">{u.cargo} · nivel {u.nivel}</div></div>
                  {u.nombre_completo === a.asignado && <span className="chip" style={{ marginLeft: 'auto', color: 'var(--acento)' }}>actual</span>}
                </div>
              ))}
            </Tarjeta>
          </div>
        </div>
      )}
    </Modal>
  );
}

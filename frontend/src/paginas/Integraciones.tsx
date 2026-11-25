import { useState } from 'react';
import { motion } from 'framer-motion';
import { Activity, Database, Mail, Network, Power, Radio, ServerCog, ShieldCheck, Timer, Waypoints } from 'lucide-react';
import { api, type Fila } from '../nucleo/api';
import { useAlEvento, useAvisos, useDatos, useEventos } from '../nucleo/contexto';
import { colorSemaforo, etiqueta, fechaHora, haceCuanto, numero } from '../nucleo/formato';
import { GraficoArea } from '../componentes/Graficos';
import { Cargando, Encabezado, Interruptor, Segmentado, Tarjeta } from '../componentes/Ui';

const SISTEMAS: Record<string, { i: typeof Database; d: string; color: string }> = {
  ERP: { i: Database, d: 'Tipo de cambio diario (RN15), cierres contables (RN18) y KRI financieros', color: '#22d3ee' },
  SIEM: { i: ShieldCheck, d: 'Eventos de seguridad que alimentan los KRI tecnológicos', color: '#a855f7' },
  NORMATIVO: { i: Network, d: 'Novedades regulatorias de cada jurisdicción (CU12)', color: '#eab308' },
  NOTIFICACIONES: { i: Mail, d: 'Correo y SMS de las alertas (CU07)', color: '#22c55e' },
};

/** Estado de los sistemas externos del diagrama de contexto y del bus de eventos. */
export default function Integraciones() {
  const { avisar, error } = useAvisos();
  const { ultimos, conectado } = useEventos();
  const { datos: d, recargar } = useDatos<Fila>('/api/integraciones');
  const [moneda, setMoneda] = useState('PEN');
  const { datos: tc } = useDatos<Fila[]>(`/api/erp/tipos-cambio/${moneda}?dias=120`);
  useAlEvento(['MEDICION'], () => recargar());
  const sim = d?.simulador ?? {};
  const alternar = async (activo: boolean) => {
    try {
      await api.post('/api/integraciones/simulador', { activo });
      avisar('info', activo ? 'Simulador encendido' : 'Simulador apagado', activo ? 'El ERP y el SIEM vuelven a publicar mediciones' : 'No se publicarán mediciones automáticas');
      recargar();
    } catch (e) {
      error(e);
    }
  };
  if (!d) return <Cargando filas={5} alto={110} />;
  return (
    <>
      <Encabezado icono={<Waypoints size={22} />} titulo="Integraciones y bus de eventos"
        texto="Sistemas externos del diagrama de contexto. En la demostración el ERP y el SIEM están simulados y publican mediciones; la base de datos avisa cada cambio por LISTEN/NOTIFY y la API lo reenvía a los navegadores (SSE)." />
      <div className="rejilla r-4" style={{ marginBottom: 16 }}>
        {(d.sistemas ?? []).map((s: Fila, k: number) => {
          const S = SISTEMAS[s.sistema];
          return (
            <Tarjeta key={s.sistema} inclinar retraso={k * 0.05}>
              <div className="fila entre">
                <span className="kpi-icono" style={{ width: 40, height: 40, borderRadius: 12, display: 'grid', placeItems: 'center', background: `${S.color}22`, color: S.color }}><S.i size={20} /></span>
                <span className="en-vivo" style={Number(s.incidencias) ? { color: 'var(--medio)', borderColor: 'rgba(234,179,8,.3)', background: 'rgba(234,179,8,.1)' } : undefined}><i /> {Number(s.incidencias) ? `${s.incidencias} incid.` : 'Operativo'}</span>
              </div>
              <h3 style={{ margin: '12px 0 4px' }}>{s.sistema}</h3>
              <p className="pequeno tenue">{S.d}</p>
              <div className="sep" />
              <div className="fila entre pequeno"><span className="tenue">Operaciones hoy</span><strong>{s.operaciones_hoy}</strong></div>
              <div className="fila entre pequeno"><span className="tenue">Última actividad</span><span>{s.ultima_actividad ? haceCuanto(s.ultima_actividad.length > 10 ? s.ultima_actividad : `${s.ultima_actividad}T12:00:00`) : '—'}</span></div>
            </Tarjeta>
          );
        })}
      </div>

      <div className="rejilla r-3" style={{ marginBottom: 16 }}>
        <Tarjeta>
          <div className="tarjeta-cabecera"><h3><ServerCog size={16} /> Simulador ERP / SIEM</h3><div className="acciones"><Interruptor valor={!!sim.activo} cambiar={alternar} /></div></div>
          <div className="columna pequeno" style={{ gap: 8 }}>
            <div className="fila entre"><span className="tenue"><Timer size={12} /> Intervalo</span><strong>{sim.intervalo_seg} s</strong></div>
            <div className="fila entre"><span className="tenue">Probabilidad de evento</span><strong>{numero(Number(sim.probabilidad_evento) * 100, 0)} %</strong></div>
            <div className="fila entre"><span className="tenue">Mediciones publicadas</span><strong>{sim.publicadas}</strong></div>
            <div className="fila entre"><span className="tenue">Fuera de umbral</span><strong style={{ color: 'var(--critico)' }}>{sim.fuera_de_umbral}</strong></div>
            <div className="fila entre"><span className="tenue">Última</span><span>{sim.ultima_medicion ? `${sim.ultima_medicion.kri}: ${numero(sim.ultima_medicion.valor)}` : '—'}</span></div>
          </div>
          <div className="sep" />
          <div className="columna pequeno" style={{ gap: 6 }}>
            <strong><Activity size={13} /> Servidor de trabajos</strong>
            {Object.entries(d.tareas ?? {}).map(([k, v]: [string, any]) => (
              <div key={k} className="fila entre"><span className="tenue">{etiqueta(k)}</span><span>{v.resultado} · {haceCuanto(v.fecha)}</span></div>
            ))}
            <div className="fila entre"><span className="tenue">Navegadores conectados</span><strong>{d.eventos?.conectados}</strong></div>
            <div className="fila entre"><span className="tenue">Eventos difundidos</span><strong>{d.eventos?.eventos}</strong></div>
          </div>
        </Tarjeta>
        <Tarjeta className="span-2">
          <div className="tarjeta-cabecera"><h3><Radio size={16} /> Consola de eventos en vivo</h3><div className="acciones"><span className={`en-vivo ${conectado ? '' : 'no'}`}><i /> canal sigre_eventos</span></div></div>
          <div className="consola">
            {ultimos.length === 0 && <div style={{ color: '#64748b' }}>Esperando eventos de PostgreSQL…</div>}
            {ultimos.map((e) => (
              <motion.div key={e._recibido + e.evento + (e.id_kri ?? e.id_alerta ?? e.id_notificacion ?? '')} initial={{ opacity: 0, x: -10, backgroundColor: 'rgba(34,211,238,.25)' }} animate={{ opacity: 1, x: 0, backgroundColor: 'rgba(0,0,0,0)' }} transition={{ duration: 0.8 }}>
                <span style={{ color: '#64748b' }}>{new Date(e._recibido).toLocaleTimeString('es-BO')}</span>{' '}
                <span style={{ color: e.evento.startsWith('ALERTA') ? '#fca5a5' : e.evento === 'MEDICION' ? '#67e8f9' : '#c4b5fd' }}>{e.evento.padEnd(18, ' ')}</span>{' '}
                {e.evento === 'MEDICION' ? <>{e.origen} · {e.kri} = <span style={{ color: colorSemaforo(e.semaforo) }}>{numero(e.valor)} {e.unidad} [{e.semaforo}]</span></> : JSON.stringify(Object.fromEntries(Object.entries(e).filter(([k]) => !['evento', '_recibido'].includes(k))))}
              </motion.div>
            ))}
          </div>
        </Tarjeta>
      </div>

      <div className="rejilla r-3">
        <Tarjeta className="span-2">
          <div className="tarjeta-cabecera">
            <h3><Database size={16} /> Tipo de cambio publicado por el ERP (RN15)</h3>
            <div className="acciones"><Segmentado valor={moneda} cambiar={setMoneda} opciones={['PEN', 'CLP', 'ARS', 'COP', 'BRL', 'EUR'].map((m) => ({ valor: m, texto: m }))} /></div>
          </div>
          {tc && <GraficoArea alto={210} formato={(v) => numero(v, 2)} etiquetas={tc.map((x) => x.fecha.slice(5))} series={[{ nombre: `${moneda} por USD`, color: '#22d3ee', datos: tc.map((x) => Number(x.unidades_por_usd)) }]} />}
        </Tarjeta>
        <Tarjeta>
          <h3 style={{ marginBottom: 10 }}>Últimas mediciones automáticas</h3>
          <div className="columna" style={{ gap: 8, maxHeight: 280, overflow: 'auto' }}>
            {(d.ultimas_mediciones ?? []).map((m: Fila) => (
              <div key={m.id_medicion} className="fila pequeno" style={{ gap: 8, flexWrap: 'nowrap' }}>
                <i style={{ width: 8, height: 8, borderRadius: 4, background: colorSemaforo(m.semaforo), flexShrink: 0 }} />
                <span className="crece" style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{m.kri}</span>
                <strong>{numero(m.valor)} {m.unidad}</strong>
                <span className="tenue">{fechaHora(m.fecha).split(',').pop()}</span>
              </div>
            ))}
          </div>
          {sim.activo === false && <button className="boton bloque" style={{ marginTop: 12 }} onClick={() => alternar(true)}><Power size={15} /> Encender simulador</button>}
        </Tarjeta>
      </div>
    </>
  );
}

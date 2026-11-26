import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { AlertTriangle, BadgeCheck, CalendarClock, CirclePause, CirclePlay, Eye, Scale, ServerOff, ShieldX, TrendingUp, UserX } from 'lucide-react';
import { api, type Fila } from '../nucleo/api';
import { useAlEvento, useAvisos, useDatos } from '../nucleo/contexto';
import { etiqueta, fecha, fechaHora, numero, usd } from '../nucleo/formato';
import { GraficoArea } from '../componentes/Graficos';
import { Bandera, Campo, Cargando, ChipEstado, ChipNivel, Encabezado, Girando, Kpi, Modal, Pestanas, Tabla, Tarjeta, Vacio } from '../componentes/Ui';

/** Prototipo P02 · CU17: el Auditor Financiero valida u observa el impacto financiero (RN16) sin conflicto de funciones (RN17). */
export default function Validacion() {
  const { datos: pendientes, recargar } = useDatos<Fila[]>('/api/validacion-financiera/pendientes');
  const { datos: historial, recargar: recargarH } = useDatos<Fila[]>('/api/validacion-financiera');
  const [pestana, setPestana] = useState<'pendientes' | 'historial'>('pendientes');
  const [elegida, setElegida] = useState<number | null>(null);
  useAlEvento(['EVALUACION', 'VALIDACION'], () => { recargar(); recargarH(); });
  useEffect(() => {
    if (!elegida && pendientes?.length) setElegida(pendientes.find((p) => !p.conflicto)?.id_evaluacion ?? pendientes[0].id_evaluacion);
  }, [pendientes, elegida]);
  const lista = pendientes ?? [];
  const elegir = (id: number) => {
    setElegida(id);
    // En el teléfono la bandeja y el detalle se apilan: se lleva al usuario al detalle
    if (window.matchMedia('(max-width: 767px)').matches) setTimeout(() => document.getElementById('panel-validacion')?.scrollIntoView({ behavior: 'smooth' }), 120);
  };
  return (
    <>
      <Encabezado icono={<Scale size={22} />} titulo="Validación financiera (CU17 · P02)"
        texto="Los riesgos financieros Altos o Críticos, o con impacto de 100 000 USD o más, se validan contra el ERP en 3 días hábiles (RN16). Quien evaluó o es responsable del riesgo no puede validarlo (RN17)." />
      <div className="rejilla r-4" style={{ marginBottom: 16 }}>
        <Kpi titulo="Pendientes" valor={lista.length} icono={<CalendarClock size={18} />} pie={`${lista.filter((p) => p.pospuesta).length} en pausa por ERP no disponible`} />
        <Kpi titulo="Fuera de plazo" valor={lista.filter((p) => p.fuera_de_plazo).length} icono={<AlertTriangle size={18} />} color="var(--critico)" retraso={0.05} pie="más de 3 días hábiles" />
        <Kpi titulo="Con conflicto (RN17)" valor={lista.filter((p) => p.conflicto).length} icono={<UserX size={18} />} color="var(--alto)" retraso={0.1} pie="no puede validarlas usted" />
        <Kpi titulo="Exposición por validar" texto={usd(lista.reduce((s, p) => s + Number(p.impacto_usd ?? 0), 0), true)} icono={<TrendingUp size={18} />} color="var(--acento-2)" retraso={0.15} />
      </div>
      <Pestanas id="val" valor={pestana} cambiar={setPestana} opciones={[{ valor: 'pendientes', texto: 'Bandeja', contador: lista.length }, { valor: 'historial', texto: 'Validaciones realizadas' }]} />
      {pestana === 'pendientes' ? (
        !pendientes ? <Cargando /> : lista.length === 0 ? <Tarjeta><Vacio texto="No hay evaluaciones pendientes de validación" icono={<BadgeCheck size={36} />} /></Tarjeta> : (
          <div className="rejilla validacion-rejilla">
            <div className="columna" style={{ gap: 10 }}>
              {lista.map((p, k) => (
                <motion.div key={p.id_evaluacion} className="tarjeta" initial={{ opacity: 0, x: -12 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: k * 0.05 }}
                  onClick={() => elegir(p.id_evaluacion)} onKeyDown={(e) => e.key === 'Enter' && elegir(p.id_evaluacion)} tabIndex={0} role="button" aria-pressed={elegida === p.id_evaluacion} style={{ padding: 14, cursor: 'pointer', borderColor: elegida === p.id_evaluacion ? 'var(--acento)' : undefined, boxShadow: elegida === p.id_evaluacion ? 'var(--brillo)' : undefined }}>
                  <div className="fila entre"><span className="codigo">{p.codigo}</span><ChipNivel nivel={p.nivel} valor={p.valor} /></div>
                  <div style={{ fontWeight: 600, margin: '6px 0 4px', fontSize: 13 }}>{p.titulo}</div>
                  <div className="pequeno tenue"><Bandera codigo={p.codigo_pais} /> {p.filial} · {usd(p.impacto_usd, true)}</div>
                  <div className="fila" style={{ marginTop: 8, gap: 6 }}>
                    {p.pospuesta
                      ? <span className="chip" style={{ color: 'var(--medio)' }}><CirclePause size={11} /> En pausa · {p.dias_habiles_transcurridos} de 3 días</span>
                      : <span className="chip" style={{ color: p.fuera_de_plazo ? 'var(--critico)' : 'var(--bajo)' }}><CalendarClock size={11} /> {p.dias_habiles_transcurridos} de 3 días hábiles</span>}
                    {p.conflicto && <span className="chip" style={{ color: 'var(--alto)' }}><UserX size={11} /> RN17</span>}
                  </div>
                </motion.div>
              ))}
            </div>
            {elegida && <div id="panel-validacion" style={{ scrollMarginTop: 80 }}><PanelValidacion key={elegida} id={elegida} alValidar={() => { setElegida(null); recargar(); recargarH(); }} alCambiarPlazo={recargar} /></div>}
          </div>
        )
      ) : (
        <Tarjeta style={{ padding: 0 }}>
          <Tabla filas={historial ?? []} clave={(v) => v.id_validacion}
            columnas={[
              { clave: 'fecha', titulo: 'Fecha', render: (v) => fechaHora(v.fecha) },
              { clave: 'codigo', titulo: 'Riesgo', render: (v) => <><span className="codigo">{v.codigo}</span><div className="pequeno">{v.titulo}</div></> },
              { clave: 'filial', titulo: 'Filial', render: (v) => <><Bandera codigo={v.codigo_pais} /> {v.filial}</> },
              { clave: 'resultado', titulo: 'Resultado', render: (v) => <ChipEstado estado={v.resultado} /> },
              { clave: 'impacto_usd_declarado', titulo: 'Declarado', clase: 'num', render: (v) => usd(v.impacto_usd_declarado, true) },
              { clave: 'impacto_usd_validado', titulo: 'Validado', clase: 'num', render: (v) => usd(v.impacto_usd_validado, true) },
              { clave: 'auditor', titulo: 'Auditor' },
              { clave: 'observacion', titulo: 'Observación', render: (v) => <span className="pequeno tenue">{v.observacion ?? '—'}</span> },
            ]} />
        </Tarjeta>
      )}
    </>
  );
}

function PanelValidacion({ id, alValidar, alCambiarPlazo }: { id: number; alValidar: () => void; alCambiarPlazo: () => void }) {
  const { avisar, error } = useAvisos();
  const { datos: d, recargar } = useDatos<Fila>(`/api/validacion-financiera/pendientes/${id}`);
  const [observacion, setObservacion] = useState('');
  const [enviando, setEnviando] = useState<string | null>(null);
  const [posponiendo, setPosponiendo] = useState(false);
  const [motivo, setMotivo] = useState('');
  useAlEvento(['VALIDACION'], recargar);
  if (!d) return <Cargando filas={5} />;
  // CU17, excepción «ERP no disponible»: posponer deja el plazo de 3 días hábiles en pausa
  const cambiarPlazo = async (accion: 'posponer' | 'reanudar') => {
    setEnviando(accion);
    try {
      const r = await api.post(`/api/validacion-financiera/${id}/${accion}`, accion === 'posponer' ? { motivo } : {});
      if (accion === 'posponer') {
        avisar('alerta', 'Validación pospuesta', `${e.codigo}: el plazo queda en pausa y se registró la incidencia del ERP. Se avisó al Gestor y al Director.`);
        setPosponiendo(false);
        setMotivo('');
      } else {
        avisar('exito', 'Plazo reanudado', `${e.codigo}: la nueva fecha límite es el ${fecha(r.fecha_limite)}.`);
      }
      recargar();
      alCambiarPlazo();
    } catch (x) {
      error(x);
    } finally {
      setEnviando(null);
    }
  };
  const e = d.evaluacion;
  const diferencia = e.impacto_usd && e.impacto_usd_erp ? ((Number(e.impacto_usd) - Number(e.impacto_usd_erp)) / Number(e.impacto_usd_erp)) * 100 : 0;
  const validar = async (resultado: 'VALIDADA' | 'OBSERVADA') => {
    setEnviando(resultado);
    try {
      const r = await api.post(`/api/validacion-financiera/${id}`, { resultado, observacion });
      avisar(resultado === 'VALIDADA' ? 'exito' : 'alerta', `Impacto ${etiqueta(resultado).toLowerCase()}`, `${e.codigo}: ${usd(r.impacto_usd_validado)} con TC ${numero(r.tipo_cambio, 4)}. Se notificó al Gestor y al Director.`);
      alValidar();
    } catch (x) {
      error(x);
    } finally {
      setEnviando(null);
    }
  };
  return (
    <Tarjeta brillante>
      <div className="fila entre">
        <div><span className="codigo">{e.codigo}</span><h2 style={{ marginTop: 4 }}>{e.titulo}</h2><div className="pequeno tenue"><Bandera codigo={e.codigo_pais} /> {e.filial} · evaluado por {e.evaluador} el {fecha(e.fecha)} · responsable {e.responsable}</div></div>
        <ChipNivel nivel={e.nivel} valor={e.valor} />
      </div>
      {e.conflicto && <div className="item-verificacion no" style={{ marginTop: 14 }}><ShieldX size={20} color="var(--critico)" /><div className="pequeno"><strong>Conflicto de funciones (RN17).</strong> Usted es responsable o evaluador de este riesgo: otro Auditor Financiero debe validarlo. Si lo intenta, el trigger chk_independencia lo rechazará.</div></div>}
      {e.pospuesta && (
        <div className="item-verificacion pausa" style={{ marginTop: 14 }}>
          <CirclePause size={20} color="var(--medio)" />
          <div className="pequeno crece">
            <strong>Validación pospuesta: ERP no disponible (CU17).</strong> El plazo de 3 días hábiles está en pausa desde el {fechaHora(e.pospuesta_desde)} ({e.pospuesta_por}). Motivo: {e.motivo_posposicion}
            <div className="tenue" style={{ marginTop: 4 }}>Se reanuda al resolver la incidencia del ERP en <em>Integraciones</em>, con este botón o al validar.</div>
          </div>
          {!e.conflicto && <button className="boton chico" disabled={!!enviando} onClick={() => cambiarPlazo('reanudar')}>{enviando === 'reanudar' ? <Girando /> : <CirclePlay size={14} />} Reanudar plazo</button>}
        </div>
      )}
      <div className="rejilla r-3" style={{ marginTop: 16 }}>
        <div className="tarjeta" style={{ padding: 14 }}><div className="pequeno tenue">Impacto declarado</div><div style={{ fontSize: 22, fontWeight: 700, fontFamily: 'var(--fuente-titulo)' }}>{usd(e.impacto_usd)}</div><div className="pequeno tenue">{numero(e.impacto_financiero)} {e.moneda} · TC {numero(e.tipo_cambio, 4)}</div></div>
        <div className="tarjeta" style={{ padding: 14 }}><div className="pequeno tenue">Contraste con el ERP</div><div style={{ fontSize: 22, fontWeight: 700, fontFamily: 'var(--fuente-titulo)' }}>{usd(e.impacto_usd_erp)}</div><div className="pequeno tenue">TC del ERP al {fecha(e.fecha)}: {numero(e.tipo_cambio_erp, 4)}</div></div>
        <div className="tarjeta" style={{ padding: 14, borderColor: Math.abs(diferencia) > 2 ? 'var(--critico)' : 'var(--bajo)' }}><div className="pequeno tenue">Diferencia</div><div style={{ fontSize: 22, fontWeight: 700, fontFamily: 'var(--fuente-titulo)', color: Math.abs(diferencia) > 2 ? 'var(--critico)' : 'var(--bajo)' }}>{numero(diferencia, 2)} %</div><div className="pequeno tenue">{e.pospuesta ? 'plazo en pausa' : `plazo hasta ${fecha(e.fecha_limite)}`}{Number(e.dias_en_pausa) > 0 ? ` · ${e.dias_en_pausa} día(s) en pausa` : ''}</div></div>
      </div>
      <h3 style={{ margin: '18px 0 8px' }}>Tipo de cambio {e.moneda}/USD alrededor de la fecha de evaluación</h3>
      {d.serie_tipo_cambio.length > 1 ? <GraficoArea alto={170} formato={(v) => numero(v, 3)} etiquetas={d.serie_tipo_cambio.map((x: Fila) => x.fecha.slice(5))} series={[{ nombre: `${e.moneda} por USD`, color: '#22d3ee', datos: d.serie_tipo_cambio.map((x: Fila) => Number(x.unidades_por_usd)) }]} />
        : <p className="pequeno tenue">Moneda con tipo de cambio fijo o en dólares.</p>}
      {d.kris_financieros.length > 0 && (
        <div className="fila" style={{ marginTop: 10 }}>
          {d.kris_financieros.map((k: Fila) => <span key={k.id_kri} className="chip"><Eye size={11} /> {k.nombre}: {numero(k.valor)} {k.unidad}</span>)}
        </div>
      )}
      <Campo etiqueta="Observación" ayuda="Obligatoria si observa el impacto: el Gestor deberá reevaluar el riesgo (CU02)" className="">
        <textarea className="entrada" style={{ marginTop: 14 }} value={observacion} onChange={(x) => setObservacion(x.target.value)} placeholder="Fuentes revisadas, ajustes y conclusión" />
      </Campo>
      <div className="fila" style={{ marginTop: 14, justifyContent: 'flex-end' }}>
        {!e.pospuesta && !e.conflicto && !e.fuera_de_plazo && (
          <button className="boton" style={{ marginRight: 'auto' }} disabled={!!enviando} onClick={() => setPosponiendo(true)}
            title="CU17: si el ERP no responde, el plazo de 3 días hábiles queda en pausa"><ServerOff size={16} /> Posponer: ERP no disponible</button>
        )}
        <button className="boton peligro" disabled={!!enviando || observacion.trim().length < 5} onClick={() => validar('OBSERVADA')}>{enviando === 'OBSERVADA' ? <Girando /> : <AlertTriangle size={16} />} Observar</button>
        <button className="boton primario" disabled={!!enviando} onClick={() => validar('VALIDADA')}>{enviando === 'VALIDADA' ? <Girando /> : <BadgeCheck size={16} />} Validar impacto</button>
      </div>
      {d.historial.length > 0 && <div className="pequeno tenue" style={{ marginTop: 10 }}>Validaciones anteriores del riesgo: {d.historial.map((h: Fila) => `${etiqueta(h.resultado)} (${fecha(h.fecha)})`).join(' · ')}</div>}
      <div style={{ display: 'none' }}><ChipEstado estado="PENDIENTE" /></div>
      <Modal abierto={posponiendo} cerrar={() => setPosponiendo(false)} icono={<ServerOff size={20} />}
        titulo="Posponer la validación" subtitulo={`${e.codigo} · CU17, excepción «ERP no disponible»`}
        pie={<>
          <button className="boton" onClick={() => setPosponiendo(false)}>Cancelar</button>
          <button className="boton primario" disabled={!!enviando || motivo.trim().length < 10} onClick={() => cambiarPlazo('posponer')}>
            {enviando === 'posponer' ? <Girando /> : <CirclePause size={16} />} Posponer y pausar el plazo
          </button>
        </>}>
        <p className="pequeno" style={{ marginTop: 0 }}>
          El plazo de 3 días hábiles (RN16) queda en pausa: hoy le quedan <strong>{Math.max(0, 3 - Number(e.dias_habiles_transcurridos))}</strong> día(s) y los conservará
          cuando el ERP vuelva a responder. Se registra una incidencia del ERP en <em>Integraciones</em> y se avisa al Gestor y al Director.
        </p>
        <Campo etiqueta="¿Qué falla en el ERP?" ayuda="Mínimo 10 caracteres; queda en la bitácora (RN13)">
          <textarea className="entrada" autoFocus value={motivo} maxLength={300} onChange={(x) => setMotivo(x.target.value)}
            placeholder={`Por ejemplo: el ERP no devuelve el tipo de cambio ${e.moneda}/USD del ${fecha(e.fecha)}`} />
        </Campo>
      </Modal>
    </Tarjeta>
  );
}

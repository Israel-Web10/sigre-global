import { useState } from 'react';
import { motion } from 'framer-motion';
import { CheckCircle2, FilePlus2, GitCompareArrows, PlayCircle, XCircle } from 'lucide-react';
import { api, conParametros, type Fila } from '../nucleo/api';
import { useAvisos, useDatos, useSesion } from '../nucleo/contexto';
import { etiqueta, fechaHora, numero, sumarDias } from '../nucleo/formato';
import { Bandera, Campo, Cargando, ChipEstado, Encabezado, Girando, Kpi, Modal, Tarjeta, Vacio } from '../componentes/Ui';

/** CU18: conciliación mensual de los KRI financieros con el cierre del ERP (tolerancia RN18). */
export default function Conciliacion() {
  const { tiene } = useSesion();
  const { avisar, error } = useAvisos();
  const { datos: periodos } = useDatos<string[]>('/api/conciliacion/periodos');
  const [periodo, setPeriodo] = useState('');
  const per = periodo || periodos?.[0] || '';
  const { datos: d, recargar } = useDatos<Fila>(per ? conParametros('/api/conciliacion', { periodo: per }) : null, [per]);
  const [ejecutando, setEjecutando] = useState(false);
  const [resultado, setResultado] = useState<Fila | null>(null);
  const [hallazgo, setHallazgo] = useState<Fila | null>(null);
  const filas = d?.indicadores ?? [];
  const ejecutar = async () => {
    setEjecutando(true);
    try {
      const r = await api.post('/api/conciliacion/ejecutar', { periodo: per });
      setResultado(r);
      avisar(r.con_diferencia ? 'alerta' : 'exito', `Conciliación ${r.periodo}`, `${r.conciliadas} conciliadas · ${r.con_diferencia} con diferencia · ${r.sin_datos} sin datos`);
      recargar();
    } catch (e) {
      error(e);
    } finally {
      setEjecutando(false);
    }
  };
  const tol = Number(d?.tolerancia_pct ?? 2);
  return (
    <>
      <Encabezado icono={<GitCompareArrows size={22} />} titulo="Conciliación con el ERP (CU18)"
        texto={`Compara el valor de cada KRI financiero del sistema con el cierre contable del ERP. Si la diferencia supera la tolerancia de ${numero(tol)} % queda CON_DIFERENCIA, se genera una alerta y se registra el hallazgo financiero (RN18).`}>
        <select className="entrada" style={{ width: 140 }} aria-label="Período" value={per} onChange={(e) => setPeriodo(e.target.value)}>
          {(periodos ?? []).map((p) => <option key={p}>{p}</option>)}
        </select>
        {tiene('AUDITOR_FINANCIERO') && <button className="boton primario" disabled={ejecutando} onClick={ejecutar}>{ejecutando ? <Girando /> : <PlayCircle size={16} />} Ejecutar conciliación</button>}
      </Encabezado>
      <div className="rejilla r-4" style={{ marginBottom: 16 }}>
        <Kpi titulo="KRI financieros" valor={filas.length} icono={<GitCompareArrows size={18} />} />
        <Kpi titulo="Conciliados" valor={filas.filter((f: Fila) => f.estado === 'CONCILIADA').length} icono={<CheckCircle2 size={18} />} color="var(--bajo)" retraso={0.05} />
        <Kpi titulo="Con diferencia" valor={filas.filter((f: Fila) => f.estado === 'CON_DIFERENCIA').length} icono={<XCircle size={18} />} color="var(--critico)" retraso={0.1} />
        <Kpi titulo="Por conciliar" valor={filas.filter((f: Fila) => !f.estado).length} icono={<PlayCircle size={18} />} color="var(--medio)" retraso={0.15} />
      </div>
      {!d ? <Cargando /> : filas.length === 0 ? <Vacio texto="Sin indicadores financieros" /> : (
        <div className="rejilla r-auto">
          {filas.map((f: Fila, k: number) => {
            const sis = Number(f.valor_sistema ?? f.valor_sistema_actual ?? 0);
            const erp = Number(f.valor_erp ?? 0);
            const dif = f.diferencia_pct != null ? Number(f.diferencia_pct) : erp ? (Math.abs(sis - erp) / Math.abs(erp)) * 100 : null;
            const max = Math.max(Math.abs(sis), Math.abs(erp), 0.0001);
            const fuera = dif != null && dif > tol;
            return (
              <Tarjeta key={f.id_kri} inclinar retraso={k * 0.04} style={{ borderTop: `3px solid ${!f.estado ? 'var(--borde-fuerte)' : fuera ? 'var(--critico)' : 'var(--bajo)'}` }}>
                <div className="fila entre"><strong>{f.nombre}</strong>{f.estado ? <ChipEstado estado={f.estado} /> : <span className="chip">Pendiente</span>}</div>
                <div className="pequeno tenue"><Bandera codigo={f.codigo_pais} /> {f.filial} · {f.codigo}</div>
                <div className="columna" style={{ gap: 8, margin: '14px 0' }}>
                  {[['Sistema', sis, '#22d3ee'], ['ERP', erp, '#a855f7']].map(([t, v, c]) => (
                    <div key={t as string}>
                      <div className="fila entre pequeno"><span>{t}</span><strong>{f.valor_erp == null && t === 'ERP' ? 'sin cierre' : `${numero(v)} ${f.unidad}`}</strong></div>
                      <div className="barra-avance"><motion.span initial={{ width: 0 }} animate={{ width: `${(Math.abs(Number(v)) / max) * 100}%` }} transition={{ duration: 0.8 }} style={{ background: c as string }} /></div>
                    </div>
                  ))}
                </div>
                <div className="fila entre">
                  <span className="pequeno tenue">Diferencia</span>
                  <strong style={{ fontSize: 20, fontFamily: 'var(--fuente-titulo)', color: dif == null ? undefined : fuera ? 'var(--critico)' : 'var(--bajo)' }}>{dif == null ? '—' : `${numero(dif, 2)} %`}</strong>
                </div>
                {f.fecha_conciliacion && <div className="pequeno tenue">{f.auditor} · {fechaHora(f.fecha_conciliacion)}</div>}
                {f.estado === 'CON_DIFERENCIA' && (f.hallazgo_registrado
                  ? <div className="item-verificacion ok pequeno" style={{ marginTop: 10 }}><CheckCircle2 size={15} /> Hallazgo financiero registrado</div>
                  : tiene('AUDITOR_FINANCIERO') && <button className="boton peligro bloque" style={{ marginTop: 10 }} onClick={() => setHallazgo(f)}><FilePlus2 size={15} /> Registrar hallazgo (O7)</button>)}
              </Tarjeta>
            );
          })}
        </div>
      )}
      <Modal abierto={!!resultado} cerrar={() => setResultado(null)} icono={<GitCompareArrows size={20} />} titulo={`Resultado de la conciliación ${resultado?.periodo ?? ''}`}>
        {resultado && <div className="columna" style={{ gap: 6 }}>{resultado.detalle.length === 0 ? <span className="tenue">No había indicadores por conciliar en el período.</span> : resultado.detalle.map((x: string) => (
          <div key={x} className={`item-verificacion ${x.includes('CON_DIFERENCIA') ? 'no' : x.includes('CONCILIADA') ? 'ok' : ''}`} style={{ padding: 10 }}><span className="pequeno">{x}</span></div>
        ))}</div>}
      </Modal>
      {hallazgo && <ModalHallazgo f={hallazgo} cerrar={() => setHallazgo(null)} alGuardar={recargar} />}
    </>
  );
}

function ModalHallazgo({ f, cerrar, alGuardar }: { f: Fila; cerrar: () => void; alGuardar: () => void }) {
  const { avisar, error } = useAvisos();
  const [h, setH] = useState<Fila>({
    severidad: 'MEDIA', fechaLimite: sumarDias(30),
    descripcion: `El valor de «${f.nombre}» del sistema (${numero(f.valor_sistema)} ${f.unidad}) difiere ${numero(f.diferencia_pct, 2)} % del cierre del ERP (${numero(f.valor_erp)} ${f.unidad}).`,
    recomendacion: 'Revisar la fuente de datos del indicador y conciliar con el área contable.',
  });
  const [enviando, setEnviando] = useState(false);
  const guardar = async () => {
    setEnviando(true);
    try {
      await api.post(`/api/conciliacion/${f.id_conciliacion}/hallazgo`, h);
      avisar('exito', 'Hallazgo financiero registrado', 'La alerta de la diferencia quedó atendida.');
      alGuardar();
      cerrar();
    } catch (e) {
      error(e);
    } finally {
      setEnviando(false);
    }
  };
  return (
    <Modal abierto cerrar={cerrar} icono={<FilePlus2 size={20} />} titulo="Registrar hallazgo financiero" subtitulo={`${f.filial} · ${f.nombre} · ${f.codigo}`}
      pie={<><button className="boton" onClick={cerrar}>Cancelar</button><button className="boton primario" disabled={enviando} onClick={guardar}>{enviando ? <Girando /> : <FilePlus2 size={16} />} Registrar</button></>}>
      <div className="columna" style={{ gap: 12 }}>
        <Campo etiqueta="Descripción"><textarea className="entrada" value={h.descripcion} onChange={(e) => setH({ ...h, descripcion: e.target.value })} /></Campo>
        <Campo etiqueta="Recomendación"><textarea className="entrada" value={h.recomendacion} onChange={(e) => setH({ ...h, recomendacion: e.target.value })} /></Campo>
        <div className="rejilla r-2">
          <Campo etiqueta="Severidad"><select className="entrada" value={h.severidad} onChange={(e) => setH({ ...h, severidad: e.target.value })}>{['ALTA', 'MEDIA', 'BAJA'].map((s) => <option key={s} value={s}>{etiqueta(s)}</option>)}</select></Campo>
          <Campo etiqueta="Fecha límite"><input className="entrada" type="date" value={h.fechaLimite} onChange={(e) => setH({ ...h, fechaLimite: e.target.value })} /></Campo>
        </div>
        <Campo etiqueta="Plan de acción" ayuda={h.severidad === 'ALTA' ? 'Obligatorio para severidad Alta (RN08)' : undefined}><textarea className="entrada" value={h.planAccion ?? ''} onChange={(e) => setH({ ...h, planAccion: e.target.value })} /></Campo>
      </div>
    </Modal>
  );
}

import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { CalendarDays, CalendarPlus, ClipboardCheck, FilePlus2, Flag, Lock, Play, SearchCheck } from 'lucide-react';
import { api, conParametros, type Fila } from '../nucleo/api';
import { useAvisos, useDatos, useSesion } from '../nucleo/contexto';
import { etiqueta, fecha, sumarDias } from '../nucleo/formato';
import { Bandera, Campo, Cargando, ChipEstado, ChipSeveridad, Encabezado, Girando, Kpi, Modal, Pestanas, Tabla, Tarjeta, Vacio } from '../componentes/Ui';
import { CATEGORIAS } from './Riesgos';

const MESES = ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic'];
const COLOR_TIPO: Record<string, string> = { FINANCIERA: '#22d3ee', OPERATIVA: '#f97316', CUMPLIMIENTO: '#a855f7', TECNOLOGICA: '#6366f1' };
const sev = (s: string) => (s === 'ALTA' ? 'CRITICA' : s === 'MEDIA' ? 'ADVERTENCIA' : 'PREVENTIVA');

/** CU10 (plan anual de auditorías) y CU11 (hallazgos con RN08). */
export default function Auditorias() {
  const { tiene } = useSesion();
  const anio = new Date().getFullYear();
  const [pestana, setPestana] = useState<'plan' | 'hallazgos'>('plan');
  const { datos: auditorias, recargar } = useDatos<Fila[]>(conParametros('/api/auditorias', { anio }));
  const { datos: hallazgos, recargar: recargarH } = useDatos<Fila[]>('/api/hallazgos');
  const [nueva, setNueva] = useState(false);
  const [elegida, setElegida] = useState<number | null>(null);
  const lista = auditorias ?? [];
  const abiertos = (hallazgos ?? []).filter((h) => h.estado !== 'CERRADO');

  return (
    <>
      <Encabezado icono={<ClipboardCheck size={22} />} titulo={`Plan anual de auditorías ${anio}`}
        texto="Planificación con control de disponibilidad del auditor (CU10) y registro de hallazgos: los de severidad Alta exigen riesgo vinculado y plan de acción (RN08).">
        {tiene('AUDITOR_INTERNO') && <button className="boton primario" onClick={() => setNueva(true)}><CalendarPlus size={16} /> Planificar auditoría</button>}
      </Encabezado>
      <div className="rejilla r-4" style={{ marginBottom: 16 }}>
        <Kpi titulo="Auditorías del año" valor={lista.length} icono={<CalendarDays size={18} />} />
        <Kpi titulo="En ejecución" valor={lista.filter((a) => a.estado === 'EN_EJECUCION').length} icono={<Play size={18} />} color="var(--acento-2)" retraso={0.05} />
        <Kpi titulo="Hallazgos abiertos" valor={abiertos.length} icono={<Flag size={18} />} color="var(--alto)" retraso={0.1} pie={<>{abiertos.filter((h) => h.vencido).length} vencidos</>} />
        <Kpi titulo="Severidad alta" valor={abiertos.filter((h) => h.severidad === 'ALTA').length} icono={<SearchCheck size={18} />} color="var(--critico)" retraso={0.15} pie="vinculados a un riesgo (RN08)" />
      </div>
      <Pestanas id="aud" valor={pestana} cambiar={setPestana} opciones={[{ valor: 'plan', texto: 'Cronograma' }, { valor: 'hallazgos', texto: 'Hallazgos', contador: abiertos.length }]} />
      {pestana === 'plan' ? (
        !auditorias ? <Cargando /> : (
          <Tarjeta>
            <div style={{ display: 'grid', gridTemplateColumns: '220px repeat(12, 1fr)', gap: 0, fontSize: 12 }}>
              <div />
              {MESES.map((m, k) => <div key={m} className="centrado tenue" style={{ padding: '4px 0', borderLeft: '1px solid var(--borde)', fontWeight: k === new Date().getMonth() ? 700 : 500, color: k === new Date().getMonth() ? 'var(--acento)' : undefined }}>{m}</div>)}
              {lista.map((a, k) => {
                const ini = new Date(`${a.fecha_inicio}T12:00:00`);
                const fin = new Date(`${a.fecha_fin}T12:00:00`);
                const pos = (d: Date) => (d.getMonth() + (d.getDate() - 1) / 31) / 12 * 100;
                return (
                  <div key={a.id_auditoria} style={{ display: 'contents' }}>
                    <div style={{ padding: '10px 8px', borderTop: '1px solid var(--borde)', cursor: 'pointer' }} onClick={() => setElegida(a.id_auditoria)}>
                      <div className="fila" style={{ gap: 6 }}><span className="codigo">{a.codigo}</span><ChipEstado estado={a.estado} /></div>
                      <div className="pequeno tenue"><Bandera codigo={a.codigo_pais} /> {a.filial} · {a.auditor}</div>
                    </div>
                    <div style={{ gridColumn: 'span 12', position: 'relative', borderTop: '1px solid var(--borde)' }}>
                      <motion.div initial={{ scaleX: 0 }} animate={{ scaleX: 1 }} transition={{ delay: k * 0.07, duration: 0.6 }} onClick={() => setElegida(a.id_auditoria)}
                        style={{ position: 'absolute', top: 14, height: 28, left: `${pos(ini)}%`, width: `${Math.max(2.5, pos(fin) - pos(ini))}%`, transformOrigin: 'left', borderRadius: 8, cursor: 'pointer',
                          background: `linear-gradient(90deg, ${COLOR_TIPO[a.tipo]}, ${COLOR_TIPO[a.tipo]}aa)`, boxShadow: `0 6px 18px -8px ${COLOR_TIPO[a.tipo]}`, color: '#fff', fontSize: 11, fontWeight: 700, padding: '6px 8px', whiteSpace: 'nowrap', overflow: 'hidden' }}>
                        {etiqueta(a.tipo)} · {a.hallazgos} hallazgo(s)
                      </motion.div>
                    </div>
                  </div>
                );
              })}
            </div>
            {lista.length === 0 && <Vacio texto="No hay auditorías planificadas" />}
          </Tarjeta>
        )
      ) : (
        <TablaHallazgos hallazgos={hallazgos ?? []} recargar={recargarH} />
      )}
      <ModalPlanificar abierto={nueva} cerrar={() => setNueva(false)} alCrear={recargar} />
      {elegida && <DetalleAuditoria id={elegida} cerrar={() => setElegida(null)} alCambiar={() => { recargar(); recargarH(); }} />}
    </>
  );
}

function TablaHallazgos({ hallazgos, recargar }: { hallazgos: Fila[]; recargar: () => void }) {
  const ir = useNavigate();
  const { tiene, usuario } = useSesion();
  const { error } = useAvisos();
  const cambiar = (h: Fila, estado: string) => api.put(`/api/hallazgos/${h.id_hallazgo}`, { estado }).then(recargar).catch(error);
  return (
    <Tarjeta style={{ padding: 0 }}>
      <Tabla filas={hallazgos} clave={(h) => h.id_hallazgo} vacio="Sin hallazgos"
        columnas={[
          { clave: 'auditoria', titulo: 'Origen', render: (h) => h.auditoria ? <span className="codigo">{h.auditoria}</span> : <span className="chip" style={{ color: 'var(--acento)' }}>Conciliación {h.periodo_conciliacion}</span> },
          { clave: 'descripcion', titulo: 'Hallazgo', render: (h) => <div style={{ maxWidth: 360 }}><div style={{ fontWeight: 600 }}>{h.descripcion}</div><div className="pequeno tenue">{h.recomendacion}</div></div> },
          { clave: 'severidad', titulo: 'Severidad', render: (h) => <ChipSeveridad s={sev(h.severidad)} /> },
          { clave: 'riesgo_codigo', titulo: 'Riesgo', render: (h) => h.riesgo_codigo ? <span className="codigo" style={{ cursor: 'pointer' }} onClick={() => ir(`/riesgos/${h.id_riesgo}`)}>{h.riesgo_codigo}</span> : '—' },
          { clave: 'responsable', titulo: 'Responsable' },
          { clave: 'fecha_limite', titulo: 'Límite', render: (h) => <span style={{ color: h.vencido ? 'var(--critico)' : undefined }}>{fecha(h.fecha_limite)}</span> },
          { clave: 'estado', titulo: 'Estado', render: (h) => <ChipEstado estado={h.estado} /> },
          { clave: 'acc', titulo: '', render: (h) => h.estado === 'CERRADO' ? null : (
            <div className="fila" style={{ flexWrap: 'nowrap' }}>
              {h.estado === 'ABIERTO' && (h.id_responsable === usuario?.id || tiene('AUDITOR_INTERNO', 'AUDITOR_FINANCIERO')) && <button className="boton chico" onClick={() => cambiar(h, 'EN_PROCESO')}>En proceso</button>}
              {tiene('AUDITOR_INTERNO', 'AUDITOR_FINANCIERO') && <button className="boton chico exito" onClick={() => cambiar(h, 'CERRADO')}>Cerrar</button>}
            </div>
          ) },
        ]} />
    </Tarjeta>
  );
}

function ModalPlanificar({ abierto, cerrar, alCrear }: { abierto: boolean; cerrar: () => void; alCrear: () => void }) {
  const { avisar, error } = useAvisos();
  const { datos: cat } = useDatos<Fila>('/api/catalogos');
  const [f, setF] = useState<Fila>({ tipo: 'OPERATIVA', fechaInicio: sumarDias(10), fechaFin: sumarDias(24) });
  const { datos: auditores } = useDatos<Fila[]>(conParametros('/api/auditorias/auditores', { inicio: f.fechaInicio, fin: f.fechaFin }));
  const [enviando, setEnviando] = useState(false);
  const guardar = async () => {
    setEnviando(true);
    try {
      const r = await api.post('/api/auditorias', { ...f, idFilial: Number(f.idFilial), idAuditor: Number(f.idAuditor) });
      avisar('exito', `Auditoría ${r.codigo} planificada`, 'Se notificó al auditor asignado.');
      alCrear();
      cerrar();
    } catch (e) {
      error(e);
    } finally {
      setEnviando(false);
    }
  };
  return (
    <Modal abierto={abierto} cerrar={cerrar} icono={<CalendarPlus size={20} />} titulo="Planificar auditoría (CU10)" subtitulo="El código AUD-AAAA-### lo asigna la base de datos"
      pie={<><button className="boton" onClick={cerrar}>Cancelar</button><button className="boton primario" disabled={enviando || !f.idFilial || !f.idAuditor || (f.alcance ?? '').length < 10} onClick={guardar}>{enviando ? <Girando /> : <CalendarPlus size={16} />} Planificar</button></>}>
      <div className="rejilla r-2">
        <Campo etiqueta="Filial"><select className="entrada" value={f.idFilial ?? ''} onChange={(e) => setF({ ...f, idFilial: e.target.value })}><option value="">—</option>{(cat?.filiales ?? []).map((x: Fila) => <option key={x.id_filial} value={x.id_filial}>{x.nombre}</option>)}</select></Campo>
        <Campo etiqueta="Tipo"><select className="entrada" value={f.tipo} onChange={(e) => setF({ ...f, tipo: e.target.value })}>{Object.keys(COLOR_TIPO).map((t) => <option key={t} value={t}>{etiqueta(t)}</option>)}</select></Campo>
        <Campo etiqueta="Inicio"><input className="entrada" type="date" value={f.fechaInicio} onChange={(e) => setF({ ...f, fechaInicio: e.target.value })} /></Campo>
        <Campo etiqueta="Fin"><input className="entrada" type="date" value={f.fechaFin} onChange={(e) => setF({ ...f, fechaFin: e.target.value })} /></Campo>
      </div>
      <Campo etiqueta="Alcance" className=""><textarea className="entrada" value={f.alcance ?? ''} onChange={(e) => setF({ ...f, alcance: e.target.value })} placeholder="Procesos, periodos y controles a revisar" /></Campo>
      <div className="etiqueta-campo" style={{ margin: '14px 0 8px' }}>Auditor (disponibilidad en esas fechas)</div>
      <div className="columna" style={{ gap: 8 }}>
        {(auditores ?? []).map((a) => (
          <button key={a.id_usuario} type="button" disabled={!!a.conflicto} onClick={() => setF({ ...f, idAuditor: a.id_usuario })} className={`item-verificacion ${a.conflicto ? 'no' : f.idAuditor === a.id_usuario ? 'ok' : ''}`}
            style={{ cursor: a.conflicto ? 'not-allowed' : 'pointer', font: 'inherit', color: 'var(--texto)', textAlign: 'left' }}>
            <div className="crece"><strong>{a.nombre_completo}</strong><div className="pequeno tenue">{a.roles} · {a.carga_anual} auditoría(s) este año</div></div>
            <span className="pequeno" style={{ color: a.conflicto ? 'var(--critico)' : 'var(--bajo)' }}>{a.conflicto ? `Ocupado: ${a.conflicto}` : 'Disponible'}</span>
          </button>
        ))}
      </div>
    </Modal>
  );
}

function DetalleAuditoria({ id, cerrar, alCambiar }: { id: number; cerrar: () => void; alCambiar: () => void }) {
  const { tiene } = useSesion();
  const { avisar, error } = useAvisos();
  const { datos: d, recargar } = useDatos<Fila>(`/api/auditorias/${id}`);
  const { datos: cat } = useDatos<Fila>('/api/catalogos');
  const [h, setH] = useState<Fila>({ severidad: 'MEDIA', fechaLimite: sumarDias(45), crearRiesgo: false, categoriaRiesgo: 'OPERATIVO' });
  const [ocupado, setOcupado] = useState(false);
  const a = d?.auditoria;
  const responsables = useMemo(() => (cat?.usuarios ?? []).filter((u: Fila) => a && (u.corporativo || u.id_filial === a.id_filial)), [cat, a]);
  const hacer = async (f: () => Promise<any>, ok: (r: any) => string) => {
    setOcupado(true);
    try {
      avisar('exito', ok(await f()));
      recargar();
      alCambiar();
    } catch (e) {
      error(e);
    } finally {
      setOcupado(false);
    }
  };
  const puede = tiene('AUDITOR_INTERNO', 'AUDITOR_FINANCIERO');
  return (
    <Modal abierto cerrar={cerrar} ancho icono={<ClipboardCheck size={20} />} titulo={a ? `${a.codigo} · ${etiqueta(a.tipo)}` : 'Auditoría'} subtitulo={a ? `${a.filial} · ${fecha(a.fecha_inicio)} – ${fecha(a.fecha_fin)} · ${a.auditor}` : ''}
      pie={a && puede ? <>
        {a.estado === 'PLANIFICADA' && <button className="boton primario" disabled={ocupado} onClick={() => hacer(() => api.post(`/api/auditorias/${id}/iniciar`), () => 'Auditoría en ejecución')}><Play size={16} /> Iniciar</button>}
        {a.estado === 'EN_EJECUCION' && <button className="boton" disabled={ocupado} onClick={() => hacer(() => api.post(`/api/auditorias/${id}/cerrar`), () => 'Auditoría cerrada')}><Lock size={16} /> Cerrar auditoría</button>}
      </> : undefined}>
      {!d ? <Cargando /> : (
        <div className="columna" style={{ gap: 16 }}>
          <div className="fila"><ChipEstado estado={a.estado} /><span className="suave">{a.alcance}</span></div>
          <h3>Hallazgos ({d.hallazgos.length})</h3>
          {d.hallazgos.length === 0 && <span className="tenue pequeno">Sin hallazgos registrados</span>}
          {d.hallazgos.map((x: Fila) => (
            <div key={x.id_hallazgo} className="item-verificacion">
              <ChipSeveridad s={sev(x.severidad)} />
              <div className="crece pequeno"><strong>{x.descripcion}</strong><div className="tenue">{x.recomendacion} · {x.responsable} · límite {fecha(x.fecha_limite)}{x.riesgo_codigo ? ` · ${x.riesgo_codigo}` : ''}</div></div>
              <ChipEstado estado={x.estado} />
            </div>
          ))}
          {a.estado === 'EN_EJECUCION' && puede && (
            <div className="tarjeta" style={{ padding: 16 }}>
              <h3 style={{ marginBottom: 12 }}><FilePlus2 size={16} /> Registrar hallazgo (CU11)</h3>
              <div className="rejilla r-2">
                <Campo etiqueta="Descripción"><textarea className="entrada" value={h.descripcion ?? ''} onChange={(e) => setH({ ...h, descripcion: e.target.value })} /></Campo>
                <Campo etiqueta="Recomendación"><textarea className="entrada" value={h.recomendacion ?? ''} onChange={(e) => setH({ ...h, recomendacion: e.target.value })} /></Campo>
                <Campo etiqueta="Severidad"><select className="entrada" value={h.severidad} onChange={(e) => setH({ ...h, severidad: e.target.value })}>{['ALTA', 'MEDIA', 'BAJA'].map((s) => <option key={s} value={s}>{etiqueta(s)}</option>)}</select></Campo>
                <Campo etiqueta="Responsable"><select className="entrada" value={h.idResponsable ?? ''} onChange={(e) => setH({ ...h, idResponsable: Number(e.target.value) })}><option value="">—</option>{responsables.map((u: Fila) => <option key={u.id_usuario} value={u.id_usuario}>{u.nombre_completo}</option>)}</select></Campo>
                <Campo etiqueta="Fecha límite"><input className="entrada" type="date" value={h.fechaLimite} onChange={(e) => setH({ ...h, fechaLimite: e.target.value })} /></Campo>
                <Campo etiqueta="Riesgo vinculado" ayuda={h.severidad === 'ALTA' ? 'Obligatorio para severidad Alta (RN08)' : undefined}>
                  <select className="entrada" value={h.crearRiesgo ? 'nuevo' : h.idRiesgo ?? ''} onChange={(e) => setH({ ...h, crearRiesgo: e.target.value === 'nuevo', idRiesgo: e.target.value && e.target.value !== 'nuevo' ? Number(e.target.value) : null })}>
                    <option value="">Sin vincular</option><option value="nuevo">➕ Crear un riesgo nuevo desde el hallazgo</option>
                    {d.riesgos.map((r: Fila) => <option key={r.id_riesgo} value={r.id_riesgo}>{r.codigo} · {r.titulo}</option>)}
                  </select>
                </Campo>
                {h.crearRiesgo && <Campo etiqueta="Categoría del nuevo riesgo"><select className="entrada" value={h.categoriaRiesgo} onChange={(e) => setH({ ...h, categoriaRiesgo: e.target.value })}>{CATEGORIAS.map((c) => <option key={c.c} value={c.c}>{etiqueta(c.c)}</option>)}</select></Campo>}
                <Campo etiqueta="Plan de acción" ayuda={h.severidad === 'ALTA' ? 'Obligatorio para severidad Alta (RN08)' : undefined}><textarea className="entrada" value={h.planAccion ?? ''} onChange={(e) => setH({ ...h, planAccion: e.target.value })} /></Campo>
              </div>
              <button className="boton primario" style={{ marginTop: 12 }} disabled={ocupado} onClick={() => hacer(() => api.post(`/api/auditorias/${id}/hallazgos`, h), (r) => r.riesgo_creado ? `Hallazgo registrado y riesgo ${r.riesgo_creado} creado` : 'Hallazgo registrado')}>
                {ocupado ? <Girando /> : <FilePlus2 size={16} />} Registrar hallazgo
              </button>
            </div>
          )}
        </div>
      )}
    </Modal>
  );
}

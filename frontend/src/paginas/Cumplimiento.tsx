import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { BookOpenCheck, CalendarClock, CheckSquare, CloudDownload, FilePlus2, Gavel, Landmark, ListChecks, RefreshCcw, Scale, Square } from 'lucide-react';
import { api, conParametros, descargar, type Fila } from '../nucleo/api';
import { useAvisos, useDatos, useSesion } from '../nucleo/contexto';
import { etiqueta, fecha, numero } from '../nucleo/formato';
import { Dona } from '../componentes/Graficos';
import { Bandera, Campo, Cargando, ChipEstado, Encabezado, Girando, Kpi, Modal, Pestanas, Tabla, Tarjeta, Vacio, ZonaArrastre } from '../componentes/Ui';

/** CU12 (normativas y obligaciones, RN09) y CU13 (evaluación del cumplimiento, RN10). */
export default function Cumplimiento() {
  const { tiene } = useSesion();
  const { datos: normativas, recargar: recargarN } = useDatos<Fila[]>('/api/cumplimiento/normativas');
  const [normativa, setNormativa] = useState<number | null>(null);
  const [estado, setEstado] = useState('');
  const { datos: obligaciones, recargar } = useDatos<Fila[]>(conParametros('/api/cumplimiento/obligaciones', { normativa, estado }));
  const [pestana, setPestana] = useState<'obligaciones' | 'normativas' | 'servicio'>('obligaciones');
  const [elegida, setElegida] = useState<number | null>(null);
  const [nueva, setNueva] = useState<Fila | null>(null);
  const todas = obligaciones ?? [];
  const conteo = useMemo(() => {
    const c: Record<string, number> = { CUMPLE: 0, PARCIAL: 0, NO_CUMPLE: 0, PENDIENTE: 0 };
    todas.forEach((o) => c[o.estado_cumplimiento]++);
    return c;
  }, [todas]);
  const evaluadas = conteo.CUMPLE + conteo.PARCIAL + conteo.NO_CUMPLE;
  const actualizar = () => { recargar(); recargarN(); };

  return (
    <>
      <Encabezado icono={<BookOpenCheck size={22} />} titulo="Cumplimiento normativo (CU12 · CU13)"
        texto="Normativas por jurisdicción y obligaciones por filial. Las que vencen en 30 días generan un aviso (RN09) y una obligación que no se cumple se registra automáticamente como riesgo Legal (RN10)." />
      <div className="rejilla r-4" style={{ marginBottom: 16 }}>
        <Tarjeta className="fila" style={{ gap: 16 }}>
          <Dona tamano={104} grosor={14} datos={[
            { nombre: 'Cumple', valor: conteo.CUMPLE, color: '#22c55e' }, { nombre: 'Parcial', valor: conteo.PARCIAL, color: '#eab308' },
            { nombre: 'No cumple', valor: conteo.NO_CUMPLE, color: '#ef4444' }, { nombre: 'Pendiente', valor: conteo.PENDIENTE, color: '#64748b' }]}
            centro={<strong style={{ fontSize: 18 }}>{evaluadas ? numero((100 * conteo.CUMPLE) / evaluadas, 0) : 0} %</strong>} />
          <div className="pequeno columna" style={{ gap: 4 }}>
            <strong>Cumplimiento</strong>
            <span style={{ color: 'var(--bajo)' }}>● {conteo.CUMPLE} cumple</span><span style={{ color: 'var(--medio)' }}>● {conteo.PARCIAL} parcial</span>
            <span style={{ color: 'var(--critico)' }}>● {conteo.NO_CUMPLE} no cumple</span><span className="tenue">● {conteo.PENDIENTE} pendiente</span>
          </div>
        </Tarjeta>
        <Kpi titulo="Normativas" valor={(normativas ?? []).length} icono={<Landmark size={18} />} retraso={0.05} pie={`${new Set((normativas ?? []).map((n) => n.jurisdiccion)).size} jurisdicciones`} />
        <Kpi titulo="Vencen en 30 días" valor={todas.filter((o) => o.estado_cumplimiento === 'PENDIENTE' && o.dias_para_vencer <= 30).length} icono={<CalendarClock size={18} />} color="var(--alto)" retraso={0.1} pie="avisos preventivos (RN09)" />
        <Kpi titulo="Riesgos legales generados" valor={todas.filter((o) => o.riesgo_codigo).length} icono={<Gavel size={18} />} color="var(--critico)" retraso={0.15} pie="por incumplimiento (RN10)" />
      </div>
      <Pestanas id="cump" valor={pestana} cambiar={setPestana} opciones={[
        { valor: 'obligaciones', texto: 'Obligaciones', icono: <ListChecks size={15} />, contador: conteo.PENDIENTE },
        { valor: 'normativas', texto: 'Normativas', icono: <Scale size={15} /> },
        { valor: 'servicio', texto: 'Servicio normativo', icono: <CloudDownload size={15} /> },
      ]} />
      {pestana === 'obligaciones' && (
        <>
          <div className="fila" style={{ marginBottom: 12 }}>
            <select className="entrada" style={{ width: 320, maxWidth: '100%' }} aria-label="Filtrar por normativa" value={normativa ?? ''} onChange={(e) => setNormativa(e.target.value ? Number(e.target.value) : null)}>
              <option value="">Todas las normativas</option>{(normativas ?? []).map((n) => <option key={n.id_normativa} value={n.id_normativa}>{n.jurisdiccion} · {n.nombre}</option>)}
            </select>
            <select className="entrada" style={{ width: 170 }} aria-label="Filtrar por estado" value={estado} onChange={(e) => setEstado(e.target.value)}>
              <option value="">Todo estado</option>{['PENDIENTE', 'CUMPLE', 'PARCIAL', 'NO_CUMPLE'].map((s) => <option key={s} value={s}>{etiqueta(s)}</option>)}
            </select>
          </div>
          <Tarjeta style={{ padding: 0 }}>
            {!obligaciones ? <div style={{ padding: 16 }}><Cargando /></div> : (
              <Tabla filas={todas} clave={(o) => o.id_obligacion} alClic={(o) => setElegida(o.id_obligacion)} vacio="Sin obligaciones"
                columnas={[
                  { clave: 'descripcion', titulo: 'Obligación', render: (o) => <div style={{ maxWidth: 340 }}><div style={{ fontWeight: 600 }}>{o.descripcion}</div><div className="pequeno tenue">{o.normativa} · {o.organismo}</div></div> },
                  { clave: 'filial', titulo: 'Filial', render: (o) => <span style={{ whiteSpace: 'nowrap' }}><Bandera codigo={o.codigo_pais} /> {o.filial}</span> },
                  { clave: 'periodicidad', titulo: 'Periodicidad', render: (o) => etiqueta(o.periodicidad) },
                  { clave: 'dias_para_vencer', titulo: 'Vence', orden: (o) => Number(o.dias_para_vencer), render: (o) => (
                    <div><div>{fecha(o.fecha_vencimiento)}</div>{o.estado_cumplimiento === 'PENDIENTE' && <div className="pequeno" style={{ color: o.dias_para_vencer < 0 ? 'var(--critico)' : o.dias_para_vencer <= 30 ? 'var(--alto)' : 'var(--texto-3)' }}>{o.dias_para_vencer < 0 ? `vencida hace ${-o.dias_para_vencer} d` : `en ${o.dias_para_vencer} d`}</div>}</div>) },
                  { clave: 'estado_cumplimiento', titulo: 'Estado', render: (o) => <ChipEstado estado={o.estado_cumplimiento} /> },
                  { clave: 'riesgo_codigo', titulo: 'Riesgo legal', render: (o) => o.riesgo_codigo ? <span className="codigo">{o.riesgo_codigo}</span> : <span className="tenue">—</span> },
                  { clave: 'alertas', titulo: 'Avisos', clase: 'num', render: (o) => Number(o.alertas) > 0 ? <span className="contador rojo">{o.alertas}</span> : <span className="tenue">0</span> },
                ]} />
            )}
          </Tarjeta>
        </>
      )}
      {pestana === 'normativas' && (
        <div className="rejilla r-auto">
          {(normativas ?? []).map((n, k) => {
            const pct = n.porcentaje == null ? null : Number(n.porcentaje);
            return (
              <Tarjeta key={n.id_normativa} inclinar retraso={Math.min(k * 0.03, 0.4)}>
                <div className="fila entre"><span className="chip">{n.jurisdiccion}</span><span className="pequeno tenue">{n.organismo}</span></div>
                <h3 style={{ margin: '10px 0 6px', minHeight: 40 }}>{n.nombre}</h3>
                <p className="pequeno tenue" style={{ minHeight: 34 }}>{n.descripcion}</p>
                <div className="fila entre pequeno" style={{ marginTop: 8 }}><span>{n.obligaciones} obligaciones</span><strong style={{ color: pct == null ? undefined : pct >= 80 ? 'var(--bajo)' : pct >= 50 ? 'var(--medio)' : 'var(--critico)' }}>{pct == null ? 'sin evaluar' : `${numero(pct, 0)} %`}</strong></div>
                <div className="barra-avance"><motion.span initial={{ width: 0 }} animate={{ width: `${pct ?? 0}%` }} style={{ background: pct == null ? undefined : pct >= 80 ? 'var(--bajo)' : pct >= 50 ? 'var(--medio)' : 'var(--critico)' }} /></div>
                <div className="fila entre" style={{ marginTop: 10 }}>
                  <span className="pequeno tenue">Vigente desde {fecha(n.vigencia_desde)}</span>
                  {tiene('OFICIAL_CUMPLIMIENTO') && <button className="boton chico" onClick={() => setNueva(n)}><FilePlus2 size={13} /> Obligación</button>}
                </div>
              </Tarjeta>
            );
          })}
        </div>
      )}
      {pestana === 'servicio' && <ServicioNormativo alImportar={actualizar} />}
      {elegida && <DetalleObligacion id={elegida} cerrar={() => setElegida(null)} alCambiar={actualizar} />}
      {nueva && <ModalObligacion normativa={nueva} cerrar={() => setNueva(null)} alCrear={actualizar} />}
    </>
  );
}

function ServicioNormativo({ alImportar }: { alImportar: () => void }) {
  const { tiene } = useSesion();
  const { avisar, error } = useAvisos();
  const { datos, recargar } = useDatos<Fila[]>('/api/cumplimiento/servicio-normativo/novedades');
  const importar = async (c: string) => {
    try {
      await api.post(`/api/cumplimiento/servicio-normativo/${c}/importar`);
      avisar('exito', 'Normativa registrada (CU12)', 'Ahora puede definir sus obligaciones por filial.');
      recargar();
      alImportar();
    } catch (e) {
      error(e);
    }
  };
  return (
    <Tarjeta>
      <div className="tarjeta-cabecera"><h3><CloudDownload size={16} /> Novedades del servicio normativo externo</h3><p>Sistema externo del diagrama de contexto (simulado en la demostración)</p></div>
      <div className="columna" style={{ gap: 10 }}>
        {(datos ?? []).map((n, k) => (
          <motion.div key={n.codigo} className="item-verificacion" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: k * 0.06 }}>
            <Landmark size={20} color="var(--acento)" />
            <div className="crece">
              <div className="fila" style={{ gap: 8 }}><span className="codigo">{n.codigo}</span><strong>{n.nombre}</strong><span className="chip">{n.jurisdiccion} · {n.organismo}</span></div>
              <div className="pequeno suave">{n.descripcion} Vigencia: {fecha(n.vigencia)}.</div>
            </div>
            {n.registrada ? <span className="chip" style={{ color: 'var(--bajo)' }}>Registrada</span>
              : tiene('OFICIAL_CUMPLIMIENTO') && <button className="boton chico primario" onClick={() => importar(n.codigo)}>Registrar</button>}
          </motion.div>
        ))}
      </div>
    </Tarjeta>
  );
}

function ModalObligacion({ normativa, cerrar, alCrear }: { normativa: Fila; cerrar: () => void; alCrear: () => void }) {
  const { avisar, error } = useAvisos();
  const { datos: cat } = useDatos<Fila>('/api/catalogos');
  const [f, setF] = useState<Fila>({ periodicidad: 'MENSUAL' });
  const guardar = async () => {
    try {
      const r = await api.post(`/api/cumplimiento/normativas/${normativa.id_normativa}/obligaciones`, { ...f, idFilial: Number(f.idFilial) });
      avisar('exito', 'Obligación registrada', r.alerta_generada ? `Vence en ${r.dias_para_vencer} días: se generó el aviso RN09.` : `Vence en ${r.dias_para_vencer} días.`);
      alCrear();
      cerrar();
    } catch (e) {
      error(e);
    }
  };
  return (
    <Modal abierto cerrar={cerrar} icono={<FilePlus2 size={20} />} titulo="Registrar obligación (CU12)" subtitulo={normativa.nombre}
      pie={<><button className="boton" onClick={cerrar}>Cancelar</button><button className="boton primario" disabled={!f.idFilial || !f.fechaVencimiento || (f.descripcion ?? '').length < 5} onClick={guardar}>Registrar</button></>}>
      <div className="columna" style={{ gap: 12 }}>
        <Campo etiqueta="Descripción"><input className="entrada" value={f.descripcion ?? ''} onChange={(e) => setF({ ...f, descripcion: e.target.value })} /></Campo>
        <div className="rejilla r-3">
          <Campo etiqueta="Filial"><select className="entrada" value={f.idFilial ?? ''} onChange={(e) => setF({ ...f, idFilial: e.target.value })}><option value="">—</option>{(cat?.filiales ?? []).map((x: Fila) => <option key={x.id_filial} value={x.id_filial}>{x.nombre}</option>)}</select></Campo>
          <Campo etiqueta="Periodicidad"><select className="entrada" value={f.periodicidad} onChange={(e) => setF({ ...f, periodicidad: e.target.value })}>{['UNICA', 'MENSUAL', 'TRIMESTRAL', 'SEMESTRAL', 'ANUAL'].map((p) => <option key={p} value={p}>{etiqueta(p)}</option>)}</select></Campo>
          <Campo etiqueta="Vencimiento"><input className="entrada" type="date" value={f.fechaVencimiento ?? ''} onChange={(e) => setF({ ...f, fechaVencimiento: e.target.value })} /></Campo>
        </div>
      </div>
    </Modal>
  );
}

function DetalleObligacion({ id, cerrar, alCambiar }: { id: number; cerrar: () => void; alCambiar: () => void }) {
  const { tiene } = useSesion();
  const ir = useNavigate();
  const { avisar, error } = useAvisos();
  const { datos: d, recargar } = useDatos<Fila>(`/api/cumplimiento/obligaciones/${id}`);
  const [lista, setLista] = useState<Fila[] | null>(null);
  const [observacion, setObservacion] = useState('');
  const [archivo, setArchivo] = useState<File | null>(null);
  const [enviando, setEnviando] = useState(false);
  const items = lista ?? d?.lista_sugerida ?? [];
  const pct = items.length ? (100 * items.filter((i: Fila) => i.cumple).length) / items.length : 0;
  const resultado = pct >= 100 ? 'CUMPLE' : pct >= 50 ? 'PARCIAL' : 'NO_CUMPLE';
  const evaluar = async () => {
    setEnviando(true);
    try {
      const r = await api.post(`/api/cumplimiento/obligaciones/${id}/evaluaciones`, { observacion, listaVerificacion: items });
      if (archivo) {
        const fd = new FormData();
        fd.append('archivo', archivo);
        fd.append('descripcion', 'Evidencia de cumplimiento');
        await api.subir(`/api/cumplimiento/evaluaciones/${r.id_eval_cump}/evidencias`, fd);
      }
      if (r.riesgo_generado) avisar('alerta', `No cumple: se registró el riesgo legal ${r.riesgo_generado}`, 'Se generó una alerta crítica para el responsable (RN10).');
      else avisar('exito', `Evaluación registrada: ${etiqueta(r.resultado)} (${numero(r.porcentaje, 0)} %)`);
      setLista(null);
      setObservacion('');
      setArchivo(null);
      recargar();
      alCambiar();
    } catch (e) {
      error(e);
    } finally {
      setEnviando(false);
    }
  };
  const o = d?.obligacion;
  return (
    <Modal abierto cerrar={cerrar} ancho icono={<BookOpenCheck size={20} />} titulo={o?.descripcion ?? 'Obligación'} subtitulo={o ? `${o.normativa} · ${o.filial} · vence ${fecha(o.fecha_vencimiento)}` : ''}
      pie={o && tiene('OFICIAL_CUMPLIMIENTO') ? <>
        {o.periodicidad !== 'UNICA' && o.estado_cumplimiento !== 'PENDIENTE' && <button className="boton" onClick={() => api.post(`/api/cumplimiento/obligaciones/${id}/renovar`).then(() => { recargar(); alCambiar(); avisar('info', 'Obligación renovada al siguiente período'); }).catch(error)}><RefreshCcw size={15} /> Renovar período</button>}
        <button className="boton primario" disabled={enviando || (resultado === 'NO_CUMPLE' && observacion.trim().length < 5)} onClick={evaluar}>{enviando ? <Girando /> : <Gavel size={16} />} Registrar evaluación</button>
      </> : undefined}>
      {!d ? <Cargando /> : (
        <div className="rejilla r-2" style={{ gap: 20 }}>
          <div className="columna" style={{ gap: 10 }}>
            <div className="fila"><ChipEstado estado={o.estado_cumplimiento} />{o.riesgo_codigo && <span className="chip" style={{ color: 'var(--critico)', cursor: 'pointer' }} onClick={() => ir(`/riesgos/${o.id_riesgo_generado}`)}>Riesgo {o.riesgo_codigo}</span>}</div>
            {tiene('OFICIAL_CUMPLIMIENTO') && (
              <>
                <strong className="pequeno">Lista de verificación (CU13)</strong>
                {items.map((it: Fila, k: number) => (
                  <button key={k} type="button" className={`item-verificacion ${it.cumple ? 'ok' : ''}`} style={{ cursor: 'pointer', font: 'inherit', color: 'var(--texto)', textAlign: 'left' }}
                    onClick={() => setLista(items.map((x: Fila, j: number) => (j === k ? { ...x, cumple: !x.cumple } : x)))}>
                    {it.cumple ? <CheckSquare size={18} color="var(--bajo)" /> : <Square size={18} />}<span className="pequeno">{it.punto}</span>
                  </button>
                ))}
                <div className="fila entre"><span className="pequeno tenue">Resultado calculado</span><span className="fila"><strong>{numero(pct, 0)} %</strong><ChipEstado estado={resultado} /></span></div>
                <textarea className="entrada" placeholder={resultado === 'NO_CUMPLE' ? 'Describa la brecha (obligatorio: se registrará como riesgo Legal, RN10)' : 'Observación'} value={observacion} onChange={(e) => setObservacion(e.target.value)} />
                <ZonaArrastre alElegir={setArchivo} texto={archivo ? `📎 ${archivo.name}` : 'Adjunte la evidencia del cumplimiento'} />
              </>
            )}
          </div>
          <div className="columna" style={{ gap: 10 }}>
            <strong className="pequeno">Evaluaciones anteriores</strong>
            {d.evaluaciones.length === 0 && <span className="tenue pequeno">Sin evaluaciones</span>}
            <div className="linea-tiempo">
              {d.evaluaciones.map((e: Fila) => (
                <div key={e.id_eval_cump} className="hito" style={{ ['--c' as any]: e.resultado === 'CUMPLE' ? 'var(--bajo)' : e.resultado === 'PARCIAL' ? 'var(--medio)' : 'var(--critico)' }}>
                  <div className="fila"><ChipEstado estado={e.resultado} /><strong>{numero(e.porcentaje, 0)} %</strong></div>
                  <p>{fecha(e.fecha)} · {e.evaluador}{e.observacion ? ` · ${e.observacion}` : ''}</p>
                </div>
              ))}
            </div>
            {d.evidencias.length > 0 && <strong className="pequeno">Evidencias</strong>}
            {d.evidencias.map((ev: Fila) => (
              <button key={ev.id_evidencia} className="boton chico" style={{ justifyContent: 'flex-start' }} onClick={() => descargar(`/api/evidencias/${ev.id_evidencia}/archivo`, ev.archivo, true).catch(error)}>📎 {ev.archivo}</button>
            ))}
            {d.alertas.length > 0 && <strong className="pequeno">Avisos</strong>}
            {d.alertas.map((a: Fila) => <div key={a.id_alerta} className="item-verificacion pequeno"><ChipEstado estado={a.estado} /> {a.mensaje}</div>)}
          </div>
        </div>
      )}
      {!d && <Vacio texto="" />}
    </Modal>
  );
}

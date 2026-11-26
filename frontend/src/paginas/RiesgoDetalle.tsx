import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  AppWindow, ArrowLeft, Bell, Calculator, CheckCircle2, ClipboardList, FileText, Gauge, History, Lock, Plus, Send, ShieldAlert, Stamp, Target,
  Trash2, Wrench,
} from 'lucide-react';
import { api, descargar, type Fila } from '../nucleo/api';
import { useAlEvento, useAvisos, useDatos, useSesion } from '../nucleo/contexto';
import { colorSemaforo, etiqueta, fecha, fechaHora, haceCuanto, numero, operacion, sumarDias, usd } from '../nucleo/formato';
import { MapaCalor, Sparkline } from '../componentes/Graficos';
import { FlujoEstados, ModalAprobacion, ModalEvaluacion, VistaRapidaRiesgo } from '../componentes/Riesgo';
import {
  Bandera, BarraAvance, Campo, Cargando, ChipEstado, ChipNivel, ChipSeveridad, Girando, Pestanas, RelojSla, Semaforo, Tarjeta, Vacio,
} from '../componentes/Ui';
import { useVentanas } from '../componentes/Ventanas';
import { Diferencias } from './Bitacora';

const ESTRATEGIAS = [
  { e: 'EVITAR', d: 'Eliminar la actividad que origina el riesgo' },
  { e: 'REDUCIR', d: 'Disminuir la probabilidad o el impacto con acciones' },
  { e: 'TRANSFERIR', d: 'Trasladar el impacto a un tercero (seguro, contrato)' },
  { e: 'ACEPTAR', d: 'Asumir el riesgo con justificación (RN12, aprueba el Director)' },
];

export default function RiesgoDetalle() {
  const { id } = useParams();
  const [params] = useSearchParams();
  const ir = useNavigate();
  const { abrir } = useVentanas();
  const { datos: d, recargar, error: err } = useDatos<Fila>(`/api/riesgos/${id}`, [id]);
  const [pestana, setPestana] = useState<'resumen' | 'tratamiento' | 'kri' | 'alertas' | 'historial'>('resumen');
  const [evaluar, setEvaluar] = useState<'INHERENTE' | 'RESIDUAL' | null>(params.get('evaluar') ? 'INHERENTE' : null);
  const [aprobar, setAprobar] = useState(false);
  const { error, avisar } = useAvisos();
  useAlEvento(['EVALUACION', 'PLAN', 'ACCION', 'VALIDACION', 'ALERTA_GENERADA', 'MEDICION'], (e) => {
    if (Number(e.id_riesgo) === Number(id)) recargar();
  });

  if (err) return <Vacio texto={err.message}><button className="boton" onClick={() => ir('/riesgos')}>Volver al catálogo</button></Vacio>;
  if (!d) return <Cargando filas={6} alto={80} />;
  const r = d.riesgo;
  const pm = d.permisos;
  const evalVigente = d.evaluaciones.find((e: Fila) => e.tipo === 'INHERENTE');

  const cerrarRiesgo = async () => {
    try {
      await api.post(`/api/riesgos/${id}/cerrar`);
      avisar('exito', 'Riesgo cerrado', `${r.codigo} quedó CERRADO.`);
      recargar();
    } catch (e) {
      error(e);
    }
  };

  return (
    <div className="columna" style={{ gap: 16 }}>
      <div className="fila" style={{ gap: 8 }}>
        <Link to="/riesgos" className="boton chico fantasma"><ArrowLeft size={15} /> Catálogo</Link>
        <span className="tenue pequeno">/ {r.codigo}</span>
      </div>

      <Tarjeta brillante>
        <div className="fila" style={{ alignItems: 'flex-start', gap: 18 }}>
          <div className="crece" style={{ minWidth: 260 }}>
            <div className="fila" style={{ gap: 8 }}>
              <span className="codigo" style={{ fontSize: 14 }}>{r.codigo}</span>
              <span className="chip">{etiqueta(r.categoria)}</span>
              <ChipNivel nivel={r.nivel} valor={r.valor} />
              <ChipEstado estado={r.estado} />
              {r.origen !== 'MANUAL' && <span className="chip" style={{ color: 'var(--acento-3)' }}>Origen: {etiqueta(r.origen)}</span>}
            </div>
            <h1 style={{ margin: '10px 0 6px', fontSize: 24 }}>{r.titulo}</h1>
            <div className="suave pequeno"><Bandera codigo={r.codigo_pais} /> {r.filial} · Proceso: {r.proceso} · Responsable: <strong>{r.responsable}</strong> · Registrado el {fecha(r.fecha_registro)} por {r.registrador ?? 'Sistema'}</div>
          </div>
          <div className="fila" style={{ gap: 8 }}>
            <button className="boton" onClick={() => abrir({ id: `riesgo-${r.id_riesgo}`, titulo: `${r.codigo} · ${r.titulo}`, icono: <ShieldAlert size={15} />, contenido: <VistaRapidaRiesgo id={r.id_riesgo} />, ancho: 520, alto: 560 })}><AppWindow size={16} /> Ventana</button>
            {pm.evaluar && <button className="boton primario" onClick={() => setEvaluar('INHERENTE')}><Calculator size={16} /> Evaluar</button>}
            {pm.evaluarResidual && <button className="boton" onClick={() => setEvaluar('RESIDUAL')}><Target size={16} /> Evaluación residual</button>}
            {pm.aprobar && <button className="boton primario" onClick={() => setAprobar(true)}><Stamp size={16} /> Verificar y aprobar</button>}
            {pm.cerrar && <button className="boton exito" onClick={cerrarRiesgo}><Lock size={16} /> Cerrar riesgo</button>}
          </div>
        </div>
        <div className="sep" />
        <FlujoEstados estado={r.estado} />
      </Tarjeta>

      <div className="rejilla r-4">
        <Tarjeta className="kpi"><div className="kpi-cab">Nivel inherente</div><div className="kpi-valor" style={{ color: 'var(--texto)' }}>{r.valor ?? '—'}</div><div className="kpi-pie">P {r.probabilidad ?? '—'} × I {r.impacto ?? '—'} · apetito {r.apetito_riesgo}</div></Tarjeta>
        <Tarjeta className="kpi"><div className="kpi-cab">Impacto financiero</div><div className="kpi-valor" style={{ fontSize: 22 }}>{usd(r.impacto_usd, true)}</div><div className="kpi-pie">{r.impacto_financiero ? `${numero(r.impacto_financiero)} ${r.moneda} · TC ${numero(r.tipo_cambio, 4)}` : 'Sin estimación monetaria'}</div></Tarjeta>
        <Tarjeta className="kpi"><div className="kpi-cab">Validación financiera</div><div style={{ marginTop: 4 }}><ChipEstado estado={r.estado_validacion} /></div><div className="kpi-pie">{r.evaluacion_vigente === false ? 'Evaluación vencida (RN03)' : `Vigente hasta ${fecha(r.vigente_hasta)}`}</div></Tarjeta>
        <Tarjeta className="kpi"><div className="kpi-cab">Tratamiento</div><div className="kpi-valor" style={{ fontSize: 22 }}>{r.estrategia ? etiqueta(r.estrategia) : '—'}</div><div className="kpi-pie" style={{ width: '100%', display: 'block' }}>{r.estado_plan ? <><ChipEstado estado={r.estado_plan} /> <BarraAvance valor={Number(r.avance_plan ?? 0)} /></> : 'Sin plan'}</div></Tarjeta>
      </div>

      <Pestanas id="riesgo" valor={pestana} cambiar={setPestana} opciones={[
        { valor: 'resumen', texto: 'Resumen', icono: <FileText size={15} /> },
        { valor: 'tratamiento', texto: 'Tratamiento', icono: <Wrench size={15} />, contador: d.acciones.length },
        { valor: 'kri', texto: 'Indicadores', icono: <Gauge size={15} />, contador: d.kris.length },
        { valor: 'alertas', texto: 'Alertas y hallazgos', icono: <Bell size={15} />, contador: Number(r.alertas_abiertas) },
        { valor: 'historial', texto: 'Historial', icono: <History size={15} />, contador: d.historial.length },
      ]} />

      {pestana === 'resumen' && <Resumen d={d} recargar={recargar} />}
      {pestana === 'tratamiento' && <Tratamiento d={d} recargar={recargar} />}
      {pestana === 'kri' && (
        <div className="rejilla r-auto">
          {d.kris.length === 0 && <Vacio texto="Este riesgo no tiene indicadores KRI" />}
          {d.kris.map((k: Fila) => (
            <Tarjeta key={k.id_kri} inclinar>
              <div className="fila entre"><strong>{k.nombre}</strong><Semaforo valor={k.semaforo} /></div>
              <div className="pequeno tenue">{etiqueta(k.fuente)} · {etiqueta(k.frecuencia)} · {etiqueta(k.direccion)}</div>
              <div className="fila entre" style={{ marginTop: 12, alignItems: 'flex-end' }}>
                <div style={{ fontFamily: 'var(--fuente-titulo)', fontSize: 28, fontWeight: 700, color: colorSemaforo(k.semaforo) }}>{numero(k.ultimo_valor)} <span className="pequeno tenue">{k.unidad}</span></div>
                <Sparkline datos={(k.serie ?? []).map(Number)} color={colorSemaforo(k.semaforo)} umbral={[Number(k.umbral_amarillo), Number(k.umbral_rojo)]} />
              </div>
              <div className="pequeno tenue">Umbrales: amarillo {numero(k.umbral_amarillo)} · rojo {numero(k.umbral_rojo)} · {haceCuanto(k.fecha_medicion)}</div>
            </Tarjeta>
          ))}
        </div>
      )}
      {pestana === 'alertas' && (
        <div className="rejilla r-2">
          <Tarjeta>
            <div className="tarjeta-cabecera"><h3><Bell size={16} /> Alertas</h3></div>
            {d.alertas.length === 0 ? <Vacio texto="Sin alertas" /> : d.alertas.map((a: Fila) => (
              <div key={a.id_alerta} className="tarjeta-alerta" style={{ ['--color' as any]: a.severidad === 'CRITICA' ? 'var(--critico)' : 'var(--alto)', marginBottom: 8 }} onClick={() => ir(`/alertas?id=${a.id_alerta}`)}>
                <div className="fila entre"><ChipSeveridad s={a.severidad} /><ChipEstado estado={a.estado} /></div>
                <p>{a.mensaje}</p>
                <div className="fila entre pequeno tenue"><span>{a.asignado} · {fechaHora(a.fecha_generacion)}</span><RelojSla segundos={a.segundos_restantes} abierta={!['RESUELTA', 'CERRADA'].includes(a.estado)} /></div>
              </div>
            ))}
          </Tarjeta>
          <Tarjeta>
            <div className="tarjeta-cabecera"><h3><ClipboardList size={16} /> Hallazgos de auditoría</h3></div>
            {d.obligacion && <div className="item-verificacion" style={{ marginBottom: 10 }}><FileText size={18} /><div className="pequeno">Generado por incumplimiento (RN10) de <strong>{d.obligacion.normativa}</strong>: {d.obligacion.descripcion}</div></div>}
            {d.hallazgos.length === 0 ? <Vacio texto="Sin hallazgos vinculados" /> : d.hallazgos.map((h: Fila) => (
              <div key={h.id_hallazgo} className="item-verificacion" style={{ marginBottom: 8 }}>
                <ChipSeveridad s={h.severidad === 'ALTA' ? 'CRITICA' : h.severidad === 'MEDIA' ? 'ADVERTENCIA' : 'PREVENTIVA'} />
                <div className="crece pequeno"><strong>{h.auditoria ?? 'Conciliación'}</strong> · {h.descripcion}<div className="tenue">{h.responsable} · límite {fecha(h.fecha_limite)} · {etiqueta(h.estado)}</div></div>
              </div>
            ))}
          </Tarjeta>
        </div>
      )}
      {pestana === 'historial' && (
        <Tarjeta>
          <div className="tarjeta-cabecera"><h3><History size={16} /> Historia del riesgo</h3><p>Leída de la bitácora inalterable: quién, qué, cuándo y qué cambió (RN13)</p></div>
          <div className="linea-tiempo">
            {d.historial.map((h: Fila) => (
              <div key={h.id_bitacora} className="hito" style={{ ['--c' as any]: h.resultado === 'OK' ? 'var(--acento)' : 'var(--critico)' }}>
                <div className="fila" style={{ gap: 8 }}>
                  <strong>{operacion(h.operacion)}</strong>
                  {h.resultado !== 'OK' && <ChipEstado estado={h.resultado} />}
                  <span className="chip">{h.entidad}</span>
                  <span className="pequeno tenue">{h.usuario} · {fechaHora(h.fecha_hora)} · {h.origen === 'TRIGGER' ? 'trigger' : 'aplicación'}</span>
                </div>
                {h.detalle?.cambios && <div style={{ marginTop: 6 }}><Diferencias cambios={h.detalle.cambios} /></div>}
                {h.detalle?.motivo && <p style={{ color: 'var(--critico)' }}>{h.detalle.motivo}</p>}
              </div>
            ))}
          </div>
        </Tarjeta>
      )}

      {evaluar && <ModalEvaluacion riesgo={{ ...r, impacto_financiero: evalVigente?.impacto_financiero }} tipo={evaluar} abierto={!!evaluar} cerrar={() => setEvaluar(null)} alGuardar={recargar} />}
      <ModalAprobacion idRiesgo={Number(id)} abierto={aprobar} cerrar={() => setAprobar(false)} alTerminar={recargar} />
    </div>
  );
}

function Resumen({ d, recargar }: { d: Fila; recargar: () => void }) {
  const r = d.riesgo;
  const { tiene } = useSesion();
  const { error } = useAvisos();
  const [control, setControl] = useState<Fila>({ tipo: 'PREVENTIVO', efectividad: 3 });
  const agregar = async () => {
    try {
      await api.post(`/api/riesgos/${r.id_riesgo}/controles`, control);
      setControl({ tipo: 'PREVENTIVO', efectividad: 3 });
      recargar();
    } catch (e) {
      error(e);
    }
  };
  return (
    <div className="rejilla r-3">
      <Tarjeta className="span-2">
        <h3 style={{ marginBottom: 8 }}>Descripción</h3>
        <p className="suave">{r.descripcion}</p>
        <div className="rejilla r-2" style={{ marginTop: 16 }}>
          <div className="item-verificacion"><div><strong className="pequeno">Causa</strong><p className="pequeno suave">{r.causa}</p></div></div>
          <div className="item-verificacion"><div><strong className="pequeno">Consecuencia</strong><p className="pequeno suave">{r.consecuencia}</p></div></div>
        </div>
        <h3 style={{ margin: '20px 0 10px' }}>Controles existentes</h3>
        <div className="columna" style={{ gap: 8 }}>
          {d.controles.length === 0 && <span className="tenue pequeno">Sin controles registrados</span>}
          {d.controles.map((c: Fila) => (
            <div key={c.id_control} className="fila entre item-verificacion" style={{ padding: 10 }}>
              <span className="fila" style={{ gap: 8 }}><span className="chip">{etiqueta(c.tipo)}</span>{c.descripcion}</span>
              <span className="fila" style={{ gap: 6 }}>
                <span className="pequeno tenue">efectividad</span>
                {[1, 2, 3, 4, 5].map((n) => <i key={n} style={{ width: 8, height: 8, borderRadius: 4, background: n <= c.efectividad ? 'var(--acento)' : 'var(--superficie-3)', display: 'inline-block' }} />)}
                {tiene('GESTOR') && <button className="icono-btn" aria-label="Eliminar control" onClick={() => api.del(`/api/riesgos/${r.id_riesgo}/controles/${c.id_control}`).then(recargar).catch(error)}><Trash2 size={14} /></button>}
              </span>
            </div>
          ))}
          {tiene('GESTOR') && r.estado !== 'CERRADO' && (
            <div className="fila" style={{ gap: 8 }}>
              <input className="entrada crece" placeholder="Nuevo control…" value={control.descripcion ?? ''} onChange={(e) => setControl({ ...control, descripcion: e.target.value })} />
              <select className="entrada" style={{ width: 140 }} value={control.tipo} onChange={(e) => setControl({ ...control, tipo: e.target.value })}>
                {['PREVENTIVO', 'DETECTIVO', 'CORRECTIVO'].map((t) => <option key={t} value={t}>{etiqueta(t)}</option>)}
              </select>
              <select className="entrada" style={{ width: 80 }} value={control.efectividad} onChange={(e) => setControl({ ...control, efectividad: Number(e.target.value) })}>
                {[1, 2, 3, 4, 5].map((n) => <option key={n}>{n}</option>)}
              </select>
              <button className="boton" disabled={!control.descripcion} onClick={agregar}><Plus size={15} /></button>
            </div>
          )}
        </div>
      </Tarjeta>
      <Tarjeta>
        <h3 style={{ marginBottom: 10 }}>Posición en la matriz</h3>
        <MapaCalor celdas={[]} marcar={r.probabilidad ? { p: r.probabilidad, i: r.impacto } : null} compacto />
        <h3 style={{ margin: '18px 0 10px' }}>Evaluaciones</h3>
        <div className="linea-tiempo">
          {d.evaluaciones.map((e: Fila) => (
            <div key={e.id_evaluacion} className="hito" style={{ ['--c' as any]: e.tipo === 'RESIDUAL' ? 'var(--bajo)' : 'var(--alto)' }}>
              <div className="fila" style={{ gap: 6 }}><strong>{etiqueta(e.tipo)}</strong><ChipNivel nivel={e.nivel} valor={e.valor} />{!e.vigente && <span className="chip">vencida</span>}</div>
              <p>{fecha(e.fecha)} · {e.evaluador}{e.impacto_usd ? ` · ${usd(e.impacto_usd, true)}` : ''}</p>
              {e.resultado_validacion && <p style={{ color: e.resultado_validacion === 'VALIDADA' ? 'var(--bajo)' : 'var(--critico)' }}>{etiqueta(e.resultado_validacion)} por {e.auditor_financiero}{e.observacion_validacion ? `: ${e.observacion_validacion}` : ''}</p>}
            </div>
          ))}
          {d.evaluaciones.length === 0 && <span className="tenue pequeno">Aún no fue evaluado</span>}
        </div>
      </Tarjeta>
    </div>
  );
}

function Tratamiento({ d, recargar }: { d: Fila; recargar: () => void }) {
  const r = d.riesgo;
  const plan = d.plan;
  const { datos: cat } = useDatos<Fila>('/api/catalogos');
  const { avisar, error } = useAvisos();
  const [estrategia, setEstrategia] = useState(plan?.estrategia ?? 'REDUCIR');
  const [justificacion, setJustificacion] = useState(plan?.justificacion ?? '');
  const [accion, setAccion] = useState<Fila>({ fechaLimite: sumarDias(30), costo: 0 });
  const [ocupado, setOcupado] = useState(false);
  const editable = d.permisos.definirPlan;
  useEffect(() => {
    setEstrategia(plan?.estrategia ?? 'REDUCIR');
    setJustificacion(plan?.justificacion ?? '');
  }, [plan?.id_plan, plan?.estrategia]);

  const hacer = async (f: () => Promise<unknown>, ok?: string) => {
    setOcupado(true);
    try {
      await f();
      if (ok) avisar('exito', ok);
      recargar();
    } catch (e) {
      error(e);
    } finally {
      setOcupado(false);
    }
  };
  const responsables = (cat?.usuarios ?? []).filter((u: Fila) => u.corporativo || u.id_filial === r.id_filial);

  return (
    <div className="rejilla r-3">
      <Tarjeta className="span-2">
        <div className="tarjeta-cabecera">
          <h3><Wrench size={16} /> Plan de mitigación (CU03)</h3>
          <div className="acciones">{plan && <ChipEstado estado={plan.estado} />}</div>
        </div>
        {!plan && !editable && <Vacio texto={r.estado === 'IDENTIFICADO' ? 'Primero evalúe el riesgo (CU02)' : 'Sin plan de mitigación'} />}
        {(plan || editable) && (
          <>
            <div className="rejilla" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: 10 }}>
              {ESTRATEGIAS.map((e) => (
                <button key={e.e} type="button" disabled={!editable} onClick={() => setEstrategia(e.e)} className="tarjeta"
                  style={{ textAlign: 'left', padding: 12, cursor: editable ? 'pointer' : 'default', font: 'inherit', color: 'var(--texto)', borderColor: estrategia === e.e ? 'var(--acento)' : undefined, boxShadow: estrategia === e.e ? 'var(--brillo)' : undefined }}>
                  <strong>{etiqueta(e.e)}</strong>
                  <div className="pequeno tenue">{e.d}</div>
                </button>
              ))}
            </div>
            <Campo etiqueta="Justificación" className="" ayuda={estrategia === 'ACEPTAR' ? 'Obligatoria para aceptar el riesgo (RN12)' : undefined}>
              <textarea className="entrada" disabled={!editable} value={justificacion} onChange={(e) => setJustificacion(e.target.value)} style={{ marginTop: 12 }} />
            </Campo>
            {editable && (
              <div className="fila" style={{ marginTop: 12 }}>
                <button className="boton" disabled={ocupado} onClick={() => hacer(() => api.put(`/api/riesgos/${r.id_riesgo}/plan`, { estrategia, justificacion }), 'Plan guardado en borrador')}>Guardar borrador</button>
                {plan?.estado === 'BORRADOR' && <button className="boton primario" disabled={ocupado} onClick={() => hacer(() => api.post(`/api/riesgos/${r.id_riesgo}/plan/proponer`), 'Plan propuesto: se notificó al aprobador (RN04)')}><Send size={15} /> Proponer para aprobación</button>}
              </div>
            )}
            {plan?.motivo_rechazo && <div className="item-verificacion no" style={{ marginTop: 12 }}><div className="pequeno"><strong>Rechazado:</strong> {plan.motivo_rechazo}</div></div>}
            {plan && <div className="pequeno tenue" style={{ marginTop: 10 }}>Propuesto por {plan.proponente ?? '—'} {plan.fecha_propuesta ? `el ${fechaHora(plan.fecha_propuesta)}` : ''}{plan.aprobador ? ` · ${plan.estado === 'APROBADO' || plan.estado === 'CERRADO' ? 'aprobado' : 'revisado'} por ${plan.aprobador}` : ''} · costo {numero(plan.costo_estimado)}</div>}
          </>
        )}

        <h3 style={{ margin: '22px 0 10px' }}>Acciones</h3>
        <div className="columna" style={{ gap: 10 }}>
          {d.acciones.length === 0 && <span className="tenue pequeno">Sin acciones (RN05 exige al menos una salvo que se acepte el riesgo)</span>}
          {d.acciones.map((a: Fila, k: number) => (
            <motion.div key={a.id_accion} className="item-verificacion" initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: k * 0.05 }}>
              {a.estado === 'CERRADA' ? <CheckCircle2 size={20} color="var(--bajo)" /> : <span className="chip">{a.avance} %</span>}
              <div className="crece">
                <div className="fila entre"><strong style={{ fontSize: 13 }}>{a.descripcion}</strong><ChipEstado estado={a.estado} /></div>
                <div className="pequeno tenue">{a.responsable} · vence {fecha(a.fecha_limite)}{a.vencida && <span style={{ color: 'var(--critico)' }}> · vencida</span>} · {numero(a.costo)} · {a.evidencias} evidencia(s)</div>
                <div style={{ marginTop: 6 }}><BarraAvance valor={a.avance} rayada={a.estado === 'EN_CURSO'} /></div>
              </div>
              {plan?.estado === 'BORRADOR' && editable && <button className="icono-btn" onClick={() => hacer(() => api.del(`/api/riesgos/${r.id_riesgo}/plan/acciones/${a.id_accion}`))}><Trash2 size={15} /></button>}
            </motion.div>
          ))}
          {plan?.estado === 'BORRADOR' && editable && (
            <div className="tarjeta" style={{ padding: 12 }}>
              <div className="rejilla" style={{ gridTemplateColumns: '2fr 1.3fr 1fr 0.8fr auto', gap: 8, alignItems: 'end' }}>
                <Campo etiqueta="Nueva acción"><input className="entrada" value={accion.descripcion ?? ''} onChange={(e) => setAccion({ ...accion, descripcion: e.target.value })} /></Campo>
                <Campo etiqueta="Responsable">
                  <select className="entrada" value={accion.idResponsable ?? ''} onChange={(e) => setAccion({ ...accion, idResponsable: Number(e.target.value) })}>
                    <option value="">—</option>{responsables.map((u: Fila) => <option key={u.id_usuario} value={u.id_usuario}>{u.nombre_completo}</option>)}
                  </select>
                </Campo>
                <Campo etiqueta="Fecha límite"><input className="entrada" type="date" value={accion.fechaLimite} onChange={(e) => setAccion({ ...accion, fechaLimite: e.target.value })} /></Campo>
                <Campo etiqueta="Costo"><input className="entrada" type="number" value={accion.costo} onChange={(e) => setAccion({ ...accion, costo: Number(e.target.value) })} /></Campo>
                <button className="boton primario" disabled={!accion.descripcion || !accion.idResponsable || ocupado}
                  onClick={() => hacer(async () => { await api.post(`/api/riesgos/${r.id_riesgo}/plan/acciones`, accion); setAccion({ fechaLimite: sumarDias(30), costo: 0 }); })}><Plus size={16} /></button>
              </div>
            </div>
          )}
        </div>
      </Tarjeta>
      <Tarjeta>
        <h3 style={{ marginBottom: 10 }}>Evidencias</h3>
        {d.evidencias.length === 0 ? <Vacio texto="Sin evidencias cargadas" /> : d.evidencias.map((e: Fila) => (
          <div key={e.id_evidencia} className="fila item-verificacion" style={{ padding: 10, marginBottom: 8, cursor: 'pointer' }}
            onClick={() => descargar(`/api/evidencias/${e.id_evidencia}/archivo`, e.archivo, true).catch(error)}>
            <FileText size={18} color="var(--acento)" />
            <div className="crece pequeno"><strong>{e.archivo}</strong><div className="tenue">{e.usuario} · {haceCuanto(e.fecha_carga)}</div></div>
          </div>
        ))}
        <div className="sep" />
        <p className="pequeno tenue">El avance de las acciones lo registra cada responsable en «Mis acciones» (CU05). Al 100 % se exige evidencia y, cerradas todas, la evaluación residual decide si el riesgo queda MITIGADO (RN11).</p>
        {ocupado && <Girando />}
      </Tarjeta>
    </div>
  );
}

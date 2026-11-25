import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { AnimatePresence, motion } from 'framer-motion';
import { Calculator, Check, CheckCircle2, CircleDollarSign, Gavel, Info, ShieldAlert, ShieldCheck, Stamp, X, XCircle } from 'lucide-react';
import { api, type Fila } from '../nucleo/api';
import { useAvisos, useDatos } from '../nucleo/contexto';
import { colorNivel, etiqueta, fecha, numero, usd } from '../nucleo/formato';
import { MapaCalor } from './Graficos';
import { Bandera, Cargando, Campo, ChipEstado, ChipNivel, Girando, Modal } from './Ui';

const ESCALA_P = ['', 'Rara', 'Improbable', 'Posible', 'Probable', 'Casi segura'];
const ESCALA_I = ['', 'Insignificante', 'Menor', 'Moderado', 'Mayor', 'Catastrófico'];

// ---------------------------------------------------------------- Flujo de estados D08

const ESTADOS = ['IDENTIFICADO', 'EVALUADO', 'EN_TRATAMIENTO', 'MITIGADO', 'CERRADO'];

export function FlujoEstados({ estado }: { estado: string }) {
  const aceptado = estado === 'ACEPTADO';
  const lista = aceptado ? ['IDENTIFICADO', 'EVALUADO', 'ACEPTADO', 'CERRADO'] : ESTADOS;
  const idx = lista.indexOf(estado);
  return (
    <div className="fila" style={{ gap: 0, flexWrap: 'nowrap', overflowX: 'auto' }}>
      {lista.map((e, i) => {
        const hecho = i < idx;
        const actual = i === idx;
        return (
          <div key={e} className="fila" style={{ gap: 0, flexWrap: 'nowrap', flex: i < lista.length - 1 ? 1 : 'none' }}>
            <motion.div
              initial={{ scale: 0.6, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              transition={{ delay: i * 0.08 }}
              className="columna"
              style={{ alignItems: 'center', gap: 6, minWidth: 92 }}
            >
              <span
                className={actual ? 'pulso-rojo' : ''}
                style={{
                  width: 34, height: 34, borderRadius: 12, display: 'grid', placeItems: 'center', fontWeight: 700, fontSize: 13,
                  background: actual ? 'var(--degradado)' : hecho ? 'color-mix(in srgb, var(--bajo) 22%, transparent)' : 'var(--superficie-3)',
                  color: actual ? '#fff' : hecho ? 'var(--bajo)' : 'var(--texto-3)',
                  boxShadow: actual ? '0 0 0 4px var(--acento-suave)' : undefined,
                  animation: actual ? 'none' : undefined,
                }}
              >
                {hecho ? <Check size={16} /> : i + 1}
              </span>
              <span className="pequeno" style={{ color: actual ? 'var(--texto)' : 'var(--texto-3)', fontWeight: actual ? 700 : 500, whiteSpace: 'nowrap' }}>{etiqueta(e)}</span>
            </motion.div>
            {i < lista.length - 1 && (
              <div style={{ flex: 1, height: 3, borderRadius: 3, background: 'var(--superficie-3)', minWidth: 20, marginBottom: 22, overflow: 'hidden' }}>
                <motion.div initial={{ width: 0 }} animate={{ width: hecho ? '100%' : '0%' }} transition={{ duration: 0.6, delay: i * 0.1 }} style={{ height: '100%', background: 'var(--bajo)' }} />
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}

// ---------------------------------------------------------------- Prototipo P03: evaluar el riesgo (CU02 y residual)

export function ModalEvaluacion({ riesgo, tipo, abierto, cerrar, alGuardar }: { riesgo: Fila; tipo: 'INHERENTE' | 'RESIDUAL'; abierto: boolean; cerrar: () => void; alGuardar: () => void }) {
  const { avisar, error } = useAvisos();
  const financiero = riesgo.categoria === 'FINANCIERO' && tipo === 'INHERENTE';
  const [p, setP] = useState(Number(riesgo.probabilidad ?? 3));
  const [i, setI] = useState(Number(riesgo.impacto ?? 3));
  const [monto, setMonto] = useState<string>(riesgo.impacto_financiero ? String(riesgo.impacto_financiero) : '');
  const [moneda, setMoneda] = useState<string>(riesgo.moneda_filial ?? 'USD');
  const [comentario, setComentario] = useState('');
  const [vista, setVista] = useState<Fila | null>(null);
  const [guardando, setGuardando] = useState(false);

  useEffect(() => {
    if (!abierto) return;
    const t = setTimeout(() => {
      api.post(`/api/riesgos/${riesgo.id_riesgo}/evaluaciones/previsualizar`, {
        probabilidad: p, impacto: i, impactoFinanciero: financiero && monto ? Number(monto) : null, moneda,
      }).then(setVista).catch(() => {});
    }, 180);
    return () => clearTimeout(t);
  }, [p, i, monto, moneda, abierto, riesgo.id_riesgo, financiero]);

  const guardar = async () => {
    setGuardando(true);
    try {
      const r = await api.post(`/api/riesgos/${riesgo.id_riesgo}/evaluaciones`, {
        tipo, probabilidad: p, impacto: i, impactoFinanciero: financiero && monto ? Number(monto) : null, moneda: financiero ? moneda : null, comentario,
      });
      avisar('exito', `Evaluación ${tipo === 'INHERENTE' ? 'inherente' : 'residual'} registrada`,
        `Nivel ${etiqueta(r.nivel)} (${r.valor}). Riesgo ${etiqueta(r.estado_riesgo)}${r.estado_validacion === 'PENDIENTE' ? ' · requiere validación financiera (RN16)' : ''}.`);
      alGuardar();
      cerrar();
    } catch (e) {
      error(e);
    } finally {
      setGuardando(false);
    }
  };

  const valor = p * i;
  return (
    <Modal abierto={abierto} cerrar={cerrar} ancho icono={<Calculator size={20} />}
      titulo={tipo === 'INHERENTE' ? 'Evaluar el riesgo (CU02 · P03)' : 'Evaluación residual (CU05 · RN11)'}
      subtitulo={<><span className="codigo">{riesgo.codigo}</span> · {riesgo.titulo}</>}
      pie={<>
        <button className="boton" onClick={cerrar}>Cancelar</button>
        <button className="boton primario" onClick={guardar} disabled={guardando || (financiero && !monto)}>{guardando ? <Girando /> : <Check size={16} />} Registrar evaluación</button>
      </>}>
      <div className="rejilla r-2" style={{ gap: 24 }}>
        <div className="columna" style={{ gap: 18 }}>
          <Deslizador titulo="Probabilidad" valor={p} cambiar={setP} escala={ESCALA_P} />
          <Deslizador titulo="Impacto" valor={i} cambiar={setI} escala={ESCALA_I} />
          {financiero && (
            <div className="rejilla" style={{ gridTemplateColumns: '1fr 110px', gap: 10 }}>
              <Campo etiqueta={<><CircleDollarSign size={14} /> Impacto financiero estimado</>} ayuda="Se convierte a USD con el tipo de cambio del ERP (RN15)">
                <input className="entrada" type="number" min={0} value={monto} onChange={(e) => setMonto(e.target.value)} placeholder="0,00" />
              </Campo>
              <Campo etiqueta="Moneda">
                <select className="entrada" value={moneda} onChange={(e) => setMoneda(e.target.value)}>
                  {[riesgo.moneda_filial, 'USD'].filter((x, k, a) => x && a.indexOf(x) === k).map((m) => <option key={m}>{m}</option>)}
                </select>
              </Campo>
            </div>
          )}
          <Campo etiqueta="Justificación de la evaluación">
            <textarea className="entrada" value={comentario} onChange={(e) => setComentario(e.target.value)} placeholder="Supuestos, fuentes y controles considerados…" />
          </Campo>
        </div>
        <div className="columna" style={{ gap: 14 }}>
          <MapaCalor celdas={[]} marcar={{ p, i }} alElegir={(pp, ii) => { setP(pp); setI(ii); }} compacto />
          <motion.div key={valor} initial={{ scale: 0.94, opacity: 0.6 }} animate={{ scale: 1, opacity: 1 }} className="tarjeta" style={{ borderColor: colorNivel(vista?.nivel), padding: 14 }}>
            <div className="fila entre">
              <div>
                <div className="pequeno tenue">Nivel calculado (RN02: P × I)</div>
                <div style={{ fontFamily: 'var(--fuente-titulo)', fontSize: 34, fontWeight: 700, color: colorNivel(vista?.nivel) }}>{valor} · {etiqueta(vista?.nivel)}</div>
              </div>
              <ShieldAlert size={36} color={colorNivel(vista?.nivel)} />
            </div>
            {vista && (
              <div className="columna pequeno" style={{ gap: 6, marginTop: 8 }}>
                {financiero && vista.impacto_usd != null && <span><CircleDollarSign size={13} /> {numero(monto)} {vista.moneda} ÷ {numero(vista.tipo_cambio, 4)} = <strong>{usd(vista.impacto_usd)}</strong></span>}
                <span style={{ color: vista.supera_apetito ? 'var(--critico)' : 'var(--bajo)' }}>● {vista.supera_apetito ? 'Supera' : 'Dentro de'} el apetito de la filial ({vista.apetito})</span>
                {tipo === 'INHERENTE' && <span>● Aprobación del tratamiento: {vista.aprobador}</span>}
                {tipo === 'INHERENTE' && vista.requiere_validacion && <span style={{ color: 'var(--medio)' }}>● {vista.motivo_validacion}: validación del Auditor Financiero hasta el {fecha(vista.plazo_validacion)}</span>}
                <span className="tenue">● Vigente hasta el {fecha(vista.vigente_hasta)} (RN03)</span>
              </div>
            )}
          </motion.div>
        </div>
      </div>
    </Modal>
  );
}

function Deslizador({ titulo, valor, cambiar, escala }: { titulo: string; valor: number; cambiar: (v: number) => void; escala: string[] }) {
  return (
    <div>
      <div className="fila entre" style={{ marginBottom: 8 }}>
        <span className="etiqueta-campo">{titulo}</span>
        <span className="chip" style={{ color: 'var(--acento)' }}>{valor} · {escala[valor]}</span>
      </div>
      <div className="fila" style={{ gap: 6, flexWrap: 'nowrap' }}>
        {[1, 2, 3, 4, 5].map((n) => (
          <button key={n} type="button" onClick={() => cambiar(n)} className="boton" style={{
            flex: 1, height: 44, flexDirection: 'column', gap: 0, padding: 0,
            background: n <= valor ? `color-mix(in srgb, ${colorNivel(['', 'BAJO', 'MEDIO', 'MEDIO', 'ALTO', 'CRITICO'][n])} ${n === valor ? 70 : 28}%, transparent)` : undefined,
            borderColor: n === valor ? 'var(--texto)' : undefined, color: n <= valor ? '#fff' : undefined,
          }}>
            <strong>{n}</strong>
            <span style={{ fontSize: 9.5, opacity: 0.85 }}>{escala[n]}</span>
          </button>
        ))}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------- CU04: verificar y aprobar el tratamiento

export function ModalAprobacion({ idRiesgo, abierto, cerrar, alTerminar }: { idRiesgo: number; abierto: boolean; cerrar: () => void; alTerminar: () => void }) {
  const { avisar, error } = useAvisos();
  const { datos: v, cargando } = useDatos<Fila>(abierto ? `/api/riesgos/${idRiesgo}/plan/verificacion` : null, [abierto]);
  const [motivo, setMotivo] = useState('');
  const [rechazando, setRechazando] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const accion = async (tipo: 'aprobar' | 'rechazar') => {
    setEnviando(true);
    try {
      if (tipo === 'aprobar') {
        const r = await api.post(`/api/riesgos/${idRiesgo}/plan/aprobar`);
        avisar('exito', 'Tratamiento aprobado', `El riesgo pasó a ${etiqueta(r.estado_riesgo)}. Se notificó a los responsables de las acciones.`);
      } else {
        await api.post(`/api/riesgos/${idRiesgo}/plan/rechazar`, { motivo });
        avisar('info', 'Tratamiento rechazado', 'El plan volvió a borrador y se notificó al proponente.');
      }
      alTerminar();
      cerrar();
    } catch (e) {
      error(e);
    } finally {
      setEnviando(false);
    }
  };
  const r = v?.riesgo;
  return (
    <Modal abierto={abierto} cerrar={cerrar} ancho icono={<Stamp size={20} />} titulo="Aprobar el tratamiento (CU04)"
      subtitulo={r ? <><span className="codigo">{r.codigo}</span> · {r.titulo}</> : 'Lista de verificación del aprobador'}
      pie={<>
        {rechazando ? (
          <>
            <input className="entrada" style={{ flex: 1 }} placeholder="Motivo del rechazo (obligatorio)" value={motivo} onChange={(e) => setMotivo(e.target.value)} autoFocus />
            <button className="boton" onClick={() => setRechazando(false)}>Volver</button>
            <button className="boton peligro" disabled={motivo.trim().length < 5 || enviando} onClick={() => accion('rechazar')}><XCircle size={16} /> Confirmar rechazo</button>
          </>
        ) : (
          <>
            <button className="boton peligro" onClick={() => setRechazando(true)} disabled={v?.plan?.estado !== 'PROPUESTO'}><X size={16} /> Rechazar</button>
            <button className="boton primario" onClick={() => accion('aprobar')} disabled={enviando || !v}>
              {enviando ? <Girando /> : <Gavel size={16} />} Aprobar
            </button>
          </>
        )}
      </>}>
      {cargando || !v ? <Cargando /> : (
        <div className="rejilla r-2" style={{ gap: 20 }}>
          <div className="lista-verificacion">
            {v.items.map((it: Fila, k: number) => (
              <motion.div key={it.clave} className={`item-verificacion ${it.cumple ? 'ok' : 'no'}`} initial={{ opacity: 0, x: -14 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: k * 0.09 }}>
                <motion.span initial={{ scale: 0, rotate: -90 }} animate={{ scale: 1, rotate: 0 }} transition={{ delay: 0.2 + k * 0.09, type: 'spring' }}>
                  {it.cumple ? <CheckCircle2 size={22} color="var(--bajo)" /> : <XCircle size={22} color="var(--critico)" />}
                </motion.span>
                <div className="crece">
                  <div className="fila" style={{ gap: 8 }}><strong style={{ fontSize: 13 }}>{it.descripcion}</strong><span className="chip">{it.regla}</span></div>
                  <div className="pequeno suave">{it.detalle}</div>
                </div>
              </motion.div>
            ))}
            <AnimatePresence>
              <motion.div className="tarjeta" style={{ padding: 12, borderColor: v.aprobable ? 'var(--bajo)' : 'var(--alto)' }} initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
                {v.aprobable
                  ? <span style={{ color: 'var(--bajo)' }}><ShieldCheck size={16} /> Todas las reglas se cumplen: puede aprobar.</span>
                  : <span style={{ color: 'var(--alto)' }}><Info size={16} /> Hay reglas sin cumplir. Si intenta aprobar, el sistema lo rechazará y quedará registrado en la bitácora.</span>}
              </motion.div>
            </AnimatePresence>
          </div>
          <div className="columna">
            <div className="tarjeta" style={{ padding: 14 }}>
              <div className="fila entre"><span className="tenue pequeno">Nivel vigente</span><ChipNivel nivel={r.nivel} valor={r.valor} /></div>
              <div className="fila entre" style={{ marginTop: 8 }}><span className="tenue pequeno">Estrategia</span><strong>{etiqueta(v.plan?.estrategia)}</strong></div>
              <div className="fila entre" style={{ marginTop: 8 }}><span className="tenue pequeno">Costo estimado</span><strong>{numero(v.plan?.costo_estimado)}</strong></div>
              <div className="fila entre" style={{ marginTop: 8 }}><span className="tenue pequeno">Filial</span><span><Bandera codigo={r.codigo_pais} /> {r.filial}</span></div>
              <div className="fila entre" style={{ marginTop: 8 }}><span className="tenue pequeno">Estado del plan</span><ChipEstado estado={v.plan?.estado} /></div>
              {v.plan?.justificacion && <p className="pequeno suave" style={{ marginTop: 10 }}>“{v.plan.justificacion}”</p>}
            </div>
            <strong className="pequeno tenue" style={{ textTransform: 'uppercase', letterSpacing: '.08em' }}>Acciones del plan</strong>
            {v.acciones.length === 0 && <span className="tenue pequeno">Sin acciones</span>}
            {v.acciones.map((a: Fila) => (
              <div key={a.id_accion} className="item-verificacion" style={{ padding: 10 }}>
                <div className="crece">
                  <div style={{ fontSize: 13, fontWeight: 600 }}>{a.descripcion}</div>
                  <div className="pequeno tenue">{a.responsable} · vence {fecha(a.fecha_limite)} · {numero(a.costo)}</div>
                </div>
              </div>
            ))}
            <Link to={`/riesgos/${idRiesgo}`} className="pequeno" onClick={cerrar}>Ver la ficha completa del riesgo →</Link>
          </div>
        </div>
      )}
    </Modal>
  );
}

// ---------------------------------------------------------------- Vista rápida (para ventanas flotantes)

export function VistaRapidaRiesgo({ id }: { id: number }) {
  const { datos: d } = useDatos<Fila>(`/api/riesgos/${id}`);
  const r = d?.riesgo;
  const acciones = useMemo(() => d?.acciones ?? [], [d]);
  if (!r) return <Cargando />;
  return (
    <div className="columna" style={{ gap: 12 }}>
      <div className="fila" style={{ gap: 8 }}>
        <span className="codigo">{r.codigo}</span>
        <ChipNivel nivel={r.nivel} valor={r.valor} />
        <ChipEstado estado={r.estado} />
      </div>
      <h3>{r.titulo}</h3>
      <p className="suave pequeno">{r.descripcion}</p>
      <div className="rejilla r-2 pequeno">
        <div><span className="tenue">Filial</span><div><Bandera codigo={r.codigo_pais} /> {r.filial}</div></div>
        <div><span className="tenue">Responsable</span><div>{r.responsable}</div></div>
        <div><span className="tenue">Impacto</span><div>{usd(r.impacto_usd)}</div></div>
        <div><span className="tenue">Validación</span><div>{etiqueta(r.estado_validacion)}</div></div>
      </div>
      <FlujoEstados estado={r.estado} />
      {acciones.length > 0 && (
        <div className="columna" style={{ gap: 8 }}>
          <strong className="pequeno">Acciones ({acciones.length})</strong>
          {acciones.map((a: Fila) => (
            <div key={a.id_accion}>
              <div className="fila entre pequeno"><span>{a.descripcion}</span><strong>{a.avance} %</strong></div>
              <div className="barra-avance"><span style={{ width: `${a.avance}%` }} /></div>
            </div>
          ))}
        </div>
      )}
      <Link to={`/riesgos/${id}`} className="boton chico" style={{ alignSelf: 'flex-start' }}>Abrir ficha completa</Link>
    </div>
  );
}

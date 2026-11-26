import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { AnimatePresence, motion } from 'framer-motion';
import {
  AlertTriangle, AppWindow, ArrowLeft, ArrowRight, Banknote, Check, Cpu, Factory, LayoutGrid, Megaphone, Plus, Scale, Search, ShieldAlert,
  Table2, X,
} from 'lucide-react';
import { api, conParametros, type Fila } from '../nucleo/api';
import { useAvisos, useDatos, useSesion } from '../nucleo/contexto';
import { colorNivel, etiqueta, fecha, usd } from '../nucleo/formato';
import { VistaRapidaRiesgo } from '../componentes/Riesgo';
import { Bandera, BarraAvance, Campo, Cargando, ChipEstado, ChipNivel, Encabezado, Girando, Modal, Segmentado, Tabla, Tarjeta, Vacio } from '../componentes/Ui';
import { useVentanas } from '../componentes/Ventanas';

export const CATEGORIAS = [
  { c: 'FINANCIERO', i: Banknote, color: '#22d3ee', d: 'Tipo de cambio, liquidez, crédito, tasas' },
  { c: 'TECNOLOGICO', i: Cpu, color: '#6366f1', d: 'Ciberseguridad, sistemas, continuidad' },
  { c: 'LEGAL', i: Scale, color: '#a855f7', d: 'Regulación, contratos, litigios' },
  { c: 'OPERATIVO', i: Factory, color: '#f97316', d: 'Procesos, proveedores, personas' },
  { c: 'REPUTACIONAL', i: Megaphone, color: '#ec4899', d: 'Imagen, clientes, medios' },
];

export default function Riesgos() {
  const { usuario, tiene } = useSesion();
  const { abrir } = useVentanas();
  const ir = useNavigate();
  const [params, setParams] = useSearchParams();
  const [q, setQ] = useState('');
  const [categoria, setCategoria] = useState('');
  const [estado, setEstado] = useState('');
  const [nivel, setNivel] = useState(params.get('nivel') ?? '');
  const [filial, setFilial] = useState('');
  const [vista, setVista] = useState<'tabla' | 'tarjetas'>(() => (window.matchMedia('(max-width: 767px)').matches ? 'tarjetas' : 'tabla'));
  const [nuevo, setNuevo] = useState(false);
  const pCelda = params.get('p');
  const iCelda = params.get('i');
  const { datos, cargando, recargar } = useDatos<Fila[]>(conParametros('/api/riesgos', { categoria, estado, nivel, filial }));
  const { datos: cat } = useDatos<Fila>('/api/catalogos');

  const filas = useMemo(() => {
    const t = q.trim().toLowerCase();
    return (datos ?? []).filter((r) =>
      (!t || `${r.codigo} ${r.titulo} ${r.proceso} ${r.responsable}`.toLowerCase().includes(t)) &&
      (!pCelda || (String(r.probabilidad) === pCelda && String(r.impacto) === iCelda)));
  }, [datos, q, pCelda, iCelda]);

  const ventana = (r: Fila) => abrir({ id: `riesgo-${r.id_riesgo}`, titulo: `${r.codigo} · ${r.titulo}`, icono: <ShieldAlert size={15} />, contenido: <VistaRapidaRiesgo id={r.id_riesgo} />, ancho: 520, alto: 560 });

  return (
    <>
      <Encabezado icono={<ShieldAlert size={22} />} titulo="Catálogo de riesgos"
        texto={`${filas.length} riesgos ${usuario?.corporativo ? 'de todas las filiales' : `de ${usuario?.filial}`} (RN14). Cada registro, evaluación y cambio de estado queda en la bitácora.`}>
        <Segmentado valor={vista} cambiar={setVista} opciones={[{ valor: 'tabla', texto: <><Table2 size={14} /> Tabla</> }, { valor: 'tarjetas', texto: <><LayoutGrid size={14} /> Tarjetas</> }]} />
        {tiene('GESTOR') && <button className="boton primario" onClick={() => setNuevo(true)}><Plus size={16} /> Registrar riesgo</button>}
      </Encabezado>

      <Tarjeta style={{ padding: 12, marginBottom: 16 }}>
        <div className="fila filtros" style={{ gap: 10 }}>
          <div className="con-icono crece" style={{ minWidth: 220 }}><Search size={16} /><input className="entrada" placeholder="Buscar por código, título, proceso o responsable" aria-label="Buscar riesgos" value={q} onChange={(e) => setQ(e.target.value)} /></div>
          <select className="entrada" style={{ width: 160 }} aria-label="Filtrar por categoría" value={categoria} onChange={(e) => setCategoria(e.target.value)}>
            <option value="">Todas las categorías</option>{CATEGORIAS.map((c) => <option key={c.c} value={c.c}>{etiqueta(c.c)}</option>)}
          </select>
          <select className="entrada" style={{ width: 160 }} aria-label="Filtrar por estado" value={estado} onChange={(e) => setEstado(e.target.value)}>
            <option value="">Todos los estados</option>{['IDENTIFICADO', 'EVALUADO', 'EN_TRATAMIENTO', 'MITIGADO', 'ACEPTADO', 'CERRADO'].map((c) => <option key={c} value={c}>{etiqueta(c)}</option>)}
          </select>
          <select className="entrada" style={{ width: 140 }} aria-label="Filtrar por nivel" value={nivel} onChange={(e) => setNivel(e.target.value)}>
            <option value="">Todos los niveles</option>{['CRITICO', 'ALTO', 'MEDIO', 'BAJO'].map((c) => <option key={c} value={c}>{etiqueta(c)}</option>)}
          </select>
          {usuario?.corporativo && (
            <select className="entrada" style={{ width: 190 }} aria-label="Filtrar por filial" value={filial} onChange={(e) => setFilial(e.target.value)}>
              <option value="">Todas las filiales</option>{(cat?.filiales ?? []).map((f: Fila) => <option key={f.id_filial} value={f.id_filial}>{f.nombre}</option>)}
            </select>
          )}
          {pCelda && <button className="chip" style={{ cursor: 'pointer' }} onClick={() => setParams({})}>Celda P{pCelda} × I{iCelda} <X size={12} /></button>}
        </div>
      </Tarjeta>

      {cargando && !datos ? <Cargando filas={8} /> : vista === 'tabla' ? (
        <Tarjeta style={{ padding: 0 }}>
          <Tabla etiqueta="Catálogo de riesgos" filas={filas} clave={(r) => r.id_riesgo} alClic={(r) => ir(`/riesgos/${r.id_riesgo}`)} vacio="No hay riesgos con esos filtros"
            columnas={[
              { clave: 'codigo', titulo: 'Código', render: (r) => <span className="codigo">{r.codigo}</span> },
              { clave: 'titulo', titulo: 'Riesgo', render: (r) => <div style={{ maxWidth: 330 }}><div style={{ fontWeight: 600, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{r.titulo}</div><div className="pequeno tenue">{etiqueta(r.categoria)} · {r.proceso}</div></div> },
              { clave: 'filial', titulo: 'Filial', render: (r) => <span style={{ whiteSpace: 'nowrap' }}><Bandera codigo={r.codigo_pais} /> {r.filial}</span> },
              { clave: 'valor', titulo: 'Nivel', orden: (r) => Number(r.valor ?? 0), render: (r) => <ChipNivel nivel={r.nivel} valor={r.valor} /> },
              { clave: 'estado', titulo: 'Estado', render: (r) => <ChipEstado estado={r.estado} /> },
              { clave: 'impacto_usd', titulo: 'Impacto', clase: 'num', orden: (r) => Number(r.impacto_usd ?? 0), render: (r) => usd(r.impacto_usd, true) },
              { clave: 'avance_plan', titulo: 'Plan', orden: (r) => Number(r.avance_plan ?? -1), render: (r) => r.estrategia ? <div style={{ minWidth: 90 }}><div className="pequeno">{etiqueta(r.estrategia)} · {r.avance_plan ?? 0} %</div><BarraAvance valor={Number(r.avance_plan ?? 0)} /></div> : <span className="tenue">—</span> },
              { clave: 'alertas_abiertas', titulo: 'Alertas', clase: 'num', orden: (r) => Number(r.alertas_abiertas), render: (r) => Number(r.alertas_abiertas) > 0 ? <span className="contador rojo">{r.alertas_abiertas}</span> : <span className="tenue">0</span> },
              { clave: 'v', titulo: '', render: (r) => <button className="icono-btn" title="Abrir en ventana" onClick={(e) => { e.stopPropagation(); ventana(r); }}><AppWindow size={16} /></button> },
            ]} />
        </Tarjeta>
      ) : (
        <div className="rejilla r-auto">
          {filas.length === 0 && <Vacio texto="No hay riesgos con esos filtros" />}
          {filas.map((r, k) => {
            const C = CATEGORIAS.find((c) => c.c === r.categoria) ?? CATEGORIAS[0];
            return (
              <Tarjeta key={r.id_riesgo} inclinar retraso={Math.min(k * 0.03, 0.5)} onClick={() => ir(`/riesgos/${r.id_riesgo}`)} style={{ cursor: 'pointer', borderTop: `3px solid ${colorNivel(r.nivel)}` }}>
                <div className="fila entre">
                  <span className="fila" style={{ gap: 8 }}><span className="kpi-icono" style={{ width: 30, height: 30, borderRadius: 9, display: 'grid', placeItems: 'center', background: `${C.color}22`, color: C.color }}><C.i size={16} /></span><span className="codigo">{r.codigo}</span></span>
                  <ChipNivel nivel={r.nivel} valor={r.valor} />
                </div>
                <h3 style={{ margin: '12px 0 6px', minHeight: 40 }}>{r.titulo}</h3>
                <div className="pequeno tenue"><Bandera codigo={r.codigo_pais} /> {r.filial} · {r.responsable}</div>
                <div className="sep" />
                <div className="fila entre"><ChipEstado estado={r.estado} /><span className="pequeno">{usd(r.impacto_usd, true)}</span></div>
                {r.estrategia && <div style={{ marginTop: 10 }}><div className="fila entre pequeno"><span>{etiqueta(r.estrategia)}</span><span>{r.avance_plan ?? 0} %</span></div><BarraAvance valor={Number(r.avance_plan ?? 0)} /></div>}
              </Tarjeta>
            );
          })}
        </div>
      )}

      <AsistenteRiesgo abierto={nuevo} cerrar={() => setNuevo(false)} catalogos={cat} alCrear={(id) => { recargar(); ir(`/riesgos/${id}?evaluar=1`); }} />
    </>
  );
}

/** CU01: asistente de registro en tres pasos con control de duplicados en vivo. */
function AsistenteRiesgo({ abierto, cerrar, catalogos, alCrear }: { abierto: boolean; cerrar: () => void; catalogos: Fila | null; alCrear: (id: number) => void }) {
  const { usuario } = useSesion();
  const { avisar, error } = useAvisos();
  const [paso, setPaso] = useState(0);
  const [f, setF] = useState<Fila>({});
  const [similares, setSimilares] = useState<Fila[]>([]);
  const [enviando, setEnviando] = useState(false);
  const cambiar = (k: string, v: unknown) => setF((x) => ({ ...x, [k]: v }));

  useEffect(() => {
    if (abierto) {
      setPaso(0);
      setF({ idFilial: usuario?.idFilial, categoria: '' });
      setSimilares([]);
    }
  }, [abierto, usuario?.idFilial]);

  useEffect(() => {
    if (!f.titulo || f.titulo.length < 4) return setSimilares([]);
    const t = setTimeout(() => api.get(conParametros('/api/riesgos/similares', { titulo: f.titulo, filial: f.idFilial })).then(setSimilares).catch(() => {}), 300);
    return () => clearTimeout(t);
  }, [f.titulo, f.idFilial]);

  const responsables = (catalogos?.usuarios ?? []).filter((u: Fila) => u.corporativo || u.id_filial === Number(f.idFilial));
  const identico = similares.some((s) => s.identico);
  const validos = [
    !!f.categoria && (f.titulo ?? '').trim().length >= 5 && (f.proceso ?? '').trim().length >= 3 && !identico,
    (f.descripcion ?? '').trim().length >= 10 && (f.causa ?? '').trim().length >= 5 && (f.consecuencia ?? '').trim().length >= 5,
    !!f.idResponsable,
  ];

  const registrar = async () => {
    setEnviando(true);
    try {
      const r = await api.post('/api/riesgos', { ...f, idFilial: Number(f.idFilial), idResponsable: Number(f.idResponsable) });
      avisar('exito', `Riesgo ${r.codigo} registrado`, 'Quedó IDENTIFICADO. Continúe con la evaluación (CU02).');
      cerrar();
      alCrear(r.id_riesgo);
    } catch (e) {
      error(e);
    } finally {
      setEnviando(false);
    }
  };

  const PASOS = ['Identificación', 'Análisis', 'Responsable'];
  return (
    <Modal abierto={abierto} cerrar={cerrar} ancho icono={<Plus size={20} />} titulo="Registrar riesgo (CU01)" subtitulo="El código RSK-CAT-#### lo asigna la base de datos"
      pie={<>
        {paso > 0 && <button className="boton" onClick={() => setPaso(paso - 1)}><ArrowLeft size={16} /> Anterior</button>}
        <span className="crece" />
        {paso < 2
          ? <button className="boton primario" disabled={!validos[paso]} onClick={() => setPaso(paso + 1)}>Siguiente <ArrowRight size={16} /></button>
          : <button className="boton primario" disabled={!validos.every(Boolean) || enviando} onClick={registrar}>{enviando ? <Girando /> : <Check size={16} />} Registrar</button>}
      </>}>
      <div className="fila" style={{ marginBottom: 20, gap: 8, flexWrap: 'nowrap' }}>
        {PASOS.map((p, k) => (
          <div key={p} className="crece">
            <div className="barra-avance" style={{ height: 5 }}><motion.span animate={{ width: k <= paso ? '100%' : '0%' }} /></div>
            <div className="pequeno" style={{ marginTop: 6, color: k === paso ? 'var(--texto)' : 'var(--texto-3)', fontWeight: 600 }}>{k + 1}. {p}</div>
          </div>
        ))}
      </div>
      <AnimatePresence mode="wait">
        <motion.div key={paso} initial={{ opacity: 0, x: 30 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -30 }} transition={{ duration: 0.25 }}>
          {paso === 0 && (
            <div className="columna" style={{ gap: 16 }}>
              <div className="rejilla" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: 10 }}>
                {CATEGORIAS.map((c) => (
                  <motion.button key={c.c} type="button" whileHover={{ y: -3 }} whileTap={{ scale: 0.97 }} onClick={() => cambiar('categoria', c.c)} className="tarjeta"
                    style={{ cursor: 'pointer', textAlign: 'left', padding: 12, borderColor: f.categoria === c.c ? c.color : undefined, boxShadow: f.categoria === c.c ? `0 0 0 2px ${c.color}55` : undefined, color: 'var(--texto)', font: 'inherit' }}>
                    <c.i size={20} color={c.color} />
                    <div style={{ fontWeight: 700, marginTop: 6 }}>{etiqueta(c.c)}</div>
                    <div className="pequeno tenue">{c.d}</div>
                  </motion.button>
                ))}
              </div>
              <div className="rejilla r-2">
                {usuario?.corporativo ? (
                  <Campo etiqueta="Filial">
                    <select className="entrada" value={f.idFilial ?? ''} onChange={(e) => cambiar('idFilial', Number(e.target.value))}>
                      {(catalogos?.filiales ?? []).map((x: Fila) => <option key={x.id_filial} value={x.id_filial}>{x.nombre}</option>)}
                    </select>
                  </Campo>
                ) : <Campo etiqueta="Filial"><input className="entrada" disabled value={usuario?.filial} /></Campo>}
                <Campo etiqueta="Proceso afectado"><input className="entrada" value={f.proceso ?? ''} onChange={(e) => cambiar('proceso', e.target.value)} placeholder="Ej.: Tesorería" /></Campo>
              </div>
              <Campo etiqueta="Título del riesgo" ayuda="Se comprueba en vivo que no exista otro igual en la filial (CU01)">
                <input className="entrada" value={f.titulo ?? ''} onChange={(e) => cambiar('titulo', e.target.value)} placeholder="Ej.: Pérdida por variación del tipo de cambio" />
              </Campo>
              <AnimatePresence>
                {similares.length > 0 && (
                  <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }} exit={{ opacity: 0, height: 0 }} className="item-verificacion" style={{ borderColor: identico ? 'var(--critico)' : 'var(--medio)' }}>
                    <AlertTriangle size={18} color={identico ? 'var(--critico)' : 'var(--medio)'} />
                    <div className="crece pequeno">
                      <strong>{identico ? 'Ya existe un riesgo con este título en la filial' : 'Riesgos parecidos ya registrados'}</strong>
                      {similares.map((s) => <div key={s.id_riesgo}><span className="codigo">{s.codigo}</span> {s.titulo} · {s.filial} · {etiqueta(s.estado)}</div>)}
                    </div>
                  </motion.div>
                )}
              </AnimatePresence>
            </div>
          )}
          {paso === 1 && (
            <div className="columna" style={{ gap: 14 }}>
              <Campo etiqueta="Descripción"><textarea className="entrada" value={f.descripcion ?? ''} onChange={(e) => cambiar('descripcion', e.target.value)} placeholder="¿Qué puede pasar y en qué contexto?" /></Campo>
              <div className="rejilla r-2">
                <Campo etiqueta="Causa"><textarea className="entrada" value={f.causa ?? ''} onChange={(e) => cambiar('causa', e.target.value)} placeholder="¿Por qué podría ocurrir?" /></Campo>
                <Campo etiqueta="Consecuencia"><textarea className="entrada" value={f.consecuencia ?? ''} onChange={(e) => cambiar('consecuencia', e.target.value)} placeholder="¿Qué efecto tendría?" /></Campo>
              </div>
            </div>
          )}
          {paso === 2 && (
            <div className="rejilla r-2" style={{ gap: 18 }}>
              <Campo etiqueta="Responsable del riesgo" ayuda="Debe tener acceso a la filial del riesgo (RN14)">
                <select className="entrada" value={f.idResponsable ?? ''} onChange={(e) => cambiar('idResponsable', e.target.value)}>
                  <option value="">Seleccione…</option>
                  {responsables.map((u: Fila) => <option key={u.id_usuario} value={u.id_usuario}>{u.nombre_completo} · {u.cargo}</option>)}
                </select>
              </Campo>
              <div className="tarjeta" style={{ padding: 14 }}>
                <div className="pequeno tenue">Resumen</div>
                <h3 style={{ margin: '4px 0' }}>{f.titulo}</h3>
                <div className="pequeno suave">{etiqueta(f.categoria)} · {f.proceso}</div>
                <p className="pequeno" style={{ marginTop: 8 }}>{f.descripcion}</p>
                <div className="pequeno tenue" style={{ marginTop: 8 }}>Fecha de registro: {fecha(new Date().toISOString())} · estado inicial IDENTIFICADO</div>
              </div>
            </div>
          )}
        </motion.div>
      </AnimatePresence>
    </Modal>
  );
}

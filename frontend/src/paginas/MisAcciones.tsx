import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { CalendarClock, CheckCircle2, ListChecks, Paperclip, Save, Timer, TriangleAlert } from 'lucide-react';
import { api, type Fila } from '../nucleo/api';
import { useAvisos, useDatos } from '../nucleo/contexto';
import { etiqueta, fecha, numero } from '../nucleo/formato';
import { Dona } from '../componentes/Graficos';
import { Bandera, BarraAvance, Cargando, ChipEstado, Encabezado, Girando, Kpi, Modal, Tarjeta, Vacio, ZonaArrastre } from '../componentes/Ui';

/** CU05: el responsable registra el avance de sus acciones y adjunta evidencias. */
export default function MisAcciones() {
  const { datos, recargar } = useDatos<Fila[]>('/api/acciones/mias');
  const [elegida, setElegida] = useState<Fila | null>(null);
  const ir = useNavigate();
  const lista = datos ?? [];
  const abiertas = lista.filter((a) => a.estado !== 'CERRADA');
  const vencidas = lista.filter((a) => a.vencida);
  const promedio = abiertas.length ? abiertas.reduce((s, a) => s + Number(a.avance), 0) / abiertas.length : 100;
  return (
    <>
      <Encabezado icono={<ListChecks size={22} />} titulo="Mis acciones de mitigación (CU05)"
        texto="Registre el avance de cada acción. El avance no puede disminuir y, para cerrarla al 100 %, se exige al menos una evidencia." />
      <div className="rejilla r-4" style={{ marginBottom: 16 }}>
        <Kpi titulo="Acciones abiertas" valor={abiertas.length} icono={<ListChecks size={18} />} />
        <Kpi titulo="Vencidas" valor={vencidas.length} icono={<TriangleAlert size={18} />} color="var(--critico)" retraso={0.05} pie="generan alerta ACCION_VENCIDA" />
        <Kpi titulo="Cerradas" valor={lista.length - abiertas.length} icono={<CheckCircle2 size={18} />} color="var(--bajo)" retraso={0.1} />
        <Tarjeta retraso={0.15} className="fila" style={{ justifyContent: 'center' }}>
          <Dona tamano={110} grosor={14} datos={[{ nombre: 'Avance', valor: promedio, color: '#22d3ee' }, { nombre: 'Pendiente', valor: 100 - promedio, color: 'transparent' }]}
            centro={<div><strong style={{ fontSize: 20 }}>{numero(promedio, 0)} %</strong><div className="pequeno tenue">avance</div></div>} />
        </Tarjeta>
      </div>
      {!datos ? <Cargando filas={5} alto={80} /> : lista.length === 0 ? <Tarjeta><Vacio texto="No tiene acciones asignadas" /></Tarjeta> : (
        <div className="columna" style={{ gap: 10 }}>
          {lista.map((a, k) => (
            <motion.div key={a.id_accion} className="tarjeta" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: k * 0.04 }}
              style={{ borderLeft: `4px solid ${a.vencida ? 'var(--critico)' : a.estado === 'CERRADA' ? 'var(--bajo)' : 'var(--acento)'}`, padding: 16 }}>
              <div className="fila" style={{ gap: 16, alignItems: 'center' }}>
                <div className="crece" style={{ minWidth: 240 }}>
                  <div className="fila" style={{ gap: 8 }}><span className="codigo" style={{ cursor: 'pointer' }} onClick={() => ir(`/riesgos/${a.id_riesgo}`)}>{a.codigo}</span><ChipEstado estado={a.estado} />{a.estado_plan !== 'APROBADO' && a.estado !== 'CERRADA' && <span className="chip">plan {etiqueta(a.estado_plan).toLowerCase()}</span>}</div>
                  <strong style={{ display: 'block', margin: '6px 0 2px' }}>{a.descripcion}</strong>
                  <div className="pequeno tenue"><Bandera codigo={a.codigo_pais} /> {a.filial} · {a.titulo}</div>
                </div>
                <div style={{ width: 220 }}>
                  <div className="fila entre pequeno"><span>Avance</span><strong>{a.avance} %</strong></div>
                  <BarraAvance valor={a.avance} rayada={a.estado === 'EN_CURSO'} />
                  <div className="pequeno" style={{ marginTop: 6, color: a.vencida ? 'var(--critico)' : 'var(--texto-3)' }}>
                    <CalendarClock size={12} /> {fecha(a.fecha_limite)} · {a.estado === 'CERRADA' ? 'cerrada' : a.dias_restantes >= 0 ? `faltan ${a.dias_restantes} días` : `vencida hace ${-a.dias_restantes} días`}
                  </div>
                </div>
                <span className="chip"><Paperclip size={12} /> {a.evidencias}</span>
                <button className="boton" disabled={a.estado === 'CERRADA' || a.estado_plan !== 'APROBADO'} onClick={() => setElegida(a)}><Timer size={15} /> Registrar avance</button>
              </div>
            </motion.div>
          ))}
        </div>
      )}
      {elegida && <ModalAvance accion={elegida} cerrar={() => setElegida(null)} alGuardar={recargar} />}
    </>
  );
}

function ModalAvance({ accion, cerrar, alGuardar }: { accion: Fila; cerrar: () => void; alGuardar: () => void }) {
  const { avisar, error } = useAvisos();
  const [avance, setAvance] = useState(Number(accion.avance));
  const [comentario, setComentario] = useState('');
  const [archivo, setArchivo] = useState<File | null>(null);
  const [guardando, setGuardando] = useState(false);
  const guardar = async () => {
    setGuardando(true);
    try {
      if (archivo) {
        const fd = new FormData();
        fd.append('archivo', archivo);
        fd.append('descripcion', comentario || 'Evidencia del avance');
        await api.subir(`/api/acciones/${accion.id_accion}/evidencias`, fd);
      }
      const r = await api.post(`/api/acciones/${accion.id_accion}/avance`, { avance, comentario });
      avisar('exito', r.estado === 'CERRADA' ? 'Acción cerrada' : 'Avance registrado', `${accion.descripcion}: ${r.avance} %`);
      alGuardar();
      cerrar();
    } catch (e) {
      error(e);
    } finally {
      setGuardando(false);
    }
  };
  return (
    <Modal abierto cerrar={cerrar} icono={<Timer size={20} />} titulo="Registrar avance" subtitulo={accion.descripcion}
      pie={<><button className="boton" onClick={cerrar}>Cancelar</button><button className="boton primario" onClick={guardar} disabled={guardando || avance < accion.avance}>{guardando ? <Girando /> : <Save size={16} />} Guardar</button></>}>
      <div className="columna" style={{ gap: 18 }}>
        <div>
          <div className="fila entre"><span className="etiqueta-campo">Avance</span><strong style={{ fontSize: 26, fontFamily: 'var(--fuente-titulo)' }}>{avance} %</strong></div>
          <input type="range" aria-label="Porcentaje de avance" aria-valuetext={`${avance} %`} min={0} max={100} step={5} value={avance} onChange={(e) => setAvance(Math.max(Number(accion.avance), Number(e.target.value)))} style={{ width: '100%', accentColor: '#22d3ee' }} />
          <div className="pequeno tenue">Mínimo {accion.avance} %: el avance no puede disminuir (CU05)</div>
        </div>
        <textarea className="entrada" aria-label="Comentario del avance" placeholder="Comentario del avance" value={comentario} onChange={(e) => setComentario(e.target.value)} />
        <ZonaArrastre alElegir={setArchivo} texto={archivo ? `📎 ${archivo.name}` : 'Adjunte la evidencia (obligatoria para cerrar al 100 %)'} />
        {avance === 100 && Number(accion.evidencias) === 0 && !archivo && <div className="item-verificacion no pequeno"><TriangleAlert size={16} /> Para cerrar la acción debe adjuntar una evidencia.</div>}
      </div>
    </Modal>
  );
}

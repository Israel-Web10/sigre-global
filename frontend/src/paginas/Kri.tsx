import { useMemo, useState } from 'react';
import { motion } from 'framer-motion';
import { AppWindow, Gauge, PlusCircle, Radio, Settings2, SlidersHorizontal, Zap } from 'lucide-react';
import { api, conParametros, type Fila } from '../nucleo/api';
import { useAlEvento, useAvisos, useDatos, useSesion } from '../nucleo/contexto';
import { colorSemaforo, etiqueta, fechaHora, haceCuanto, numero } from '../nucleo/formato';
import { GraficoArea, Medidor, Sparkline } from '../componentes/Graficos';
import { Bandera, Cargando, Encabezado, Girando, Modal, Segmentado, Semaforo, Tarjeta, Vacio } from '../componentes/Ui';
import { useVentanas } from '../componentes/Ventanas';

/** CU06: indicadores clave de riesgo con semáforo (RN06) y mediciones del ERP, el SIEM o manuales. */
export default function Kri() {
  const { tiene } = useSesion();
  const { abrir } = useVentanas();
  const [fuente, setFuente] = useState('');
  const [semaforo, setSemaforo] = useState('');
  const { datos, recargar } = useDatos<Fila[]>(conParametros('/api/kri', { fuente, semaforo }));
  const [elegido, setElegido] = useState<Fila | null>(null);
  const [destello, setDestello] = useState<Record<number, number>>({});
  useAlEvento(['MEDICION'], (e) => {
    setDestello((d) => ({ ...d, [e.id_kri]: Date.now() }));
    recargar();
  });
  const conteo = useMemo(() => {
    const c: Record<string, number> = { VERDE: 0, AMARILLO: 0, ROJO: 0 };
    (datos ?? []).forEach((k) => k.semaforo && c[k.semaforo]++);
    return c;
  }, [datos]);

  return (
    <>
      <Encabezado icono={<Gauge size={22} />} titulo="Indicadores clave de riesgo (CU06)"
        texto="El semáforo lo calcula un trigger al registrar cada medición (RN06). Si el valor cruza un umbral se genera la alerta automáticamente (CU07).">
        <Segmentado valor={semaforo} cambiar={setSemaforo} opciones={[
          { valor: '', texto: 'Todos' },
          { valor: 'ROJO', texto: <><i style={{ width: 8, height: 8, borderRadius: 4, background: 'var(--critico)', display: 'inline-block' }} /> {conteo.ROJO}</> },
          { valor: 'AMARILLO', texto: <><i style={{ width: 8, height: 8, borderRadius: 4, background: 'var(--medio)', display: 'inline-block' }} /> {conteo.AMARILLO}</> },
          { valor: 'VERDE', texto: <><i style={{ width: 8, height: 8, borderRadius: 4, background: 'var(--bajo)', display: 'inline-block' }} /> {conteo.VERDE}</> },
        ]} />
        <select className="entrada" style={{ width: 150 }} value={fuente} onChange={(e) => setFuente(e.target.value)}>
          <option value="">Todas las fuentes</option><option value="ERP">ERP</option><option value="SIEM">SIEM</option><option value="MANUAL">Manual</option>
        </select>
      </Encabezado>
      {!datos ? <Cargando filas={4} alto={180} /> : datos.length === 0 ? <Vacio texto="Sin indicadores" /> : (
        <div className="rejilla r-auto">
          {datos.map((k, i) => (
            <div key={k.id_kri} style={{ position: 'relative', borderRadius: 16 }}>
              {destello[k.id_kri] && (
                <motion.span key={destello[k.id_kri]} initial={{ opacity: 1 }} animate={{ opacity: 0 }} transition={{ duration: 1.8 }}
                  style={{ position: 'absolute', inset: -3, borderRadius: 18, boxShadow: `0 0 0 3px ${colorSemaforo(k.semaforo)}, 0 0 30px ${colorSemaforo(k.semaforo)}`, pointerEvents: 'none', zIndex: 2 }} />
              )}
              <Tarjeta inclinar retraso={Math.min(i * 0.03, 0.4)} onClick={() => setElegido(k)} style={{ cursor: 'pointer', height: '100%' }}>
                <div className="fila entre" style={{ flexWrap: 'nowrap' }}>
                  <div style={{ minWidth: 0 }}>
                    <strong style={{ display: 'block', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{k.nombre}</strong>
                    <span className="pequeno tenue"><Bandera codigo={k.codigo_pais} /> {k.riesgo_codigo} · {k.filial}</span>
                  </div>
                  <Semaforo valor={k.semaforo} />
                </div>
                <div style={{ display: 'grid', placeItems: 'center', margin: '6px 0 -4px' }}>
                  <Medidor valor={k.ultimo_valor == null ? null : Number(k.ultimo_valor)} amarillo={Number(k.umbral_amarillo)} rojo={Number(k.umbral_rojo)} direccion={k.direccion} unidad={k.unidad} />
                </div>
                <div className="fila entre">
                  <span className="chip" style={{ color: k.fuente === 'SIEM' ? 'var(--acento-3)' : k.fuente === 'ERP' ? 'var(--acento)' : 'var(--texto-2)' }}>{k.fuente === 'MANUAL' ? 'Manual' : <><Radio size={11} /> {k.fuente}</>}</span>
                  <Sparkline datos={(k.serie ?? []).map(Number)} color={colorSemaforo(k.semaforo)} ancho={120} alto={32} />
                </div>
                <div className="pequeno tenue" style={{ marginTop: 6 }}>{haceCuanto(k.fecha_medicion)} · {k.alertas_abiertas > 0 ? <span style={{ color: 'var(--critico)' }}>{k.alertas_abiertas} alerta(s) abierta(s)</span> : 'sin alertas abiertas'}</div>
              </Tarjeta>
            </div>
          ))}
        </div>
      )}
      {elegido && <DetalleKri k={elegido} cerrar={() => setElegido(null)} recargar={recargar} puedeAdministrar={tiene('GESTOR', 'DIRECTOR', 'ADMIN')}
        alVentana={() => { abrir({ id: `kri-${elegido.id_kri}`, titulo: elegido.nombre, icono: <Gauge size={15} />, contenido: <GraficoKri id={elegido.id_kri} />, ancho: 640, alto: 420 }); setElegido(null); }} />}
    </>
  );
}

export function GraficoKri({ id }: { id: number }) {
  const { datos: d, recargar } = useDatos<Fila>(`/api/kri/${id}?limite=60`);
  useAlEvento(['MEDICION'], (e) => Number(e.id_kri) === id && recargar());
  if (!d) return <Cargando />;
  const m = d.mediciones as Fila[];
  return (
    <GraficoArea alto={260} formato={(v) => numero(v, 1)} etiquetas={m.map((x) => fechaHora(x.fecha))}
      series={[
        { nombre: d.kri.nombre, color: '#22d3ee', datos: m.map((x) => Number(x.valor)) },
        { nombre: 'Umbral rojo', color: '#ef4444', datos: m.map(() => Number(d.kri.umbral_rojo)) },
        { nombre: 'Umbral amarillo', color: '#eab308', datos: m.map(() => Number(d.kri.umbral_amarillo)) },
      ]} />
  );
}

function DetalleKri({ k, cerrar, recargar, puedeAdministrar, alVentana }: { k: Fila; cerrar: () => void; recargar: () => void; puedeAdministrar: boolean; alVentana: () => void }) {
  const { tiene } = useSesion();
  const { avisar, error } = useAvisos();
  const { datos: d, recargar: recargarDetalle } = useDatos<Fila>(`/api/kri/${k.id_kri}?limite=40`);
  const [valor, setValor] = useState('');
  const [umbrales, setUmbrales] = useState({ umbralAmarillo: k.umbral_amarillo, umbralRojo: k.umbral_rojo });
  const [ocupado, setOcupado] = useState(false);
  const hacer = async (f: () => Promise<any>, ok: (r: any) => void) => {
    setOcupado(true);
    try {
      ok(await f());
      recargar();
      recargarDetalle();
    } catch (e) {
      error(e);
    } finally {
      setOcupado(false);
    }
  };
  return (
    <Modal abierto cerrar={cerrar} ancho icono={<Gauge size={20} />} titulo={k.nombre} subtitulo={`${k.riesgo_codigo} · ${k.riesgo_titulo} · ${etiqueta(k.direccion)} · ${etiqueta(k.frecuencia)}`}
      pie={<button className="boton" onClick={alVentana}><AppWindow size={16} /> Abrir gráfico en ventana</button>}>
      {!d ? <Cargando /> : (
        <div className="columna" style={{ gap: 18 }}>
          <GraficoArea alto={230} formato={(v) => numero(v, 1)} etiquetas={d.mediciones.map((x: Fila) => fechaHora(x.fecha))}
            series={[
              { nombre: 'Valor', color: '#22d3ee', datos: d.mediciones.map((x: Fila) => Number(x.valor)) },
              { nombre: 'Umbral rojo', color: '#ef4444', datos: d.mediciones.map(() => Number(k.umbral_rojo)) },
              { nombre: 'Umbral amarillo', color: '#eab308', datos: d.mediciones.map(() => Number(k.umbral_amarillo)) },
            ]} />
          <div className="rejilla r-3">
            {tiene('RESPONSABLE', 'GESTOR', 'GERENTE_FILIAL') && (
              <div className="tarjeta" style={{ padding: 14 }}>
                <h3 style={{ marginBottom: 10 }}><PlusCircle size={15} /> Medición manual (CU06)</h3>
                <div className="fila" style={{ flexWrap: 'nowrap' }}>
                  <input className="entrada" type="number" placeholder={`Valor en ${k.unidad}`} value={valor} onChange={(e) => setValor(e.target.value)} />
                  <button className="boton primario" disabled={!valor || ocupado} onClick={() => hacer(() => api.post(`/api/kri/${k.id_kri}/mediciones`, { valor: Number(valor) }), (r) => {
                    setValor('');
                    avisar(r.semaforo === 'VERDE' ? 'exito' : 'alerta', `Semáforo ${etiqueta(r.semaforo)}`, r.alerta ? `Se generó la alerta #${r.alerta.id_alerta}: ${r.alerta.mensaje}` : 'Medición registrada');
                  })}>{ocupado ? <Girando /> : 'Registrar'}</button>
                </div>
              </div>
            )}
            {puedeAdministrar && (
              <div className="tarjeta" style={{ padding: 14 }}>
                <h3 style={{ marginBottom: 10 }}><SlidersHorizontal size={15} /> Umbrales (CP19)</h3>
                <div className="fila" style={{ flexWrap: 'nowrap' }}>
                  <input className="entrada" type="number" title="Amarillo" value={umbrales.umbralAmarillo} onChange={(e) => setUmbrales({ ...umbrales, umbralAmarillo: e.target.value })} style={{ borderColor: 'var(--medio)' }} />
                  <input className="entrada" type="number" title="Rojo" value={umbrales.umbralRojo} onChange={(e) => setUmbrales({ ...umbrales, umbralRojo: e.target.value })} style={{ borderColor: 'var(--critico)' }} />
                  <button className="boton" disabled={ocupado} onClick={() => hacer(() => api.put(`/api/kri/${k.id_kri}/umbrales`, { umbralAmarillo: Number(umbrales.umbralAmarillo), umbralRojo: Number(umbrales.umbralRojo) }), () => avisar('exito', 'Umbrales actualizados', 'El cambio quedó en la bitácora (MODIFICAR_UMBRAL)'))}><Settings2 size={15} /></button>
                </div>
              </div>
            )}
            {puedeAdministrar && k.fuente !== 'MANUAL' && (
              <div className="tarjeta" style={{ padding: 14 }}>
                <h3 style={{ marginBottom: 10 }}><Zap size={15} /> Simular {k.fuente}</h3>
                <div className="fila">
                  {['VERDE', 'AMARILLO', 'ROJO'].map((z) => (
                    <button key={z} className="boton chico" style={{ borderColor: colorSemaforo(z), color: colorSemaforo(z) }} disabled={ocupado}
                      onClick={() => hacer(() => api.post(`/api/integraciones/kri/${k.id_kri}/evento`, { zona: z }), (r) => avisar('info', `${k.fuente} publicó ${numero(r.valor)} ${k.unidad}`, `Semáforo ${etiqueta(r.semaforo)}`))}>{etiqueta(z)}</button>
                  ))}
                </div>
              </div>
            )}
          </div>
          <div className="tabla-contenedor" style={{ maxHeight: 220 }}>
            <table className="tabla">
              <thead><tr><th>Fecha</th><th className="num">Valor</th><th>Semáforo</th><th>Origen</th><th>Usuario</th></tr></thead>
              <tbody>
                {[...d.mediciones].reverse().slice(0, 25).map((m: Fila) => (
                  <tr key={m.id_medicion}><td>{fechaHora(m.fecha)}</td><td className="num"><strong>{numero(m.valor)}</strong> {k.unidad}</td><td><Semaforo valor={m.semaforo} /></td><td>{etiqueta(m.origen)}</td><td className="tenue">{m.usuario}</td></tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </Modal>
  );
}

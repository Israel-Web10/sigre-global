import { useState } from 'react';
import { motion } from 'framer-motion';
import { Database, Fingerprint, Lock, Search, ScrollText, ShieldCheck, ShieldX } from 'lucide-react';
import { api, conParametros, type Fila } from '../nucleo/api';
import { useAvisos, useDatos } from '../nucleo/contexto';
import { etiqueta, fechaHora, numero, operacion } from '../nucleo/formato';
import { Barras, GraficoArea } from '../componentes/Graficos';
import { Cargando, ChipEstado, Encabezado, Girando, Kpi, Modal, Tarjeta } from '../componentes/Ui';

/** Muestra los cambios (antes → después) que registró el trigger fn_auditar. */
export function Diferencias({ cambios }: { cambios: Record<string, { antes: unknown; despues: unknown }> }) {
  const v = (x: unknown) => (x === null || x === undefined ? '∅' : typeof x === 'object' ? JSON.stringify(x) : String(x));
  return (
    <div className="diff">
      {Object.entries(cambios).map(([k, c]) => (
        <div key={k} className="diff-fila">
          <div className="tenue">{k}</div>
          <div className="antes">− {v(c?.antes)}</div>
          <div className="despues">+ {v(c?.despues)}</div>
        </div>
      ))}
    </div>
  );
}

export default function Bitacora() {
  const { error } = useAvisos();
  const [f, setF] = useState<Fila>({ pagina: 1 });
  const [detalle, setDetalle] = useState<Fila | null>(null);
  const [prueba, setPrueba] = useState<Fila | null>(null);
  const [probando, setProbando] = useState(false);
  const { datos, cargando, recargar } = useDatos<Fila>(conParametros('/api/bitacora', { ...f, tamano: 40 }));
  const { datos: res } = useDatos<Fila>('/api/bitacora/resumen');
  const cambiar = (k: string, v: unknown) => setF((x) => ({ ...x, [k]: v, pagina: k === 'pagina' ? v : 1 }));

  const probar = async () => {
    setProbando(true);
    try {
      setPrueba(await api.post('/api/bitacora/prueba-inalterable'));
      recargar();
    } catch (e) {
      error(e);
    } finally {
      setProbando(false);
    }
  };

  const t = res?.totales ?? {};
  const paginas = Math.max(1, Math.ceil(Number(datos?.total ?? 0) / 40));
  return (
    <>
      <Encabezado icono={<ScrollText size={22} />} titulo="Bitácora de auditoría (CU16)"
        texto="Cada inserción, modificación o borrado de las tablas principales lo registra un trigger de PostgreSQL con el usuario, la fecha y los valores antes y después. La tabla solo admite inserciones (RN13).">
        <button className="boton primario" onClick={probar} disabled={probando}>{probando ? <Girando /> : <Fingerprint size={16} />} Probar que es inalterable</button>
      </Encabezado>

      <div className="rejilla r-4" style={{ marginBottom: 16 }}>
        <Kpi titulo="Registros" valor={Number(t.registros ?? 0)} icono={<Database size={18} />} pie={<>desde {fechaHora(t.desde)}</>} />
        <Kpi titulo="Registrados por triggers" valor={Number(t.por_trigger ?? 0)} icono={<ShieldCheck size={18} />} color="var(--bajo)" retraso={0.05} pie="fn_auditar en 19 tablas" />
        <Kpi titulo="Intentos rechazados" valor={Number(t.rechazados ?? 0)} icono={<ShieldX size={18} />} color="var(--critico)" retraso={0.1} pie="reglas de negocio que impidieron la operación" />
        <Kpi titulo="Hoy" valor={Number(t.hoy ?? 0)} icono={<Lock size={18} />} color="var(--acento-2)" retraso={0.15} pie={`${numero(t.por_aplicacion, 0)} registros de la aplicación`} />
      </div>

      <div className="rejilla r-3" style={{ marginBottom: 16 }}>
        <Tarjeta className="span-2">
          <h3 style={{ marginBottom: 8 }}>Actividad de los últimos 14 días</h3>
          <GraficoArea alto={180} etiquetas={(res?.por_dia ?? []).map((x: Fila) => x.dia.slice(5))} series={[{ nombre: 'Operaciones', color: '#22d3ee', datos: (res?.por_dia ?? []).map((x: Fila) => Number(x.total)) }]} />
        </Tarjeta>
        <Tarjeta>
          <h3 style={{ marginBottom: 12 }}>Operaciones más frecuentes</h3>
          <Barras datos={(res?.por_operacion ?? []).slice(0, 7).map((o: Fila) => ({ nombre: operacion(o.operacion), valor: Number(o.total) }))} />
        </Tarjeta>
      </div>

      <Tarjeta style={{ padding: 12, marginBottom: 12 }}>
        <div className="fila">
          <div className="con-icono crece" style={{ minWidth: 220 }}><Search size={16} /><input className="entrada" placeholder="Buscar en el detalle, la operación o el usuario" value={f.q ?? ''} onChange={(e) => cambiar('q', e.target.value)} /></div>
          <select className="entrada" style={{ width: 200 }} aria-label="Filtrar por operación" value={f.operacion ?? ''} onChange={(e) => cambiar('operacion', e.target.value)}>
            <option value="">Todas las operaciones</option>{(res?.operaciones ?? []).map((o: string) => <option key={o} value={o}>{operacion(o)}</option>)}
          </select>
          <select className="entrada" style={{ width: 180 }} aria-label="Filtrar por entidad" value={f.entidad ?? ''} onChange={(e) => cambiar('entidad', e.target.value)}>
            <option value="">Todas las entidades</option>{(res?.entidades ?? []).map((o: string) => <option key={o}>{o}</option>)}
          </select>
          <select className="entrada" style={{ width: 140 }} aria-label="Filtrar por resultado" value={f.resultado ?? ''} onChange={(e) => cambiar('resultado', e.target.value)}>
            <option value="">Todo resultado</option><option value="OK">Correcto</option><option value="RECHAZADO">Rechazado</option><option value="FALLIDO">Fallido</option>
          </select>
          <input className="entrada" type="date" style={{ width: 150 }} value={f.desde ?? ''} onChange={(e) => cambiar('desde', e.target.value)} title="Desde" />
          <input className="entrada" type="date" style={{ width: 150 }} value={f.hasta ?? ''} onChange={(e) => cambiar('hasta', e.target.value)} title="Hasta" />
        </div>
      </Tarjeta>

      <Tarjeta style={{ padding: 0 }}>
        {cargando && !datos ? <div style={{ padding: 16 }}><Cargando filas={8} /></div> : (
          <div className="tabla-contenedor" style={{ border: 0 }}>
            <table className="tabla">
              <thead><tr><th>N.º</th><th>Fecha y hora</th><th>Usuario</th><th>Operación</th><th>Entidad</th><th>Resultado</th><th>Origen</th><th>Detalle</th></tr></thead>
              <tbody>
                {(datos?.registros ?? []).map((b: Fila, i: number) => (
                  <motion.tr key={b.id_bitacora} className="clic" onClick={() => setDetalle(b)} initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: Math.min(i * 0.01, 0.3) }}>
                    <td className="mono tenue">{b.id_bitacora}</td>
                    <td style={{ whiteSpace: 'nowrap' }}>{fechaHora(b.fecha_hora)}</td>
                    <td>{b.usuario}</td>
                    <td><strong>{operacion(b.operacion)}</strong></td>
                    <td><span className="chip">{b.entidad}{b.id_entidad ? ` #${b.id_entidad}` : ''}</span></td>
                    <td><ChipEstado estado={b.resultado} /></td>
                    <td className="pequeno tenue">{etiqueta(b.origen)}</td>
                    <td className="pequeno tenue" style={{ maxWidth: 320, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                      {b.detalle?.cambios ? Object.keys(b.detalle.cambios).join(', ') : b.detalle?.motivo ?? (b.detalle?.registro ? 'registro completo' : JSON.stringify(b.detalle ?? {}))}
                    </td>
                  </motion.tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <div className="fila entre" style={{ padding: 12 }}>
          <span className="pequeno tenue">{numero(datos?.total, 0)} registros · página {f.pagina} de {paginas}</span>
          <div className="fila">
            <button className="boton chico" disabled={f.pagina <= 1} onClick={() => cambiar('pagina', f.pagina - 1)}>Anterior</button>
            <button className="boton chico" disabled={f.pagina >= paginas} onClick={() => cambiar('pagina', f.pagina + 1)}>Siguiente</button>
          </div>
        </div>
      </Tarjeta>

      <Modal abierto={!!detalle} cerrar={() => setDetalle(null)} ancho icono={<ScrollText size={20} />} titulo={detalle ? `${operacion(detalle.operacion)} · #${detalle.id_bitacora}` : ''}
        subtitulo={detalle ? `${detalle.usuario} · ${fechaHora(detalle.fecha_hora)} · ${detalle.entidad}${detalle.id_entidad ? ` #${detalle.id_entidad}` : ''}` : ''}>
        {detalle && (
          <div className="columna">
            <div className="fila"><ChipEstado estado={detalle.resultado} /><span className="chip">Origen: {etiqueta(detalle.origen)}</span></div>
            {detalle.detalle?.cambios && <Diferencias cambios={detalle.detalle.cambios} />}
            <pre className="consola" style={{ height: 'auto', maxHeight: 360, whiteSpace: 'pre-wrap' }}>{JSON.stringify(detalle.detalle, null, 2)}</pre>
          </div>
        )}
      </Modal>

      <Modal abierto={!!prueba} cerrar={() => setPrueba(null)} ancho icono={<Fingerprint size={20} />} titulo="Prueba de inalterabilidad (RN13)"
        subtitulo={prueba ? `Se intentó alterar el registro #${prueba.registro_probado}. Todo se revirtió y el intento quedó registrado.` : ''}>
        {prueba && (
          <div className="columna" style={{ gap: 10 }}>
            {prueba.capas.map((c: Fila, k: number) => (
              <motion.div key={k} className={`item-verificacion ${c.bloqueado ? 'ok' : 'no'}`} initial={{ opacity: 0, x: -20 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: k * 0.25 }}>
                <motion.span initial={{ scale: 0 }} animate={{ scale: 1 }} transition={{ delay: 0.2 + k * 0.25, type: 'spring' }}>
                  {c.bloqueado ? <ShieldCheck size={24} color="var(--bajo)" /> : <ShieldX size={24} color="var(--critico)" />}
                </motion.span>
                <div className="crece">
                  <div className="fila" style={{ gap: 8 }}><strong>{c.capa}</strong><span className="chip">{c.identidad}</span>{c.sqlstate && <span className="chip mono">SQLSTATE {c.sqlstate}</span>}</div>
                  <code className="tenue">{c.sentencia}</code>
                  <div className="pequeno" style={{ color: c.bloqueado ? 'var(--bajo)' : 'var(--critico)', marginTop: 4 }}>{c.bloqueado ? 'Bloqueado: ' : ''}{c.mensaje}</div>
                </div>
              </motion.div>
            ))}
            <div className="tarjeta" style={{ padding: 14, borderColor: prueba.protegida ? 'var(--bajo)' : 'var(--critico)' }}>
              {prueba.protegida ? 'La bitácora está protegida en dos capas: los permisos de la base de datos (el rol de la aplicación no tiene UPDATE ni DELETE) y el trigger trg_bitacora, que rechaza cualquier cambio incluso del dueño del esquema.' : 'Atención: alguna capa permitió la operación.'}
            </div>
          </div>
        )}
      </Modal>
    </>
  );
}

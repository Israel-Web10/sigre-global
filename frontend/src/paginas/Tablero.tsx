import { lazy, Suspense, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AnimatePresence, motion } from 'framer-motion';
import {
  Activity, AlertOctagon, AppWindow, BadgeDollarSign, Bell, BookOpenCheck, Flame, Gauge, Globe2, LayoutDashboard, RefreshCw,
  ShieldAlert, TrendingUp, Zap,
} from 'lucide-react';
import { conParametros, type Fila } from '../nucleo/api';
import { useAlEvento, useDatos, useEventos, useSesion } from '../nucleo/contexto';
import { banderaHtml, colorNivel, colorSemaforo, colorValor, etiqueta, haceCuanto, nivelDe, numero, operacion, usd } from '../nucleo/formato';
import { Barras, Dona, GraficoArea, MapaCalor, Sparkline } from '../componentes/Graficos';
import { Bandera, Cargando, ChipEstado, ChipNivel, Kpi, Segmentado, Tarjeta, Vacio } from '../componentes/Ui';
import { useVentanas } from '../componentes/Ventanas';

const Globo = lazy(() => import('../componentes/Globo'));

const CATEGORIAS = ['', 'FINANCIERO', 'TECNOLOGICO', 'LEGAL', 'OPERATIVO', 'REPUTACIONAL'];
const COLOR_CAT: Record<string, string> = { FINANCIERO: '#22d3ee', TECNOLOGICO: '#6366f1', LEGAL: '#a855f7', OPERATIVO: '#f97316', REPUTACIONAL: '#ec4899' };

export default function Tablero() {
  const { usuario } = useSesion();
  const { ultimos } = useEventos();
  const { abrir } = useVentanas();
  const ir = useNavigate();
  const [filial, setFilial] = useState<string>('');
  const [categoria, setCategoria] = useState('');
  const { datos: d, cargando, recargar } = useDatos<Fila>(conParametros('/api/tablero', { filial, categoria }));
  const espera = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  useAlEvento(['MEDICION', 'ALERTA_GENERADA', 'ALERTA_ACTUALIZADA', 'EVALUACION', 'PLAN', 'VALIDACION', 'CUMPLIMIENTO'], () => {
    clearTimeout(espera.current);
    espera.current = setTimeout(recargar, 2500);
  });

  const puntos = useMemo(
    () => (d?.por_filial ?? []).map((f: Fila) => ({
      id: f.id_filial, nombre: f.nombre, lat: Number(f.latitud), lon: Number(f.longitud), matriz: f.casa_matriz, valor: Number(f.valor_maximo ?? 0),
      color: f.valor_maximo ? colorValor(Number(f.valor_maximo)) : 'var(--acento)', etiqueta: `${banderaHtml(f.codigo_pais)} ${f.ciudad} · ${f.riesgos}`,
    })),
    [d]
  );

  const k = d?.kpi ?? {};
  const hora = new Date().getHours();
  const saludo = hora < 12 ? 'Buenos días' : hora < 19 ? 'Buenas tardes' : 'Buenas noches';

  const abrirMapa = () =>
    abrir({
      id: 'mapa-calor', titulo: 'Mapa de calor corporativo', icono: <Flame size={15} />, ancho: 520, alto: 560,
      contenido: <MapaCalor celdas={d?.matriz ?? []} alElegir={(p, i) => ir(`/riesgos?p=${p}&i=${i}`)} />,
    });

  return (
    <div className="columna" style={{ gap: 18 }}>
      <motion.div className="encabezado-pagina saludo" initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }} style={{ marginBottom: 0 }}>
        <div className="textos">
          <h1>{saludo}, {usuario?.nombre.split(' ')[0]} <span className="ola">👋</span></h1>
          <p>
            Panorama de riesgos {usuario?.corporativo ? 'de todo el grupo' : `de ${usuario?.filial}`} al {new Date().toLocaleDateString('es-BO', { dateStyle: 'long' })}.
            Los indicadores se actualizan solos cuando llegan mediciones del ERP y del SIEM.
          </p>
        </div>
        <div className="acciones">
          {usuario?.corporativo && (
            <select className="entrada" style={{ width: 210 }} value={filial} onChange={(e) => setFilial(e.target.value)}>
              <option value="">Todas las filiales</option>
              {(d?.por_filial ?? []).map((f: Fila) => <option key={f.id_filial} value={f.id_filial}>{f.nombre}</option>)}
            </select>
          )}
          <button className="boton" onClick={abrirMapa}><AppWindow size={16} /> Mapa en ventana</button>
          <button className="boton" onClick={recargar}><RefreshCw size={16} className={cargando ? 'girando' : ''} /></button>
        </div>
      </motion.div>

      <div className="fila entre">
        <Segmentado valor={categoria} cambiar={setCategoria} opciones={CATEGORIAS.map((c) => ({ valor: c, texto: c ? etiqueta(c) : 'Todas' }))} />
        <div className="ticker crece ocultar-movil" style={{ maxWidth: 760 }}>
          <span className="ticker-item" style={{ color: 'var(--acento)' }}><Zap size={13} /> Flujo en vivo</span>
          <AnimatePresence initial={false}>
            {ultimos.filter((e) => e.evento === 'MEDICION' || e.evento === 'ALERTA_GENERADA').slice(0, 5).map((e) => (
              <motion.span key={e._recibido + e.evento + (e.id_kri ?? e.id_alerta)} className="ticker-item" initial={{ opacity: 0, x: -20 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0 }}>
                {e.evento === 'MEDICION' ? (
                  <><i style={{ width: 8, height: 8, borderRadius: 4, background: colorSemaforo(e.semaforo) }} /> {e.origen} · {e.kri} <b>{numero(e.valor)} {e.unidad}</b></>
                ) : (
                  <><Bell size={12} color="var(--critico)" /> Alerta {etiqueta(e.severidad).toLowerCase()}: {String(e.mensaje).slice(0, 48)}…</>
                )}
              </motion.span>
            ))}
          </AnimatePresence>
        </div>
      </div>

      {!d ? <Cargando filas={5} alto={120} /> : (
        <>
          <div className="rejilla r-6">
            <Kpi titulo="Riesgos activos" valor={k.riesgos_activos} icono={<ShieldAlert size={18} />} retraso={0} onClick={() => ir('/riesgos')}
              pie={<>{k.sin_evaluar} sin evaluar · {k.sobre_apetito} sobre el apetito</>} />
            <Kpi titulo="Críticos y altos" valor={Number(k.criticos ?? 0) + Number(k.altos ?? 0)} icono={<Flame size={18} />} color="var(--critico)" retraso={0.05}
              pie={<><span style={{ color: 'var(--critico)' }}>{k.criticos} críticos</span> · {k.altos} altos</>} onClick={() => ir('/riesgos?nivel=CRITICO')} />
            <Kpi titulo="Alertas abiertas" valor={k.alertas_abiertas} icono={<Bell size={18} />} color="var(--alto)" retraso={0.1} onClick={() => ir('/alertas')}
              pie={<><span style={{ color: k.sla_vencidas ? 'var(--critico)' : undefined }}>{k.sla_vencidas} con SLA vencido</span> · {k.escaladas} escaladas</>} />
            <Kpi titulo="KRI en rojo" valor={k.kri_rojos} icono={<Gauge size={18} />} color="var(--medio)" retraso={0.15} onClick={() => ir('/kri')}
              pie={<>{k.kri_amarillos} en amarillo de {k.kri_total}</>} />
            <Kpi titulo="Cumplimiento" valor={Number(k.cumplimiento_pct ?? 0)} decimales={1} sufijo=" %" icono={<BookOpenCheck size={18} />} color="var(--bajo)" retraso={0.2}
              pie={<>{k.obligaciones_por_vencer} vencen en 30 días</>} onClick={() => ir('/cumplimiento')} />
            <Kpi titulo="Exposición validada" texto={usd(k.impacto_usd_validado, true)} icono={<BadgeDollarSign size={18} />} color="var(--acento-2)" retraso={0.25}
              pie={<>de {usd(k.impacto_usd_total, true)} · {k.validaciones_pendientes} por validar</>} />
          </div>

          <div className="rejilla r-3">
            <Tarjeta className="span-2" brillante style={{ padding: 0, overflow: 'hidden' }}>
              <div className="tarjeta-cabecera" style={{ padding: '16px 18px 0', marginBottom: 0 }}>
                <h2><Globe2 size={18} /> Red de filiales</h2>
                <p className="ocultar-movil">Altura y color de cada columna: nivel del riesgo más alto · arrastre para girar</p>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1.6fr) minmax(0,1fr)', minHeight: 420 }} className="rejilla-globo">
                <div style={{ height: 430 }}>
                  <Suspense fallback={<Cargando filas={1} alto={400} />}>
                    <Globo puntos={puntos} zoom={0.8} alAlternar={(p) => setFilial(String(p.id))} />
                  </Suspense>
                </div>
                <div style={{ padding: '8px 14px 14px', overflow: 'auto', maxHeight: 430 }}>
                  {(d.por_filial ?? []).map((f: Fila, i: number) => (
                    <motion.div key={f.id_filial} className={`filial-fila ${String(f.id_filial) === filial ? 'activa' : ''}`} onClick={() => setFilial(String(f.id_filial) === filial ? '' : String(f.id_filial))}
                      initial={{ opacity: 0, x: 10 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: i * 0.03 }}>
                      <Bandera codigo={f.codigo_pais} />
                      <div className="crece" style={{ minWidth: 0 }}>
                        <div style={{ fontWeight: 600, fontSize: 13, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{f.nombre}</div>
                        <div className="pequeno tenue">{f.riesgos} riesgos · {f.alertas} alertas · apetito {f.apetito_riesgo}</div>
                      </div>
                      {f.valor_maximo ? <span className="chip solido" style={{ background: colorValor(f.valor_maximo) }}>{f.valor_maximo}</span> : <span className="chip">—</span>}
                    </motion.div>
                  ))}
                </div>
              </div>
            </Tarjeta>
            <Tarjeta retraso={0.1}>
              <div className="tarjeta-cabecera">
                <h2><Flame size={18} /> Mapa de calor</h2>
                <div className="acciones"><span className="pequeno tenue">RN02 · P × I</span></div>
              </div>
              <MapaCalor celdas={d.matriz ?? []} alElegir={(p, i) => ir(`/riesgos?p=${p}&i=${i}`)} />
              <div className="fila" style={{ marginTop: 12, justifyContent: 'center', gap: 12 }}>
                {['BAJO', 'MEDIO', 'ALTO', 'CRITICO'].map((n) => (
                  <span key={n} className="fila pequeno suave" style={{ gap: 5 }}><i style={{ width: 10, height: 10, borderRadius: 3, background: colorNivel(n), display: 'inline-block' }} />{etiqueta(n)}</span>
                ))}
              </div>
            </Tarjeta>
          </div>

          <div className="rejilla r-3">
            <Tarjeta className="span-2" retraso={0.05}>
              <div className="tarjeta-cabecera">
                <h2><TrendingUp size={18} /> Alertas de los últimos 30 días</h2>
                <div className="acciones"><span className="chip" style={{ color: 'var(--bajo)' }}>{numero(k.sla_cumplido_pct ?? 0, 1)} % atendidas en plazo (RN07)</span></div>
              </div>
              <GraficoArea
                etiquetas={(d.tendencia_alertas ?? []).map((x: Fila) => new Date(`${x.dia}T12:00:00`).toLocaleDateString('es-BO', { day: '2-digit', month: 'short' }))}
                series={[
                  { nombre: 'Críticas', color: '#ef4444', datos: (d.tendencia_alertas ?? []).map((x: Fila) => Number(x.criticas)) },
                  { nombre: 'Advertencias', color: '#f97316', datos: (d.tendencia_alertas ?? []).map((x: Fila) => Number(x.advertencias)) },
                  { nombre: 'Preventivas', color: '#38bdf8', datos: (d.tendencia_alertas ?? []).map((x: Fila) => Number(x.preventivas)) },
                ]}
              />
            </Tarjeta>
            <Tarjeta retraso={0.1}>
              <div className="tarjeta-cabecera"><h2><LayoutDashboard size={18} /> Por categoría</h2></div>
              <div className="fila" style={{ gap: 18, alignItems: 'center', justifyContent: 'center' }}>
                <Dona datos={(d.por_categoria ?? []).map((c: Fila) => ({ nombre: etiqueta(c.categoria), valor: Number(c.total), color: COLOR_CAT[c.categoria] }))}
                  centro={<div><div style={{ fontFamily: 'var(--fuente-titulo)', fontSize: 30, fontWeight: 700 }}>{k.riesgos_activos}</div><div className="pequeno tenue">riesgos</div></div>} />
                <div className="columna crece" style={{ gap: 8, minWidth: 150 }}>
                  {(d.por_categoria ?? []).map((c: Fila) => (
                    <div key={c.categoria} className="fila entre pequeno" onClick={() => setCategoria(c.categoria)} style={{ cursor: 'pointer' }}>
                      <span className="fila" style={{ gap: 6 }}><i style={{ width: 9, height: 9, borderRadius: 3, background: COLOR_CAT[c.categoria], display: 'inline-block' }} />{etiqueta(c.categoria)}</span>
                      <strong>{c.total}</strong>
                    </div>
                  ))}
                </div>
              </div>
            </Tarjeta>
          </div>

          <div className="rejilla r-3">
            <Tarjeta className="span-2" retraso={0.05}>
              <div className="tarjeta-cabecera">
                <h2><AlertOctagon size={18} /> Riesgos de mayor nivel</h2>
                <div className="acciones"><button className="boton chico" onClick={() => ir('/riesgos')}>Ver catálogo</button></div>
              </div>
              <div className="tabla-contenedor">
                <table className="tabla">
                  <thead><tr><th>Riesgo</th><th>Filial</th><th>Nivel</th><th>Estado</th><th className="num">Impacto</th><th>Avance</th></tr></thead>
                  <tbody>
                    {(d.top_riesgos ?? []).map((r: Fila) => (
                      <tr key={r.id_riesgo} className="clic" onClick={() => ir(`/riesgos/${r.id_riesgo}`)}>
                        <td><div className="codigo">{r.codigo}</div><div style={{ maxWidth: 280, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{r.titulo}</div></td>
                        <td><Bandera codigo={r.codigo_pais} /> {r.filial}</td>
                        <td><ChipNivel nivel={r.nivel} valor={r.valor} /></td>
                        <td><ChipEstado estado={r.estado} /></td>
                        <td className="num">{usd(r.impacto_usd, true)}</td>
                        <td style={{ minWidth: 90 }}>{r.avance_plan != null ? <><div className="barra-avance"><span style={{ width: `${r.avance_plan}%` }} /></div><span className="pequeno tenue">{r.avance_plan} %</span></> : <span className="tenue">—</span>}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </Tarjeta>
            <Tarjeta retraso={0.1}>
              <div className="tarjeta-cabecera"><h2><Gauge size={18} /> KRI fuera de umbral</h2></div>
              {(d.kri_criticos ?? []).length === 0 ? <Vacio texto="Todos los indicadores están en verde" /> : (
                <div className="columna" style={{ gap: 12 }}>
                  {(d.kri_criticos ?? []).map((x: Fila) => (
                    <div key={x.id_kri} className="fila" style={{ gap: 12, flexWrap: 'nowrap', cursor: 'pointer' }} onClick={() => ir('/kri')}>
                      <span style={{ width: 10, height: 10, borderRadius: 5, background: colorSemaforo(x.semaforo), flexShrink: 0 }} className={x.semaforo === 'ROJO' ? 'pulso-rojo' : ''} />
                      <div className="crece">
                        <div style={{ fontSize: 13, fontWeight: 600 }}>{x.nombre}</div>
                        <div className="pequeno tenue"><Bandera codigo={x.codigo_pais} /> {x.codigo} · {x.fuente}</div>
                      </div>
                      <Sparkline datos={(x.serie ?? []).map(Number)} color={colorSemaforo(x.semaforo)} ancho={80} alto={30} />
                      <strong style={{ color: colorSemaforo(x.semaforo), minWidth: 60, textAlign: 'right' }}>{numero(x.valor)} <span className="pequeno tenue">{x.unidad}</span></strong>
                    </div>
                  ))}
                </div>
              )}
            </Tarjeta>
          </div>

          <div className="rejilla r-3">
            <Tarjeta retraso={0.05}>
              <div className="tarjeta-cabecera"><h2><TrendingUp size={18} /> Nivel inherente vs residual</h2></div>
              <GraficoArea alto={190} formato={(v) => numero(v, 1)}
                etiquetas={(d.tendencia_riesgos ?? []).map((x: Fila) => x.mes.slice(2))}
                series={[
                  { nombre: 'Inherente', color: '#f97316', datos: (d.tendencia_riesgos ?? []).map((x: Fila) => Number(x.nivel_inherente ?? 0)) },
                  { nombre: 'Residual', color: '#22c55e', datos: (d.tendencia_riesgos ?? []).map((x: Fila) => Number(x.nivel_residual ?? 0)) },
                ]} />
            </Tarjeta>
            <Tarjeta retraso={0.1}>
              <div className="tarjeta-cabecera"><h2><BookOpenCheck size={18} /> Cumplimiento por normativa</h2></div>
              <Barras formato={(v) => `${numero(v, 0)} %`}
                datos={(d.cumplimiento ?? []).slice(0, 6).map((c: Fila) => ({
                  nombre: <span title={c.nombre}>{String(c.nombre).slice(0, 34)}{c.nombre.length > 34 ? '…' : ''}</span>,
                  valor: Number(c.porcentaje ?? 0),
                  color: Number(c.porcentaje ?? 0) >= 80 ? 'var(--bajo)' : Number(c.porcentaje ?? 0) >= 50 ? 'var(--medio)' : 'var(--critico)',
                }))} />
            </Tarjeta>
            <Tarjeta retraso={0.15}>
              <div className="tarjeta-cabecera"><h2><Activity size={18} /> Actividad reciente</h2><div className="acciones"><span className="pequeno tenue">bitácora RN13</span></div></div>
              <div className="linea-tiempo" style={{ maxHeight: 260, overflow: 'auto' }}>
                {(d.actividad ?? []).map((a: Fila) => (
                  <div key={a.id_bitacora} className="hito" style={{ ['--c' as any]: a.resultado === 'OK' ? 'var(--acento)' : 'var(--critico)' }}>
                    <strong>{operacion(a.operacion)}</strong>
                    <p>{a.usuario} · {haceCuanto(a.fecha_hora)}{a.resultado !== 'OK' && <span style={{ color: 'var(--critico)' }}> · {etiqueta(a.resultado)}</span>}</p>
                  </div>
                ))}
              </div>
            </Tarjeta>
          </div>
          <div className="pequeno tenue centrado">Nivel promedio del portafolio: {numero(k.nivel_promedio, 1)} ({etiqueta(nivelDe(Number(k.nivel_promedio ?? 0)))}) · {k.planes_por_aprobar} planes por aprobar · {k.evaluaciones_vencidas} evaluaciones vencidas (RN03)</div>
        </>
      )}
    </div>
  );
}

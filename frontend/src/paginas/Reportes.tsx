import { useState } from 'react';
import { motion } from 'framer-motion';
import { BarChart3, Download, FileSpreadsheet, FileText, Printer, ShieldCheck } from 'lucide-react';
import { descargar, type Fila } from '../nucleo/api';
import { useAvisos, useDatos, useSesion } from '../nucleo/contexto';
import { Encabezado, Girando, Tarjeta, Vacio } from '../componentes/Ui';

const COLORES = ['#22d3ee', '#6366f1', '#a855f7', '#f97316', '#22c55e', '#ec4899'];

/** CU14: reportes exportables a Excel (RF22, RF24). La exportación queda en la bitácora. */
export default function Reportes() {
  const { usuario } = useSesion();
  const { error, avisar } = useAvisos();
  const { datos } = useDatos<Fila[]>('/api/reportes');
  const { datos: cat } = useDatos<Fila>('/api/catalogos');
  const [filial, setFilial] = useState('');
  const [bajando, setBajando] = useState<string | null>(null);
  const bajar = async (r: Fila, formato: 'xlsx' | 'pdf' | 'csv' = 'xlsx') => {
    setBajando(`${r.clave}.${formato}`);
    try {
      await descargar(`/api/reportes/${r.clave}.${formato}${filial ? `?filial=${filial}` : ''}`, `SIGRE_${r.clave}_${new Date().toISOString().slice(0, 10)}.${formato}`);
      avisar('exito', 'Reporte generado', `${r.titulo} (${formato.toUpperCase()}). La exportación quedó registrada en la bitácora.`);
    } catch (e) {
      error(e);
    } finally {
      setBajando(null);
    }
  };
  return (
    <>
      <Encabezado icono={<BarChart3 size={22} />} titulo="Reportes" texto="Reportes en Excel, PDF o CSV listos para el comité de riesgos (RF18, RF24). El alcance respeta la filial del usuario (RN14).">
        {usuario?.corporativo && (
          <select className="entrada" style={{ width: 220 }} aria-label="Filial del reporte" value={filial} onChange={(e) => setFilial(e.target.value)}>
            <option value="">Todas las filiales</option>{(cat?.filiales ?? []).map((f: Fila) => <option key={f.id_filial} value={f.id_filial}>{f.nombre}</option>)}
          </select>
        )}
        <button className="boton" onClick={() => window.print()}><Printer size={16} /> Imprimir pantalla</button>
      </Encabezado>
      {!datos ? null : datos.length === 0 ? <Vacio texto="Su rol no tiene reportes disponibles" /> : (
        <div className="rejilla r-3">
          {datos.map((r, k) => (
            <Tarjeta key={r.clave} inclinar retraso={k * 0.06} style={{ overflow: 'hidden' }}>
              <motion.div style={{ position: 'absolute', right: -30, top: -30, width: 120, height: 120, borderRadius: '50%', background: COLORES[k % COLORES.length], opacity: 0.12 }}
                animate={{ scale: [1, 1.15, 1] }} transition={{ duration: 5 + k, repeat: Infinity }} />
              <span className="icono-titulo" style={{ color: COLORES[k % COLORES.length], background: `${COLORES[k % COLORES.length]}1f` }}><FileSpreadsheet size={20} /></span>
              <h3 style={{ margin: '14px 0 6px' }}>{r.titulo}</h3>
              <p className="pequeno suave" style={{ minHeight: 38 }}>{r.descripcion}</p>
              <div className="fila" style={{ marginTop: 14, gap: 8, flexWrap: 'nowrap' }}>
                <button className="boton primario crece" disabled={!!bajando} onClick={() => bajar(r, 'xlsx')}>
                  {bajando === `${r.clave}.xlsx` ? <Girando /> : <Download size={16} />} Excel
                </button>
                <button className="boton crece" disabled={!!bajando} onClick={() => bajar(r, 'pdf')}>
                  {bajando === `${r.clave}.pdf` ? <Girando /> : <FileText size={16} />} PDF
                </button>
                <button className="boton" disabled={!!bajando} onClick={() => bajar(r, 'csv')} title="CSV separado por punto y coma" aria-label={`Descargar ${r.titulo} en CSV`}>
                  {bajando === `${r.clave}.csv` ? <Girando /> : 'CSV'}
                </button>
              </div>
            </Tarjeta>
          ))}
        </div>
      )}
      <Tarjeta style={{ marginTop: 16 }}>
        <div className="fila" style={{ gap: 12 }}>
          <ShieldCheck size={22} color="var(--bajo)" />
          <p className="pequeno suave">Los reportes se generan en el servidor a partir de las vistas de consulta (v_riesgo, v_alerta) con el formato Office Open XML. Cada descarga registra en la bitácora la operación EXPORTAR_REPORTE con el usuario y el alcance.</p>
        </div>
      </Tarjeta>
    </>
  );
}

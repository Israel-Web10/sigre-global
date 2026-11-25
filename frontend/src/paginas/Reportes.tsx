import { useState } from 'react';
import { motion } from 'framer-motion';
import { BarChart3, Download, FileSpreadsheet, Printer, ShieldCheck } from 'lucide-react';
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
  const bajar = async (r: Fila) => {
    setBajando(r.clave);
    try {
      await descargar(`/api/reportes/${r.clave}.xlsx${filial ? `?filial=${filial}` : ''}`, `SIGRE_${r.clave}_${new Date().toISOString().slice(0, 10)}.xlsx`);
      avisar('exito', 'Reporte generado', `${r.titulo} (Excel). La exportación quedó registrada en la bitácora.`);
    } catch (e) {
      error(e);
    } finally {
      setBajando(null);
    }
  };
  return (
    <>
      <Encabezado icono={<BarChart3 size={22} />} titulo="Reportes" texto="Libros de Excel con filtros y encabezados listos para el comité de riesgos. El alcance respeta la filial del usuario (RN14).">
        {usuario?.corporativo && (
          <select className="entrada" style={{ width: 220 }} value={filial} onChange={(e) => setFilial(e.target.value)}>
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
              <button className="boton primario bloque" style={{ marginTop: 14 }} disabled={!!bajando} onClick={() => bajar(r)}>
                {bajando === r.clave ? <Girando /> : <Download size={16} />} Descargar Excel
              </button>
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

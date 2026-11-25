import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { CalendarClock, Gavel, Stamp, User } from 'lucide-react';
import type { Fila } from '../nucleo/api';
import { useAlEvento, useDatos, useSesion } from '../nucleo/contexto';
import { colorNivel, etiqueta, fechaHora, numero } from '../nucleo/formato';
import { ModalAprobacion } from '../componentes/Riesgo';
import { Bandera, Cargando, ChipEstado, ChipNivel, Encabezado, Tarjeta, Vacio } from '../componentes/Ui';

/** CU04: bandeja de planes que esperan la aprobación del usuario según su atribución (RN04). */
export default function Aprobaciones() {
  const { usuario } = useSesion();
  const ir = useNavigate();
  const { datos, recargar } = useDatos<Fila[]>('/api/riesgos/aprobaciones');
  const [elegido, setElegido] = useState<number | null>(null);
  useAlEvento(['PLAN'], recargar);
  return (
    <>
      <Encabezado icono={<Stamp size={22} />} titulo="Aprobación de tratamientos (CU04)"
        texto={usuario?.roles.includes('DIRECTOR')
          ? 'Como Director de Riesgos aprueba los planes de riesgos Altos y Críticos, y las aceptaciones de riesgo (RN04, RN12).'
          : `Como Gerente de ${usuario?.filial} aprueba los planes de riesgos Bajos y Medios de su filial (RN04).`} />
      {!datos ? <Cargando filas={4} alto={90} /> : datos.length === 0 ? <Tarjeta><Vacio texto="No hay planes pendientes de su aprobación" icono={<Gavel size={36} />} /></Tarjeta> : (
        <div className="rejilla r-auto">
          {datos.map((p, k) => (
            <Tarjeta key={p.id_riesgo} inclinar retraso={k * 0.05} style={{ borderTop: `3px solid ${colorNivel(p.nivel)}` }}>
              <div className="fila entre"><span className="codigo">{p.codigo}</span><ChipNivel nivel={p.nivel} valor={p.valor} /></div>
              <h3 style={{ margin: '10px 0 6px', cursor: 'pointer' }} onClick={() => ir(`/riesgos/${p.id_riesgo}`)}>{p.titulo}</h3>
              <div className="pequeno tenue"><Bandera codigo={p.codigo_pais} /> {p.filial} · {etiqueta(p.categoria)}</div>
              <div className="sep" />
              <div className="columna pequeno" style={{ gap: 6 }}>
                <span className="fila entre"><span className="tenue">Estrategia</span><strong>{etiqueta(p.estrategia)}</strong></span>
                <span className="fila entre"><span className="tenue">Acciones · costo</span><strong>{p.acciones} · {numero(p.costo_estimado)}</strong></span>
                <span className="fila entre"><span className="tenue">Validación financiera</span><ChipEstado estado={p.estado_validacion} /></span>
                <span className="fila entre"><span className="tenue"><User size={12} /> Propuesto por</span><span>{p.proponente}</span></span>
                <span className="fila entre"><span className="tenue"><CalendarClock size={12} /> Fecha</span><span>{fechaHora(p.fecha_propuesta)}</span></span>
              </div>
              <button className="boton primario bloque" style={{ marginTop: 14 }} onClick={() => setElegido(p.id_riesgo)}><Gavel size={16} /> Revisar y decidir</button>
            </Tarjeta>
          ))}
        </div>
      )}
      {elegido && <ModalAprobacion idRiesgo={elegido} abierto={!!elegido} cerrar={() => setElegido(null)} alTerminar={recargar} />}
    </>
  );
}

import { useState } from 'react';
import { motion } from 'framer-motion';
import { Building2, KeyRound, Save, Settings2, ShieldCheck, SlidersHorizontal, UserPlus, Users } from 'lucide-react';
import { api, type Fila } from '../nucleo/api';
import { useAvisos, useDatos, useSesion } from '../nucleo/contexto';
import { etiqueta, haceCuanto } from '../nucleo/formato';
import { Avatar, Bandera, Campo, Cargando, ChipEstado, Encabezado, Girando, Interruptor, Modal, Pestanas, Tabla, Tarjeta } from '../componentes/Ui';

const ROLES = ['GESTOR', 'RESPONSABLE', 'AUDITOR_INTERNO', 'OFICIAL_CUMPLIMIENTO', 'DIRECTOR', 'GERENTE_FILIAL', 'ADMIN', 'AUDITOR_FINANCIERO'];

/** CU15: usuarios y roles (RN17), filiales con su apetito de riesgo y parámetros de las reglas. */
export default function Administracion() {
  const { tiene } = useSesion();
  const admin = tiene('ADMIN');
  const [pestana, setPestana] = useState<'usuarios' | 'roles' | 'filiales' | 'parametros'>(admin ? 'usuarios' : 'filiales');
  return (
    <>
      <Encabezado icono={<Settings2 size={22} />} titulo="Administración (CU15)" texto="Toda modificación queda en la bitácora con sus valores anteriores y nuevos. Las reglas críticas también las valida la base de datos." />
      <Pestanas id="adm" valor={pestana} cambiar={setPestana} opciones={[
        ...(admin ? [{ valor: 'usuarios' as const, texto: 'Usuarios', icono: <Users size={15} /> }, { valor: 'roles' as const, texto: 'Roles y permisos', icono: <ShieldCheck size={15} /> }] : []),
        { valor: 'filiales', texto: 'Filiales y apetito', icono: <Building2 size={15} /> },
        { valor: 'parametros', texto: 'Parámetros', icono: <SlidersHorizontal size={15} /> },
      ]} />
      {pestana === 'usuarios' && <UsuariosAdmin />}
      {pestana === 'roles' && <RolesAdmin />}
      {pestana === 'filiales' && <FilialesAdmin />}
      {pestana === 'parametros' && <ParametrosAdmin />}
    </>
  );
}

function UsuariosAdmin() {
  const { datos, recargar } = useDatos<Fila[]>('/api/admin/usuarios');
  const [editar, setEditar] = useState<Fila | null>(null);
  const [q, setQ] = useState('');
  const filas = (datos ?? []).filter((u) => `${u.nombre_completo} ${u.login} ${u.filial}`.toLowerCase().includes(q.toLowerCase()));
  return (
    <>
      <div className="fila" style={{ marginBottom: 12 }}>
        <input className="entrada crece" placeholder="Buscar usuario" value={q} onChange={(e) => setQ(e.target.value)} style={{ maxWidth: 360 }} />
        <button className="boton primario" onClick={() => setEditar({ roles: [], idioma: 'es', activo: true })}><UserPlus size={16} /> Nuevo usuario</button>
      </div>
      <Tarjeta style={{ padding: 0 }}>
        {!datos ? <div style={{ padding: 16 }}><Cargando /></div> : (
          <Tabla filas={filas} clave={(u) => u.id_usuario} alClic={(u) => setEditar(u)}
            columnas={[
              { clave: 'nombre_completo', titulo: 'Usuario', render: (u) => <div className="fila" style={{ gap: 10, flexWrap: 'nowrap' }}><Avatar nombre={u.nombre_completo} chico /><div><strong>{u.nombre_completo}</strong><div className="pequeno tenue">{u.login} · {u.email}</div></div></div> },
              { clave: 'filial', titulo: 'Filial', render: (u) => <><Bandera codigo={u.codigo_pais} /> {u.filial}</> },
              { clave: 'roles', titulo: 'Roles', render: (u) => <div className="fila" style={{ gap: 4 }}>{u.roles.map((r: string) => <span key={r} className="chip">{etiqueta(r)}</span>)}</div> },
              { clave: 'superior', titulo: 'Superior', render: (u) => u.superior ?? '—' },
              { clave: 'totp_activo', titulo: '2FA', render: (u) => u.totp_activo ? <span className="chip" style={{ color: 'var(--bajo)' }}>TOTP</span> : <span className="tenue">—</span> },
              { clave: 'ultimo_acceso', titulo: 'Último acceso', render: (u) => <span className="pequeno">{haceCuanto(u.ultimo_acceso)}</span> },
              { clave: 'activo', titulo: 'Estado', render: (u) => <ChipEstado estado={u.activo ? 'OK' : 'RECHAZADO'} /> },
            ]} />
        )}
      </Tarjeta>
      {editar && <ModalUsuario u={editar} usuarios={datos ?? []} cerrar={() => setEditar(null)} alGuardar={recargar} />}
    </>
  );
}

function ModalUsuario({ u, usuarios, cerrar, alGuardar }: { u: Fila; usuarios: Fila[]; cerrar: () => void; alGuardar: () => void }) {
  const { avisar, error } = useAvisos();
  const { datos: cat } = useDatos<Fila>('/api/catalogos');
  const nuevo = !u.id_usuario;
  const [f, setF] = useState<Fila>({ nombreCompleto: u.nombre_completo, login: u.login, email: u.email, cargo: u.cargo, idFilial: u.id_filial, idSuperior: u.id_superior, idioma: u.idioma, activo: u.activo, roles: u.roles ?? [] });
  const [clave, setClave] = useState('');
  const [enviando, setEnviando] = useState(false);
  const alternarRol = (r: string) => setF((x) => ({ ...x, roles: x.roles.includes(r) ? x.roles.filter((y: string) => y !== r) : [...x.roles, r] }));
  const conflicto = f.roles.includes('GESTOR') && f.roles.includes('AUDITOR_FINANCIERO');
  const guardar = async () => {
    setEnviando(true);
    try {
      if (nuevo) await api.post('/api/admin/usuarios', { ...f, clave });
      else {
        await api.put(`/api/admin/usuarios/${u.id_usuario}`, f);
        if (clave) await api.post(`/api/admin/usuarios/${u.id_usuario}/clave`, { clave });
      }
      avisar('exito', nuevo ? 'Usuario creado' : 'Usuario actualizado');
      alGuardar();
      cerrar();
    } catch (e) {
      error(e);
    } finally {
      setEnviando(false);
    }
  };
  return (
    <Modal abierto cerrar={cerrar} ancho icono={<Users size={20} />} titulo={nuevo ? 'Nuevo usuario' : u.nombre_completo} subtitulo={nuevo ? 'Alta de usuario con sus roles (CU15)' : `${u.login} · ${u.email}`}
      pie={<><button className="boton" onClick={cerrar}>Cancelar</button><button className="boton primario" disabled={enviando} onClick={guardar}>{enviando ? <Girando /> : <Save size={16} />} Guardar</button></>}>
      <div className="rejilla r-2" style={{ gap: 20 }}>
        <div className="columna" style={{ gap: 12 }}>
          <Campo etiqueta="Nombre completo"><input className="entrada" value={f.nombreCompleto ?? ''} onChange={(e) => setF({ ...f, nombreCompleto: e.target.value })} /></Campo>
          <div className="rejilla r-2">
            <Campo etiqueta="Usuario"><input className="entrada" disabled={!nuevo} value={f.login ?? ''} onChange={(e) => setF({ ...f, login: e.target.value.toLowerCase() })} /></Campo>
            <Campo etiqueta="Correo"><input className="entrada" value={f.email ?? ''} onChange={(e) => setF({ ...f, email: e.target.value })} /></Campo>
          </div>
          <Campo etiqueta="Cargo"><input className="entrada" value={f.cargo ?? ''} onChange={(e) => setF({ ...f, cargo: e.target.value })} /></Campo>
          <div className="rejilla r-2">
            <Campo etiqueta="Filial"><select className="entrada" value={f.idFilial ?? ''} onChange={(e) => setF({ ...f, idFilial: Number(e.target.value) })}><option value="">—</option>{(cat?.filiales ?? []).map((x: Fila) => <option key={x.id_filial} value={x.id_filial}>{x.nombre}</option>)}</select></Campo>
            <Campo etiqueta="Superior (escalamiento)"><select className="entrada" value={f.idSuperior ?? ''} onChange={(e) => setF({ ...f, idSuperior: e.target.value ? Number(e.target.value) : null })}><option value="">Ninguno</option>{usuarios.filter((x) => x.id_usuario !== u.id_usuario).map((x) => <option key={x.id_usuario} value={x.id_usuario}>{x.nombre_completo}</option>)}</select></Campo>
          </div>
          <div className="rejilla r-2">
            <Campo etiqueta={nuevo ? 'Clave inicial' : 'Nueva clave (opcional)'} ayuda="Mínimo 8 caracteres con letras y números"><input className="entrada" type="password" value={clave} onChange={(e) => setClave(e.target.value)} autoComplete="new-password" /></Campo>
            <Campo etiqueta="Idioma"><select className="entrada" value={f.idioma ?? 'es'} onChange={(e) => setF({ ...f, idioma: e.target.value })}><option value="es">Español</option><option value="en">English</option><option value="pt">Português</option></select></Campo>
          </div>
          {!nuevo && <div className="fila"><Interruptor valor={!!f.activo} cambiar={(v) => setF({ ...f, activo: v })} /> <span>{f.activo ? 'Cuenta activa' : 'Cuenta desactivada'}</span></div>}
        </div>
        <div className="columna" style={{ gap: 8 }}>
          <span className="etiqueta-campo">Roles</span>
          {ROLES.map((r) => (
            <button key={r} type="button" onClick={() => alternarRol(r)} className={`item-verificacion ${f.roles.includes(r) ? 'ok' : ''}`} style={{ cursor: 'pointer', font: 'inherit', color: 'var(--texto)', textAlign: 'left', padding: 10 }}>
              <input type="checkbox" readOnly checked={f.roles.includes(r)} /> <span>{etiqueta(r)}</span>
            </button>
          ))}
          {conflicto && <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="item-verificacion no pequeno">RN17: el Auditor Financiero no puede ser también Gestor de Riesgos (separación de funciones). La base de datos rechazará la combinación.</motion.div>}
        </div>
      </div>
    </Modal>
  );
}

function RolesAdmin() {
  const { datos } = useDatos<Fila[]>('/api/admin/roles');
  if (!datos) return <Cargando />;
  return (
    <div className="rejilla r-auto">
      {datos.map((r, k) => (
        <Tarjeta key={r.id_rol} inclinar retraso={k * 0.04}>
          <div className="fila entre"><span className="chip">A{r.id_rol}</span>{r.corporativo ? <span className="chip" style={{ color: 'var(--acento)' }}>Corporativo</span> : <span className="chip">Por filial</span>}</div>
          <h3 style={{ margin: '10px 0 4px' }}>{r.nombre}</h3>
          <p className="pequeno tenue">{r.descripcion}</p>
          <div className="sep" />
          <div className="columna pequeno" style={{ gap: 4 }}>{(r.permisos ?? []).map((p: string) => <span key={p}><ShieldCheck size={12} color="var(--bajo)" /> {p}</span>)}</div>
          <div className="pequeno tenue" style={{ marginTop: 10 }}>{r.usuarios} usuario(s) activo(s)</div>
        </Tarjeta>
      ))}
    </div>
  );
}

function FilialesAdmin() {
  const { avisar, error } = useAvisos();
  const { datos, recargar } = useDatos<Fila[]>('/api/admin/filiales');
  const [cambios, setCambios] = useState<Record<number, number>>({});
  if (!datos) return <Cargando />;
  const guardar = async (f: Fila) => {
    try {
      await api.put(`/api/admin/filiales/${f.id_filial}`, { apetitoRiesgo: cambios[f.id_filial] });
      avisar('exito', `Apetito de ${f.nombre} actualizado`, 'Se aplica a la evaluación residual (RN11). Quedó en la bitácora (MODIFICAR_APETITO).');
      setCambios((c) => { const n = { ...c }; delete n[f.id_filial]; return n; });
      recargar();
    } catch (e) {
      error(e);
    }
  };
  return (
    <div className="rejilla r-auto">
      {datos.map((f, k) => {
        const v = cambios[f.id_filial] ?? f.apetito_riesgo;
        return (
          <Tarjeta key={f.id_filial} retraso={k * 0.03}>
            <div className="fila entre"><strong><Bandera codigo={f.codigo_pais} /> {f.nombre}</strong><span className="chip">{f.moneda}</span></div>
            <div className="pequeno tenue">{f.ciudad}, {f.pais} · {f.region} · {f.usuarios} usuarios · {f.riesgos} riesgos</div>
            <div className="fila entre" style={{ marginTop: 14 }}><span className="etiqueta-campo">Apetito de riesgo (RN11)</span><strong style={{ fontSize: 22, fontFamily: 'var(--fuente-titulo)' }}>{v}</strong></div>
            <input type="range" min={1} max={25} value={v} onChange={(e) => setCambios({ ...cambios, [f.id_filial]: Number(e.target.value) })} style={{ width: '100%', accentColor: '#22d3ee' }} />
            {cambios[f.id_filial] != null && cambios[f.id_filial] !== f.apetito_riesgo && <button className="boton chico primario" onClick={() => guardar(f)}><Save size={13} /> Guardar</button>}
          </Tarjeta>
        );
      })}
    </div>
  );
}

function ParametrosAdmin() {
  const { tiene } = useSesion();
  const { avisar, error } = useAvisos();
  const { datos, recargar } = useDatos<Fila[]>('/api/admin/parametros');
  const [valores, setValores] = useState<Record<string, string>>({});
  if (!datos) return <Cargando />;
  const guardar = async (p: Fila) => {
    try {
      await api.put(`/api/admin/parametros/${p.clave}`, { valor: valores[p.clave] });
      avisar('exito', 'Parámetro actualizado', `${p.descripcion}: ${valores[p.clave]}`);
      recargar();
    } catch (e) {
      error(e);
    }
  };
  return (
    <Tarjeta style={{ padding: 0 }}>
      <table className="tabla">
        <thead><tr><th>Regla</th><th>Parámetro</th><th>Valor</th><th>Rango</th><th /></tr></thead>
        <tbody>
          {datos.map((p) => (
            <tr key={p.clave}>
              <td>{p.regla ? <span className="chip">{p.regla}</span> : <span className="tenue">Demo</span>}</td>
              <td><strong>{p.descripcion}</strong><div className="mono tenue">{p.clave}</div></td>
              <td style={{ width: 150 }}><input className="entrada" disabled={!tiene('ADMIN')} value={valores[p.clave] ?? p.valor} onChange={(e) => setValores({ ...valores, [p.clave]: e.target.value })} /></td>
              <td className="pequeno tenue">{p.minimo ?? '—'} a {p.maximo ?? '∞'}</td>
              <td>{valores[p.clave] != null && valores[p.clave] !== p.valor && <button className="boton chico primario" onClick={() => guardar(p)}><KeyRound size={13} /> Aplicar</button>}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </Tarjeta>
  );
}

import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import QRCode from 'qrcode';
import { KeyRound, Mail, MessageSquareText, Palette, ShieldCheck, Smartphone, User } from 'lucide-react';
import { api, type Fila } from '../nucleo/api';
import { useAvisos, useDatos, usePreferencias, useSesion } from '../nucleo/contexto';
import { etiqueta, fechaHora } from '../nucleo/formato';
import { Avatar, Campo, Encabezado, Interruptor, Pestanas, Tarjeta, Vacio } from '../componentes/Ui';

/** Perfil del usuario: doble factor TOTP (RNF01), clave, preferencias y bandeja simulada de correo y SMS. */
export default function Perfil() {
  const { usuario, refrescar } = useSesion();
  const { tema, alternarTema, idioma, setIdioma, animaciones, setAnimaciones } = usePreferencias();
  const { avisar, error } = useAvisos();
  const [params] = useSearchParams();
  const [pestana, setPestana] = useState<'seguridad' | 'correo' | 'preferencias'>((params.get('pestana') as any) ?? 'seguridad');
  const [alta, setAlta] = useState<Fila | null>(null);
  const [qr, setQr] = useState('');
  const [codigo, setCodigo] = useState('');
  const [claves, setClaves] = useState({ actual: '', nueva: '' });
  const { datos: externas } = useDatos<Fila[]>(pestana === 'correo' ? '/api/notificaciones/externas' : null, [pestana]);

  useEffect(() => {
    if (alta?.url) QRCode.toDataURL(alta.url, { width: 220, margin: 1, color: { dark: '#0b1220', light: '#ffffff' } }).then(setQr);
  }, [alta]);

  const iniciar2fa = () => api.post('/api/auth/2fa/iniciar').then(setAlta).catch(error);
  const confirmar = async () => {
    try {
      await api.post('/api/auth/2fa/confirmar', { codigo });
      avisar('exito', 'Doble factor activado', 'Desde ahora se pedirá el código al ingresar.');
      setAlta(null);
      setCodigo('');
      refrescar();
    } catch (e) {
      error(e);
    }
  };
  const desactivar = async () => {
    try {
      await api.post('/api/auth/2fa/desactivar', { codigo });
      avisar('info', 'Doble factor desactivado');
      setCodigo('');
      refrescar();
    } catch (e) {
      error(e);
    }
  };
  const cambiarClave = async () => {
    try {
      await api.post('/api/auth/clave', claves);
      avisar('exito', 'Clave actualizada');
      setClaves({ actual: '', nueva: '' });
    } catch (e) {
      error(e);
    }
  };

  return (
    <>
      <Encabezado icono={<User size={22} />} titulo="Mi perfil" />
      <div className="rejilla r-3">
        <Tarjeta brillante>
          <div className="columna" style={{ alignItems: 'center', textAlign: 'center', gap: 10 }}>
            <div style={{ transform: 'scale(2.2)', margin: '22px 0' }}><Avatar nombre={usuario?.nombre} /></div>
            <h2>{usuario?.nombre}</h2>
            <span className="suave">{usuario?.cargo}</span>
            <span className="chip">{usuario?.filial}</span>
            <div className="fila" style={{ justifyContent: 'center' }}>{usuario?.roles.map((r) => <span key={r} className="chip" style={{ color: 'var(--acento)' }}>{etiqueta(r)}</span>)}</div>
            <span className="pequeno tenue">{usuario?.email} · {usuario?.corporativo ? 'acceso a todas las filiales' : 'acceso a su filial (RN14)'}</span>
          </div>
        </Tarjeta>
        <div className="span-2">
          <Pestanas id="perfil" valor={pestana} cambiar={setPestana} opciones={[
            { valor: 'seguridad', texto: 'Seguridad', icono: <ShieldCheck size={15} /> },
            { valor: 'correo', texto: 'Correos y SMS', icono: <Mail size={15} /> },
            { valor: 'preferencias', texto: 'Preferencias', icono: <Palette size={15} /> },
          ]} />
          {pestana === 'seguridad' && (
            <div className="rejilla r-2">
              <Tarjeta>
                <h3 style={{ marginBottom: 6 }}><Smartphone size={16} /> Doble factor (TOTP)</h3>
                <p className="pequeno suave" style={{ marginBottom: 12 }}>Código de 6 dígitos de Google Authenticator, Microsoft Authenticator o similar (RFC 6238).</p>
                {usuario?.totpActivo ? (
                  <div className="columna">
                    <span className="chip" style={{ color: 'var(--bajo)' }}><ShieldCheck size={12} /> Activado</span>
                    <input className="entrada mono" placeholder="Código actual" maxLength={6} value={codigo} onChange={(e) => setCodigo(e.target.value.replace(/\D/g, ''))} />
                    <button className="boton peligro" disabled={codigo.length !== 6} onClick={desactivar}>Desactivar</button>
                  </div>
                ) : alta ? (
                  <div className="columna" style={{ alignItems: 'center' }}>
                    {qr && <img src={qr} alt="Código QR del doble factor" style={{ borderRadius: 12, width: 200 }} />}
                    <code className="tenue" style={{ wordBreak: 'break-all' }}>{alta.secreto}</code>
                    <input className="entrada mono" style={{ textAlign: 'center', letterSpacing: '.4em', fontSize: 20 }} placeholder="000000" maxLength={6} value={codigo} onChange={(e) => setCodigo(e.target.value.replace(/\D/g, ''))} />
                    <button className="boton primario bloque" disabled={codigo.length !== 6} onClick={confirmar}>Confirmar y activar</button>
                  </div>
                ) : <button className="boton primario" onClick={iniciar2fa}><ShieldCheck size={16} /> Configurar doble factor</button>}
              </Tarjeta>
              <Tarjeta>
                <h3 style={{ marginBottom: 12 }}><KeyRound size={16} /> Cambiar clave</h3>
                <div className="columna">
                  <Campo etiqueta="Clave actual"><input className="entrada" type="password" value={claves.actual} onChange={(e) => setClaves({ ...claves, actual: e.target.value })} autoComplete="current-password" /></Campo>
                  <Campo etiqueta="Nueva clave" ayuda="Mínimo 8 caracteres con letras y números. Se guarda con Argon2id."><input className="entrada" type="password" value={claves.nueva} onChange={(e) => setClaves({ ...claves, nueva: e.target.value })} autoComplete="new-password" /></Campo>
                  <button className="boton" disabled={!claves.actual || claves.nueva.length < 8} onClick={cambiarClave}>Actualizar clave</button>
                </div>
              </Tarjeta>
            </div>
          )}
          {pestana === 'correo' && (
            <Tarjeta>
              <h3 style={{ marginBottom: 4 }}><MessageSquareText size={16} /> Bandeja del servicio de notificaciones</h3>
              <p className="pequeno tenue" style={{ marginBottom: 12 }}>En la demostración los correos y SMS no salen del sistema: se guardan aquí para comprobar que se enviaron (CU07).</p>
              {!externas?.length ? <Vacio texto="Sin mensajes enviados" /> : (
                <div className="columna" style={{ gap: 8 }}>
                  {externas.map((m) => (
                    <div key={m.id_notificacion} className="item-verificacion">
                      {m.canal === 'SMS' ? <Smartphone size={18} color="var(--alto)" /> : <Mail size={18} color="var(--acento)" />}
                      <div className="crece">
                        <div className="fila entre"><strong className="pequeno">{m.titulo}</strong><span className="pequeno tenue">{fechaHora(m.fecha)}</span></div>
                        <div className="pequeno tenue">Para: {m.destinatario} &lt;{m.canal === 'SMS' ? 'móvil' : m.email}&gt;</div>
                        <div className="pequeno suave">{m.mensaje}</div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </Tarjeta>
          )}
          {pestana === 'preferencias' && (
            <Tarjeta>
              <div className="columna" style={{ gap: 16 }}>
                <div className="fila entre"><span>Tema oscuro</span><Interruptor valor={tema === 'oscuro'} cambiar={alternarTema} /></div>
                <div className="fila entre"><span>Animaciones y efectos 3D</span><Interruptor valor={animaciones} cambiar={setAnimaciones} /></div>
                <div className="fila entre"><span>Idioma de la interfaz</span>
                  <select className="entrada" style={{ width: 160 }} value={idioma} onChange={(e) => setIdioma(e.target.value as any)}><option value="es">Español</option><option value="en">English</option><option value="pt">Português</option></select>
                </div>
              </div>
            </Tarjeta>
          )}
        </div>
      </div>
    </>
  );
}

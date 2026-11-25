import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import QRCode from 'qrcode';
import { BellRing, Download, Fingerprint, MonitorSmartphone, RotateCw, Smartphone, WifiOff } from 'lucide-react';
import { Encabezado, Segmentado, Tarjeta } from '../componentes/Ui';

const PANTALLAS = [
  { valor: '/', texto: 'Tablero' },
  { valor: '/alertas', texto: 'Alertas' },
  { valor: '/mis-acciones', texto: 'Mis acciones' },
  { valor: '/riesgos', texto: 'Riesgos' },
  { valor: '/kri', texto: 'KRI' },
];

/** La misma aplicación funciona como app móvil (PWA): aquí se muestra dentro de un teléfono. */
export default function Movil() {
  const [ruta, setRuta] = useState('/alertas');
  const [clave, setClave] = useState(0);
  const [qr, setQr] = useState('');
  const [instalar, setInstalar] = useState<any>(null);
  useEffect(() => {
    QRCode.toDataURL(window.location.origin, { width: 180, margin: 1 }).then(setQr);
    const h = (e: Event) => {
      e.preventDefault();
      setInstalar(e);
    };
    window.addEventListener('beforeinstallprompt', h);
    return () => window.removeEventListener('beforeinstallprompt', h);
  }, []);
  return (
    <>
      <Encabezado icono={<MonitorSmartphone size={22} />} titulo="Vista móvil (PWA)"
        texto="SIGRE Global es una aplicación web progresiva: el mismo código se adapta al teléfono, se instala en la pantalla de inicio y sigue abriendo sin conexión. Los responsables atienden alertas y registran avances desde el celular.">
        <Segmentado valor={ruta} cambiar={(r) => { setRuta(r); setClave((k) => k + 1); }} opciones={PANTALLAS} />
        <button className="icono-btn" onClick={() => setClave((k) => k + 1)} title="Recargar"><RotateCw size={17} /></button>
      </Encabezado>
      <div className="fila" style={{ alignItems: 'flex-start', gap: 30, justifyContent: 'center' }}>
        <motion.div className="telefono flotando" initial={{ rotateY: -25, opacity: 0, y: 30 }} animate={{ rotateY: 0, opacity: 1, y: 0 }} transition={{ duration: 0.9 }} style={{ transformStyle: 'preserve-3d' }}>
          <iframe key={clave} src={ruta} title="SIGRE Global en un teléfono" />
        </motion.div>
        <div className="columna" style={{ gap: 14, maxWidth: 380 }}>
          {[
            { i: Smartphone, t: 'Diseño adaptable', d: 'Menú inferior, tarjetas a una columna y tablero de alertas deslizable.' },
            { i: BellRing, t: 'Alertas al instante', d: 'Las alertas llegan en tiempo real con su cuenta regresiva del SLA.' },
            { i: WifiOff, t: 'Funciona sin conexión', d: 'El service worker guarda la aplicación; los datos se piden a la red.' },
            { i: Fingerprint, t: 'Acceso seguro', d: 'La misma autenticación con JWT y doble factor TOTP.' },
          ].map((x, k) => (
            <Tarjeta key={x.t} inclinar retraso={0.2 + k * 0.08}>
              <div className="fila" style={{ gap: 12, flexWrap: 'nowrap' }}>
                <span className="icono-titulo"><x.i size={20} /></span>
                <div><strong>{x.t}</strong><p className="pequeno suave">{x.d}</p></div>
              </div>
            </Tarjeta>
          ))}
          <Tarjeta retraso={0.6}>
            <div className="fila" style={{ gap: 14, flexWrap: 'nowrap' }}>
              {qr && <img src={qr} alt="Código QR de la aplicación" style={{ width: 110, borderRadius: 10 }} />}
              <div className="pequeno suave">Escanee el código desde un teléfono conectado a la misma red (use la IP del equipo en lugar de «localhost») y elija «Agregar a la pantalla de inicio».</div>
            </div>
            {instalar && <button className="boton primario bloque" style={{ marginTop: 12 }} onClick={() => instalar.prompt()}><Download size={16} /> Instalar en este equipo</button>}
          </Tarjeta>
        </div>
      </div>
    </>
  );
}

import { lazy, Suspense, useEffect, useMemo, useState } from 'react';
import { motion } from 'framer-motion';
import { ArrowRight, Fingerprint, Globe2, KeyRound, Lock, Moon, ShieldCheck, Sun, User, Zap } from 'lucide-react';
import { api, type Fila } from '../nucleo/api';
import { useAvisos, usePreferencias, useSesion } from '../nucleo/contexto';
import { banderaHtml } from '../nucleo/formato';
import { Avatar, Bandera, Contador, Girando } from '../componentes/Ui';

const Globo = lazy(() => import('../componentes/Globo'));

const MODULOS = ['Riesgos ISO 31000', 'Matriz 5×5', 'KRI en tiempo real', 'Alertas con SLA', 'Escalamiento automático', 'Auditorías', 'Validación financiera',
  'Conciliación con el ERP', 'Cumplimiento normativo', 'Bitácora inalterable', 'Doble factor TOTP', 'PWA móvil', 'Triggers PostgreSQL', 'API REST OpenAPI'];

export default function Login() {
  const { iniciar } = useSesion();
  const { t, tema, alternarTema, idioma, setIdioma } = usePreferencias();
  const { error, avisar } = useAvisos();
  const [info, setInfo] = useState<Fila | null>(null);
  const [demo, setDemo] = useState<Fila | null>(null);
  const [login, setLogin] = useState('');
  const [clave, setClave] = useState('');
  const [recordar, setRecordar] = useState(true);
  const [enviando, setEnviando] = useState<string | null>(null);
  const [desafio, setDesafio] = useState<string | null>(null);
  const [codigo, setCodigo] = useState('');

  useEffect(() => {
    api.get('/api/sistema/info').then(setInfo).catch(() => setInfo({ estado: 'SIN_CONEXION' }));
    api.get('/api/auth/demo-usuarios').then(setDemo).catch(() => {});
  }, []);

  const puntos = useMemo(
    () => (info?.filiales ?? []).map((f: Fila) => ({
      id: f.id_filial, nombre: f.nombre, lat: Number(f.latitud), lon: Number(f.longitud), matriz: f.casa_matriz,
      color: f.casa_matriz ? 'var(--acento)' : 'var(--medio)', etiqueta: `${banderaHtml(f.codigo_pais)} ${f.ciudad}`,
    })),
    [info]
  );

  const entrar = async (u: string, c: string, quien = 'form') => {
    setEnviando(quien);
    try {
      const r = await api.post('/api/auth/login', { login: u, clave: c });
      if (r.requiere2fa) {
        setDesafio(r.desafio);
        avisar('info', 'Doble factor', 'Ingrese el código de su aplicación autenticadora');
      } else {
        iniciar(r.token, r.usuario, recordar);
      }
    } catch (e) {
      error(e);
    } finally {
      setEnviando(null);
    }
  };

  const segundoFactor = async () => {
    setEnviando('2fa');
    try {
      const r = await api.post('/api/auth/2fa', { desafio, codigo });
      iniciar(r.token, r.usuario, recordar);
    } catch (e) {
      error(e);
    } finally {
      setEnviando(null);
    }
  };

  const cifras = info?.cifras ?? {};

  return (
    <div className="login">
      <section className="login-escena">
        <div className="globo">
          <Suspense fallback={null}>{puntos.length > 0 && <Globo puntos={puntos} />}</Suspense>
        </div>
        <div className="fila" style={{ gap: 12 }}>
          <div className="marca-logo"><Globe2 size={20} /></div>
          <div className="marca-texto">
            <strong>SIGRE Global</strong>
            <span>{info?.caso ?? 'Gestión de riesgos empresariales'}</span>
          </div>
        </div>
        <motion.div className="login-titulo" initial={{ opacity: 0, y: 30 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.8, delay: 0.2 }}>
          <span className="chip" style={{ marginBottom: 16, color: 'var(--acento)' }}><Zap size={12} /> Monitoreo en tiempo real de {cifras.filiales ?? 12} filiales</span>
          <h1>
            Riesgos globales,<br />
            <span className="texto-degradado">una sola visión.</span>
          </h1>
          <p>Identifique, evalúe, trate y monitoree los riesgos de todas las filiales con alertas automáticas, auditorías y cumplimiento normativo en una única plataforma.</p>
          <div className="login-cifras">
            {[['paises', 'países'], ['riesgos', 'riesgos activos'], ['indicadores', 'indicadores KRI'], ['registros_bitacora', 'eventos auditados']].map(([k, n]) => (
              <div key={k}>
                <strong><Contador valor={Number(cifras[k] ?? 0)} /></strong>
                <span>{n}</span>
              </div>
            ))}
          </div>
          <div className="marquesina" style={{ marginTop: 26 }}>
            <div className="marquesina-pista">
              {[...MODULOS, ...MODULOS].map((m, i) => (
                <span key={i} className="chip" style={{ background: 'var(--superficie)' }}><ShieldCheck size={12} /> {m}</span>
              ))}
            </div>
          </div>
        </motion.div>
        <div className="pequeno tenue" style={{ marginTop: 22 }}>
          {info?.estado === 'OPERATIVO' ? `● Servicio operativo · ${info.base_de_datos} · Java ${info.java}` : '○ Conectando con el servidor…'}
        </div>
      </section>

      <section className="login-panel">
        <motion.div className="login-caja" initial={{ opacity: 0, x: 30 }} animate={{ opacity: 1, x: 0 }} transition={{ duration: 0.6 }}>
          <div className="fila entre" style={{ marginBottom: 26 }}>
            <span className="chip"><Lock size={12} /> Conexión segura · JWT + Argon2id</span>
            <div className="fila" style={{ gap: 4 }}>
              <select className="entrada" style={{ width: 70, height: 32 }} value={idioma} onChange={(e) => setIdioma(e.target.value as any)}>
                <option value="es">ES</option><option value="en">EN</option><option value="pt">PT</option>
              </select>
              <button className="icono-btn" onClick={alternarTema}>{tema === 'oscuro' ? <Sun size={17} /> : <Moon size={17} />}</button>
            </div>
          </div>

          {!desafio ? (
            <>
              <h2>{t('bienvenida')}</h2>
              <p className="suave" style={{ margin: '6px 0 22px' }}>Ingrese con su cuenta corporativa para continuar.</p>
              <form className="columna" style={{ gap: 14 }} onSubmit={(e) => { e.preventDefault(); entrar(login, clave); }}>
                <div className="campo">
                  <label>{t('usuario')}</label>
                  <div className="con-icono"><User size={16} /><input className="entrada" value={login} onChange={(e) => setLogin(e.target.value.toLowerCase())} autoComplete="username" placeholder="usuario" /></div>
                </div>
                <div className="campo">
                  <label>{t('clave')}</label>
                  <div className="con-icono"><KeyRound size={16} /><input className="entrada" type="password" value={clave} onChange={(e) => setClave(e.target.value)} autoComplete="current-password" placeholder="••••••••" /></div>
                </div>
                <label className="fila pequeno suave" style={{ cursor: 'pointer' }}>
                  <input type="checkbox" checked={recordar} onChange={(e) => setRecordar(e.target.checked)} /> {t('recordar')}
                </label>
                <button className="boton primario grande bloque" disabled={!login || !clave || !!enviando}>
                  {enviando === 'form' ? <Girando /> : <ArrowRight size={18} />} {t('ingresar')}
                </button>
              </form>
            </>
          ) : (
            <motion.div initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }} className="columna" style={{ gap: 16 }}>
              <span className="icono-titulo" style={{ width: 56, height: 56 }}><Fingerprint size={28} /></span>
              <h2>Verificación en dos pasos</h2>
              <p className="suave">Escriba el código de 6 dígitos que muestra su aplicación autenticadora (TOTP, RFC 6238).</p>
              <input className="entrada mono" style={{ height: 56, fontSize: 28, letterSpacing: '0.5em', textAlign: 'center' }} maxLength={6} value={codigo}
                onChange={(e) => setCodigo(e.target.value.replace(/\D/g, ''))} autoFocus onKeyDown={(e) => e.key === 'Enter' && codigo.length === 6 && segundoFactor()} />
              <button className="boton primario grande bloque" disabled={codigo.length !== 6 || !!enviando} onClick={segundoFactor}>
                {enviando ? <Girando /> : <ShieldCheck size={18} />} Verificar
              </button>
              <button className="boton fantasma" onClick={() => { setDesafio(null); setCodigo(''); }}>Volver</button>
            </motion.div>
          )}

          {demo?.habilitado && !desafio && (
            <div style={{ marginTop: 28 }}>
              <div className="fila entre">
                <strong className="pequeno" style={{ letterSpacing: '0.08em', textTransform: 'uppercase', color: 'var(--texto-3)' }}>{t('accesoRapido')}</strong>
                <span className="pequeno tenue">un clic = un rol</span>
              </div>
              <div className="usuarios-demo">
                {demo.usuarios.map((u: Fila, i: number) => (
                  <motion.button key={u.login} className="usuario-demo" onClick={() => entrar(u.login, demo.clave, u.login)} disabled={!!enviando}
                    initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.3 + i * 0.04 }}>
                    {enviando === u.login ? <span className="avatar"><Girando /></span> : <Avatar nombre={u.nombre} />}
                    <div>
                      <strong>{u.nombre}</strong>
                      <span><Bandera codigo={u.codigo_pais} /> {u.roles}</span>
                    </div>
                  </motion.button>
                ))}
              </div>
            </div>
          )}
          <p className="pequeno tenue centrado" style={{ marginTop: 26 }}>
            UMSA · Informática · INF-241 Análisis y Diseño de Sistemas · Proyecto final
          </p>
        </motion.div>
      </section>
    </div>
  );
}

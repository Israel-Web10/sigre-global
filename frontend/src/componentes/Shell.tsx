import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { NavLink, useLocation, useNavigate } from 'react-router-dom';
import { AnimatePresence, motion } from 'framer-motion';
import {
  AlertTriangle, Bell, CheckCheck, ChevronsLeft, ChevronsRight, Globe2, LogOut, Mail, Moon, Search, ShieldCheck, Sparkles, Sun, X,
} from 'lucide-react';
import { api, type Fila } from '../nucleo/api';
import { useAlEvento, useAvisos, useEventos, usePreferencias, useSesion } from '../nucleo/contexto';
import { etiqueta, haceCuanto } from '../nucleo/formato';
import { GRUPOS, NAVEGACION, visible } from '../nucleo/navegacion';
import { Avatar } from './Ui';
import { useVentanas } from './Ventanas';

const ICONO_NOTIF: Record<string, [string, ReactNode]> = {
  ALERTA: ['var(--critico)', <AlertTriangle size={16} />],
  ESCALAMIENTO: ['var(--alto)', <AlertTriangle size={16} />],
  APROBACION: ['var(--acento-2)', <ShieldCheck size={16} />],
  VALIDACION: ['var(--medio)', <ShieldCheck size={16} />],
  TAREA: ['var(--acento)', <CheckCheck size={16} />],
  INFO: ['var(--info)', <Bell size={16} />],
};

export default function Shell({ children }: { children: ReactNode }) {
  const { usuario, salir } = useSesion();
  const { t, idioma, setIdioma, tema, alternarTema } = usePreferencias();
  const { conectado } = useEventos();
  const { avisar } = useAvisos();
  const { hay } = useVentanas();
  const ubicacion = useLocation();
  const ir = useNavigate();
  const [compacta, setCompacta] = useState(false);
  const [panel, setPanel] = useState(false);
  const [paleta, setPaleta] = useState(false);
  const [notif, setNotif] = useState<{ no_leidas: number; notificaciones: Fila[] }>({ no_leidas: 0, notificaciones: [] });
  const [alertasAbiertas, setAlertasAbiertas] = useState(0);
  const roles = usuario?.roles ?? [];
  const items = NAVEGACION.filter((i) => visible(i, roles));
  const actual = items.find((i) => (i.ruta === '/' ? ubicacion.pathname === '/' : ubicacion.pathname.startsWith(i.ruta)));

  const cargarNotif = () => api.get('/api/notificaciones').then(setNotif).catch(() => {});
  const cargarAlertas = () => api.get('/api/alertas/resumen').then((r) => setAlertasAbiertas(r?.mias ?? 0)).catch(() => {});
  useEffect(() => {
    cargarNotif();
    cargarAlertas();
  }, []);

  useAlEvento(['NOTIFICACION'], (e) => {
    cargarNotif();
    const [color] = ICONO_NOTIF[e.tipo] ?? ICONO_NOTIF.INFO;
    avisar(e.tipo === 'ALERTA' || e.tipo === 'ESCALAMIENTO' ? 'alerta' : 'info', e.titulo, e.mensaje);
    void color;
  });
  useAlEvento(['ALERTA_GENERADA', 'ALERTA_ACTUALIZADA'], () => cargarAlertas());

  useEffect(() => {
    const k = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        setPaleta((p) => !p);
      }
    };
    window.addEventListener('keydown', k);
    return () => window.removeEventListener('keydown', k);
  }, []);

  const abrirNotificacion = async (n: Fila) => {
    if (!n.leida) await api.post(`/api/notificaciones/${n.id_notificacion}/leida`).catch(() => {});
    setPanel(false);
    cargarNotif();
    if (n.enlace) ir(n.enlace);
  };

  return (
    <div className={`app ${compacta ? 'compacta' : ''}`}>
      <aside className="barra-lateral">
        <div className="marca">
          <div className="marca-logo"><Globe2 size={20} /></div>
          <div className="marca-texto">
            <strong>SIGRE Global</strong>
            <span>Gestión de riesgos empresariales</span>
          </div>
        </div>
        <nav className="navegacion">
          {GRUPOS.map((g) => {
            const del = items.filter((i) => i.grupo === g);
            if (!del.length) return null;
            return (
              <div className="nav-grupo" key={g}>
                <div className="nav-titulo">{t(g)}</div>
                {del.map((i) => (
                  <NavLink key={i.ruta} to={i.ruta} end={i.ruta === '/'} className={({ isActive }) => `nav-item ${isActive ? 'activo' : ''}`} title={t(i.clave)}>
                    <i.icono size={18} />
                    <span>{t(i.clave)}</span>
                    {i.ruta === '/alertas' && alertasAbiertas > 0 && <span className="contador rojo">{alertasAbiertas}</span>}
                  </NavLink>
                ))}
              </div>
            );
          })}
        </nav>
        <div className="usuario-lateral">
          <Avatar nombre={usuario?.nombre} />
          <div className="datos crece">
            <strong>{usuario?.nombre}</strong>
            <span>{usuario?.roles.map((r) => etiqueta(r)).join(', ')}</span>
          </div>
          <button className="icono-btn" onClick={() => setCompacta((c) => !c)} title="Contraer menú">
            {compacta ? <ChevronsRight size={17} /> : <ChevronsLeft size={17} />}
          </button>
        </div>
      </aside>

      <div className="principal">
        <header className="cabecera">
          <div className="titulo-pagina">
            <small>{usuario?.filial} · {actual?.cu ?? 'SIGRE Global'}</small>
            <strong>{actual ? t(actual.clave) : 'SIGRE Global'}</strong>
          </div>
          <button className="buscador" onClick={() => setPaleta(true)}>
            <Search size={16} />
            <span>{t('buscar')}</span>
            <kbd>Ctrl K</kbd>
          </button>
          <span className={`en-vivo ${conectado ? '' : 'no'}`} title="Eventos en tiempo real (PostgreSQL LISTEN/NOTIFY → SSE)">
            <i /> <span className="ocultar-movil">{conectado ? t('enVivo') : t('desconectado')}</span>
          </span>
          <select className="entrada ocultar-movil" style={{ width: 70, height: 34 }} value={idioma} onChange={(e) => setIdioma(e.target.value as any)} title="Idioma">
            <option value="es">ES</option>
            <option value="en">EN</option>
            <option value="pt">PT</option>
          </select>
          <button className="icono-btn" onClick={alternarTema} title={tema === 'oscuro' ? t('temaClaro') : t('temaOscuro')}>
            {tema === 'oscuro' ? <Sun size={18} /> : <Moon size={18} />}
          </button>
          <button className="icono-btn" onClick={() => setPanel(true)} title={t('notificaciones')}>
            <motion.span key={notif.no_leidas} initial={{ rotate: -25 }} animate={{ rotate: 0 }} transition={{ type: 'spring', stiffness: 400, damping: 6 }} style={{ display: 'grid' }}>
              <Bell size={18} />
            </motion.span>
            {notif.no_leidas > 0 && <span className="punto-contador">{notif.no_leidas > 99 ? '99+' : notif.no_leidas}</span>}
          </button>
          <button className="icono-btn" onClick={salir} title={t('cerrarSesion')}>
            <LogOut size={18} />
          </button>
        </header>

        <main className={`contenido ${hay ? 'con-barra-ventanas' : ''}`}>
          <AnimatePresence mode="wait">
            <motion.div key={ubicacion.pathname} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -6 }} transition={{ duration: 0.28 }}>
              {children}
            </motion.div>
          </AnimatePresence>
        </main>
      </div>

      <nav className="navegacion-movil">
        {items.filter((i) => i.movil).map((i) => (
          <NavLink key={i.ruta} to={i.ruta} end={i.ruta === '/'} className={({ isActive }) => (isActive ? 'activo' : '')}>
            <i.icono size={20} />
            {t(i.clave).split(' ')[0]}
            {i.ruta === '/alertas' && alertasAbiertas > 0 && <span className="punto-contador" style={{ top: -2, right: 4 }}>{alertasAbiertas}</span>}
          </NavLink>
        ))}
      </nav>

      <AnimatePresence>
        {panel && (
          <>
            <motion.div className="velo" style={{ background: 'rgba(2,6,14,.35)', zIndex: 69 }} initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={() => setPanel(false)} />
            <motion.aside className="panel-lateral" initial={{ x: 440 }} animate={{ x: 0 }} exit={{ x: 440 }} transition={{ type: 'spring', damping: 30, stiffness: 300 }}>
              <div className="modal-cabecera">
                <span className="icono-titulo"><Bell size={18} /></span>
                <div className="crece">
                  <h2>{t('notificaciones')}</h2>
                  <p>{notif.no_leidas} sin leer</p>
                </div>
                <button className="icono-btn" onClick={() => setPanel(false)}><X size={18} /></button>
              </div>
              <div className="fila" style={{ padding: '10px 16px', borderBottom: '1px solid var(--borde)' }}>
                <button className="boton chico" onClick={() => api.post('/api/notificaciones/leidas').then(cargarNotif)}>
                  <CheckCheck size={14} /> {t('marcarLeidas')}
                </button>
                <button className="boton chico fantasma" onClick={() => { setPanel(false); ir('/perfil?pestana=correo'); }}>
                  <Mail size={14} /> Correos y SMS
                </button>
              </div>
              <div className="lista">
                {notif.notificaciones.length === 0 && <div className="vacio"><Sparkles size={30} />{t('sinNotificaciones')}</div>}
                {notif.notificaciones.map((n, i) => {
                  const [color, icono] = ICONO_NOTIF[n.tipo] ?? ICONO_NOTIF.INFO;
                  return (
                    <motion.div key={n.id_notificacion} className={`notificacion ${n.leida ? '' : 'no-leida'}`} onClick={() => abrirNotificacion(n)}
                      initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: Math.min(i * 0.03, 0.4) }}>
                      <span className="ico" style={{ background: `color-mix(in srgb, ${color} 16%, transparent)`, color }}>{icono}</span>
                      <div className="crece">
                        <strong>{n.titulo}</strong>
                        <p>{n.mensaje}</p>
                        <small>{haceCuanto(n.fecha)}</small>
                      </div>
                    </motion.div>
                  );
                })}
              </div>
            </motion.aside>
          </>
        )}
      </AnimatePresence>

      <AnimatePresence>{paleta && <Paleta cerrar={() => setPaleta(false)} />}</AnimatePresence>
    </div>
  );
}

/** Paleta de comandos (Ctrl+K): navegación y búsqueda de riesgos por código o título. */
function Paleta({ cerrar }: { cerrar: () => void }) {
  const { usuario } = useSesion();
  const { t } = usePreferencias();
  const ir = useNavigate();
  const [texto, setTexto] = useState('');
  const [riesgos, setRiesgos] = useState<Fila[]>([]);
  const [indice, setIndice] = useState(0);
  const entrada = useRef<HTMLInputElement>(null);
  useEffect(() => {
    entrada.current?.focus();
    api.get('/api/riesgos').then(setRiesgos).catch(() => {});
  }, []);
  const opciones = useMemo(() => {
    const q = texto.toLowerCase().trim();
    const paginas = NAVEGACION.filter((i) => visible(i, usuario?.roles ?? []))
      .filter((i) => !q || t(i.clave).toLowerCase().includes(q) || (i.cu ?? '').toLowerCase().includes(q))
      .map((i) => ({ clave: i.ruta, texto: t(i.clave), detalle: i.cu ?? '', icono: <i.icono size={16} />, accion: () => ir(i.ruta) }));
    const rs = q.length < 2 ? [] : riesgos
      .filter((r) => `${r.codigo} ${r.titulo} ${r.filial}`.toLowerCase().includes(q))
      .slice(0, 8)
      .map((r) => ({ clave: `r${r.id_riesgo}`, texto: `${r.codigo} · ${r.titulo}`, detalle: r.filial, icono: <ShieldCheck size={16} />, accion: () => ir(`/riesgos/${r.id_riesgo}`) }));
    return [...rs, ...paginas];
  }, [texto, riesgos, usuario, t, ir]);
  const elegir = (i: number) => {
    opciones[i]?.accion();
    cerrar();
  };
  return (
    <motion.div className="paleta" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onMouseDown={(e) => e.target === e.currentTarget && cerrar()}>
      <motion.div className="caja" initial={{ y: -20, scale: 0.97 }} animate={{ y: 0, scale: 1 }} exit={{ y: -20, opacity: 0 }}>
        <div style={{ position: 'relative' }}>
          <Search size={18} style={{ position: 'absolute', left: 18, top: 20, color: 'var(--texto-3)' }} />
          <input ref={entrada} value={texto} placeholder="Buscar riesgos (código o título) o ir a una pantalla…"
            onChange={(e) => { setTexto(e.target.value); setIndice(0); }}
            onKeyDown={(e) => {
              if (e.key === 'Escape') cerrar();
              if (e.key === 'ArrowDown') { e.preventDefault(); setIndice((i) => Math.min(opciones.length - 1, i + 1)); }
              if (e.key === 'ArrowUp') { e.preventDefault(); setIndice((i) => Math.max(0, i - 1)); }
              if (e.key === 'Enter') elegir(indice);
            }} />
        </div>
        <div className="resultados">
          {opciones.map((o, i) => (
            <div key={o.clave} className={`opcion ${i === indice ? 'activa' : ''}`} onMouseEnter={() => setIndice(i)} onClick={() => elegir(i)}>
              {o.icono}
              <span className="crece">{o.texto}</span>
              <span className="pequeno tenue">{o.detalle}</span>
            </div>
          ))}
          {!opciones.length && <div className="vacio">Sin resultados</div>}
        </div>
      </motion.div>
    </motion.div>
  );
}

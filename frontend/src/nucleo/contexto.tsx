import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { AlertTriangle, CheckCircle2, Info, X, XCircle } from 'lucide-react';
import { api, enSesionExpirada, ErrorApi, token, type Fila } from './api';
import { traducir, type Idioma } from './i18n';

// ---------------------------------------------------------------- Sesión (RNF01, RN14)

export interface Usuario {
  id: number;
  login: string;
  nombre: string;
  cargo: string;
  idFilial: number;
  filial: string;
  filialCodigo: string;
  roles: string[];
  corporativo: boolean;
  idioma: Idioma;
  email?: string;
  totpActivo?: boolean;
}

interface Sesion {
  usuario: Usuario | null;
  cargando: boolean;
  iniciar: (t: string, u: Usuario, recordar: boolean) => void;
  salir: () => void;
  refrescar: () => Promise<void>;
  tiene: (...roles: string[]) => boolean;
}

const CtxSesion = createContext<Sesion>(null as unknown as Sesion);
export const useSesion = () => useContext(CtxSesion);

export function ProveedorSesion({ children }: { children: ReactNode }) {
  const [usuario, setUsuario] = useState<Usuario | null>(null);
  const [cargando, setCargando] = useState(true);

  const refrescar = useCallback(async () => {
    if (!token.leer()) {
      setUsuario(null);
      setCargando(false);
      return;
    }
    try {
      setUsuario(await api.get<Usuario>('/api/auth/yo'));
      setCargando(false);
    } catch (e) {
      if (e instanceof ErrorApi && (e.estado === 0 || e.estado >= 500)) {
        // El servidor no responde (por ejemplo, se está reiniciando): se conserva la sesión y se reintenta
        setTimeout(() => refrescarRef.current(), 3000);
        return;
      }
      token.borrar();
      setUsuario(null);
      setCargando(false);
    }
  }, []);
  const refrescarRef = useRef(refrescar);
  refrescarRef.current = refrescar;

  useEffect(() => {
    enSesionExpirada(() => {
      token.borrar();
      setUsuario(null);
    });
    refrescar();
  }, [refrescar]);

  const valor = useMemo<Sesion>(
    () => ({
      usuario,
      cargando,
      iniciar: (t, u, recordar) => {
        token.guardar(t, recordar);
        setUsuario(u);
        refrescar();
      },
      salir: () => {
        token.borrar();
        setUsuario(null);
      },
      refrescar,
      tiene: (...roles) => !!usuario && roles.some((r) => usuario.roles.includes(r)),
    }),
    [usuario, cargando, refrescar]
  );
  return <CtxSesion.Provider value={valor}>{children}</CtxSesion.Provider>;
}

// ---------------------------------------------------------------- Preferencias (idioma y tema)

interface Preferencias {
  idioma: Idioma;
  setIdioma: (i: Idioma) => void;
  t: (clave: string) => string;
  tema: 'oscuro' | 'claro';
  alternarTema: () => void;
  animaciones: boolean;
  setAnimaciones: (a: boolean) => void;
}

const CtxPref = createContext<Preferencias>(null as unknown as Preferencias);
export const usePreferencias = () => useContext(CtxPref);

const leer = (k: string, d: string) => {
  try {
    return localStorage.getItem(k) ?? d;
  } catch {
    return d;
  }
};
const escribir = (k: string, v: string) => {
  try {
    localStorage.setItem(k, v);
  } catch {
    /* sin almacenamiento */
  }
};

export function ProveedorPreferencias({ children }: { children: ReactNode }) {
  const [idioma, setIdiomaE] = useState<Idioma>(leer('sigre.idioma', 'es') as Idioma);
  const [tema, setTema] = useState<'oscuro' | 'claro'>(leer('sigre.tema', 'oscuro') as 'oscuro' | 'claro');
  const [animaciones, setAnim] = useState(leer('sigre.animaciones', 'si') === 'si');

  useEffect(() => {
    document.documentElement.dataset.tema = tema;
    document.documentElement.lang = idioma;
    document.documentElement.dataset.animaciones = animaciones ? 'si' : 'no';
  }, [tema, idioma, animaciones]);

  const valor = useMemo<Preferencias>(
    () => ({
      idioma,
      setIdioma: (i) => {
        setIdiomaE(i);
        escribir('sigre.idioma', i);
      },
      t: (c) => traducir(idioma, c),
      tema,
      alternarTema: () =>
        setTema((x) => {
          const n = x === 'oscuro' ? 'claro' : 'oscuro';
          escribir('sigre.tema', n);
          return n;
        }),
      animaciones,
      setAnimaciones: (a) => {
        setAnim(a);
        escribir('sigre.animaciones', a ? 'si' : 'no');
      },
    }),
    [idioma, tema, animaciones]
  );
  return <CtxPref.Provider value={valor}>{children}</CtxPref.Provider>;
}

// ---------------------------------------------------------------- Avisos (toasts)

type TipoAviso = 'exito' | 'error' | 'info' | 'alerta';
interface Aviso {
  id: number;
  tipo: TipoAviso;
  titulo: string;
  texto?: string;
  regla?: string | null;
}

interface Avisos {
  avisar: (tipo: TipoAviso, titulo: string, texto?: string, regla?: string | null) => void;
  error: (e: unknown) => void;
}

const CtxAvisos = createContext<Avisos>(null as unknown as Avisos);
export const useAvisos = () => useContext(CtxAvisos);

export function ProveedorAvisos({ children }: { children: ReactNode }) {
  const [lista, setLista] = useState<Aviso[]>([]);
  const n = useRef(0);
  const quitar = (id: number) => setLista((l) => l.filter((a) => a.id !== id));
  const avisar = useCallback((tipo: TipoAviso, titulo: string, texto?: string, regla?: string | null) => {
    const id = ++n.current;
    setLista((l) => [...l.slice(-4), { id, tipo, titulo, texto, regla }]);
    setTimeout(() => quitar(id), tipo === 'error' ? 8000 : 5000);
  }, []);
  const error = useCallback(
    (e: unknown) => {
      if (e instanceof ErrorApi) {
        avisar('error', e.regla ? `Regla ${e.regla}` : e.tipo, e.message, e.regla);
      } else {
        avisar('error', 'Error', e instanceof Error ? e.message : String(e));
      }
    },
    [avisar]
  );
  const iconos = { exito: CheckCircle2, error: XCircle, info: Info, alerta: AlertTriangle };
  return (
    <CtxAvisos.Provider value={{ avisar, error }}>
      {children}
      <div className="avisos" role="status" aria-live="polite">
        <AnimatePresence>
          {lista.map((a) => {
            const I = iconos[a.tipo];
            return (
              <motion.div
                key={a.id}
                layout
                initial={{ opacity: 0, x: 60, scale: 0.95 }}
                animate={{ opacity: 1, x: 0, scale: 1 }}
                exit={{ opacity: 0, x: 60, scale: 0.95 }}
                className={`aviso aviso-${a.tipo}`}
              >
                <I size={20} />
                <div>
                  <strong>{a.titulo}</strong>
                  {a.texto && <p>{a.texto}</p>}
                </div>
                <button className="icono-btn" onClick={() => quitar(a.id)} aria-label="Cerrar">
                  <X size={15} />
                </button>
                <span className="aviso-barra" />
              </motion.div>
            );
          })}
        </AnimatePresence>
      </div>
    </CtxAvisos.Provider>
  );
}

// ---------------------------------------------------------------- Eventos en tiempo real (SSE)

export type Evento = Fila & { evento: string; _recibido: number };
type Oyente = (e: Evento) => void;

interface Eventos {
  conectado: boolean;
  ultimos: Evento[];
  suscribir: (f: Oyente) => () => void;
}

const CtxEventos = createContext<Eventos>({ conectado: false, ultimos: [], suscribir: () => () => {} });
export const useEventos = () => useContext(CtxEventos);

/** Llama a la función cada vez que llega un evento de los tipos indicados. */
export function useAlEvento(tipos: string[], f: Oyente) {
  const { suscribir } = useEventos();
  const ref = useRef(f);
  ref.current = f;
  const clave = tipos.join(',');
  useEffect(() => suscribir((e) => (tipos.includes(e.evento) || tipos.includes('*')) && ref.current(e)), [suscribir, clave]);
}

export function ProveedorEventos({ children }: { children: ReactNode }) {
  const { usuario } = useSesion();
  const [conectado, setConectado] = useState(false);
  const [ultimos, setUltimos] = useState<Evento[]>([]);
  const oyentes = useRef(new Set<Oyente>());

  useEffect(() => {
    if (!usuario) return;
    let fuente: EventSource | null = null;
    let reintento: ReturnType<typeof setTimeout>;
    let vivo = true;
    const tipos = ['MEDICION', 'ALERTA_GENERADA', 'ALERTA_ACTUALIZADA', 'NOTIFICACION', 'EVALUACION', 'PLAN', 'ACCION', 'VALIDACION', 'CUMPLIMIENTO'];
    const abrir = () => {
      const t = token.leer();
      if (!t || !vivo) return;
      fuente = new EventSource(`/api/eventos?token=${encodeURIComponent(t)}`);
      fuente.addEventListener('CONECTADO', () => setConectado(true));
      for (const tipo of tipos) {
        fuente.addEventListener(tipo, (m) => {
          try {
            const e = { ...JSON.parse((m as MessageEvent).data), evento: tipo, _recibido: Date.now() } as Evento;
            setUltimos((l) => [e, ...l].slice(0, 40));
            oyentes.current.forEach((f) => f(e));
          } catch {
            /* evento no válido */
          }
        });
      }
      fuente.onerror = () => {
        setConectado(false);
        fuente?.close();
        reintento = setTimeout(abrir, 4000);
      };
    };
    abrir();
    return () => {
      vivo = false;
      clearTimeout(reintento);
      fuente?.close();
      setConectado(false);
    };
  }, [usuario?.id]);

  const suscribir = useCallback((f: Oyente) => {
    oyentes.current.add(f);
    return () => {
      oyentes.current.delete(f);
    };
  }, []);

  return <CtxEventos.Provider value={{ conectado, ultimos, suscribir }}>{children}</CtxEventos.Provider>;
}

// ---------------------------------------------------------------- Datos con recarga

/** Carga datos de la API, con recarga manual y estado de error. */
export function useDatos<T = any>(ruta: string | null, deps: unknown[] = []) {
  const [datos, setDatos] = useState<T | null>(null);
  const [error, setError] = useState<ErrorApi | null>(null);
  const [cargando, setCargando] = useState(true);
  const version = useRef(0);
  const cargar = useCallback(async () => {
    if (!ruta) return;
    const v = ++version.current;
    setCargando(true);
    try {
      const d = await api.get<T>(ruta);
      if (v === version.current) {
        setDatos(d);
        setError(null);
      }
    } catch (e) {
      if (v === version.current) setError(e as ErrorApi);
    } finally {
      if (v === version.current) setCargando(false);
    }
  }, [ruta]);
  useEffect(() => {
    cargar();
  }, [cargar, ...deps]);
  return { datos, error, cargando, recargar: cargar, setDatos };
}

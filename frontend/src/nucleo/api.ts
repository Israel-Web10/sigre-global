// Cliente de la API REST de SIGRE Global (JWT en el encabezado Authorization).

export type Fila = Record<string, any>;

export class ErrorApi extends Error {
  estado: number;
  regla: string | null;
  tipo: string;
  constructor(estado: number, mensaje: string, regla: string | null, tipo: string) {
    super(mensaje);
    this.estado = estado;
    this.regla = regla;
    this.tipo = tipo;
  }
}

const CLAVE_TOKEN = 'sigre.token';

export const token = {
  leer: (): string | null => {
    try {
      return sessionStorage.getItem(CLAVE_TOKEN) ?? localStorage.getItem(CLAVE_TOKEN);
    } catch {
      return null;
    }
  },
  guardar: (t: string, recordar: boolean) => {
    try {
      (recordar ? localStorage : sessionStorage).setItem(CLAVE_TOKEN, t);
    } catch {
      /* sin almacenamiento */
    }
  },
  borrar: () => {
    try {
      sessionStorage.removeItem(CLAVE_TOKEN);
      localStorage.removeItem(CLAVE_TOKEN);
    } catch {
      /* sin almacenamiento */
    }
  },
};

let alExpirar: (() => void) | null = null;
export const enSesionExpirada = (f: () => void) => {
  alExpirar = f;
};

async function pedir<T>(metodo: string, ruta: string, cuerpo?: unknown, formulario?: FormData): Promise<T> {
  const encabezados: Record<string, string> = {};
  const t = token.leer();
  if (t) encabezados['Authorization'] = `Bearer ${t}`;
  if (cuerpo !== undefined) encabezados['Content-Type'] = 'application/json';
  let r: Response;
  try {
    r = await fetch(ruta, {
      method: metodo,
      headers: encabezados,
      body: formulario ?? (cuerpo !== undefined ? JSON.stringify(cuerpo) : undefined),
    });
  } catch {
    throw new ErrorApi(0, 'No hay conexión con el servidor de SIGRE Global', null, 'Conexión');
  }
  if (r.status === 401 && !ruta.startsWith('/api/auth/login')) alExpirar?.();
  const texto = await r.text();
  let datos: any = null;
  try {
    datos = texto ? JSON.parse(texto) : null;
  } catch {
    datos = texto;
  }
  if (!r.ok) {
    const m = datos && typeof datos === 'object' ? datos : {};
    throw new ErrorApi(r.status, m.mensaje || m.message || `Error ${r.status}`, m.regla ?? null, m.tipo || 'Error');
  }
  return datos as T;
}

export const api = {
  get: <T = any>(ruta: string) => pedir<T>('GET', ruta),
  post: <T = any>(ruta: string, cuerpo: unknown = {}) => pedir<T>('POST', ruta, cuerpo),
  put: <T = any>(ruta: string, cuerpo: unknown = {}) => pedir<T>('PUT', ruta, cuerpo),
  del: <T = any>(ruta: string) => pedir<T>('DELETE', ruta),
  subir: <T = any>(ruta: string, f: FormData) => pedir<T>('POST', ruta, undefined, f),
};

/** Descarga un archivo protegido (Excel, evidencia) usando el token del encabezado. */
export async function descargar(ruta: string, nombre: string, abrir = false) {
  const t = token.leer();
  const r = await fetch(ruta, { headers: t ? { Authorization: `Bearer ${t}` } : {} });
  if (!r.ok) {
    let m = `Error ${r.status}`;
    try {
      m = (await r.json()).mensaje ?? m;
    } catch {
      /* sin cuerpo */
    }
    throw new ErrorApi(r.status, m, null, 'Descarga');
  }
  const blob = await r.blob();
  const url = URL.createObjectURL(blob);
  if (abrir) {
    window.open(url, '_blank');
  } else {
    const a = document.createElement('a');
    a.href = url;
    a.download = nombre;
    document.body.appendChild(a);
    a.click();
    a.remove();
  }
  setTimeout(() => URL.revokeObjectURL(url), 60000);
}

/** Construye una ruta con parámetros de consulta omitiendo los vacíos. */
export function conParametros(ruta: string, p: Record<string, unknown>) {
  const q = Object.entries(p)
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
    .join('&');
  return q ? `${ruta}?${q}` : ruta;
}

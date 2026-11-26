// Formatos y catálogos de presentación compartidos por las pantallas.

export const NIVELES = ['BAJO', 'MEDIO', 'ALTO', 'CRITICO'] as const;

export const colorNivel = (n?: string | null) =>
  ({ BAJO: 'var(--bajo)', MEDIO: 'var(--medio)', ALTO: 'var(--alto)', CRITICO: 'var(--critico)' } as Record<string, string>)[n ?? ''] ??
  'var(--texto-3)';

/** Fondos más oscuros para distintivos con texto blanco (contraste WCAG AA). */
export const colorNivelSolido = (n?: string | null) =>
  ({ BAJO: 'var(--bajo-solido)', MEDIO: 'var(--medio-solido)', ALTO: 'var(--alto-solido)', CRITICO: 'var(--critico-solido)' } as Record<string, string>)[n ?? ''] ??
  'var(--texto-3)';

export const colorValorSolido = (v: number) => colorNivelSolido(v <= 4 ? 'BAJO' : v <= 9 ? 'MEDIO' : v <= 14 ? 'ALTO' : 'CRITICO');

export const nivelDe = (v: number) => (v <= 4 ? 'BAJO' : v <= 9 ? 'MEDIO' : v <= 14 ? 'ALTO' : 'CRITICO');

export const colorValor = (v: number) => colorNivel(nivelDe(v));

export const colorSemaforo = (s?: string | null) =>
  ({ VERDE: 'var(--bajo)', AMARILLO: 'var(--medio)', ROJO: 'var(--critico)' } as Record<string, string>)[s ?? ''] ?? 'var(--texto-3)';

export const colorSeveridad = (s?: string | null) =>
  ({ CRITICA: 'var(--critico)', ADVERTENCIA: 'var(--alto)', PREVENTIVA: 'var(--info)' } as Record<string, string>)[s ?? ''] ?? 'var(--texto-3)';

const ETIQUETAS: Record<string, string> = {
  FINANCIERO: 'Financiero', TECNOLOGICO: 'Tecnológico', LEGAL: 'Legal', OPERATIVO: 'Operativo', REPUTACIONAL: 'Reputacional',
  IDENTIFICADO: 'Identificado', EVALUADO: 'Evaluado', EN_TRATAMIENTO: 'En tratamiento', MITIGADO: 'Mitigado', ACEPTADO: 'Aceptado',
  CERRADO: 'Cerrado', BAJO: 'Bajo', MEDIO: 'Medio', ALTO: 'Alto', CRITICO: 'Crítico',
  BORRADOR: 'Borrador', PROPUESTO: 'Propuesto', APROBADO: 'Aprobado',
  EVITAR: 'Evitar', REDUCIR: 'Reducir', TRANSFERIR: 'Transferir', ACEPTAR: 'Aceptar',
  NO_REQUERIDA: 'No requerida', PENDIENTE: 'Pendiente', VALIDADA: 'Validada', OBSERVADA: 'Observada',
  GENERADA: 'Generada', NOTIFICADA: 'Notificada', EN_ATENCION: 'En atención', ESCALADA: 'Escalada', RESUELTA: 'Resuelta', CERRADA: 'Cerrada',
  CRITICA: 'Crítica', ADVERTENCIA: 'Advertencia', PREVENTIVA: 'Preventiva',
  KRI_UMBRAL: 'Umbral de KRI', ACCION_VENCIDA: 'Acción vencida', OBLIGACION_POR_VENCER: 'Obligación por vencer',
  CONCILIACION_DIFERENCIA: 'Diferencia de conciliación', VALIDACION_FUERA_PLAZO: 'Validación fuera de plazo',
  HALLAZGO_VENCIDO: 'Hallazgo vencido', RIESGO_LEGAL: 'Riesgo legal',
  EN_CURSO: 'En curso', CUMPLE: 'Cumple', PARCIAL: 'Parcial', NO_CUMPLE: 'No cumple',
  PLANIFICADA: 'Planificada', EN_EJECUCION: 'En ejecución', ABIERTO: 'Abierto', EN_PROCESO: 'En proceso',
  ALTA: 'Alta', MEDIA: 'Media', CONCILIADA: 'Conciliada', CON_DIFERENCIA: 'Con diferencia',
  VERDE: 'Verde', AMARILLO: 'Amarillo', ROJO: 'Rojo', MANUAL: 'Manual', ERP: 'ERP', SIEM: 'SIEM',
  INHERENTE: 'Inherente', RESIDUAL: 'Residual', HALLAZGO: 'Hallazgo', CUMPLIMIENTO: 'Cumplimiento',
  GESTOR: 'Gestor de Riesgos', RESPONSABLE: 'Responsable de Riesgo', AUDITOR_INTERNO: 'Auditor Interno',
  OFICIAL_CUMPLIMIENTO: 'Oficial de Cumplimiento', DIRECTOR: 'Director de Riesgos', GERENTE_FILIAL: 'Gerente de Filial',
  ADMIN: 'Administrador', AUDITOR_FINANCIERO: 'Auditor Financiero',
  FINANCIERA: 'Financiera', OPERATIVA: 'Operativa', TECNOLOGICA: 'Tecnológica', OK: 'Correcto', RECHAZADO: 'Rechazado',
  TRIGGER: 'Trigger', APLICACION: 'Aplicación', PREVENTIVO: 'Preventivo', DETECTIVO: 'Detectivo', CORRECTIVO: 'Correctivo',
  MAYOR: 'Creciente', MENOR: 'Decreciente', DIARIA: 'Diaria', SEMANAL: 'Semanal', MENSUAL: 'Mensual', TRIMESTRAL: 'Trimestral',
  SEMESTRAL: 'Semestral', ANUAL: 'Anual', UNICA: 'Única',
};

export const etiqueta = (c?: string | null) => (c ? ETIQUETAS[c] ?? c.replaceAll('_', ' ').toLowerCase().replace(/^\w/, (x) => x.toUpperCase()) : '—');

export const operacion = (c?: string | null) =>
  c ? c.replaceAll('_', ' ').toLowerCase().replace(/^\w/, (x) => x.toUpperCase()) : '—';

const nf = new Intl.NumberFormat('es-BO', { maximumFractionDigits: 2 });
const nf0 = new Intl.NumberFormat('es-BO', { maximumFractionDigits: 0 });

export const numero = (v: unknown, dec = 2) =>
  v === null || v === undefined || v === '' ? '—' : (dec === 0 ? nf0 : nf).format(Number(v));

export const usd = (v: unknown, compacto = false) => {
  if (v === null || v === undefined) return '—';
  const n = Number(v);
  if (compacto && Math.abs(n) >= 1_000_000) return `${nf.format(n / 1_000_000)} M USD`;
  if (compacto && Math.abs(n) >= 10_000) return `${nf0.format(n / 1000)} mil USD`;
  return `${nf.format(n)} USD`;
};

export const fecha = (v?: string | null) => {
  if (!v) return '—';
  const d = new Date(v.length === 10 ? `${v}T12:00:00` : v);
  return d.toLocaleDateString('es-BO', { day: '2-digit', month: 'short', year: 'numeric' });
};

export const fechaHora = (v?: string | null) => {
  if (!v) return '—';
  return new Date(v).toLocaleString('es-BO', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
};

export const haceCuanto = (v?: string | null) => {
  if (!v) return '—';
  const s = (Date.now() - new Date(v).getTime()) / 1000;
  if (s < 0) return 'en el futuro';
  if (s < 60) return 'hace un momento';
  if (s < 3600) return `hace ${Math.floor(s / 60)} min`;
  if (s < 86400) return `hace ${Math.floor(s / 3600)} h`;
  if (s < 86400 * 30) return `hace ${Math.floor(s / 86400)} d`;
  return fecha(v);
};

/** Tiempo restante de un SLA en texto corto ("3 h 12 min", "vencido hace 2 h"). */
export const restante = (segundos: number) => {
  const a = Math.abs(segundos);
  const d = Math.floor(a / 86400);
  const h = Math.floor((a % 86400) / 3600);
  const m = Math.floor((a % 3600) / 60);
  const s = Math.floor(a % 60);
  const t = d > 0 ? `${d} d ${h} h` : h > 0 ? `${h} h ${m} min` : m > 0 ? `${m} min ${s} s` : `${s} s`;
  return segundos >= 0 ? t : `vencido hace ${t}`;
};

/** Bandera en SVG (Windows no dibuja los emojis de banderas). */
export const BANDERAS: Record<string, string> = {
  BO: '<rect width="30" height="7" fill="#d52b1e"/><rect y="7" width="30" height="6" fill="#f9e300"/><rect y="13" width="30" height="7" fill="#007934"/>',
  PE: '<rect width="30" height="20" fill="#fff"/><rect width="10" height="20" fill="#d91023"/><rect x="20" width="10" height="20" fill="#d91023"/>',
  CL: '<rect width="30" height="20" fill="#fff"/><rect y="10" width="30" height="10" fill="#d52b1e"/><rect width="10" height="10" fill="#0039a6"/><circle cx="5" cy="5" r="2" fill="#fff"/>',
  AR: '<rect width="30" height="20" fill="#74acdf"/><rect y="6.7" width="30" height="6.6" fill="#fff"/><circle cx="15" cy="10" r="2.2" fill="#f6b40e"/>',
  CO: '<rect width="30" height="10" fill="#fcd116"/><rect y="10" width="30" height="5" fill="#003893"/><rect y="15" width="30" height="5" fill="#ce1126"/>',
  BR: '<rect width="30" height="20" fill="#009b3a"/><path d="M15 3 27 10 15 17 3 10Z" fill="#fedf00"/><circle cx="15" cy="10" r="4" fill="#002776"/>',
  ES: '<rect width="30" height="20" fill="#aa151b"/><rect y="5" width="30" height="10" fill="#f1bf00"/>',
};

export const banderaHtml = (codigo?: string | null) =>
  `<span class="bandera-svg"><svg viewBox="0 0 30 20">${BANDERAS[(codigo ?? '').toUpperCase()] ?? '<rect width="30" height="20" fill="#64748b"/>'}</svg></span>`;

/** Para textos planos (listas desplegables): solo el código del país. */
export const bandera = (codigo?: string | null) => (codigo ? codigo.toUpperCase() : '');

export const iniciales = (nombre?: string | null) =>
  (nombre ?? '?')
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0])
    .join('')
    .toUpperCase();

export const hoy = () => new Date().toISOString().slice(0, 10);

export const sumarDias = (dias: number) => {
  const d = new Date();
  d.setDate(d.getDate() + dias);
  return d.toISOString().slice(0, 10);
};

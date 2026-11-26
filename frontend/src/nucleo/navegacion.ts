import {
  Activity, AppWindow, Bell, BookOpenCheck, ClipboardCheck, FileSpreadsheet, Gauge, GitCompareArrows, LayoutDashboard,
  ListChecks, Network, Presentation, Scale, ScrollText, Settings2, ShieldAlert, Smartphone, Stamp, User, type LucideIcon,
} from 'lucide-react';

export interface ItemNav {
  ruta: string;
  clave: string;
  icono: LucideIcon;
  grupo: 'principal' | 'monitoreo' | 'auditoria' | 'gestion' | 'sistema';
  roles?: string[];
  cu?: string;
  movil?: boolean;
  /** Clave de traducción del texto corto de la barra inferior del teléfono */
  corta?: string;
}

const CORPORATIVOS = ['GESTOR', 'DIRECTOR', 'AUDITOR_INTERNO', 'AUDITOR_FINANCIERO', 'OFICIAL_CUMPLIMIENTO', 'ADMIN', 'GERENTE_FILIAL'];

export const NAVEGACION: ItemNav[] = [
  { ruta: '/', clave: 'tablero', icono: LayoutDashboard, grupo: 'principal', cu: 'CU14', movil: true },
  { ruta: '/riesgos', clave: 'riesgos', icono: ShieldAlert, grupo: 'principal', cu: 'CU01-CU02', movil: true },
  { ruta: '/aprobaciones', clave: 'aprobaciones', icono: Stamp, grupo: 'principal', roles: ['DIRECTOR', 'GERENTE_FILIAL'], cu: 'CU04' },
  { ruta: '/mis-acciones', clave: 'misAcciones', icono: ListChecks, grupo: 'principal', roles: ['RESPONSABLE', 'GESTOR', 'GERENTE_FILIAL'], cu: 'CU05', movil: true, corta: 'misAccionesCorto' },
  { ruta: '/kri', clave: 'kri', icono: Gauge, grupo: 'monitoreo', cu: 'CU06' },
  { ruta: '/alertas', clave: 'alertas', icono: Bell, grupo: 'monitoreo', cu: 'CU07-CU09', movil: true },
  { ruta: '/integraciones', clave: 'integraciones', icono: Network, grupo: 'monitoreo', roles: ['ADMIN', 'GESTOR', 'DIRECTOR'] },
  { ruta: '/auditorias', clave: 'auditorias', icono: ClipboardCheck, grupo: 'auditoria', roles: CORPORATIVOS, cu: 'CU10-CU11' },
  { ruta: '/validacion-financiera', clave: 'validacion', icono: Scale, grupo: 'auditoria', roles: ['AUDITOR_FINANCIERO', 'DIRECTOR'], cu: 'CU17' },
  { ruta: '/conciliacion', clave: 'conciliacion', icono: GitCompareArrows, grupo: 'auditoria', roles: ['AUDITOR_FINANCIERO', 'DIRECTOR', 'AUDITOR_INTERNO'], cu: 'CU18' },
  { ruta: '/cumplimiento', clave: 'cumplimiento', icono: BookOpenCheck, grupo: 'auditoria', roles: ['OFICIAL_CUMPLIMIENTO', 'DIRECTOR', 'GESTOR', 'GERENTE_FILIAL', 'AUDITOR_INTERNO', 'ADMIN'], cu: 'CU12-CU13' },
  { ruta: '/reportes', clave: 'reportes', icono: FileSpreadsheet, grupo: 'gestion', roles: CORPORATIVOS, cu: 'CU14' },
  { ruta: '/bitacora', clave: 'bitacora', icono: ScrollText, grupo: 'gestion', roles: ['AUDITOR_INTERNO', 'AUDITOR_FINANCIERO', 'DIRECTOR', 'ADMIN'], cu: 'CU16' },
  { ruta: '/administracion', clave: 'administracion', icono: Settings2, grupo: 'gestion', roles: ['ADMIN', 'DIRECTOR'], cu: 'CU15' },
  { ruta: '/arquitectura', clave: 'arquitectura', icono: Activity, grupo: 'sistema' },
  { ruta: '/movil', clave: 'movil', icono: Smartphone, grupo: 'sistema' },
  { ruta: '/presentacion', clave: 'presentacion', icono: Presentation, grupo: 'sistema' },
  { ruta: '/perfil', clave: 'perfil', icono: User, grupo: 'sistema' },
];

export const GRUPOS: ItemNav['grupo'][] = ['principal', 'monitoreo', 'auditoria', 'gestion', 'sistema'];

export const visible = (i: ItemNav, roles: string[]) => !i.roles || i.roles.some((r) => roles.includes(r));

export const ICONO_VENTANA = AppWindow;

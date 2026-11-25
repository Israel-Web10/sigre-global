// Textos de la interfaz en español, inglés y portugués (RNF de usabilidad multilingüe).

export type Idioma = 'es' | 'en' | 'pt';

const T: Record<string, [string, string, string]> = {
  tablero: ['Tablero ejecutivo', 'Executive dashboard', 'Painel executivo'],
  riesgos: ['Riesgos', 'Risks', 'Riscos'],
  aprobaciones: ['Aprobaciones', 'Approvals', 'Aprovações'],
  misAcciones: ['Mis acciones', 'My actions', 'Minhas ações'],
  kri: ['Indicadores KRI', 'KRI indicators', 'Indicadores KRI'],
  alertas: ['Alertas', 'Alerts', 'Alertas'],
  integraciones: ['Integraciones', 'Integrations', 'Integrações'],
  auditorias: ['Auditorías', 'Audits', 'Auditorias'],
  validacion: ['Validación financiera', 'Financial validation', 'Validação financeira'],
  conciliacion: ['Conciliación ERP', 'ERP reconciliation', 'Conciliação ERP'],
  cumplimiento: ['Cumplimiento normativo', 'Regulatory compliance', 'Conformidade regulatória'],
  reportes: ['Reportes', 'Reports', 'Relatórios'],
  bitacora: ['Bitácora', 'Audit log', 'Trilha de auditoria'],
  administracion: ['Administración', 'Administration', 'Administração'],
  arquitectura: ['Arquitectura y diseño', 'Architecture & design', 'Arquitetura e design'],
  movil: ['Vista móvil', 'Mobile view', 'Visão móvel'],
  presentacion: ['Presentación', 'Showcase', 'Apresentação'],
  perfil: ['Mi perfil', 'My profile', 'Meu perfil'],
  principal: ['Principal', 'Main', 'Principal'],
  monitoreo: ['Monitoreo', 'Monitoring', 'Monitoramento'],
  auditoria: ['Auditoría', 'Audit', 'Auditoria'],
  gestion: ['Gestión', 'Management', 'Gestão'],
  sistema: ['Sistema', 'System', 'Sistema'],
  buscar: ['Buscar o ir a… (Ctrl+K)', 'Search or jump to… (Ctrl+K)', 'Buscar ou ir para… (Ctrl+K)'],
  notificaciones: ['Notificaciones', 'Notifications', 'Notificações'],
  marcarLeidas: ['Marcar todas como leídas', 'Mark all as read', 'Marcar todas como lidas'],
  sinNotificaciones: ['No tiene notificaciones', 'No notifications', 'Sem notificações'],
  cerrarSesion: ['Cerrar sesión', 'Sign out', 'Sair'],
  enVivo: ['En vivo', 'Live', 'Ao vivo'],
  desconectado: ['Reconectando', 'Reconnecting', 'Reconectando'],
  bienvenida: ['Bienvenido', 'Welcome', 'Bem-vindo'],
  ingresar: ['Ingresar', 'Sign in', 'Entrar'],
  usuario: ['Usuario', 'Username', 'Usuário'],
  clave: ['Contraseña', 'Password', 'Senha'],
  recordar: ['Mantener la sesión en este equipo', 'Keep me signed in', 'Manter conectado'],
  accesoRapido: ['Acceso rápido de demostración', 'Demo quick access', 'Acesso rápido de demonstração'],
  temaClaro: ['Tema claro', 'Light theme', 'Tema claro'],
  temaOscuro: ['Tema oscuro', 'Dark theme', 'Tema escuro'],
  ventanas: ['Ventanas', 'Windows', 'Janelas'],
  abrirVentana: ['Abrir en ventana', 'Open in window', 'Abrir em janela'],
};

const IDX: Record<Idioma, number> = { es: 0, en: 1, pt: 2 };

export function traducir(idioma: Idioma, clave: string) {
  const f = T[clave];
  return f ? f[IDX[idioma]] : clave;
}

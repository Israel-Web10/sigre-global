import { lazy, Suspense, type ReactNode } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import Shell from './componentes/Shell';
import { Cargando } from './componentes/Ui';
import { ProveedorVentanas } from './componentes/Ventanas';
import { ProveedorAvisos, ProveedorEventos, ProveedorPreferencias, ProveedorSesion, useSesion } from './nucleo/contexto';
import Login from './paginas/Login';

const Tablero = lazy(() => import('./paginas/Tablero'));
const Riesgos = lazy(() => import('./paginas/Riesgos'));
const RiesgoDetalle = lazy(() => import('./paginas/RiesgoDetalle'));
const Aprobaciones = lazy(() => import('./paginas/Aprobaciones'));
const MisAcciones = lazy(() => import('./paginas/MisAcciones'));
const Kri = lazy(() => import('./paginas/Kri'));
const Alertas = lazy(() => import('./paginas/Alertas'));
const Integraciones = lazy(() => import('./paginas/Integraciones'));
const Auditorias = lazy(() => import('./paginas/Auditorias'));
const Validacion = lazy(() => import('./paginas/Validacion'));
const Conciliacion = lazy(() => import('./paginas/Conciliacion'));
const Cumplimiento = lazy(() => import('./paginas/Cumplimiento'));
const Reportes = lazy(() => import('./paginas/Reportes'));
const Bitacora = lazy(() => import('./paginas/Bitacora'));
const Administracion = lazy(() => import('./paginas/Administracion'));
const Arquitectura = lazy(() => import('./paginas/Arquitectura'));
const Movil = lazy(() => import('./paginas/Movil'));
const Presentacion = lazy(() => import('./paginas/Presentacion'));
const Perfil = lazy(() => import('./paginas/Perfil'));

function Protegida({ children }: { children: ReactNode }) {
  const { usuario, cargando } = useSesion();
  if (cargando) return <div style={{ padding: 40 }}><Cargando filas={6} /></div>;
  if (!usuario) return <Navigate to="/ingresar" replace />;
  return (
    <Shell>
      <Suspense fallback={<Cargando filas={6} alto={60} />}>{children}</Suspense>
    </Shell>
  );
}

function Rutas() {
  const { usuario } = useSesion();
  return (
    <Routes>
      <Route path="/ingresar" element={usuario ? <Navigate to="/" replace /> : <Login />} />
      <Route path="/" element={<Protegida><Tablero /></Protegida>} />
      <Route path="/riesgos" element={<Protegida><Riesgos /></Protegida>} />
      <Route path="/riesgos/:id" element={<Protegida><RiesgoDetalle /></Protegida>} />
      <Route path="/aprobaciones" element={<Protegida><Aprobaciones /></Protegida>} />
      <Route path="/mis-acciones" element={<Protegida><MisAcciones /></Protegida>} />
      <Route path="/kri" element={<Protegida><Kri /></Protegida>} />
      <Route path="/alertas" element={<Protegida><Alertas /></Protegida>} />
      <Route path="/integraciones" element={<Protegida><Integraciones /></Protegida>} />
      <Route path="/auditorias" element={<Protegida><Auditorias /></Protegida>} />
      <Route path="/validacion-financiera" element={<Protegida><Validacion /></Protegida>} />
      <Route path="/conciliacion" element={<Protegida><Conciliacion /></Protegida>} />
      <Route path="/cumplimiento" element={<Protegida><Cumplimiento /></Protegida>} />
      <Route path="/reportes" element={<Protegida><Reportes /></Protegida>} />
      <Route path="/bitacora" element={<Protegida><Bitacora /></Protegida>} />
      <Route path="/administracion" element={<Protegida><Administracion /></Protegida>} />
      <Route path="/arquitectura" element={<Protegida><Arquitectura /></Protegida>} />
      <Route path="/movil" element={<Protegida><Movil /></Protegida>} />
      <Route path="/presentacion" element={<Protegida><Presentacion /></Protegida>} />
      <Route path="/perfil" element={<Protegida><Perfil /></Protegida>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

export default function App() {
  return (
    <ProveedorPreferencias>
      <ProveedorSesion>
        <ProveedorAvisos>
          <ProveedorEventos>
            <BrowserRouter>
              <ProveedorVentanas>
                <Rutas />
              </ProveedorVentanas>
            </BrowserRouter>
          </ProveedorEventos>
        </ProveedorAvisos>
      </ProveedorSesion>
    </ProveedorPreferencias>
  );
}

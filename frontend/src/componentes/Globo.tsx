import { useEffect, useRef } from 'react';
import * as THREE from 'three';
import { geoEquirectangular, geoPath } from 'd3-geo';
import { feature } from 'topojson-client';
import tierra from 'world-atlas/land-110m.json';

/** Punto del globo: una filial con su color según el riesgo más alto. */
export interface PuntoGlobo {
  id: number | string;
  nombre: string;
  lat: number;
  lon: number;
  color: string;
  valor?: number;
  etiqueta?: string;
  matriz?: boolean;
}

let puntosTierra: Float32Array | null = null;

/**
 * Muestrea la superficie terrestre (world-atlas 1:110m) en una malla casi uniforme. Los
 * continentes se dibujan una vez en un lienzo equirectangular y se consulta cada píxel, lo que
 * es miles de veces más rápido que probar cada punto contra los polígonos.
 */
function calcularTierra(): Float32Array {
  if (puntosTierra) return puntosTierra;
  const geo = feature(tierra as any, (tierra as any).objects.land) as any;
  const W = 1440;
  const H = 720;
  const lienzo = document.createElement('canvas');
  lienzo.width = W;
  lienzo.height = H;
  const ctx = lienzo.getContext('2d', { willReadFrequently: true })!;
  const proyeccion = geoEquirectangular().scale(W / (2 * Math.PI)).translate([W / 2, H / 2]);
  ctx.fillStyle = '#000';
  ctx.beginPath();
  geoPath(proyeccion, ctx)(geo);
  ctx.fill();
  const pixeles = ctx.getImageData(0, 0, W, H).data;
  const pos: number[] = [];
  const paso = 1.35;
  for (let lat = -84; lat <= 84; lat += paso) {
    const n = Math.max(1, Math.round((360 * Math.cos((lat * Math.PI) / 180)) / paso));
    for (let k = 0; k < n; k++) {
      const lon = -180 + (k * 360) / n;
      const x = Math.min(W - 1, Math.floor(((lon + 180) / 360) * W));
      const y = Math.min(H - 1, Math.floor(((90 - lat) / 180) * H));
      if (pixeles[(y * W + x) * 4 + 3] > 128) {
        const v = aVector(lat, lon, 1.001);
        pos.push(v.x, v.y, v.z);
      }
    }
  }
  puntosTierra = new Float32Array(pos);
  return puntosTierra;
}

function aVector(lat: number, lon: number, r: number) {
  const f = (lat * Math.PI) / 180;
  const l = (lon * Math.PI) / 180;
  return new THREE.Vector3(r * Math.cos(f) * Math.sin(l), r * Math.sin(f), r * Math.cos(f) * Math.cos(l));
}

const colorCss = (c: string) => {
  const mapa: Record<string, string> = {
    'var(--bajo)': '#22c55e', 'var(--medio)': '#eab308', 'var(--alto)': '#f97316', 'var(--critico)': '#ef4444', 'var(--acento)': '#22d3ee',
  };
  return new THREE.Color(mapa[c] ?? c);
};

/**
 * Globo 3D (three.js) con la red de filiales: tierra de puntos, atmósfera, marcadores que
 * laten y arcos animados desde la casa matriz. Se puede girar arrastrando.
 */
export default function Globo({
  puntos, alAlternar, girar = true, etiquetas = true, centroLon = -64, centroLat = -14, zoom = 1,
}: {
  puntos: PuntoGlobo[]; alAlternar?: (p: PuntoGlobo) => void; girar?: boolean; etiquetas?: boolean; centroLon?: number; centroLat?: number; zoom?: number;
}) {
  const contenedor = useRef<HTMLDivElement>(null);
  const capa = useRef<HTMLDivElement>(null);
  const datos = useRef(puntos);
  datos.current = puntos;

  useEffect(() => {
    const host = contenedor.current!;
    const ancho = () => host.clientWidth || 600;
    const alto = () => host.clientHeight || 600;
    const escena = new THREE.Scene();
    const camara = new THREE.PerspectiveCamera(38, ancho() / alto(), 0.1, 100);
    camara.position.set(0, 0, 3.55 / zoom);
    const render = new THREE.WebGLRenderer({ antialias: true, alpha: true });
    render.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    render.setSize(ancho(), alto());
    host.appendChild(render.domElement);

    const inclinacion = new THREE.Group();
    inclinacion.rotation.x = (-centroLat * Math.PI) / 180 * 0.9;
    escena.add(inclinacion);
    const globo = new THREE.Group();
    globo.rotation.y = (-centroLon * Math.PI) / 180;
    inclinacion.add(globo);

    // Esfera base y atmósfera (brillo de Fresnel)
    globo.add(new THREE.Mesh(new THREE.SphereGeometry(0.995, 64, 64), new THREE.MeshBasicMaterial({ color: 0x07142a, transparent: true, opacity: 0.92 })));
    const atmosfera = new THREE.Mesh(
      new THREE.SphereGeometry(1.16, 64, 64),
      new THREE.ShaderMaterial({
        transparent: true,
        side: THREE.BackSide,
        blending: THREE.AdditiveBlending,
        depthWrite: false,
        uniforms: { c1: { value: new THREE.Color(0x22d3ee) }, c2: { value: new THREE.Color(0x6366f1) } },
        vertexShader: 'varying vec3 vN; void main(){ vN = normalize(normalMatrix * normal); gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }',
        fragmentShader: 'uniform vec3 c1; uniform vec3 c2; varying vec3 vN; void main(){ float i = pow(0.72 - dot(vN, vec3(0,0,1.0)), 3.2); gl_FragColor = vec4(mix(c2,c1,0.5+vN.y*0.5), 1.0) * i * 1.6; }',
      })
    );
    escena.add(atmosfera);

    // Tierra de puntos
    const geoTierra = new THREE.BufferGeometry();
    geoTierra.setAttribute('position', new THREE.BufferAttribute(calcularTierra(), 3));
    const matTierra = new THREE.PointsMaterial({ color: 0x5eead4, size: 0.0115, transparent: true, opacity: 0.78, sizeAttenuation: true });
    globo.add(new THREE.Points(geoTierra, matTierra));

    // Malla de latitudes y longitudes
    const malla = new THREE.Group();
    const matMalla = new THREE.LineBasicMaterial({ color: 0x334e7a, transparent: true, opacity: 0.25 });
    for (let lat = -60; lat <= 60; lat += 30) {
      const pts = [];
      for (let lon = -180; lon <= 180; lon += 4) pts.push(aVector(lat, lon, 1.002));
      malla.add(new THREE.Line(new THREE.BufferGeometry().setFromPoints(pts), matMalla));
    }
    for (let lon = -180; lon < 180; lon += 30) {
      const pts = [];
      for (let lat = -90; lat <= 90; lat += 4) pts.push(aVector(lat, lon, 1.002));
      malla.add(new THREE.Line(new THREE.BufferGeometry().setFromPoints(pts), matMalla));
    }
    globo.add(malla);

    // Marcadores, anillos y arcos
    const marcadores = new THREE.Group();
    globo.add(marcadores);
    type Arco = { linea: THREE.Line; total: number; fase: number; vel: number };
    let arcos: Arco[] = [];
    let anillos: THREE.Mesh[] = [];
    const objetivos: { p: PuntoGlobo; v: THREE.Vector3 }[] = [];

    const construir = () => {
      marcadores.clear();
      arcos = [];
      anillos = [];
      objetivos.length = 0;
      const lista = datos.current;
      const matriz = lista.find((p) => p.matriz) ?? lista[0];
      for (const p of lista) {
        const v = aVector(p.lat, p.lon, 1.01);
        const c = colorCss(p.color);
        const punto = new THREE.Mesh(new THREE.SphereGeometry(p.matriz ? 0.022 : 0.016, 16, 16), new THREE.MeshBasicMaterial({ color: c }));
        punto.position.copy(v);
        marcadores.add(punto);
        const halo = new THREE.Mesh(new THREE.RingGeometry(0.02, 0.028, 40), new THREE.MeshBasicMaterial({ color: c, transparent: true, opacity: 0.9, side: THREE.DoubleSide }));
        halo.position.copy(v);
        halo.lookAt(v.clone().multiplyScalar(2));
        halo.userData.base = Math.random();
        marcadores.add(halo);
        anillos.push(halo);
        const columna = new THREE.Mesh(
          new THREE.CylinderGeometry(0.0035, 0.0035, 0.06 + (p.valor ?? 0) * 0.008, 8),
          new THREE.MeshBasicMaterial({ color: c, transparent: true, opacity: 0.75 })
        );
        const largo = 0.06 + (p.valor ?? 0) * 0.008;
        columna.position.copy(v.clone().multiplyScalar(1 + largo / 2));
        columna.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), v.clone().normalize());
        marcadores.add(columna);
        objetivos.push({ p, v: v.clone().multiplyScalar(1 + largo) });
        if (matriz && p !== matriz) {
          const a = aVector(matriz.lat, matriz.lon, 1.01);
          const medio = a.clone().add(v).multiplyScalar(0.5);
          const distancia = a.distanceTo(v);
          medio.normalize().multiplyScalar(1 + distancia * 0.45);
          const curva = new THREE.QuadraticBezierCurve3(a, medio, v);
          const pts = curva.getPoints(70);
          const linea = new THREE.Line(
            new THREE.BufferGeometry().setFromPoints(pts),
            new THREE.LineBasicMaterial({ color: c, transparent: true, opacity: 0.85, blending: THREE.AdditiveBlending })
          );
          marcadores.add(linea);
          arcos.push({ linea, total: pts.length, fase: Math.random(), vel: 0.18 + Math.random() * 0.2 });
        }
      }
    };
    construir();

    // Etiquetas HTML proyectadas sobre el lienzo
    const etiquetasDom: HTMLDivElement[] = [];
    const prepararEtiquetas = () => {
      if (!capa.current) return;
      capa.current.innerHTML = '';
      etiquetasDom.length = 0;
      if (!etiquetas) return;
      for (const o of objetivos) {
        const e = document.createElement('div');
        e.className = 'globo-etiqueta';
        e.innerHTML = `<i style="background:${colorCss(o.p.color).getStyle()}"></i>${o.p.etiqueta ?? o.p.nombre}`;
        e.onclick = () => alAlternar?.(o.p);
        capa.current.appendChild(e);
        etiquetasDom.push(e);
      }
    };
    prepararEtiquetas();

    // Arrastre para girar
    let arrastrando = false;
    let ultimo = { x: 0, y: 0 };
    let velocidad = { x: 0, y: 0 };
    const abajo = (e: PointerEvent) => {
      arrastrando = true;
      ultimo = { x: e.clientX, y: e.clientY };
      host.setPointerCapture(e.pointerId);
    };
    const mover = (e: PointerEvent) => {
      if (!arrastrando) return;
      velocidad = { x: (e.clientX - ultimo.x) * 0.005, y: (e.clientY - ultimo.y) * 0.004 };
      globo.rotation.y += velocidad.x;
      inclinacion.rotation.x = Math.max(-1.1, Math.min(1.1, inclinacion.rotation.x + velocidad.y));
      ultimo = { x: e.clientX, y: e.clientY };
    };
    const arriba = () => {
      arrastrando = false;
    };
    host.addEventListener('pointerdown', abajo);
    host.addEventListener('pointermove', mover);
    host.addEventListener('pointerup', arriba);
    host.addEventListener('pointerleave', arriba);

    const reloj = new THREE.Clock();
    let cuadro = 0;
    const vec = new THREE.Vector3();
    const camaraDir = new THREE.Vector3();
    const animar = () => {
      cuadro = requestAnimationFrame(animar);
      const dt = reloj.getDelta();
      const t = reloj.elapsedTime;
      if (!arrastrando) {
        if (girar) globo.rotation.y += dt * 0.06;
        globo.rotation.y += velocidad.x;
        velocidad.x *= 0.92;
      }
      for (const a of arcos) {
        const f = (t * a.vel + a.fase) % 1.4;
        const inicio = Math.max(0, Math.floor((f - 0.45) * a.total));
        const fin = Math.min(a.total, Math.floor(f * a.total));
        a.linea.geometry.setDrawRange(inicio, Math.max(0, fin - inicio));
      }
      for (const h of anillos) {
        const k = (t * 0.7 + h.userData.base) % 1;
        h.scale.setScalar(1 + k * 2.6);
        (h.material as THREE.MeshBasicMaterial).opacity = 0.9 * (1 - k);
      }
      render.render(escena, camara);
      if (etiquetasDom.length) {
        camara.getWorldDirection(camaraDir);
        const w = ancho();
        const h = alto();
        objetivos.forEach((o, i) => {
          vec.copy(o.v);
          globo.localToWorld(vec);
          const frente = vec.clone().normalize().dot(camara.position.clone().normalize());
          vec.project(camara);
          const e = etiquetasDom[i];
          e.style.transform = `translate(${((vec.x + 1) / 2) * w}px, ${((1 - vec.y) / 2) * h}px) translate(-50%, -130%)`;
          e.style.opacity = frente > 0.25 ? String(Math.min(1, (frente - 0.25) * 3)) : '0';
          e.style.pointerEvents = frente > 0.3 ? 'auto' : 'none';
        });
      }
    };
    animar();

    const redimensionar = () => {
      camara.aspect = ancho() / alto();
      camara.updateProjectionMatrix();
      render.setSize(ancho(), alto());
    };
    const obs = new ResizeObserver(redimensionar);
    obs.observe(host);

    (host as any).__reconstruir = () => {
      construir();
      prepararEtiquetas();
    };

    return () => {
      cancelAnimationFrame(cuadro);
      obs.disconnect();
      host.removeEventListener('pointerdown', abajo);
      host.removeEventListener('pointermove', mover);
      host.removeEventListener('pointerup', arriba);
      host.removeEventListener('pointerleave', arriba);
      render.dispose();
      escena.traverse((o: any) => {
        o.geometry?.dispose?.();
        o.material?.dispose?.();
      });
      host.removeChild(render.domElement);
    };
  }, [girar, etiquetas, centroLon, centroLat, zoom]);

  useEffect(() => {
    (contenedor.current as any)?.__reconstruir?.();
  }, [puntos]);

  return (
    <div className="globo" style={{ position: 'relative', width: '100%', height: '100%', cursor: 'grab', touchAction: 'none' }}>
      <div ref={contenedor} style={{ position: 'absolute', inset: 0 }} />
      <div ref={capa} style={{ position: 'absolute', inset: 0, pointerEvents: 'none', overflow: 'hidden' }} />
    </div>
  );
}

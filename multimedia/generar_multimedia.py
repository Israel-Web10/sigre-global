"""
SIGRE Global - generador de la multimedia de la presentación.

Recorre la aplicación real (debe estar corriendo en http://localhost:8090) con un navegador
automatizado (Playwright + Microsoft Edge) y produce:
  - capturas de pantalla (WebP) de cada módulo,
  - un video MP4 del recorrido general,
  - GIF animados de los flujos principales,
  - el índice medios.json que usa la pantalla «Presentación».

Uso:  python generar_multimedia.py            (todo)
      python generar_multimedia.py capturas   (solo una parte: capturas | video | gifs)
Ingresa con los botones de acceso rápido de la demostración: no necesita claves.
"""
import json
import shutil
import subprocess
import sys
import time
from pathlib import Path

import imageio_ffmpeg
from PIL import Image
from playwright.sync_api import sync_playwright

URL = "http://localhost:8090"
RAIZ = Path(__file__).resolve().parent
SALIDA = RAIZ.parent / "frontend" / "public" / "medios"
TEMP = RAIZ / "_temporal"
FFMPEG = imageio_ffmpeg.get_ffmpeg_exe()
VISTA = {"width": 1440, "height": 900}
ARGS = ["--ignore-gpu-blocklist", "--enable-gpu"]
medios: list[dict] = []


def contexto(navegador, video: Path | None = None, tamano=VISTA):
    opciones = {"viewport": tamano, "color_scheme": "dark", "locale": "es-BO", "timezone_id": "America/La_Paz"}
    if video:
        opciones["record_video_dir"] = str(video)
        opciones["record_video_size"] = tamano
    return navegador.new_context(**opciones)


def ingresar(pagina, nombre: str):
    pagina.goto(f"{URL}/ingresar")
    pagina.get_by_role("button", name=nombre).first.click()
    pagina.wait_for_url(f"{URL}/", timeout=20000)
    pagina.wait_for_timeout(2500)


def api(pagina, ruta: str, metodo="GET", cuerpo=None):
    return pagina.evaluate(
        """async ([ruta, metodo, cuerpo]) => {
            const t = localStorage.getItem('sigre.token') || sessionStorage.getItem('sigre.token');
            const r = await fetch(ruta, {method: metodo, headers: {'Authorization': 'Bearer ' + t, 'Content-Type': 'application/json'},
                                         body: cuerpo ? JSON.stringify(cuerpo) : undefined});
            return r.json();
        }""",
        [ruta, metodo, cuerpo],
    )


def captura(pagina, nombre: str, titulo: str, descripcion: str = "", espera=1800, pagina_completa=False):
    pagina.wait_for_timeout(espera)
    png = TEMP / f"{nombre}.png"
    pagina.screenshot(path=str(png), full_page=pagina_completa)
    im = Image.open(png).convert("RGB")
    destino = SALIDA / "capturas" / f"{nombre}.webp"
    destino.parent.mkdir(parents=True, exist_ok=True)
    im.save(destino, "WEBP", quality=84, method=6)
    medios.append({"archivo": f"/medios/capturas/{nombre}.webp", "titulo": titulo, "tipo": "imagen", "descripcion": descripcion})
    print("  captura", nombre)


def a_mp4(webm: Path, destino: Path, recorte_inicio=0.0):
    subprocess.run([FFMPEG, "-y", "-loglevel", "error", "-ss", str(recorte_inicio), "-i", str(webm), "-c:v", "libx264", "-preset", "slow",
                    "-crf", "27", "-pix_fmt", "yuv420p", "-movflags", "+faststart", "-an", str(destino)], check=True)


def a_gif(webm: Path, destino: Path, inicio=0.0, duracion=None, ancho=780, fps=10):
    paleta = TEMP / "paleta.png"
    corte = ["-ss", str(inicio)] + (["-t", str(duracion)] if duracion else [])
    filtro = f"fps={fps},scale={ancho}:-1:flags=lanczos"
    subprocess.run([FFMPEG, "-y", "-loglevel", "error", *corte, "-i", str(webm), "-vf", f"{filtro},palettegen=stats_mode=diff", str(paleta)], check=True)
    subprocess.run([FFMPEG, "-y", "-loglevel", "error", *corte, "-i", str(webm), "-i", str(paleta), "-lavfi",
                    f"{filtro} [x]; [x][1:v] paletteuse=dither=bayer:bayer_scale=4:diff_mode=rectangle", str(destino)], check=True)


def grabar(navegador, nombre: str, guion, tamano=VISTA) -> Path:
    carpeta = TEMP / f"video_{nombre}"
    shutil.rmtree(carpeta, ignore_errors=True)
    ctx = contexto(navegador, carpeta, tamano)
    pagina = ctx.new_page()
    inicio = time.time()
    marcas = guion(pagina) or {}
    ctx.close()
    webm = next(carpeta.glob("*.webm"))
    print(f"  video {nombre}: {time.time() - inicio:.0f} s")
    return webm, marcas


# ------------------------------------------------------------------ capturas

def capturas(navegador):
    print("Capturas de pantalla")
    ctx = contexto(navegador)
    p = ctx.new_page()
    p.goto(f"{URL}/ingresar")
    captura(p, "01-ingreso", "Inicio de sesión", "Globo 3D de las filiales y acceso rápido por rol", espera=4500)
    ingresar(p, "Carlos Rojas")
    captura(p, "02-tablero", "Tablero ejecutivo (P01)", "Indicadores, red de filiales, mapa de calor y tendencias", espera=4500)
    p.goto(f"{URL}/riesgos")
    captura(p, "03-riesgos", "Catálogo de riesgos", "Filtros por categoría, estado, nivel y filial (RN14)")
    p.goto(f"{URL}/riesgos/1")
    captura(p, "04-riesgo", "Ficha del riesgo", "Flujo de estados D08, evaluación, tratamiento e historial", espera=2500)
    p.goto(f"{URL}/aprobaciones")
    p.wait_for_timeout(1500)
    if p.get_by_role("button", name="Revisar y decidir").count():
        p.get_by_role("button", name="Revisar y decidir").first.click()
        captura(p, "05-aprobacion", "Aprobación del tratamiento (CU04)", "Lista de verificación con RN03, RN04, RN05, RN12 y RN16", espera=2500)
        p.keyboard.press("Escape")
    p.goto(f"{URL}/alertas")
    captura(p, "06-alertas", "Centro de alertas", "Tablero por estado con la cuenta regresiva del SLA (RN07)", espera=2500)
    p.goto(f"{URL}/kri")
    captura(p, "07-kri", "Indicadores KRI", "Medidores con semáforo y series del ERP y el SIEM", espera=3000)
    p.goto(f"{URL}/integraciones")
    captura(p, "08-integraciones", "Integraciones en vivo", "ERP, SIEM, servicio normativo y consola de eventos", espera=3000)
    p.goto(f"{URL}/auditorias")
    captura(p, "09-auditorias", "Plan anual de auditorías", "Cronograma y hallazgos (CU10, CU11)", espera=2500)
    p.goto(f"{URL}/cumplimiento")
    captura(p, "10-cumplimiento", "Cumplimiento normativo", "Obligaciones por filial con avisos de vencimiento (RN09)", espera=2000)
    p.goto(f"{URL}/bitacora")
    captura(p, "11-bitacora", "Bitácora inalterable", "Registros de los triggers con valores antes y después (RN13)", espera=2500)
    p.goto(f"{URL}/arquitectura")
    captura(p, "12-arquitectura", "Arquitectura y diseño", "Capas en ejecución, triggers y diagramas del informe", espera=3000)
    p.goto(f"{URL}/administracion")
    captura(p, "13-administracion", "Administración", "Usuarios, roles, apetito por filial y parámetros (CU15)", espera=2000)
    ctx.close()

    ctx = contexto(navegador)
    p = ctx.new_page()
    ingresar(p, "Luis Mamani")
    p.goto(f"{URL}/validacion-financiera")
    captura(p, "14-validacion", "Validación financiera (P02)", "Contraste con el tipo de cambio del ERP y control RN17", espera=3000)
    p.goto(f"{URL}/conciliacion")
    captura(p, "15-conciliacion", "Conciliación con el ERP (CU18)", "Valor del sistema frente al cierre contable y tolerancia RN18", espera=2500)
    ctx.close()

    ctx = contexto(navegador, tamano={"width": 1440, "height": 1000})
    p = ctx.new_page()
    ingresar(p, "Jorge Condori")
    p.goto(f"{URL}/movil")
    captura(p, "16-movil", "Vista móvil (PWA)", "La misma aplicación dentro de un teléfono", espera=5000)
    ctx.close()


# ------------------------------------------------------------------ video del recorrido

def video(navegador):
    print("Video del recorrido general")

    def guion(p):
        p.goto(f"{URL}/ingresar")
        p.wait_for_timeout(4500)
        p.get_by_role("button", name="Carlos Rojas").first.click()
        p.wait_for_url(f"{URL}/", timeout=20000)
        p.wait_for_timeout(6000)
        p.mouse.wheel(0, 650)
        p.wait_for_timeout(3500)
        p.mouse.wheel(0, 700)
        p.wait_for_timeout(3500)
        p.goto(f"{URL}/riesgos")
        p.wait_for_timeout(3000)
        p.get_by_role("button", name="Tarjetas").click()
        p.wait_for_timeout(3000)
        p.goto(f"{URL}/riesgos/1")
        p.wait_for_timeout(3500)
        p.get_by_role("tab", name="Historial").click()
        p.wait_for_timeout(3500)
        p.goto(f"{URL}/aprobaciones")
        p.wait_for_timeout(2000)
        if p.get_by_role("button", name="Revisar y decidir").count():
            p.get_by_role("button", name="Revisar y decidir").first.click()
            p.wait_for_timeout(4500)
            p.keyboard.press("Escape")
        p.goto(f"{URL}/alertas")
        p.wait_for_timeout(4000)
        p.goto(f"{URL}/kri")
        p.wait_for_timeout(4500)
        p.goto(f"{URL}/integraciones")
        p.wait_for_timeout(5000)
        p.goto(f"{URL}/arquitectura")
        p.wait_for_timeout(5000)
        p.mouse.wheel(0, 900)
        p.wait_for_timeout(3000)

    webm, _ = grabar(navegador, "recorrido", guion)
    a_mp4(webm, SALIDA / "recorrido-sigre-global.mp4", recorte_inicio=0.5)
    medios.insert(0, {"archivo": "/medios/recorrido-sigre-global.mp4", "titulo": "Recorrido general del sistema", "tipo": "video",
                      "descripcion": "Ingreso, tablero, riesgos, aprobación, alertas, KRI, integraciones y arquitectura"})


# ------------------------------------------------------------------ GIF de los flujos

def gifs(navegador):
    print("GIF de los flujos")
    tamano = {"width": 1280, "height": 800}

    def ingreso(p):
        p.goto(f"{URL}/ingresar")
        p.wait_for_timeout(3500)
        p.get_by_role("button", name="María Quispe").first.click()
        p.wait_for_url(f"{URL}/", timeout=20000)
        p.wait_for_timeout(5000)

    webm, _ = grabar(navegador, "ingreso", ingreso, tamano)
    a_gif(webm, SALIDA / "gif-ingreso-tablero.gif", inicio=1.0, duracion=9)
    medios.append({"archivo": "/medios/gif-ingreso-tablero.gif", "titulo": "Ingreso y tablero ejecutivo", "tipo": "gif",
                   "descripcion": "Acceso rápido por rol y animación del tablero P01"})

    def evaluacion(p):
        ingresar(p, "María Quispe")
        lista = api(p, "/api/riesgos")
        candidato = next((r for r in lista if r["estado"] in ("IDENTIFICADO", "EVALUADO", "MITIGADO")), lista[0])
        p.goto(f"{URL}/riesgos/{candidato['id_riesgo']}")
        p.wait_for_timeout(2000)
        p.get_by_role("button", name="Evaluar").first.click()
        p.wait_for_timeout(1500)
        for etiqueta in ["2", "3", "4", "5"]:
            p.locator(".modal button.boton", has_text=etiqueta).nth(0).click()
            p.wait_for_timeout(700)
        for etiqueta in ["2", "3", "4"]:
            p.locator(".modal button.boton", has_text=etiqueta).nth(1).click()
            p.wait_for_timeout(700)
        p.wait_for_timeout(2000)
        return {}

    webm, _ = grabar(navegador, "evaluacion", evaluacion, tamano)
    a_gif(webm, SALIDA / "gif-evaluacion-p03.gif", inicio=6.0, duracion=9)
    medios.append({"archivo": "/medios/gif-evaluacion-p03.gif", "titulo": "Evaluación del riesgo (P03)", "tipo": "gif",
                   "descripcion": "Nivel, conversión a USD y validación requerida calculados en vivo"})

    def aprobacion(p):
        ingresar(p, "Carlos Rojas")
        p.goto(f"{URL}/aprobaciones")
        p.wait_for_timeout(2500)
        if p.get_by_role("button", name="Revisar y decidir").count():
            p.get_by_role("button", name="Revisar y decidir").first.click()
            p.wait_for_timeout(5000)

    webm, _ = grabar(navegador, "aprobacion", aprobacion, tamano)
    a_gif(webm, SALIDA / "gif-aprobacion-cu04.gif", inicio=5.0, duracion=8)
    medios.append({"archivo": "/medios/gif-aprobacion-cu04.gif", "titulo": "Aprobación del tratamiento (CU04)", "tipo": "gif",
                   "descripcion": "Lista de verificación del aprobador con las reglas de negocio"})

    def tiempo_real(p):
        ingresar(p, "María Quispe")
        p.goto(f"{URL}/kri")
        p.wait_for_timeout(3000)
        kris = api(p, "/api/kri?fuente=SIEM") or api(p, "/api/kri?fuente=ERP")
        api(p, f"/api/integraciones/kri/{kris[0]['id_kri']}/evento", "POST", {"zona": "ROJO"})
        p.wait_for_timeout(5000)
        p.goto(f"{URL}/alertas")
        p.wait_for_timeout(5000)

    webm, _ = grabar(navegador, "tiempo_real", tiempo_real, tamano)
    a_gif(webm, SALIDA / "gif-alerta-tiempo-real.gif", inicio=5.0, duracion=12)
    medios.append({"archivo": "/medios/gif-alerta-tiempo-real.gif", "titulo": "Alerta en tiempo real (CU06–CU07)", "tipo": "gif",
                   "descripcion": "El SIEM publica un valor fuera de umbral, el trigger genera la alerta y llega al navegador"})

    def bitacora(p):
        ingresar(p, "Patricia Vargas")
        p.goto(f"{URL}/bitacora")
        p.wait_for_timeout(2500)
        p.get_by_role("button", name="Probar que es inalterable").click()
        p.wait_for_timeout(5500)

    webm, _ = grabar(navegador, "bitacora", bitacora, tamano)
    a_gif(webm, SALIDA / "gif-bitacora-inalterable.gif", inicio=5.0, duracion=8)
    medios.append({"archivo": "/medios/gif-bitacora-inalterable.gif", "titulo": "Bitácora inalterable (RN13)", "tipo": "gif",
                   "descripcion": "Ni el rol de la aplicación ni el dueño del esquema pueden modificarla"})

    def movil(p):
        ingresar(p, "Jorge Condori")
        p.goto(f"{URL}/movil")
        p.wait_for_timeout(7000)

    webm, _ = grabar(navegador, "movil", movil, {"width": 1280, "height": 900})
    a_gif(webm, SALIDA / "gif-vista-movil.gif", inicio=5.0, duracion=6, ancho=760)
    medios.append({"archivo": "/medios/gif-vista-movil.gif", "titulo": "Aplicación móvil (PWA)", "tipo": "gif",
                   "descripcion": "La misma aplicación adaptada al teléfono"})


def main():
    partes = sys.argv[1:] or ["capturas", "video", "gifs"]
    SALIDA.mkdir(parents=True, exist_ok=True)
    TEMP.mkdir(exist_ok=True)
    indice = SALIDA / "medios.json"
    if indice.exists():
        medios.extend(json.loads(indice.read_text(encoding="utf-8")))
    tipo = {"capturas": "imagen", "video": "video", "gifs": "gif"}
    with sync_playwright() as pw:
        navegador = pw.chromium.launch(channel="msedge", headless=True, args=ARGS)
        for parte in partes:
            medios[:] = [m for m in medios if m["tipo"] != tipo[parte]]
            {"capturas": capturas, "video": video, "gifs": gifs}[parte](navegador)
        navegador.close()
    orden = {"video": 0, "gif": 1, "imagen": 2}
    medios.sort(key=lambda m: (orden[m["tipo"]], m["archivo"]))
    indice.write_text(json.dumps(medios, ensure_ascii=False, indent=1), encoding="utf-8")
    shutil.rmtree(TEMP, ignore_errors=True)
    total = sum(f.stat().st_size for f in SALIDA.rglob("*") if f.is_file())
    print(f"Listo: {len(medios)} medios en {SALIDA} ({total / 1048576:.1f} MB)")


if __name__ == "__main__":
    main()

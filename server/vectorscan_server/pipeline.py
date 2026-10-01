"""Pipeline de VectorScan: imagen de un plano 2D -> elementos detectados -> modelo 3D (GLB).

Usa visión por computador clásica (OpenCV) y reglas geométricas, sin redes neuronales:

1. Preparación: escala de grises, enderezado de la hoja si es una foto y normalización
   de la iluminación.
2. Paredes: los trazos gruesos del plano. Una apertura morfológica elimina lo fino
   (textos, cotas, muebles, arcos de puertas).
3. Huecos: se cierran las paredes a lo largo de su eje y lo que se rellena son los huecos.
   Si el hueco está atravesado por una línea fina es una ventana; si no, una puerta.
4. Habitaciones: regiones libres cerradas por paredes y huecos.
5. Modelo 3D: extrusión de paredes, dinteles, alféizares, cristales y suelos por habitación,
   exportado como glTF binario (GLB) con el eje Y hacia arriba.
"""

from __future__ import annotations

from dataclasses import dataclass, field

import cv2
import numpy as np
import shapely.geometry as sg
import trimesh
from shapely.ops import unary_union

LADO_MAXIMO_PX = 1600
LADO_MINIMO_PX = 300


class ErrorPlano(ValueError):
    """La imagen no contiene un plano reconocible."""


class ImagenNoValida(ErrorPlano):
    """El archivo no se puede leer como imagen."""


@dataclass
class Opciones:
    altura_m: float = 2.6
    """Altura de las paredes."""
    ancho_m: float | None = None
    """Ancho real del edificio (de muro exterior a muro exterior). Fija la escala."""
    grosor_muro_m: float = 0.25
    """Grosor supuesto del muro exterior; da la escala cuando no se indica `ancho_m`."""
    altura_puerta_m: float = 2.1
    altura_alfeizar_m: float = 0.9


@dataclass
class Hueco:
    tipo: str  # "puerta" o "ventana"
    ancho_m: float
    poligono: sg.Polygon = field(repr=False)


@dataclass
class Habitacion:
    area_m2: float
    poligono: sg.Polygon = field(repr=False)


@dataclass
class Analisis:
    """Resultado intermedio, en píxeles de la imagen normalizada."""

    imagen: np.ndarray = field(repr=False)
    paredes: np.ndarray = field(repr=False)
    grosor_px: float
    escala_m_px: float
    poligonos_pared: list[sg.Polygon] = field(repr=False)
    huecos: list[Hueco]
    habitaciones: list[Habitacion]
    hoja_enderezada: bool
    planta: sg.Polygon | sg.MultiPolygon = field(repr=False)
    """Huella del edificio (todo lo encerrado por los muros exteriores)."""

    @property
    def puertas(self) -> int:
        return sum(h.tipo == "puerta" for h in self.huecos)

    @property
    def ventanas(self) -> int:
        return sum(h.tipo == "ventana" for h in self.huecos)

    def dimensiones_m(self) -> tuple[float, float]:
        minx, miny, maxx, maxy = unary_union(self.poligonos_pared).bounds
        return (maxx - minx) * self.escala_m_px, (maxy - miny) * self.escala_m_px

    def resumen(self) -> dict:
        ancho, fondo = self.dimensiones_m()
        return {
            "paredes": len(self.poligonos_pared),
            "puertas": self.puertas,
            "ventanas": self.ventanas,
            "habitaciones": [{"id": i + 1, "area_m2": round(h.area_m2, 2)} for i, h in enumerate(self.habitaciones)],
            "ancho_m": round(ancho, 2),
            "fondo_m": round(fondo, 2),
            "escala_m_px": round(self.escala_m_px, 5),
            "grosor_muro_px": round(self.grosor_px, 1),
            "hoja_enderezada": self.hoja_enderezada,
        }


# ---------------------------------------------------------------- 1. Preparación


def leer_imagen(datos: bytes) -> np.ndarray:
    buffer = np.frombuffer(datos, np.uint8)
    imagen = cv2.imdecode(buffer, cv2.IMREAD_GRAYSCALE) if buffer.size else None
    if imagen is None:
        raise ImagenNoValida("El archivo no es una imagen válida (usa JPG, PNG o WebP).")
    if min(imagen.shape) < 64:
        raise ImagenNoValida("La imagen es demasiado pequeña.")
    return imagen


def _redimensionar(gris: np.ndarray) -> np.ndarray:
    alto, ancho = gris.shape
    lado = max(alto, ancho)
    if lado > LADO_MAXIMO_PX:
        f = LADO_MAXIMO_PX / lado
        return cv2.resize(gris, (round(ancho * f), round(alto * f)), interpolation=cv2.INTER_AREA)
    if lado < LADO_MINIMO_PX:
        f = LADO_MINIMO_PX / lado
        return cv2.resize(gris, (round(ancho * f), round(alto * f)), interpolation=cv2.INTER_CUBIC)
    return gris


def _ordenar_esquinas(puntos: np.ndarray) -> np.ndarray:
    suma = puntos.sum(axis=1)
    resta = np.diff(puntos, axis=1).ravel()
    return np.array(
        [puntos[np.argmin(suma)], puntos[np.argmin(resta)], puntos[np.argmax(suma)], puntos[np.argmax(resta)]],
        np.float32,
    )


def enderezar_hoja(gris: np.ndarray) -> tuple[np.ndarray, bool]:
    """Si la foto muestra una hoja clara sobre un fondo más oscuro, corrige la perspectiva."""
    alto, ancho = gris.shape
    suave = cv2.GaussianBlur(gris, (7, 7), 0)
    _, claro = cv2.threshold(suave, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    claro = cv2.morphologyEx(claro, cv2.MORPH_CLOSE, cv2.getStructuringElement(cv2.MORPH_RECT, (25, 25)))
    contornos, _ = cv2.findContours(claro, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if not contornos:
        return gris, False
    hoja = max(contornos, key=cv2.contourArea)
    fraccion = cv2.contourArea(hoja) / (alto * ancho)
    if not 0.2 < fraccion < 0.95:
        return gris, False  # sin fondo visible: ya es un escaneo o una captura
    aprox = cv2.approxPolyDP(cv2.convexHull(hoja), 0.02 * cv2.arcLength(hoja, True), True)
    if len(aprox) != 4:
        return gris, False
    origen = _ordenar_esquinas(aprox.reshape(4, 2).astype(np.float32))
    sup, der, inf, izq = (
        np.linalg.norm(origen[1] - origen[0]),
        np.linalg.norm(origen[2] - origen[1]),
        np.linalg.norm(origen[2] - origen[3]),
        np.linalg.norm(origen[3] - origen[0]),
    )
    w, h = round(max(sup, inf)), round(max(der, izq))
    destino = np.array([[0, 0], [w - 1, 0], [w - 1, h - 1], [0, h - 1]], np.float32)
    matriz = cv2.getPerspectiveTransform(origen, destino)
    return cv2.warpPerspective(gris, matriz, (w, h), borderValue=255), True


def normalizar_iluminacion(gris: np.ndarray) -> np.ndarray:
    """Divide entre el fondo estimado para quitar sombras y degradados de la foto."""
    lado = max(31, (min(gris.shape) // 12) | 1)
    fondo = cv2.morphologyEx(gris, cv2.MORPH_CLOSE, cv2.getStructuringElement(cv2.MORPH_RECT, (lado, lado)))
    fondo = cv2.GaussianBlur(fondo, (0, 0), lado / 4)
    return cv2.divide(gris, np.maximum(fondo, 1), scale=255)


# ---------------------------------------------------------------- 2. Paredes


def _grosor_trazos(mascara: np.ndarray, percentil: float) -> float:
    """Grosor (px) de los trazos de una máscara, medido en su eje central."""
    dist = cv2.distanceTransform(mascara, cv2.DIST_L2, 5)
    crestas = dist[(dist >= 1) & (dist >= cv2.dilate(dist, np.ones((3, 3), np.uint8)))]
    if crestas.size == 0:
        return 0.0
    return 2 * float(np.percentile(crestas, percentil))


def detectar_paredes(normalizada: np.ndarray) -> tuple[np.ndarray, np.ndarray, float]:
    """Devuelve (máscara de paredes, máscara de trazos finos, grosor del muro exterior en px)."""
    _, tinta = cv2.threshold(normalizada, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU)
    tinta = cv2.morphologyEx(tinta, cv2.MORPH_OPEN, np.ones((2, 2), np.uint8))  # ruido de la foto

    grosor = _grosor_trazos(tinta, 95)
    if grosor < 4:
        raise ErrorPlano("No se han encontrado paredes: el plano no tiene trazos gruesos.")

    k = max(3, round(grosor * 0.4))
    paredes = cv2.morphologyEx(tinta, cv2.MORPH_OPEN, cv2.getStructuringElement(cv2.MORPH_RECT, (k, k)))

    # Descarta manchas sueltas (letras en negrita, flechas, sellos).
    area_minima = (grosor * 4) ** 2
    num, etiquetas, stats, _ = cv2.connectedComponentsWithStats(paredes, connectivity=8)
    grandes = np.zeros(num, bool)
    grandes[1:] = stats[1:, cv2.CC_STAT_AREA] >= area_minima / 4
    largas = np.maximum(stats[:, cv2.CC_STAT_WIDTH], stats[:, cv2.CC_STAT_HEIGHT]) >= grosor * 4
    validas = grandes & largas
    paredes = np.where(validas[etiquetas], 255, 0).astype(np.uint8)
    # Un plano tiene al menos un tramo de pared que cruza buena parte de la imagen;
    # un texto o un dibujo suelto, no.
    lado_mayor = np.maximum(stats[validas, cv2.CC_STAT_WIDTH], stats[validas, cv2.CC_STAT_HEIGHT])
    if lado_mayor.size == 0 or lado_mayor.max() < 0.25 * min(normalizada.shape):
        raise ErrorPlano("No se han encontrado paredes en la imagen.")

    finos = cv2.bitwise_and(tinta, cv2.bitwise_not(cv2.dilate(paredes, np.ones((3, 3), np.uint8))))
    return paredes, finos, _grosor_trazos(paredes, 90)


# ---------------------------------------------------------------- 3. Huecos


def _cerrar(mascara: np.ndarray, forma: tuple[int, int]) -> np.ndarray:
    """Cierre morfológico tratando lo que hay fuera de la imagen como vacío. Con el borde por
    defecto de OpenCV, el cierre rellenaría el espacio entre las paredes y el borde."""
    m = max(forma)
    ampliada = cv2.copyMakeBorder(mascara, m, m, m, m, cv2.BORDER_CONSTANT, value=0)
    cerrada = cv2.morphologyEx(ampliada, cv2.MORPH_CLOSE, cv2.getStructuringElement(cv2.MORPH_RECT, forma))
    return cerrada[m:-m, m:-m]


def detectar_huecos(
    paredes: np.ndarray, finos: np.ndarray, grosor: float
) -> tuple[list[tuple[np.ndarray, bool]], np.ndarray]:
    """Devuelve (lista de (máscara del hueco, es_ventana), paredes con los huecos cerrados)."""
    largo = max(5, round(grosor * 20))  # ~5 m: cierra huecos de hasta ese ancho
    tramo = max(3, round(grosor * 3))
    cerradas = paredes.copy()
    for forma in ((largo, 1), (1, largo)):
        # Solo se unen tramos de pared alineados en la misma dirección que el cierre, para no
        # rellenar el espacio entre dos paredes paralelas (un pasillo, una habitación estrecha).
        alineadas = cv2.morphologyEx(
            paredes,
            cv2.MORPH_OPEN,
            cv2.getStructuringElement(cv2.MORPH_RECT, (tramo, 1) if forma[1] == 1 else (1, tramo)),
        )
        cerradas |= _cerrar(alineadas, forma)
    rellenos = cv2.bitwise_and(cerradas, cv2.bitwise_not(paredes))
    rellenos = cv2.morphologyEx(rellenos, cv2.MORPH_OPEN, np.ones((3, 3), np.uint8))

    huecos = []
    descartado = np.zeros_like(paredes)
    num, etiquetas, stats, _ = cv2.connectedComponentsWithStats(rellenos, connectivity=4)
    for i in range(1, num):
        x, y, w, h, area = stats[i]
        corto, largo_hueco = min(w, h), max(w, h)
        # Un hueco tiene el grosor de un muro y al menos ~0,6 m de ancho.
        if corto > grosor * 1.6 or largo_hueco < grosor * 2.2 or area < 0.5 * corto * largo_hueco:
            descartado[etiquetas == i] = 255
            continue
        mascara = np.where(etiquetas == i, 255, 0).astype(np.uint8)
        horizontal = w >= h
        zona = cv2.dilate(mascara, np.ones((3, 3), np.uint8))
        lineas = cv2.bitwise_and(finos, zona)
        # Proporción del hueco recorrida por una línea fina a lo largo de su eje.
        cobertura = (np.any(lineas[y : y + h, x : x + w] > 0, axis=0 if horizontal else 1)).mean()
        huecos.append((mascara, cobertura >= 0.6))
    cerradas = cv2.bitwise_and(cerradas, cv2.bitwise_not(descartado))
    return huecos, cerradas


# ---------------------------------------------------------------- 4. Habitaciones


def detectar_habitaciones(cerradas: np.ndarray, grosor: float, escala: float) -> list[np.ndarray]:
    libre = cv2.bitwise_not(cerradas)
    alto, ancho = libre.shape
    area_minima = (1.0 / escala) ** 2  # 1 m²
    num, etiquetas, stats, _ = cv2.connectedComponentsWithStats(libre, connectivity=4)
    habitaciones = []
    for i in range(1, num):
        x, y, w, h, area = stats[i]
        toca_borde = x == 0 or y == 0 or x + w == ancho or y + h == alto
        if toca_borde or area < area_minima or min(w, h) < grosor * 3:
            continue
        habitaciones.append(np.where(etiquetas == i, 255, 0).astype(np.uint8))
    return habitaciones


def detectar_planta(cerradas: np.ndarray, tolerancia: float, poligonos_pared: list[sg.Polygon]):
    """Huella del edificio: lo que no se alcanza desde el borde de la imagen sin cruzar paredes.
    Si el contorno exterior no queda cerrado, se usa el rectángulo que envuelve las paredes."""
    alto, ancho = cerradas.shape
    exterior = cv2.copyMakeBorder(cv2.bitwise_not(cerradas), 1, 1, 1, 1, cv2.BORDER_CONSTANT, value=255)
    cv2.floodFill(exterior, None, (0, 0), 0)
    dentro = cv2.bitwise_or(exterior[1:-1, 1:-1], cerradas)
    caja = sg.box(*unary_union(poligonos_pared).bounds)
    planta = unary_union(mascara_a_poligonos(dentro, tolerancia))
    if planta.is_empty or planta.area < 0.3 * caja.area:
        return caja
    return planta


# ---------------------------------------------------------------- Vectorización


def mascara_a_poligonos(mascara: np.ndarray, tolerancia: float) -> list[sg.Polygon]:
    contornos, jerarquia = cv2.findContours(mascara, cv2.RETR_CCOMP, cv2.CHAIN_APPROX_SIMPLE)
    if jerarquia is None:
        return []
    jerarquia = jerarquia[0]

    def anillo(c):
        return [(float(p[0][0]), float(p[0][1])) for p in cv2.approxPolyDP(c, tolerancia, True)]

    poligonos = []
    for i, c in enumerate(contornos):
        if jerarquia[i][3] != -1:
            continue  # es un agujero: se añade con su contorno padre
        exterior = anillo(c)
        if len(exterior) < 3:
            continue
        agujeros = []
        hijo = jerarquia[i][2]
        while hijo != -1:
            a = anillo(contornos[hijo])
            if len(a) >= 3:
                agujeros.append(a)
            hijo = jerarquia[hijo][0]
        poligono = sg.Polygon(exterior, agujeros).buffer(0)
        poligonos.extend(g for g in getattr(poligono, "geoms", [poligono]) if g.area > 1)
    return poligonos


# ---------------------------------------------------------------- Análisis completo


def analizar(datos: bytes, opciones: Opciones | None = None) -> Analisis:
    opciones = opciones or Opciones()
    gris = _redimensionar(leer_imagen(datos))
    gris, enderezada = enderezar_hoja(gris)
    gris = _redimensionar(gris)
    normalizada = normalizar_iluminacion(gris)

    paredes, finos, grosor = detectar_paredes(normalizada)
    tolerancia = max(1.0, grosor * 0.12)
    poligonos_pared = mascara_a_poligonos(paredes, tolerancia)
    if not poligonos_pared:
        raise ErrorPlano("No se han encontrado paredes en la imagen.")

    if opciones.ancho_m:
        minx, _, maxx, _ = unary_union(poligonos_pared).bounds
        escala = opciones.ancho_m / max(maxx - minx, 1)
    else:
        escala = opciones.grosor_muro_m / grosor

    mascaras_hueco, cerradas = detectar_huecos(paredes, finos, grosor)
    huecos = []
    for mascara, es_ventana in mascaras_hueco:
        for p in mascara_a_poligonos(mascara, tolerancia):
            rect = p.minimum_rotated_rectangle
            lados = sorted(sg.LineString(rect.exterior.coords[i : i + 2]).length for i in range(2))
            huecos.append(Hueco("ventana" if es_ventana else "puerta", lados[1] * escala, p))

    habitaciones = []
    for mascara in detectar_habitaciones(cerradas, grosor, escala):
        for p in mascara_a_poligonos(mascara, tolerancia):
            habitaciones.append(Habitacion(p.area * escala**2, p))
    habitaciones.sort(key=lambda h: -h.area_m2)

    planta = detectar_planta(cerradas, tolerancia, poligonos_pared)
    return Analisis(gris, paredes, grosor, escala, poligonos_pared, huecos, habitaciones, enderezada, planta)


# ---------------------------------------------------------------- 5. Modelo 3D

COLORES = {
    "pared": (0.82, 0.80, 0.76, 1.0),
    "cristal": (0.45, 0.68, 0.88, 0.4),
    "suelo": (0.60, 0.45, 0.32, 1.0),
}
COLORES_HABITACION = [
    (0.66, 0.48, 0.32, 1.0),
    (0.45, 0.62, 0.48, 1.0),
    (0.47, 0.55, 0.72, 1.0),
    (0.74, 0.58, 0.44, 1.0),
    (0.62, 0.52, 0.70, 1.0),
    (0.72, 0.68, 0.42, 1.0),
]


def _material(color: tuple[float, ...], nombre: str):
    return trimesh.visual.material.PBRMaterial(
        name=nombre,
        # glTF espera el color en espacio lineal; los de arriba están en sRGB.
        baseColorFactor=[round((c**2.2 if i < 3 else c) * 255) for i, c in enumerate(color)],
        metallicFactor=0.0,
        roughnessFactor=0.9,
        alphaMode="BLEND" if color[3] < 1 else "OPAQUE",
        doubleSided=color[3] < 1,
    )


def _extruir(poligonos, escala: float, base: float, altura: float) -> trimesh.Trimesh | None:
    mallas = []
    for p in poligonos:
        # Plano de la imagen (x derecha, y abajo) -> x, -y para que la extrusión salga hacia +Z.
        p = sg.Polygon(
            [(x * escala, -y * escala) for x, y in p.exterior.coords],
            [[(x * escala, -y * escala) for x, y in i.coords] for i in p.interiors],
        ).buffer(0)
        for g in getattr(p, "geoms", [p]):
            if g.area <= 0:
                continue
            malla = trimesh.creation.extrude_polygon(g, altura)
            malla.apply_translation([0, 0, base])
            mallas.append(malla)
    return trimesh.util.concatenate(mallas) if mallas else None


def generar_glb(analisis: Analisis, opciones: Opciones | None = None) -> bytes:
    opciones = opciones or Opciones()
    e = analisis.escala_m_px
    alto, puerta, alfeizar = opciones.altura_m, opciones.altura_puerta_m, opciones.altura_alfeizar_m
    puertas = [h.poligono for h in analisis.huecos if h.tipo == "puerta"]
    ventanas = [h.poligono for h in analisis.huecos if h.tipo == "ventana"]

    piezas = {
        "Paredes": (_extruir(analisis.poligonos_pared, e, 0, alto), COLORES["pared"]),
        "Dinteles": (_extruir(puertas + ventanas, e, puerta, alto - puerta), COLORES["pared"]),
        "Alfeizares": (_extruir(ventanas, e, 0, alfeizar), COLORES["pared"]),
        "Umbrales": (_extruir(puertas, e, -0.02, 0.02), COLORES["suelo"]),
        "Cristales": (_extruir(ventanas, e, alfeizar, puerta - alfeizar), COLORES["cristal"]),
    }
    planta = list(getattr(analisis.planta, "geoms", [analisis.planta]))
    piezas["Suelo"] = (_extruir(planta, e, -0.03, 0.025), COLORES["suelo"])
    for i, h in enumerate(analisis.habitaciones):
        color = COLORES_HABITACION[i % len(COLORES_HABITACION)]
        piezas[f"Suelo habitacion {i + 1}"] = (_extruir([h.poligono], e, -0.02, 0.02), color)

    escena = trimesh.Scene()
    for nombre, (malla, color) in piezas.items():
        if malla is None:
            continue
        malla.visual = trimesh.visual.TextureVisuals(material=_material(color, nombre))
        escena.add_geometry(malla, geom_name=nombre, node_name=nombre)

    # glTF usa Y hacia arriba: girar -90° en X (la altura pasa a Y) y centrar en el origen.
    giro = trimesh.transformations.rotation_matrix(-np.pi / 2, [1, 0, 0])
    escena.apply_transform(giro)
    (minx, miny, minz), (maxx, _, maxz) = escena.bounds
    escena.apply_translation([-(minx + maxx) / 2, -miny, -(minz + maxz) / 2])
    return escena.export(file_type="glb")


def procesar(datos: bytes, opciones: Opciones | None = None) -> tuple[bytes, Analisis]:
    analisis = analizar(datos, opciones)
    return generar_glb(analisis, opciones), analisis


# ---------------------------------------------------------------- Vista previa


def vista_previa(analisis: Analisis) -> bytes:
    """PNG con lo detectado pintado sobre el plano: paredes, puertas, ventanas y habitaciones."""
    base = cv2.cvtColor(analisis.imagen, cv2.COLOR_GRAY2BGR)
    capa = base.copy()
    for i, h in enumerate(analisis.habitaciones):
        r, g, b, _ = COLORES_HABITACION[i % len(COLORES_HABITACION)]
        pts = np.array(h.poligono.exterior.coords, np.int32)
        cv2.fillPoly(capa, [pts], (round(b * 255), round(g * 255), round(r * 255)))
    capa[analisis.paredes > 0] = (60, 60, 200)
    for h in analisis.huecos:
        pts = np.array(h.poligono.exterior.coords, np.int32)
        cv2.fillPoly(capa, [pts], (60, 170, 60) if h.tipo == "puerta" else (220, 140, 30))
    salida = cv2.addWeighted(capa, 0.65, base, 0.35, 0)
    for h in analisis.habitaciones:
        c = h.poligono.representative_point()
        texto = f"{h.area_m2:.1f} m2"
        (tw, th), _ = cv2.getTextSize(texto, cv2.FONT_HERSHEY_SIMPLEX, 0.6, 2)
        cv2.putText(
            salida,
            texto,
            (round(c.x - tw / 2), round(c.y + th / 2)),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.6,
            (30, 30, 30),
            2,
            cv2.LINE_AA,
        )
    ok, png = cv2.imencode(".png", salida)
    return png.tobytes()

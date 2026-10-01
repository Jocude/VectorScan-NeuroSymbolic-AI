"""Planos sintéticos para los tests: una vivienda de 5 estancias con puertas, ventanas,
textos, cotas y muebles, y una versión «fotografiada» con perspectiva, sombra y ruido."""

import cv2
import numpy as np

ANCHO_REAL_M = 12.0  # cota del plano: de muro exterior a muro exterior
PUERTAS = 5
VENTANAS = 3
HABITACIONES = 5


def plano_vivienda() -> np.ndarray:
    W, H = 900, 640
    plano = np.full((H, W), 255, np.uint8)
    T, t = 14, 8  # muro exterior y tabique
    cv2.rectangle(plano, (40, 40), (860, 600), 0, T)
    cv2.line(plano, (420, 40), (420, 600), 0, t)
    cv2.line(plano, (40, 330), (420, 330), 0, t)
    cv2.line(plano, (420, 300), (860, 300), 0, t)
    cv2.line(plano, (650, 300), (650, 600), 0, t)

    # Puertas: hueco en la pared, hoja y arco de apertura en trazo fino.
    for x1, y1, x2, y2 in [
        (412, 140, 428, 220),
        (200, 322, 270, 338),
        (520, 292, 590, 308),
        (642, 420, 658, 490),
        (380, 593, 460, 607),
    ]:
        cv2.rectangle(plano, (x1, y1), (x2, y2), 255, -1)
        if x2 - x1 < y2 - y1:  # puerta en pared vertical
            r = y2 - y1
            cv2.line(plano, (x2, y1), (x2 + r, y1), 0, 2)
            cv2.ellipse(plano, (x2, y1), (r, r), 0, 0, 90, 0, 1)
        else:
            r = x2 - x1
            cv2.line(plano, (x1, y1), (x1, y1 - r), 0, 2)
            cv2.ellipse(plano, (x1, y1), (r, r), 0, 270, 360, 0, 1)

    # Ventanas: hueco en el muro con una línea fina en el eje.
    for x1, y1, x2, y2 in [(120, 40, 260, 40), (560, 40, 720, 40), (860, 380, 860, 500)]:
        cv2.line(plano, (x1, y1), (x2, y2), 255, T + 2)
        cv2.line(plano, (x1, y1), (x2, y2), 90, 2)

    fuente = cv2.FONT_HERSHEY_SIMPLEX
    for texto, pos in [
        ("SALON", (170, 190)),
        ("DORMITORIO", (130, 470)),
        ("COCINA", (580, 170)),
        ("BANO", (500, 450)),
        ("DORM. 2", (700, 450)),
    ]:
        cv2.putText(plano, texto, pos, fuente, 0.8, 40, 2, cv2.LINE_AA)
    cv2.line(plano, (40, 625), (860, 625), 60, 1)
    cv2.putText(plano, "12,00 m", (410, 620), fuente, 0.5, 60, 1, cv2.LINE_AA)
    cv2.rectangle(plano, (90, 380), (190, 520), 70, 2)  # cama
    return plano


def foto_de(plano: np.ndarray, semilla: int = 0) -> np.ndarray:
    """Simula una foto con el móvil: hoja inclinada sobre una mesa, sombra y ruido."""
    rng = np.random.default_rng(semilla)
    h, w = plano.shape
    margen = 80
    hoja = cv2.copyMakeBorder(plano, margen, margen, margen, margen, cv2.BORDER_CONSTANT, value=255)
    hh, hw = hoja.shape
    origen = np.float32([[0, 0], [hw, 0], [hw, hh], [0, hh]])
    destino = np.float32([[160, 120], [hw + 90, 170], [hw + 140, hh + 230], [110, hh + 160]])
    lienzo = (hw + 300, hh + 360)
    matriz = cv2.getPerspectiveTransform(origen, destino)
    foto = cv2.warpPerspective(hoja, matriz, lienzo, borderValue=70)  # mesa oscura

    yy, xx = np.mgrid[0 : lienzo[1], 0 : lienzo[0]]
    sombra = 0.75 + 0.25 * (xx / lienzo[0])  # más oscuro a la izquierda
    foto = foto.astype(np.float32) * sombra
    foto += rng.normal(0, 6, foto.shape)
    foto = cv2.GaussianBlur(np.clip(foto, 0, 255).astype(np.uint8), (3, 3), 0)
    return foto


def a_png(imagen: np.ndarray) -> bytes:
    return cv2.imencode(".png", imagen)[1].tobytes()


def a_jpg(imagen: np.ndarray) -> bytes:
    return cv2.imencode(".jpg", imagen, [cv2.IMWRITE_JPEG_QUALITY, 85])[1].tobytes()


def plano_en_l() -> np.ndarray:
    """Vivienda en L a más resolución, con muros de 20 px y tabiques de 12 px: 3 estancias,
    3 puertas (una de ellas en el muro exterior) y 2 ventanas."""
    plano = np.full((1500, 2000), 255, np.uint8)
    contorno = np.array([[100, 100], [1900, 100], [1900, 800], [1000, 800], [1000, 1400], [100, 1400]])
    cv2.polylines(plano, [contorno], True, 0, 20)
    cv2.line(plano, (1000, 100), (1000, 800), 0, 12)  # separa el ala derecha
    cv2.line(plano, (100, 800), (1000, 800), 0, 12)  # separa el ala inferior
    for x1, y1, x2, y2 in [(994, 350, 1006, 480), (500, 794, 630, 806), (1400, 790, 1530, 810)]:
        cv2.rectangle(plano, (x1, y1), (x2, y2), 255, -1)
    for x1, y1, x2, y2 in [(300, 100, 650, 100), (1300, 100, 1700, 100)]:
        cv2.line(plano, (x1, y1), (x2, y2), 255, 22)
        cv2.line(plano, (x1, y1), (x2, y2), 100, 3)
    cv2.putText(plano, "ESTAR", (400, 500), cv2.FONT_HERSHEY_SIMPLEX, 2, 30, 4)
    return plano

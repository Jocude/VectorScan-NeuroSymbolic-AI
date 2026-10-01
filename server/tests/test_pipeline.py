import io

import numpy as np
import pytest
import trimesh

from planos import ANCHO_REAL_M, HABITACIONES, PUERTAS, VENTANAS, a_jpg, a_png, foto_de, plano_en_l, plano_vivienda
from vectorscan_server.pipeline import ErrorPlano, ImagenNoValida, Opciones, analizar, generar_glb, vista_previa


@pytest.fixture(scope="module")
def plano():
    return a_png(plano_vivienda())


@pytest.fixture(scope="module")
def analisis(plano):
    return analizar(plano, Opciones(ancho_m=ANCHO_REAL_M))


def test_detecta_puertas_ventanas_y_habitaciones(analisis):
    assert analisis.puertas == PUERTAS
    assert analisis.ventanas == VENTANAS
    assert len(analisis.habitaciones) == HABITACIONES
    assert not analisis.hoja_enderezada


def test_areas_coherentes_con_la_escala(analisis):
    # Salón: 369 x 279 px de hueco interior; 12 m son 834 px de muro a muro.
    salon = (369 * ANCHO_REAL_M / 834) * (279 * ANCHO_REAL_M / 834)
    assert analisis.habitaciones[0].area_m2 == pytest.approx(salon, rel=0.08)
    ancho, fondo = analisis.dimensiones_m()
    assert ancho == pytest.approx(ANCHO_REAL_M, rel=0.01)
    assert fondo == pytest.approx(ANCHO_REAL_M * 574 / 834, rel=0.03)


def test_escala_estimada_sin_ancho(plano):
    ancho, _ = analizar(plano).dimensiones_m()
    assert 9 < ancho < 16  # razonable para una vivienda aunque no se dé la cota


def test_ancho_de_puertas_y_ventanas(analisis):
    for hueco in analisis.huecos:
        if hueco.tipo == "puerta":
            assert 0.7 < hueco.ancho_m < 1.4
        else:
            assert 1.5 < hueco.ancho_m < 2.8


def test_foto_con_perspectiva_sombra_y_ruido():
    foto = analizar(a_jpg(foto_de(plano_vivienda())))
    assert foto.hoja_enderezada
    assert foto.puertas == PUERTAS
    assert foto.ventanas == VENTANAS
    assert len(foto.habitaciones) == HABITACIONES


def test_glb_valido_con_y_hacia_arriba(analisis):
    glb = generar_glb(analisis, Opciones(altura_m=3.0))
    assert glb[:4] == b"glTF"
    escena = trimesh.load(io.BytesIO(glb), file_type="glb")
    (minx, miny, minz), (maxx, maxy, maxz) = escena.bounds
    assert miny == pytest.approx(0, abs=1e-6)
    assert maxy == pytest.approx(3.0 + 0.03, abs=1e-3)  # altura + grosor del suelo
    assert maxx - minx == pytest.approx(ANCHO_REAL_M, rel=0.01)
    assert minx == pytest.approx(-maxx) and minz == pytest.approx(-maxz)  # centrado
    nombres = set(escena.geometry)
    assert {"Paredes", "Dinteles", "Alfeizares", "Cristales", "Umbrales", "Suelo"} <= nombres
    assert sum(n.startswith("Suelo habitacion") for n in nombres) == HABITACIONES


def test_vista_previa_es_png(analisis):
    assert vista_previa(analisis)[:8] == b"\x89PNG\r\n\x1a\n"


def test_imagen_no_valida():
    with pytest.raises(ImagenNoValida):
        analizar(b"esto no es una imagen")


def test_imagen_sin_paredes():
    vacia = np.full((400, 600), 255, np.uint8)
    with pytest.raises(ErrorPlano):
        analizar(a_png(vacia))


def test_texto_sin_paredes():
    import cv2

    hoja = np.full((400, 600), 255, np.uint8)
    for i in range(8):
        cv2.putText(hoja, "Lorem ipsum dolor sit amet", (20, 40 + i * 45), cv2.FONT_HERSHEY_SIMPLEX, 0.9, 0, 2)
    with pytest.raises(ErrorPlano):
        analizar(a_png(hoja))


def test_otro_plano_en_l_a_mas_resolucion():
    a = analizar(a_png(plano_en_l()), Opciones(ancho_m=18))
    assert a.puertas == 3
    assert a.ventanas == 2
    assert len(a.habitaciones) == 3
    # El ala inferior mide 9 x 6 m menos muros.
    assert 40 < a.habitaciones[-1].area_m2 < 54


def test_planta_cubre_el_edificio(analisis):
    ancho, fondo = analisis.dimensiones_m()
    area_planta = analisis.planta.area * analisis.escala_m_px**2
    assert area_planta == pytest.approx(ancho * fondo, rel=0.03)  # la vivienda es rectangular


def test_planta_en_l_no_es_un_rectangulo():
    a = analizar(a_png(plano_en_l()))
    caja = a.planta.envelope.area
    assert a.planta.area == pytest.approx(caja * 0.75, rel=0.05)  # le falta un cuarto

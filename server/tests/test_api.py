import pytest
from fastapi.testclient import TestClient

from planos import a_png, plano_vivienda
from vectorscan_server.main import TAMANO_MAXIMO, app

cliente = TestClient(app)


@pytest.fixture(scope="module")
def plano():
    return a_png(plano_vivienda())


def test_health():
    r = cliente.get("/health")
    assert r.status_code == 200
    assert r.json()["status"] == "ok"


def test_predict_devuelve_glb(plano):
    r = cliente.post("/predict", files={"file": ("plano.png", plano, "image/png")})
    assert r.status_code == 200
    assert r.headers["content-type"] == "model/gltf-binary"
    assert r.content[:4] == b"glTF"
    assert '"puertas": 5' in r.headers["x-vectorscan-resumen"]


def test_analyze_con_ancho(plano):
    r = cliente.post("/analyze", files={"file": ("plano.png", plano)}, data={"ancho_m": "12"})
    assert r.status_code == 200
    datos = r.json()
    assert datos["ancho_m"] == pytest.approx(12, rel=0.01)
    assert len(datos["habitaciones"]) == 5


def test_preview_png(plano):
    r = cliente.post("/preview", files={"file": ("plano.png", plano)})
    assert r.status_code == 200
    assert r.headers["content-type"] == "image/png"


def test_archivo_que_no_es_imagen():
    r = cliente.post("/predict", files={"file": ("notas.txt", b"hola", "text/plain")})
    assert r.status_code == 400


def test_imagen_sin_plano():
    import numpy as np

    r = cliente.post("/predict", files={"file": ("blanco.png", a_png(np.full((300, 300), 255, np.uint8)))})
    assert r.status_code == 422
    assert "paredes" in r.json()["detail"]


def test_imagen_demasiado_grande():
    r = cliente.post("/predict", files={"file": ("grande.png", b"0" * (TAMANO_MAXIMO + 1))})
    assert r.status_code == 413


def test_parametros_fuera_de_rango(plano):
    r = cliente.post("/predict", files={"file": ("plano.png", plano)}, data={"altura_m": "50"})
    assert r.status_code == 422


def test_pagina_web():
    r = cliente.get("/")
    assert r.status_code == 200
    assert "model-viewer" in r.text

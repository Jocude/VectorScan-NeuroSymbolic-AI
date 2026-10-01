"""API REST de VectorScan.

- POST /predict  imagen del plano -> modelo 3D (GLB). Es la ruta que usa la app Android.
- POST /analyze  imagen del plano -> JSON con paredes, puertas, ventanas y habitaciones.
- POST /preview  imagen del plano -> PNG con lo detectado pintado encima.
- GET  /health   estado del servidor.
- GET  /         página web para probar el pipeline desde el navegador.
"""

from __future__ import annotations

import json
import logging
import time
from pathlib import Path
from typing import Annotated

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.responses import FileResponse, JSONResponse, Response

from . import __version__
from .pipeline import ErrorPlano, ImagenNoValida, Opciones, analizar, generar_glb, vista_previa

TAMANO_MAXIMO = 15 * 1024 * 1024
ESTATICOS = Path(__file__).parent / "static"

log = logging.getLogger("vectorscan")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")

app = FastAPI(
    title="VectorScan API",
    version=__version__,
    description="Convierte la imagen de un plano 2D en un modelo 3D (GLB) con visión por computador clásica.",
)

Imagen = Annotated[UploadFile, File(description="Plano en JPG, PNG o WebP (máx. 15 MB).")]
AnchoM = Annotated[
    float | None,
    Form(gt=0, le=500, description="Ancho real del edificio en metros. Si se omite, se estima."),
]
AlturaM = Annotated[float, Form(ge=2, le=6, description="Altura de las paredes en metros.")]


def _leer(imagen: UploadFile) -> bytes:
    datos = imagen.file.read(TAMANO_MAXIMO + 1)
    if len(datos) > TAMANO_MAXIMO:
        raise HTTPException(413, "La imagen supera los 15 MB.")
    if not datos:
        raise HTTPException(400, "No se ha recibido ninguna imagen.")
    return datos


def _analizar(datos: bytes, opciones: Opciones):
    inicio = time.perf_counter()
    try:
        analisis = analizar(datos, opciones)
    except ErrorPlano as e:
        raise HTTPException(400 if isinstance(e, ImagenNoValida) else 422, str(e)) from e
    log.info("plano analizado en %.2f s: %s", time.perf_counter() - inicio, analisis.resumen())
    return analisis


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "version": __version__}


@app.post(
    "/predict",
    response_class=Response,
    responses={200: {"content": {"model/gltf-binary": {}}, "description": "Modelo 3D en GLB."}},
)
def predict(file: Imagen, ancho_m: AnchoM = None, altura_m: AlturaM = 2.6) -> Response:
    opciones = Opciones(altura_m=altura_m, ancho_m=ancho_m)
    analisis = _analizar(_leer(file), opciones)
    resumen = analisis.resumen()
    cabeceras = {
        "X-VectorScan-Resumen": json.dumps(
            {k: v for k, v in resumen.items() if k != "habitaciones"} | {"habitaciones": len(resumen["habitaciones"])}
        ),
        "Content-Disposition": 'attachment; filename="modelo.glb"',
    }
    return Response(generar_glb(analisis, opciones), media_type="model/gltf-binary", headers=cabeceras)


@app.post("/analyze")
def analyze(file: Imagen, ancho_m: AnchoM = None, altura_m: AlturaM = 2.6) -> JSONResponse:
    analisis = _analizar(_leer(file), Opciones(altura_m=altura_m, ancho_m=ancho_m))
    return JSONResponse(analisis.resumen())


@app.post("/preview", response_class=Response, responses={200: {"content": {"image/png": {}}}})
def preview(file: Imagen, ancho_m: AnchoM = None) -> Response:
    analisis = _analizar(_leer(file), Opciones(ancho_m=ancho_m))
    return Response(vista_previa(analisis), media_type="image/png")


@app.get("/", include_in_schema=False)
def inicio() -> FileResponse:
    return FileResponse(ESTATICOS / "index.html")

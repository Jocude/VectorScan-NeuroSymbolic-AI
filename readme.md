# VectorScan 🏗️📱

**Solución profesional para la gestión, interpretación y modelado 3D de planos mediante Inteligencia Artificial Híbrida.**

VectorScan es un sistema avanzado basado en una arquitectura neuro-simbólica capaz de generar modelos tridimensionales interactivos a partir de imágenes de planos arquitectónicos en 2D. Su objetivo principal es automatizar el proceso de digitalización y visualización 3D de espacios, facilitando su interpretación a través de tecnologías de vanguardia integradas en dispositivos móviles.

---

## 🚀 Características Principales

*   **Interpretación y Modelado Híbrido:** Generación automática de modelos 3D a partir de planos 2D, utilizando IA híbrida que combina visión artificial (Deep Learning) con razonamiento simbólico.
*   **Visualización con Sceneform:** Integración de un visor 3D de alto rendimiento para una exploración fluida, iluminación mejorada y manipulación intuitiva de la geometría generada.
*   **Experiencia de Realidad Aumentada (RA):** Soporte nativo para proyectar los modelos en el entorno físico real, permitiendo evaluar la escala y distribución espacial de manera inmersiva.
*   **Compatibilidad Profesional:** Generación y exportación de archivos en formato **GLB**, garantizando una integración fluida con herramientas de diseño técnico de la industria.
*   **Gestión de Usuarios y Proyectos:** Sistema de autenticación seguro y estructura de carpetas personalizada. Incluye soporte para interacción mediante gestos (Drag & Drop) para la organización del espacio de trabajo.

---

## ⚙️ Arquitectura del Pipeline

El sistema opera bajo un flujo de trabajo optimizado entre el cliente (aplicación móvil) y el servidor de procesamiento de Inteligencia Artificial:

1.  📸 **Captura:** El usuario selecciona o fotografía un plano arquitectónico 2D desde el dispositivo móvil.
2.  🧠 **Procesamiento:** La imagen se transmite al servidor, donde un pipeline de Deep Learning basado en **YOLO** detecta y segmenta los elementos estructurales.
3.  🏗️ **Modelado:** Mediante IA simbólica, se extraen los datos estructurados y se genera automáticamente la geometría tridimensional exacta del espacio.
4.  🔄 **Sincronización:** El modelo resultante se descarga en formato GLB y se renderiza en tiempo real en la app utilizando capacidades de Realidad Aumentada.

---

## 🛠️ Especificaciones Técnicas

| Categoría | Detalle Técnico |
| :--- | :--- |
| **Arquitectura de IA** | Neuro-simbólica (Integración de lógica y aprendizaje profundo) |
| **Detección de Objetos** | YOLO (You Only Look Once) |
| **Motor de Renderizado 3D** | Sceneform (Basado en el motor Filament de Google) |
| **Realidad Aumentada** | ARCore / Scene Viewer |
| **Persistencia de Datos** | Room Database / SQLite |
| **Comunicación de Red** | REST API (Retrofit 2 y OkHttp) |
| **Formato de Modelos** | GLB (glTF Binary) |

---

## 📱 Requisitos del Sistema

Para garantizar el correcto funcionamiento y el acceso a todas las capacidades de Realidad Aumentada y renderizado, el dispositivo debe cumplir con los siguientes criterios:

*   **Sistema Operativo:** Android 8.0 (Oreo) o superior (API nivel 26+).
*   **Compatibilidad AR:** Dispositivo certificado para *Google Play Services for AR* (ARCore).
*   **Conectividad:** Acceso a internet o red local para la comunicación con el servidor remoto de inferencia de IA.
*   **Hardware (Cámara):** Sensor con enfoque automático para la captura nítida de los documentos y planos.
*   **Almacenamiento:** Espacio disponible para la caché de modelos 3D generados y la persistencia en la base de datos local.
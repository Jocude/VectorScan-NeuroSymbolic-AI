package com.jocude.vectorscan;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;

public class VectorScanClientTest {

    @Rule
    public TemporaryFolder carpeta = new TemporaryFolder();

    private MockWebServer servidor;
    private VectorScanClient cliente;
    private File imagen;

    @Before
    public void setUp() throws Exception {
        servidor = new MockWebServer();
        servidor.start();
        OkHttpClient http = new OkHttpClient.Builder().readTimeout(2, TimeUnit.SECONDS).build();
        cliente = new VectorScanClient(servidor.url("/").toString(), http, null);
        imagen = carpeta.newFile("plano.jpg");
        Files.write(imagen.toPath(), new byte[]{1, 2, 3});
    }

    @After
    public void tearDown() throws Exception {
        servidor.shutdown();
    }

    @Test
    public void subeElPlanoYGuardaElGlb() throws Exception {
        byte[] glb = "glTF-modelo".getBytes(StandardCharsets.US_ASCII);
        servidor.enqueue(new MockResponse()
                .setBody(new Buffer().write(glb))
                .addHeader("Content-Type", "model/gltf-binary")
                .addHeader("X-VectorScan-Resumen", "{\"habitaciones\": 4, \"puertas\": 3, \"ventanas\": 2, "
                        + "\"ancho_m\": 10, \"fondo_m\": 7}"));

        File destino = new File(carpeta.getRoot(), "modelo.glb");
        ModelSummary resumen = cliente.generateModel(imagen, 12.0, destino);

        assertArrayEquals(glb, Files.readAllBytes(destino.toPath()));
        assertEquals(4, resumen.rooms);

        RecordedRequest peticion = servidor.takeRequest();
        assertEquals("POST", peticion.getMethod());
        assertEquals("/predict", peticion.getPath());
        String cuerpo = peticion.getBody().readUtf8();
        assertTrue(cuerpo.contains("name=\"file\"; filename=\"plano.jpg\""));
        assertTrue(cuerpo.contains("name=\"ancho_m\""));
        assertTrue(cuerpo.contains("12.00"));
    }

    @Test
    public void sinAnchoNoLoEnvia() throws Exception {
        servidor.enqueue(new MockResponse().setBody("glTF"));
        cliente.generateModel(imagen, null, new File(carpeta.getRoot(), "m.glb"));
        assertTrue(!servidor.takeRequest().getBody().readUtf8().contains("ancho_m"));
    }

    @Test
    public void muestraElMensajeDeErrorDelServidor() {
        servidor.enqueue(new MockResponse().setResponseCode(422)
                .setBody("{\"detail\": \"No se han encontrado paredes en la imagen.\"}"));
        try {
            cliente.generateModel(imagen, null, new File(carpeta.getRoot(), "m.glb"));
            fail("debería fallar");
        } catch (Exception e) {
            assertEquals("No se han encontrado paredes en la imagen.", e.getMessage());
        }
    }

    @Test
    public void errorSinJsonUsaMensajeGenerico() {
        servidor.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));
        try {
            cliente.generateModel(imagen, null, new File(carpeta.getRoot(), "m.glb"));
            fail("debería fallar");
        } catch (Exception e) {
            assertEquals("Error del servidor (500).", e.getMessage());
        }
    }

    @Test
    public void servidorApagado() throws Exception {
        servidor.shutdown();
        try {
            cliente.checkHealth();
            fail("debería fallar");
        } catch (Exception e) {
            assertTrue(e.getMessage().startsWith("No se puede conectar con el servidor"));
        }
    }

    @Test
    public void healthDevuelveLaVersion() throws Exception {
        servidor.enqueue(new MockResponse().setBody("{\"status\": \"ok\", \"version\": \"2.0.0\"}"));
        assertEquals("2.0.0", cliente.checkHealth());
    }

    @Test
    public void healthDeOtroServidor() {
        servidor.enqueue(new MockResponse().setBody("<html>router</html>"));
        try {
            cliente.checkHealth();
            fail("debería fallar");
        } catch (Exception e) {
            assertEquals("La dirección no corresponde a un servidor de VectorScan.", e.getMessage());
        }
    }
}

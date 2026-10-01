package com.jocude.vectorscan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ServerConfigTest {

    @Test
    public void completaEsquemaPuertoYBarra() {
        assertEquals("http://192.168.1.20:8000/", ServerConfig.normalize("192.168.1.20"));
        assertEquals("http://192.168.1.20:8000/", ServerConfig.normalize("  http://192.168.1.20  "));
    }

    @Test
    public void respetaElPuertoIndicado() {
        assertEquals("http://10.0.2.2:9000/", ServerConfig.normalize("10.0.2.2:9000"));
        // :80 es el puerto por defecto de http, así que no se escribe.
        assertEquals("http://servidor.local/", ServerConfig.normalize("http://servidor.local:80"));
    }

    @Test
    public void conHttpsNoAnadePuerto() {
        assertEquals("https://vectorscan.example.com/", ServerConfig.normalize("https://vectorscan.example.com"));
    }

    @Test
    public void mantieneLaRuta() {
        assertEquals("https://example.com/api/", ServerConfig.normalize("https://example.com/api"));
    }

    @Test
    public void rechazaDireccionesNoValidas() {
        assertNull(ServerConfig.normalize(""));
        assertNull(ServerConfig.normalize(null));
        assertNull(ServerConfig.normalize("http://"));
    }
}

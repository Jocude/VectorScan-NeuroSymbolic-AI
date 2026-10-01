package com.jocude.vectorscan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ModelSummaryTest {

    @Test
    public void leeLaCabeceraDelServidor() {
        ModelSummary s = ModelSummary.fromHeader("{\"paredes\": 5, \"puertas\": 5, \"ventanas\": 3, "
                + "\"ancho_m\": 12.0, \"fondo_m\": 8.26, \"habitaciones\": 5}");
        assertEquals(5, s.rooms);
        assertEquals(3, s.windows);
        assertEquals("5 estancias · 5 puertas · 3 ventanas · 12,0 × 8,3 m", s.describe());
    }

    @Test
    public void usaSingularCuandoHayUno() {
        ModelSummary s = new ModelSummary(1, 1, 1, 1, 4, 3);
        assertEquals("1 estancia · 1 puerta · 1 ventana · 4,0 × 3,0 m", s.describe());
    }

    @Test
    public void ignoraCabecerasAusentesOMalFormadas() {
        assertNull(ModelSummary.fromHeader(null));
        assertNull(ModelSummary.fromHeader(""));
        assertNull(ModelSummary.fromHeader("no es json"));
    }
}

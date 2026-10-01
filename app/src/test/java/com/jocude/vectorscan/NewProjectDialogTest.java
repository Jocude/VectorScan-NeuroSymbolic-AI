package com.jocude.vectorscan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

public class NewProjectDialogTest {

    @Rule
    public TemporaryFolder carpeta = new TemporaryFolder();

    @Test
    public void validaElNombre() throws Exception {
        File dir = carpeta.getRoot();
        assertNull(NewProjectDialog.validateName("Casa de la playa", dir));
        assertNotNull(NewProjectDialog.validateName("", dir));
        assertNotNull(NewProjectDialog.validateName("planta/baja", dir));

        new File(dir, "Piso.jpg").createNewFile();
        assertNotNull(NewProjectDialog.validateName("Piso", dir));
    }

    @Test
    public void leeElAnchoConComaOPunto() {
        assertEquals(12.5, NewProjectDialog.parseWidth("12,5"), 1e-9);
        assertEquals(12.5, NewProjectDialog.parseWidth(" 12.5 "), 1e-9);
        assertNull(NewProjectDialog.parseWidth(""));
        assertNull(NewProjectDialog.parseWidth("doce"));
        assertNull(NewProjectDialog.parseWidth("0.5"));
        assertNull(NewProjectDialog.parseWidth("9999"));
    }

    @Test
    public void nombreDeArchivoSeguro() {
        assertEquals("a_b_c", ProjectEditorActivity.safeName("a/b:c"));
        assertEquals("proyecto", ProjectEditorActivity.safeName("   "));
    }
}

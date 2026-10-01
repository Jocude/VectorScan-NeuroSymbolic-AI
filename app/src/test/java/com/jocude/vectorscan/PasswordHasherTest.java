package com.jocude.vectorscan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PasswordHasherTest {

    @Test
    public void verificaLaContrasenaCorrecta() {
        String hash = PasswordHasher.hash("correcta-123");
        assertTrue(PasswordHasher.verify("correcta-123", hash));
        assertFalse(PasswordHasher.verify("incorrecta", hash));
        assertFalse(PasswordHasher.verify("", hash));
    }

    @Test
    public void noGuardaLaContrasenaEnClaro() {
        String hash = PasswordHasher.hash("secreto");
        assertFalse(hash.contains("secreto"));
        assertEquals(4, hash.split("\\$").length);
        assertTrue(PasswordHasher.isHash(hash));
    }

    @Test
    public void cadaHashLlevaSuPropiaSal() {
        assertNotEquals(PasswordHasher.hash("igual"), PasswordHasher.hash("igual"));
    }

    @Test
    public void distingueContrasenasAntiguasEnClaro() {
        assertFalse(PasswordHasher.isHash("1234"));
        assertFalse(PasswordHasher.isHash(null));
        assertFalse(PasswordHasher.verify("1234", "1234"));
    }

    @Test
    public void rechazaHashesCorruptos() {
        assertFalse(PasswordHasher.verify("x", "pbkdf2$120000$no-es-base64!$zz"));
    }
}

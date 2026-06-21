package pt.tecnico.pic.util;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class FileUtilsTest {

    @Test
    void testSuggestEncryptedFileName_WithNormalExtension() {
        File inputFile = new File("documento.txt");
        String suggested = FileUtils.suggestEncryptedFileName(inputFile);
        assertEquals("documento.enc", suggested); 
    }

    @Test
    void testSuggestEncryptedFileName_WithoutExtension() {
        File inputFile = new File("notas");
        String suggested = FileUtils.suggestEncryptedFileName(inputFile);
        assertEquals("notas.enc", suggested);
    }

    @Test
    void testSuggestEncryptedFileName_WithNull() {
        String suggested = FileUtils.suggestEncryptedFileName(null);
        assertEquals("", suggested);
    }

    @Test
    void testFormatFileSize_Bytes() {
        assertEquals("500 B", FileUtils.formatSize(500));
    }

    @Test
    void testFormatFileSize_NegativeBytes() {
        assertEquals("0 B", FileUtils.formatSize(-50));
    }

    @Test
    void testFormatFileSize_KB() {
        assertEquals("1.50 KB", FileUtils.formatSize(1536));
    }

    @Test
    void testFormatFileSize_MB() {
        long megabyte = 1024 * 1024;
        assertEquals("2.00 MB", FileUtils.formatSize(2 * megabyte));
    }

    @Test
    void testFormatFileSize_GB() {
        long gigabyte = 1024L * 1024L * 1024L;
        assertEquals("10.50 GB", FileUtils.formatSize((long) (10.5 * gigabyte)));
    }

    @Test
    void testGetDefaultDirectory_ReturnsValidDirectory() {
        File defaultDir = FileUtils.getDefaultDirectory();
        assertNotNull(defaultDir);
        assertTrue(defaultDir.isDirectory());
        assertTrue(defaultDir.exists());
    }
}
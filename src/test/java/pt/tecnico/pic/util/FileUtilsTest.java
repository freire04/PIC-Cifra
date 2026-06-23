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
        assertEquals("documento.txt.enc", suggested);
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

    @Test
    void testSuggestDecryptedFileName_WithEncExtension() {
        File inputFile = new File("arquivo.txt.enc");
        String suggested = FileUtils.suggestDecryptedFileName(inputFile);
        assertEquals("arquivo.txt", suggested);
    }

    @Test
    void testSuggestDecryptedFileName_WithUppercaseEncExtension() {
        File inputFile = new File("arquivo.txt.ENC");
        String suggested = FileUtils.suggestDecryptedFileName(inputFile);
        assertEquals("arquivo.txt", suggested);
    }

    @Test
    void testSuggestDecryptedFileName_WithoutEncExtension() {
        File inputFile = new File("documento_original.txt");
        String suggested = FileUtils.suggestDecryptedFileName(inputFile);
        assertEquals("documento_original.txt.dec", suggested);
    }

    @Test
    void testSuggestDecryptedFileName_WithNoExtensionAtAll() {
        File inputFile = new File("ficheiro_semeextensao");
        String suggested = FileUtils.suggestDecryptedFileName(inputFile);
        assertEquals("ficheiro_semeextensao.dec", suggested);
    }

    @Test
    void testSuggestDecryptedFileName_WithNull() {
        String suggested = FileUtils.suggestDecryptedFileName(null);
        assertEquals("", suggested);
    }
}

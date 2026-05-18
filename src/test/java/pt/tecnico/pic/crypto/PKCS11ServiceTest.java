package pt.tecnico.pic.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.security.KeyStore;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.dto.CryptoResult;

class PKCS11ServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void fileOperationsShouldFailWithoutOpenSession() {
        PKCS11Service service = new PKCS11Service();

        assertFalse(service.isSessionOpen());
        assertEquals(OperationResult.FAILED, service.encryptFile("plain.txt", "plain.cif").getResult());
        assertEquals(OperationResult.FAILED, service.decryptFile("plain.cif", "plain.txt").getResult());
    }

    @Test
    void encryptFileShouldRejectSameInputAndOutputBeforeReadingFile() throws Exception {
        PKCS11Service service = new PKCS11Service();
        openFakeSession(service);
        String filePath = tempDir.resolve("same.txt").toString();

        CryptoResult result = service.encryptFile(filePath, filePath);

        assertEquals(OperationResult.FAILED, result.getResult());
        assertEquals("Input and output files must be different.", result.getMessage());
    }

    @Test
    void decryptFileShouldRejectSameInputAndOutputBeforeReadingFile() throws Exception {
        PKCS11Service service = new PKCS11Service();
        openFakeSession(service);
        String filePath = tempDir.resolve("same.pic").toString();

        CryptoResult result = service.decryptFile(filePath, filePath);

        assertEquals(OperationResult.FAILED, result.getResult());
        assertEquals("Input and output files must be different.", result.getMessage());
    }

    private static void openFakeSession(PKCS11Service service) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);

        setField(service, "keyStore", keyStore);
        setField(service, "secretKey", new SecretKeySpec(new byte[16], "AES"));
        setField(service, "sessionOpen", true);
    }

    private static void setField(PKCS11Service service, String fieldName, Object value) throws Exception {
        Field field = PKCS11Service.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(service, value);
    }
}

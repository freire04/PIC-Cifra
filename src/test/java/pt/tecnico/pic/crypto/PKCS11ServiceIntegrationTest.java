package pt.tecnico.pic.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import pt.tecnico.pic.domain.OperationResult;

class PKCS11ServiceIntegrationTest {

    private static final String TEST_PIN_PROPERTY = "pic.pkcs11.test.pin";
    private static final String TEST_WRONG_PIN_PROPERTY = "pic.pkcs11.test.wrongPin";

    @TempDir
    Path tempDir;

    @Test
    void shouldEncryptAndDecryptFileWithSoftHsmToken() throws Exception {
        String pin = System.getProperty(TEST_PIN_PROPERTY);
        assumeTrue(pin != null && !pin.isBlank(),
                "Set -D" + TEST_PIN_PROPERTY + "=<TOKEN_PIN> to run the PKCS#11 integration test.");

        char[] pinChars = pin.toCharArray();
        PKCS11Service service = new PKCS11Service();

        Path input = tempDir.resolve("original.txt");
        Path encrypted = tempDir.resolve("original.pic");
        Path requestedOutput = tempDir.resolve("decrypted.bin");
        Path decrypted = tempDir.resolve("decrypted.txt");
        byte[] originalBytes = "ficheiro de teste PKCS11".getBytes(StandardCharsets.UTF_8);
        Files.write(input, originalBytes);

        try {
            OperationResult openSessionResult = service.openSession(pinChars);
            assertEquals(OperationResult.SUCCESS, openSessionResult,
                    "Could not open the SoftHSM2 token session. Check that SOFTHSM2_CONF and PATH are set, "
                            + "the token is initialized, pkcs11.cfg points to the correct library/slot, "
                            + "and the provided token PIN is correct.");
            assertTrue(service.isSessionOpen());

            assertEquals(OperationResult.SUCCESS,
                    service.encryptFile(input.toString(), encrypted.toString()).getResult());
            assertTrue(Files.exists(encrypted));

            var decryptResult = service.decryptFile(encrypted.toString(), requestedOutput.toString());
            assertEquals(OperationResult.SUCCESS, decryptResult.getResult());
            assertEquals(decrypted.toString(), decryptResult.getOutputFilePath());
            assertArrayEquals(originalBytes, Files.readAllBytes(decrypted));
        } finally {
            Arrays.fill(pinChars, '\0');
            service.closeSession();
        }

        assertFalse(service.isSessionOpen());
    }

    @Test
    void openSessionShouldFailWithWrongSoftHsmPin() {
        String pin = System.getProperty(TEST_PIN_PROPERTY);
        assumeTrue(pin != null && !pin.isBlank(),
                "Set -D" + TEST_PIN_PROPERTY + "=<TOKEN_PIN> to run the PKCS#11 integration test.");

        String wrongPin = System.getProperty(TEST_WRONG_PIN_PROPERTY, "wrong-pin-" + pin);
        assumeFalse(wrongPin.equals(pin), "Wrong PIN must be different from the token PIN.");

        char[] wrongPinChars = wrongPin.toCharArray();
        PKCS11Service service = new PKCS11Service();

        try {
            assertEquals(OperationResult.FAILED, service.openSession(wrongPinChars));
            assertFalse(service.isSessionOpen());
        } finally {
            Arrays.fill(wrongPinChars, '\0');
            service.closeSession();
        }
    }
}

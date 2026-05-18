package pt.tecnico.pic.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import pt.tecnico.pic.domain.OperationResult;

class PKCS11ServiceTest {

    @Test
    void fileOperationsShouldFailWithoutOpenSession() {
        PKCS11Service service = new PKCS11Service();

        assertFalse(service.isSessionOpen());
        assertEquals(OperationResult.FAILED, service.encryptFile("plain.txt", "plain.cif").getResult());
        assertEquals(OperationResult.FAILED, service.decryptFile("plain.cif", "plain.txt").getResult());
    }
}

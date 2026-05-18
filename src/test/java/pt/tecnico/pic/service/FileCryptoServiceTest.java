package pt.tecnico.pic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import pt.tecnico.pic.crypto.CryptoService;
import pt.tecnico.pic.domain.ActionType;
import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.domain.Role;
import pt.tecnico.pic.domain.UserContext;
import pt.tecnico.pic.dto.CryptoResult;
import pt.tecnico.pic.dto.LogDTO;

class FileCryptoServiceTest {
    @Test
    void encryptFileShouldDelegateAndCreateAuditLogWithSanitizedFileName() {
        AuditService auditService = new AuditService();
        StubCryptoService cryptoService = new StubCryptoService();
        cryptoService.sessionOpen = true;
        FileCryptoService fileCryptoService = new FileCryptoService(cryptoService, auditService);
        UserContext userContext = new UserContext(42, "alice", Role.USER);

        CryptoResult result = fileCryptoService.encryptFile(userContext,
                "C:\\Users\\alice\\documents\\documento.pdf", "C:\\Users\\alice\\documents\\documento.cif");

        LogDTO log = auditService.getLogs().getFirst();

        assertEquals(OperationResult.SUCCESS, result.getResult());
        assertEquals("C:\\Users\\alice\\documents\\documento.pdf", cryptoService.lastInputPath);
        assertEquals("C:\\Users\\alice\\documents\\documento.cif", cryptoService.lastOutputPath);
        assertEquals(ActionType.ENCRYPT_FILE, log.getActionType());
        assertEquals(Role.USER, log.getActorRole());
        assertEquals("documento.pdf", log.getFileName());
    }

    @Test
    void encryptFileShouldFailWithoutUnlockedToken() {
        AuditService auditService = new AuditService();
        StubCryptoService cryptoService = new StubCryptoService();
        FileCryptoService fileCryptoService = new FileCryptoService(cryptoService, auditService);
        UserContext userContext = new UserContext(42, "alice", Role.USER);

        CryptoResult result = fileCryptoService.encryptFile(userContext, "plain.txt", "plain.cif");

        LogDTO log = auditService.getLogs().getFirst();

        assertEquals(OperationResult.FAILED, result.getResult());
        assertFalse(cryptoService.encryptCalled);
        assertEquals(OperationResult.FAILED, log.getResult());
        assertEquals(ActionType.ENCRYPT_FILE, log.getActionType());
    }

    @Test
    void unlockAndLockTokenShouldUseCryptoServiceSession() {
        AuditService auditService = new AuditService();
        StubCryptoService cryptoService = new StubCryptoService();
        FileCryptoService fileCryptoService = new FileCryptoService(cryptoService, auditService);

        assertEquals(OperationResult.SUCCESS, fileCryptoService.unlockToken("1234".toCharArray()));
        assertTrue(fileCryptoService.isTokenUnlocked());

        assertEquals(OperationResult.SUCCESS, fileCryptoService.lockToken());
        assertFalse(fileCryptoService.isTokenUnlocked());
        assertEquals(ActionType.TOKEN_UNLOCK, auditService.getLogs().get(0).getActionType());
        assertEquals(ActionType.TOKEN_LOCK, auditService.getLogs().get(1).getActionType());
    }

    private static class StubCryptoService implements CryptoService {
        private boolean sessionOpen;
        private boolean encryptCalled;
        private String lastInputPath;
        private String lastOutputPath;

        @Override
        public void initialize() {
        }

        @Override
        public OperationResult openSession(char[] pin) {
            sessionOpen = true;
            return OperationResult.SUCCESS;
        }

        @Override
        public OperationResult closeSession() {
            sessionOpen = false;
            return OperationResult.SUCCESS;
        }

        @Override
        public boolean isSessionOpen() {
            return sessionOpen;
        }

        @Override
        public CryptoResult encryptFile(String inputPath, String outputPath) {
            encryptCalled = true;
            lastInputPath = inputPath;
            lastOutputPath = outputPath;
            return new CryptoResult(
                    OperationResult.SUCCESS,
                    "File encrypted successfully.",
                    inputPath,
                    outputPath,
                    ActionType.ENCRYPT_FILE
            );
        }

        @Override
        public CryptoResult decryptFile(String inputPath, String outputPath) {
            return new CryptoResult(
                    OperationResult.SUCCESS,
                    "File decrypted successfully.",
                    inputPath,
                    outputPath,
                    ActionType.DECRYPT_FILE
            );
        }
    }
}

package pt.tecnico.pic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import pt.tecnico.pic.crypto.CryptoService;
import pt.tecnico.pic.domain.ActionType;
import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.domain.Role;
import pt.tecnico.pic.domain.UserContext;
import pt.tecnico.pic.dto.CryptoResult;
import pt.tecnico.pic.dto.LogDTO;
import pt.tecnico.pic.store.LogStore;

class FileCryptoServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void constructorsShouldCreateServicesWithCryptoService() {
        FileCryptoService defaultService = new FileCryptoService();
        AuditService auditService = newAuditService();
        FileCryptoService serviceWithAudit = new FileCryptoService(auditService);

        assertNotNull(defaultService.getAuditService());
        assertNotNull(defaultService.getCryptoService());
        assertEquals(auditService, serviceWithAudit.getAuditService());
        assertNotNull(serviceWithAudit.getCryptoService());
    }

    @Test
    void encryptFileShouldDelegateAndCreateAuditLogWithSanitizedFileName() {
        AuditService auditService = newAuditService();
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
    void encryptFileShouldAllowNullUserContextAndCreateAuditLog() {
        AuditService auditService = newAuditService();
        StubCryptoService cryptoService = new StubCryptoService();
        cryptoService.sessionOpen = true;
        FileCryptoService fileCryptoService = new FileCryptoService(cryptoService, auditService);

        CryptoResult result = fileCryptoService.encryptFile("plain.txt", "plain.cif", null);

        LogDTO log = auditService.getLogs().getFirst();

        assertEquals(OperationResult.SUCCESS, result.getResult());
        assertEquals(ActionType.ENCRYPT_FILE, log.getActionType());
        assertEquals(null, log.getUsername());
        assertEquals(null, log.getActorRole());
    }

    @Test
    void decryptFileShouldDelegateAndCreateAuditLogWithSanitizedFileName() {
        AuditService auditService = newAuditService();
        StubCryptoService cryptoService = new StubCryptoService();
        cryptoService.sessionOpen = true;
        FileCryptoService fileCryptoService = new FileCryptoService(cryptoService, auditService);
        UserContext userContext = new UserContext(42, "alice", Role.USER);

        CryptoResult result = fileCryptoService.decryptFile(userContext,
                "C:\\Users\\alice\\documents\\documento.cif", "C:\\Users\\alice\\documents\\documento.pdf");

        LogDTO log = auditService.getLogs().getFirst();

        assertEquals(OperationResult.SUCCESS, result.getResult());
        assertTrue(cryptoService.decryptCalled);
        assertEquals("C:\\Users\\alice\\documents\\documento.cif", cryptoService.lastInputPath);
        assertEquals("C:\\Users\\alice\\documents\\documento.pdf", cryptoService.lastOutputPath);
        assertEquals(ActionType.DECRYPT_FILE, log.getActionType());
        assertEquals(Role.USER, log.getActorRole());
        assertEquals("documento.cif", log.getFileName());
    }

    @Test
    void encryptFileShouldFailWithoutUnlockedToken() {
        AuditService auditService = newAuditService();
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
    void decryptFileShouldFailWithoutUnlockedToken() {
        AuditService auditService = newAuditService();
        StubCryptoService cryptoService = new StubCryptoService();
        FileCryptoService fileCryptoService = new FileCryptoService(cryptoService, auditService);
        UserContext userContext = new UserContext(42, "alice", Role.USER);

        CryptoResult result = fileCryptoService.decryptFile(userContext, "plain.cif", "plain.txt");

        LogDTO log = auditService.getLogs().getFirst();

        assertEquals(OperationResult.FAILED, result.getResult());
        assertFalse(cryptoService.decryptCalled);
        assertEquals(OperationResult.FAILED, log.getResult());
        assertEquals(ActionType.DECRYPT_FILE, log.getActionType());
    }

    @Test
    void encryptFileShouldLogFailureReturnedByCryptoService() {
        AuditService auditService = newAuditService();
        StubCryptoService cryptoService = new StubCryptoService();
        cryptoService.sessionOpen = true;
        cryptoService.encryptResult = OperationResult.FAILED;
        FileCryptoService fileCryptoService = new FileCryptoService(cryptoService, auditService);
        UserContext userContext = new UserContext(42, "alice", Role.USER);

        CryptoResult result = fileCryptoService.encryptFile(userContext, "plain.txt", "plain.cif");

        LogDTO log = auditService.getLogs().getFirst();

        assertEquals(OperationResult.FAILED, result.getResult());
        assertTrue(cryptoService.encryptCalled);
        assertEquals(OperationResult.FAILED, log.getResult());
        assertEquals(ActionType.ENCRYPT_FILE, log.getActionType());
    }

    @Test
    void decryptFileShouldLogFailureReturnedByCryptoService() {
        AuditService auditService = newAuditService();
        StubCryptoService cryptoService = new StubCryptoService();
        cryptoService.sessionOpen = true;
        cryptoService.decryptResult = OperationResult.FAILED;
        FileCryptoService fileCryptoService = new FileCryptoService(cryptoService, auditService);
        UserContext userContext = new UserContext(42, "alice", Role.USER);

        CryptoResult result = fileCryptoService.decryptFile(userContext, "plain.cif", "plain.txt");

        LogDTO log = auditService.getLogs().getFirst();

        assertEquals(OperationResult.FAILED, result.getResult());
        assertTrue(cryptoService.decryptCalled);
        assertEquals(OperationResult.FAILED, log.getResult());
        assertEquals(ActionType.DECRYPT_FILE, log.getActionType());
    }

    @Test
    void unlockAndLockTokenShouldUseCryptoServiceSession() {
        AuditService auditService = newAuditService();
        StubCryptoService cryptoService = new StubCryptoService();
        FileCryptoService fileCryptoService = new FileCryptoService(cryptoService, auditService);

        assertEquals(OperationResult.SUCCESS, fileCryptoService.unlockToken("1234".toCharArray()));
        assertTrue(fileCryptoService.isTokenUnlocked());

        assertEquals(OperationResult.SUCCESS, fileCryptoService.lockToken());
        assertFalse(fileCryptoService.isTokenUnlocked());
        assertEquals(ActionType.TOKEN_UNLOCK, auditService.getLogs().get(0).getActionType());
        assertEquals(ActionType.TOKEN_LOCK, auditService.getLogs().get(1).getActionType());
    }

    @Test
    void unlockTokenShouldLogFailureWhenPinIsRejected() {
        AuditService auditService = newAuditService();
        StubCryptoService cryptoService = new StubCryptoService();
        cryptoService.openSessionResult = OperationResult.FAILED;
        FileCryptoService fileCryptoService = new FileCryptoService(cryptoService, auditService);

        assertEquals(OperationResult.FAILED, fileCryptoService.unlockToken("bad-pin".toCharArray()));

        LogDTO log = auditService.getLogs().getFirst();

        assertFalse(fileCryptoService.isTokenUnlocked());
        assertEquals(ActionType.TOKEN_UNLOCK, log.getActionType());
        assertEquals(OperationResult.FAILED, log.getResult());
        assertEquals("Token unlock failed.", log.getMessage());
    }

    @Test
    void lockTokenShouldLogFailureReturnedByCryptoService() {
        AuditService auditService = newAuditService();
        StubCryptoService cryptoService = new StubCryptoService();
        cryptoService.closeSessionResult = OperationResult.FAILED;
        FileCryptoService fileCryptoService = new FileCryptoService(cryptoService, auditService);

        assertEquals(OperationResult.FAILED, fileCryptoService.lockToken());

        LogDTO log = auditService.getLogs().getFirst();

        assertEquals(ActionType.TOKEN_LOCK, log.getActionType());
        assertEquals(OperationResult.FAILED, log.getResult());
        assertEquals("Token lock failed.", log.getMessage());
    }

    private AuditService newAuditService() {
        return new AuditService(new LogStore(tempDir.resolve("logs.ndjson")));
    }

    private static class StubCryptoService implements CryptoService {
        private boolean sessionOpen;
        private boolean encryptCalled;
        private boolean decryptCalled;
        private String lastInputPath;
        private String lastOutputPath;
        private OperationResult openSessionResult = OperationResult.SUCCESS;
        private OperationResult closeSessionResult = OperationResult.SUCCESS;
        private OperationResult encryptResult = OperationResult.SUCCESS;
        private OperationResult decryptResult = OperationResult.SUCCESS;

        @Override
        public void initialize() {
        }

        @Override
        public OperationResult openSession(char[] pin) {
            sessionOpen = openSessionResult == OperationResult.SUCCESS;
            return openSessionResult;
        }

        @Override
        public OperationResult closeSession() {
            sessionOpen = false;
            return closeSessionResult;
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
                    encryptResult,
                    encryptResult == OperationResult.SUCCESS ? "File encrypted successfully." : "File encryption failed.",
                    inputPath,
                    outputPath,
                    ActionType.ENCRYPT_FILE
            );
        }

        @Override
        public CryptoResult decryptFile(String inputPath, String outputPath) {
            decryptCalled = true;
            lastInputPath = inputPath;
            lastOutputPath = outputPath;
            return new CryptoResult(
                    decryptResult,
                    decryptResult == OperationResult.SUCCESS ? "File decrypted successfully." : "File decryption failed.",
                    inputPath,
                    outputPath,
                    ActionType.DECRYPT_FILE
            );
        }
    }
}

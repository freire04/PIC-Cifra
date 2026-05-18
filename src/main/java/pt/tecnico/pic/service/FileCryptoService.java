package pt.tecnico.pic.service;

import java.util.Objects;

import pt.tecnico.pic.crypto.CryptoService;
import pt.tecnico.pic.crypto.PKCS11Service;
import pt.tecnico.pic.domain.ActionType;
import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.domain.Role;
import pt.tecnico.pic.domain.UserContext;
import pt.tecnico.pic.dto.CryptoResult;

public class FileCryptoService {

    private final CryptoService cryptoService;
    private final AuditService auditService;

    public FileCryptoService() {
        this(new PKCS11Service(), new AuditService());
    }

    public FileCryptoService(AuditService auditService) {
        this(new PKCS11Service(), auditService);
    }

    public FileCryptoService(CryptoService cryptoService, AuditService auditService) {
        this.cryptoService = Objects.requireNonNull(cryptoService, "cryptoService must not be null");
        this.auditService = Objects.requireNonNull(auditService, "auditService must not be null");
    }

    public OperationResult unlockToken(char[] pin) {
        OperationResult result = cryptoService.openSession(pin);

        auditService.log(
                null,
                null,
                null,
                ActionType.TOKEN_UNLOCK,
                null,
                result,
                result == OperationResult.SUCCESS ? "Token unlocked." : "Token unlock failed."
        );

        return result;
    }

    public OperationResult lockToken() {
        OperationResult result = cryptoService.closeSession();

        auditService.log(
                null,
                null,
                null,
                ActionType.TOKEN_LOCK,
                null,
                result,
                result == OperationResult.SUCCESS ? "Token locked." : "Token lock failed."
        );

        return result;
    }

    public boolean isTokenUnlocked() {
        return cryptoService.isSessionOpen();
    }

    public CryptoResult encryptFile(String inputFilePath, String outputFilePath, UserContext userContext) {
        return completeCryptoOperation(
                userContext,
                inputFilePath,
                outputFilePath,
                ActionType.ENCRYPT_FILE,
                () -> cryptoService.encryptFile(inputFilePath, outputFilePath)
        );
    }

    public CryptoResult encryptFile(UserContext userContext, String inputFilePath, String outputFilePath) {
        return encryptFile(inputFilePath, outputFilePath, userContext);
    }

    public CryptoResult decryptFile(String inputFilePath, String outputFilePath, UserContext userContext) {
        return completeCryptoOperation(
                userContext,
                inputFilePath,
                outputFilePath,
                ActionType.DECRYPT_FILE,
                () -> cryptoService.decryptFile(inputFilePath, outputFilePath)
        );
    }

    public CryptoResult decryptFile(UserContext userContext, String inputFilePath, String outputFilePath) {
        return decryptFile(inputFilePath, outputFilePath, userContext);
    }

    public AuditService getAuditService() {
        return auditService;
    }

    public CryptoService getCryptoService() {
        return cryptoService;
    }

    private CryptoResult completeCryptoOperation(UserContext userContext,
                                                 String inputFilePath,
                                                 String outputFilePath,
                                                 ActionType action,
                                                 CryptoOperation operation) {
        Integer accountId = userContext == null ? null : userContext.getAccountId();
        String username = userContext == null ? null : userContext.getUsername();
        Role actorRole = userContext == null ? null : userContext.getSelectedRole();

        CryptoResult cryptoResult;
        if (!cryptoService.isSessionOpen()) {
            cryptoResult = new CryptoResult(
                    OperationResult.FAILED,
                    "Token session is not open.",
                    inputFilePath,
                    outputFilePath,
                    action
            );
        } else {
            cryptoResult = operation.execute();
        }

        auditService.log(
                accountId,
                username,
                actorRole,
                action,
                inputFilePath,
                cryptoResult.getResult(),
                cryptoResult.getMessage()
        );

        return cryptoResult;
    }

    @FunctionalInterface
    private interface CryptoOperation {
        CryptoResult execute();
    }
}

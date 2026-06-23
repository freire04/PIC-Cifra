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
        return unlockToken(pin, null);
    }

    public OperationResult unlockToken(char[] pin, UserContext userContext) {
        OperationResult result = cryptoService.openSession(pin);

        auditService.log(
                accountId(userContext),
                username(userContext),
                actorRole(userContext),
                ActionType.TOKEN_UNLOCK,
                null,
                result,
                result == OperationResult.SUCCESS ? "Token unlocked." : "Token unlock failed."
        );

        return result;
    }

    public OperationResult lockToken() {
        return lockToken(null);
    }

    public OperationResult lockToken(UserContext userContext) {
        OperationResult result = cryptoService.closeSession();

        auditService.log(
                accountId(userContext),
                username(userContext),
                actorRole(userContext),
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
                accountId(userContext),
                username(userContext),
                actorRole(userContext),
                action,
                inputFilePath,
                cryptoResult.getResult(),
                cryptoResult.getMessage()
        );

        return cryptoResult;
    }

    private static Integer accountId(UserContext userContext) {
        return userContext == null ? null : userContext.getAccountId();
    }

    private static String username(UserContext userContext) {
        return userContext == null ? null : userContext.getUsername();
    }

    private static Role actorRole(UserContext userContext) {
        return userContext == null ? null : userContext.getSelectedRole();
    }

    @FunctionalInterface
    private interface CryptoOperation {
        CryptoResult execute();
    }
}

package pt.tecnico.pic.service;

import java.util.Objects;

import pt.tecnico.pic.domain.ActionType;
import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.domain.Role;
import pt.tecnico.pic.domain.UserContext;
import pt.tecnico.pic.dto.CryptoResult;

public class FileCryptoService {
    private final AuditService auditService;

    public FileCryptoService() {
        this(new AuditService());
    }

    public FileCryptoService(AuditService auditService) {
        this.auditService = Objects.requireNonNull(auditService, "auditService must not be null");
    }

    public CryptoResult encryptFile(UserContext userContext, String inputFilePath, String outputFilePath) {
        return completeCryptoOperation(userContext, inputFilePath, outputFilePath,
                ActionType.ENCRYPT_FILE, "File encryption registered.");
    }

    public CryptoResult decryptFile(UserContext userContext, String inputFilePath, String outputFilePath) {
        return completeCryptoOperation(userContext, inputFilePath, outputFilePath,
                ActionType.DECRYPT_FILE, "File decryption registered.");
    }

    public CryptoResult encryptFile(Integer accountId, String username, Role actorRole,
                                    String inputFilePath, String outputFilePath) {
        return completeCryptoOperation(accountId, username, actorRole, inputFilePath, outputFilePath,
                ActionType.ENCRYPT_FILE, "File encryption registered.");
    }

    public CryptoResult decryptFile(Integer accountId, String username, Role actorRole,
                                    String inputFilePath, String outputFilePath) {
        return completeCryptoOperation(accountId, username, actorRole, inputFilePath, outputFilePath,
                ActionType.DECRYPT_FILE, "File decryption registered.");
    }

    public AuditService getAuditService() {
        return auditService;
    }

    private CryptoResult completeCryptoOperation(UserContext userContext, String inputFilePath,
                                                 String outputFilePath, ActionType action, String message) {
        Integer accountId = userContext == null ? null : userContext.getAccountId();
        String username = userContext == null ? null : userContext.getUsername();
        Role actorRole = userContext == null ? null : userContext.getSelectedRole();

        return completeCryptoOperation(accountId, username, actorRole, inputFilePath, outputFilePath, action, message);
    }

    private CryptoResult completeCryptoOperation(Integer accountId, String username, Role actorRole,
                                                 String inputFilePath, String outputFilePath,
                                                 ActionType action, String message) {
        OperationResult result = OperationResult.SUCCESS;
        auditService.log(accountId, username, actorRole, action, inputFilePath, result, message);
        return new CryptoResult(result, message, inputFilePath, outputFilePath, action);
    }
}

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

    public CryptoResult encryptFile(String inputFilePath, String outputFilePath, UserContext userContext) {
        return completeCryptoOperation(
                userContext,
                inputFilePath,
                outputFilePath,
                ActionType.ENCRYPT_FILE,
                "File encryption audit event registered."
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
                "File decryption audit event registered."
        );
    }

    public CryptoResult decryptFile(UserContext userContext, String inputFilePath, String outputFilePath) {
        return decryptFile(inputFilePath, outputFilePath, userContext);
    }

    public AuditService getAuditService() {
        return auditService;
    }

    private CryptoResult completeCryptoOperation(UserContext userContext,
                                                 String inputFilePath,
                                                 String outputFilePath,
                                                 ActionType action,
                                                 String message) {
        Integer accountId = userContext == null ? null : userContext.getAccountId();
        String username = userContext == null ? null : userContext.getUsername();
        Role actorRole = userContext == null ? null : userContext.getSelectedRole();

        OperationResult result = OperationResult.SUCCESS;

        auditService.log(
                accountId,
                username,
                actorRole,
                action,
                inputFilePath,
                result,
                message
        );

        return new CryptoResult(
                result,
                message,
                inputFilePath,
                outputFilePath,
                action
        );
    }
}

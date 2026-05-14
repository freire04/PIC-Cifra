package pt.tecnico.pic.application;

import java.util.Objects;

import pt.tecnico.pic.domain.ActionType;
import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.service.AuditService;
import pt.tecnico.pic.service.FileCryptoService;

/**
 * Central application facade.
 */
public class AppController {

    private final AuditService auditService;
    private final FileCryptoService fileCryptoService;

    public AppController() {
        this(new AuditService());
    }

    public AppController(AuditService auditService) {
        this(auditService, new FileCryptoService(auditService));
    }

    public AppController(AuditService auditService, FileCryptoService fileCryptoService) {
        this.auditService = Objects.requireNonNull(auditService, "auditService must not be null");
        this.fileCryptoService = Objects.requireNonNull(fileCryptoService, "fileCryptoService must not be null");
        if (this.fileCryptoService.getAuditService() != this.auditService) {
            throw new IllegalArgumentException("fileCryptoService must use the provided auditService");
        }
    }

    public void recordLogin(Integer accountId, String username, OperationResult result, String message) {
        auditService.log(accountId, username, null, ActionType.LOGIN, null, result, message);
    }

    public AuditService getAuditService() {
        return auditService;
    }

    public FileCryptoService getFileCryptoService() {
        return fileCryptoService;
    }
}

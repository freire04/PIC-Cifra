package pt.tecnico.pic.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import pt.tecnico.pic.domain.ActionType;
import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.dto.LogDTO;
import pt.tecnico.pic.service.AuditService;
import pt.tecnico.pic.service.FileCryptoService;

class AppControllerTest {

    @Test
    void recordLoginShouldCreateAuditLogWithoutActorRole() {
        AuditService auditService = new AuditService();
        AppController appController = new AppController(auditService);

        appController.recordLogin(42, "alice", OperationResult.SUCCESS, "login ok");

        assertEquals(1, auditService.getLogs().size());

        LogDTO log = auditService.getLogs().get(0);

        assertEquals(ActionType.LOGIN, log.getActionType());
        assertEquals("alice", log.getUsername());
        assertEquals(OperationResult.SUCCESS, log.getResult());
        assertEquals("login ok", log.getMessage());
        assertNull(log.getActorRole());
    }

    @Test
    void constructorShouldAcceptSharedAuditService() {
        AuditService auditService = new AuditService();
        FileCryptoService fileCryptoService = new FileCryptoService(auditService);

        AppController appController = new AppController(auditService, fileCryptoService);

        assertSame(auditService, appController.getAuditService());
        assertSame(fileCryptoService, appController.getFileCryptoService());
    }

    @Test
    void constructorShouldRejectFileCryptoServiceWithDifferentAuditService() {
        AuditService appAuditService = new AuditService();
        AuditService fileAuditService = new AuditService();
        FileCryptoService fileCryptoService = new FileCryptoService(fileAuditService);

        assertThrows(
                IllegalArgumentException.class,
                () -> new AppController(appAuditService, fileCryptoService)
        );
    }
}
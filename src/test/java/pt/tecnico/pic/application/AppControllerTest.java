package pt.tecnico.pic.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import pt.tecnico.pic.domain.ActionType;
import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.dto.LogDTO;
import pt.tecnico.pic.service.AuditService;

class AppControllerTest {
    @Test
    void canInstantiateAppController() {
        AppController appController = new AppController();

        assertNotNull(appController);
    }

    @Test
    void recordLoginShouldCreateAuditLogWithoutActorRole() {
        AuditService auditService = new AuditService();
        AppController appController = new AppController(auditService);

        appController.recordLogin(42, "alice", OperationResult.SUCCESS, "login ok");

        LogDTO log = auditService.getLogs().getFirst();

        assertEquals(ActionType.LOGIN, log.getActionType());
        assertEquals("alice", log.getUsername());
        assertNull(log.getActorRole());
    }
}

package pt.tecnico.pic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import pt.tecnico.pic.domain.ActionType;
import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.domain.Role;
import pt.tecnico.pic.dto.LogDTO;
import pt.tecnico.pic.dto.LogFilter;

class AuditServiceTest {
    @Test
    void logShouldExposeOnlyFileNameAndKeepActorRole() {
        AuditService auditService = new AuditService();

        auditService.log(42, "alice", Role.USER, ActionType.ENCRYPT_FILE,
                "C:\\Users\\alice\\documents\\documento.pdf", OperationResult.SUCCESS, "ok");

        List<LogDTO> logs = auditService.getLogs();

        assertEquals(1, logs.size());
        assertEquals(Role.USER, logs.getFirst().getActorRole());
        assertEquals("documento.pdf", logs.getFirst().getFileName());
        assertFalse(logs.getFirst().getFileName().contains("\\"));
        assertFalse(logs.getFirst().getFileName().contains("/"));
    }

    @Test
    void logShouldAllowMissingActorRoleBeforeRoleSelection() {
        AuditService auditService = new AuditService();

        auditService.log(42, "alice", null, ActionType.LOGIN, null, OperationResult.SUCCESS, "login ok");

        LogDTO log = auditService.getLogs().getFirst();

        assertEquals(ActionType.LOGIN, log.getActionType());
        assertNull(log.getActorRole());
        assertNull(log.getFileName());
    }

    @Test
    void logShouldRedactSensitiveValuesAndFullPathsFromMessages() {
        AuditService auditService = new AuditService();

        auditService.log(42, "alice", Role.USER, ActionType.ENCRYPT_FILE,
                "/home/alice/documents/documento.pdf", OperationResult.FAILED,
                "failed password=secret pin:1234 path=/home/alice/documents/documento.pdf");

        String message = auditService.getLogs().getFirst().getMessage();

        assertFalse(message.contains("secret"));
        assertFalse(message.contains("1234"));
        assertFalse(message.contains("/home/alice"));
        assertEquals("documento.pdf", auditService.getLogs().getFirst().getFileName());
    }

    @Test
    void getLogsWithFilterShouldReturnMatchingLogsOnly() {
        AuditService auditService = new AuditService();
        LogFilter filter = new LogFilter();
        filter.setActionType(ActionType.ENCRYPT_FILE);

        auditService.log(42, "alice", Role.USER, ActionType.ENCRYPT_FILE,
                "/tmp/documento.pdf", OperationResult.SUCCESS, "ok");
        auditService.log(42, "alice", Role.USER, ActionType.DECRYPT_FILE,
                "/tmp/documento.pdf", OperationResult.SUCCESS, "ok");

        List<LogDTO> logs = auditService.getLogs(filter);

        assertEquals(1, logs.size());
        assertEquals(ActionType.ENCRYPT_FILE, logs.getFirst().getActionType());
    }
}

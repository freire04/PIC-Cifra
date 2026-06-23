package pt.tecnico.pic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import pt.tecnico.pic.domain.ActionType;
import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.domain.Role;
import pt.tecnico.pic.dto.LogDTO;
import pt.tecnico.pic.dto.LogFilter;
import pt.tecnico.pic.store.LogStore;

class AuditServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void logShouldExposeOnlyFileNameAndKeepActorRole() {
        AuditService auditService = newAuditService();

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
        AuditService auditService = newAuditService();

        auditService.log(42, "alice", null, ActionType.LOGIN, null, OperationResult.SUCCESS, "login ok");

        LogDTO log = auditService.getLogs().getFirst();

        assertEquals(ActionType.LOGIN, log.getActionType());
        assertNull(log.getActorRole());
        assertNull(log.getFileName());
    }

    @Test
    void logShouldRedactSensitiveValuesAndFullPathsFromMessages() {
        AuditService auditService = newAuditService();

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
        AuditService auditService = newAuditService();
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

    @Test
    void logsShouldRemainAvailableAfterServiceRestart() {
        Path logsPath = tempDir.resolve("persistent-logs.ndjson");
        AuditService firstService = new AuditService(new LogStore(logsPath));

        firstService.log(42, "alice", Role.USER, ActionType.ENCRYPT_FILE,
                "/tmp/documento.pdf", OperationResult.SUCCESS, "ok");

        AuditService restartedService = new AuditService(new LogStore(logsPath));
        List<LogDTO> logs = restartedService.getLogs();

        assertEquals(1, logs.size());
        assertEquals(1, logs.getFirst().getLogId());
        assertEquals("alice", logs.getFirst().getUsername());
        assertEquals("documento.pdf", logs.getFirst().getFileName());
    }

    @Test
    void logIdsShouldContinueAfterServiceRestart() {
        Path logsPath = tempDir.resolve("id-sequence.ndjson");
        AuditService firstService = new AuditService(new LogStore(logsPath));

        firstService.log(42, "alice", Role.USER, ActionType.LOGIN,
                null, OperationResult.SUCCESS, "ok");
        firstService.log(42, "alice", Role.USER, ActionType.LOGOUT,
                null, OperationResult.SUCCESS, "ok");

        AuditService restartedService = new AuditService(new LogStore(logsPath));
        restartedService.log(42, "alice", Role.USER, ActionType.LOGIN,
                null, OperationResult.SUCCESS, "ok");

        List<LogDTO> logs = restartedService.getLogs();
        assertEquals(List.of(1, 2, 3), logs.stream().map(LogDTO::getLogId).toList());
    }

    @Test
    void filtersShouldApplyToPersistedLogsAfterRestart() {
        Path logsPath = tempDir.resolve("filtered-logs.ndjson");
        AuditService firstService = new AuditService(new LogStore(logsPath));
        firstService.log(42, "alice", Role.USER, ActionType.ENCRYPT_FILE,
                "/tmp/report.pdf", OperationResult.SUCCESS, "ok");
        firstService.log(43, "bob", Role.ADMIN, ActionType.CREATE_ACCOUNT,
                null, OperationResult.SUCCESS, "ok");

        LogFilter filter = new LogFilter();
        filter.setUsername("alice");
        filter.setActionType(ActionType.ENCRYPT_FILE);
        filter.setFileName("C:\\Users\\alice\\report.pdf");

        AuditService restartedService = new AuditService(new LogStore(logsPath));
        List<LogDTO> logs = restartedService.getLogs(filter);

        assertEquals(1, logs.size());
        assertEquals("alice", logs.getFirst().getUsername());
        assertEquals("report.pdf", logs.getFirst().getFileName());
    }

    @Test
    void logShouldPersistOnlySanitizedSingleLineMessage() throws Exception {
        Path logsPath = tempDir.resolve("safe-message.ndjson");
        AuditService auditService = new AuditService(new LogStore(logsPath));

        auditService.log(42, "alice", Role.USER, ActionType.ENCRYPT_FILE,
                "C:\\Users\\alice\\report.pdf", OperationResult.ERROR,
                "password=\"very secret\" failed at C:\\Users\\alice\\report.pdf"
                        + System.lineSeparator()
                        + "java.lang.IllegalStateException: internal detail");

        String persisted = Files.readString(logsPath);
        assertFalse(persisted.contains("very secret"));
        assertFalse(persisted.contains("C:\\Users\\alice"));
        assertFalse(persisted.contains("IllegalStateException"));
    }

    private AuditService newAuditService() {
        return new AuditService(new LogStore(tempDir.resolve("logs.ndjson")));
    }
}

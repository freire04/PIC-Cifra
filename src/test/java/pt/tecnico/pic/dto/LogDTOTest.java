package pt.tecnico.pic.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import pt.tecnico.pic.domain.ActionType;
import pt.tecnico.pic.domain.Log;
import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.domain.Role;

class LogDTOTest {
    @Test
    void shouldExposeOnlyFileNameWhenBuiltWithWindowsPath() {
        LogDTO log = new LogDTO(1, LocalDateTime.now(), "alice", Role.USER, ActionType.ENCRYPT_FILE,
                "C:\\Users\\alice\\documents\\documento.pdf", OperationResult.SUCCESS, "ok");

        assertEquals("documento.pdf", log.getFileName());
        assertFalse(log.getFileName().contains("\\"));
        assertFalse(log.getFileName().contains("/"));
    }

    @Test
    void shouldExposeOnlyFileNameWhenBuiltWithUnixPath() {
        LogDTO log = new LogDTO(1, LocalDateTime.now(), "alice", Role.USER, ActionType.DECRYPT_FILE,
                "/home/alice/documents/documento.pdf", OperationResult.SUCCESS, "ok");

        assertEquals("documento.pdf", log.getFileName());
    }

    @Test
    void shouldAllowMissingFileNameForActionsWithoutFiles() {
        LogDTO log = new LogDTO(1, LocalDateTime.now(), "alice", Role.ADMIN, ActionType.LOGIN,
                null, OperationResult.SUCCESS, "ok");

        assertNull(log.getFileName());
    }

    @Test
    void shouldBuildFromLogWithStoredActorRole() {
        Log domainLog = new Log(
                7,
                42,
                LocalDateTime.now(),
                "alice",
                Role.AUDITOR,
                ActionType.VIEW_LOGS,
                "audit.log",
                OperationResult.SUCCESS,
                "ok"
        );

        LogDTO log = LogDTO.fromLog(domainLog);

        assertEquals(7, log.getLogId());
        assertEquals(Role.AUDITOR, log.getActorRole());
        assertEquals("audit.log", log.getFileName());
    }
}

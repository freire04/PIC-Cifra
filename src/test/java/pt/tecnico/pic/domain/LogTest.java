package pt.tecnico.pic.domain;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class LogTest {
    @Test
    void constructorShouldInitializeLog() {
        LocalDateTime timestamp = LocalDateTime.of(2026, 5, 6, 14, 32, 11);

        Log log = new Log(
                102,
                timestamp,
                1,
                "jdoe",
                Role.ADMIN,
                ActionType.ENCRYPT_FILE,
                "ficheiro.pdf",
                OperationResult.SUCCESS,
                "File encrypted successfully"
        );

        assertEquals(102, log.getLogId());
        assertEquals(timestamp, log.getTimestamp());
        assertEquals(1, log.getAccountId());
        assertEquals("jdoe", log.getUsername());
        assertEquals(Role.ADMIN, log.getRole());
        assertEquals(ActionType.ENCRYPT_FILE, log.getAction());
        assertEquals("ficheiro.pdf", log.getFileName());
        assertEquals(OperationResult.SUCCESS, log.getResult());
        assertEquals("File encrypted successfully", log.getMessage());
    }   
}

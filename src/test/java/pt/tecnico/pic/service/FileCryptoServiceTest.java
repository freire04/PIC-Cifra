package pt.tecnico.pic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import pt.tecnico.pic.domain.ActionType;
import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.domain.Role;
import pt.tecnico.pic.domain.UserContext;
import pt.tecnico.pic.dto.CryptoResult;
import pt.tecnico.pic.dto.LogDTO;

class FileCryptoServiceTest {
    @Test
    void encryptFileShouldCreateAuditLogWithSanitizedFileName() {
        AuditService auditService = new AuditService();
        FileCryptoService fileCryptoService = new FileCryptoService(auditService);
        UserContext userContext = new UserContext(42, "alice", Role.USER);

        CryptoResult result = fileCryptoService.encryptFile(userContext,
                "C:\\Users\\alice\\documents\\documento.pdf", "C:\\Users\\alice\\documents\\documento.cif");

        LogDTO log = auditService.getLogs().getFirst();

        assertEquals(OperationResult.SUCCESS, result.getResult());
        assertEquals(ActionType.ENCRYPT_FILE, log.getActionType());
        assertEquals(Role.USER, log.getActorRole());
        assertEquals("documento.pdf", log.getFileName());
    }
}

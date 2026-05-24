package pt.tecnico.pic.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import pt.tecnico.pic.domain.ActionType;
import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.domain.Role;
import pt.tecnico.pic.dto.LogDTO;
import pt.tecnico.pic.dto.LoginResult;
import pt.tecnico.pic.dto.RoleSelectionResult;
import pt.tecnico.pic.service.AccountService;
import pt.tecnico.pic.service.AuditService;
import pt.tecnico.pic.service.FileCryptoService;

class AppControllerTest {

    @Test
    void recordLoginShouldCreateAuditLogWithoutActorRole() {
        AuditService auditService = new AuditService();
        AppController appController = new AppController(new AccountService(), auditService);

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
        AppController appController = new AppController(new AccountService(), auditService, fileCryptoService);

        assertSame(auditService, appController.getAuditService());
        assertSame(fileCryptoService, appController.getFileCryptoService());
    }

    @Test
    void constructorShouldRejectFileCryptoServiceWithDifferentAuditService() {
        AuditService appAuditService = new AuditService();
        AuditService fileAuditService = new AuditService();
        AccountService accountService = new AccountService();
        FileCryptoService fileCryptoService = new FileCryptoService(fileAuditService);

        assertThrows(
                IllegalArgumentException.class,
                () -> new AppController(accountService, appAuditService, fileCryptoService)
        );
    }

    @Test
    void loginShouldSucceedWithValidCredentialsPlaceholder() {
        AppController appController = new AppController();

        LoginResult result = appController.login("abc", "123".toCharArray());

        assertEquals(OperationResult.SUCCESS, result.getResult());
        assertEquals("Login successful.", result.getMessage());
        assertEquals("abc", result.getUsername());
        assertFalse(result.mustChangePassword());
    }

    @Test
    void loginShouldRequirePasswordChangePlaceholder() {
        AppController appController = new AppController();

        LoginResult result = appController.login("teste", "123".toCharArray());

        assertEquals(OperationResult.SUCCESS, result.getResult());
        assertEquals("Login successful.", result.getMessage());
        assertEquals("teste", result.getUsername());
        assertTrue(result.mustChangePassword());
    }

    @Test
    void loginShouldFailWithInvalidCredentialsPlaceholder() {
        AppController appController = new AppController();

        LoginResult result = appController.login("abc", "wrong".toCharArray());

        assertEquals(OperationResult.ERROR, result.getResult());
        assertEquals("Login failed.", result.getMessage());
        assertFalse(result.mustChangePassword());
    }

    @Test
    void selectRoleShouldSucceedPlaceholder() {
        AppController appController = new AppController();

        RoleSelectionResult result =
                appController.selectRole(Role.ADMIN, null);

        assertEquals(OperationResult.SUCCESS, result.getResult());
        assertEquals(Role.ADMIN, result.getSelectedRole());
    }
}
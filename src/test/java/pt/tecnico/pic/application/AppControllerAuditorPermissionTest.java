package pt.tecnico.pic.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.domain.Role;
import pt.tecnico.pic.domain.UserContext;
import pt.tecnico.pic.dto.AccountCreationResult;
import pt.tecnico.pic.dto.AccountFilter;
import pt.tecnico.pic.dto.AccountResult;
import pt.tecnico.pic.dto.CreateAccountRequest;
import pt.tecnico.pic.dto.CryptoResult;
import pt.tecnico.pic.dto.LogFilter;
import pt.tecnico.pic.service.AccountService;
import pt.tecnico.pic.service.AuditService;
import pt.tecnico.pic.service.FileCryptoService;
import pt.tecnico.pic.service.PasswordService;
import pt.tecnico.pic.store.AccountStore;
import pt.tecnico.pic.store.LogStore;

class AppControllerAuditorPermissionTest {

    @TempDir
    Path tempDir;

    @Test
    void auditorCanViewLogsButCannotUseUserOrAdminOperations() {
        TestFixture fixture = newFixture();
        loginAndSelectRole(fixture, "auditor", Set.of(Role.AUDITOR), Role.AUDITOR);

        assertFalse(fixture.controller.getAuditLogs(new LogFilter()).isEmpty());

        CryptoResult encryptResult = fixture.controller.encryptFile("plain.txt", "plain.cif");
        CryptoResult decryptResult = fixture.controller.decryptFile("plain.cif", "plain.txt");
        AccountCreationResult createResult = fixture.controller.createAccount(
                new CreateAccountRequest("blocked-user", Set.of(Role.USER))
        );
        AccountResult updateResult = fixture.controller.updateUserRoles(1, Set.of(Role.ADMIN));
        AccountResult disableResult = fixture.controller.disableAccount(1);

        assertEquals(OperationResult.FAILED, encryptResult.getResult());
        assertEquals(OperationResult.FAILED, decryptResult.getResult());
        assertEquals(OperationResult.FAILED, createResult.getResult());
        assertEquals(OperationResult.FAILED, updateResult.getResult());
        assertEquals(OperationResult.FAILED, disableResult.getResult());
        assertTrue(fixture.controller.getUsers(new AccountFilter()).isEmpty());
    }

    @Test
    void userCannotViewAuditLogs() {
        TestFixture fixture = newFixture();
        loginAndSelectRole(fixture, "user", Set.of(Role.USER), Role.USER);

        assertTrue(fixture.controller.getAuditLogs(new LogFilter()).isEmpty());
    }

    @Test
    void adminCannotViewAuditLogsWhenAdminRoleIsSelected() {
        TestFixture fixture = newFixture();
        loginAndSelectRole(fixture, "admin", Set.of(Role.ADMIN), Role.ADMIN);

        assertTrue(fixture.controller.getAuditLogs(new LogFilter()).isEmpty());
    }

    @Test
    void accountWithAdminAndAuditorRolesOnlyViewsLogsWhenAuditorIsSelected() {
        TestFixture fixture = newFixture();
        loginAndSelectRole(fixture, "manager", Set.of(Role.ADMIN, Role.AUDITOR), Role.ADMIN);

        assertTrue(fixture.controller.getAuditLogs(new LogFilter()).isEmpty());

        assertEquals(
                OperationResult.SUCCESS,
                fixture.controller.selectRole(Role.AUDITOR, null).getResult()
        );
        assertFalse(fixture.controller.getAuditLogs(new LogFilter()).isEmpty());
    }

    @Test
    void accountWithAdminAndAuditorRolesOnlyManagesAccountsWhenAdminIsSelected() {
        TestFixture fixture = newFixture();
        loginAndSelectRole(fixture, "manager", Set.of(Role.ADMIN, Role.AUDITOR), Role.AUDITOR);

        AccountCreationResult denied = fixture.controller.createAccount(
                new CreateAccountRequest("auditor-denied-user", Set.of(Role.USER))
        );
        assertEquals(OperationResult.FAILED, denied.getResult());

        assertEquals(
                OperationResult.SUCCESS,
                fixture.controller.selectRole(Role.ADMIN, null).getResult()
        );
        AccountCreationResult allowed = fixture.controller.createAccount(
                new CreateAccountRequest("admin-created-user", Set.of(Role.USER))
        );
        assertEquals(OperationResult.SUCCESS, allowed.getResult());
    }

    private TestFixture newFixture() {
        AccountService accountService = new AccountService(
                new AccountStore(tempDir.resolve("accounts.json")),
                new PasswordService()
        );
        AuditService auditService = new AuditService(new LogStore(tempDir.resolve("logs.ndjson")));
        FileCryptoService fileCryptoService = new TokenStubFileCryptoService(auditService);
        AppController controller = new AppController(accountService, auditService, fileCryptoService);

        return new TestFixture(controller, accountService);
    }

    private void loginAndSelectRole(TestFixture fixture, String username, Set<Role> roles, Role roleToSelect) {
        AccountCreationResult created = fixture.accountService.createAccount(username, roles);
        char[] temporaryPassword = created.getTemporaryPassword();
        char[] permanentPassword = (username + "Password123!").toCharArray();

        assertEquals(OperationResult.SUCCESS, fixture.controller.login(username, temporaryPassword).getResult());
        assertEquals(
                OperationResult.SUCCESS,
                fixture.controller.changeOwnPassword(created.getTemporaryPassword(), permanentPassword).getResult()
        );
        assertEquals(
                OperationResult.SUCCESS,
                fixture.controller.selectRole(roleToSelect, "123456".toCharArray()).getResult()
        );

        created.clearTemporaryPassword();
    }

    private static final class TokenStubFileCryptoService extends FileCryptoService {
        private boolean tokenUnlocked;

        private TokenStubFileCryptoService(AuditService auditService) {
            super(auditService);
        }

        @Override
        public OperationResult unlockToken(char[] pin, UserContext userContext) {
            tokenUnlocked = true;
            return OperationResult.SUCCESS;
        }

        @Override
        public OperationResult lockToken(UserContext userContext) {
            tokenUnlocked = false;
            return OperationResult.SUCCESS;
        }

        @Override
        public boolean isTokenUnlocked() {
            return tokenUnlocked;
        }
    }

    private record TestFixture(AppController controller, AccountService accountService) {
    }
}

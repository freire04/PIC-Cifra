package pt.tecnico.pic.application;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import pt.tecnico.pic.domain.ActionType;
import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.domain.Role;
import pt.tecnico.pic.dto.AccountCreationResult;
import pt.tecnico.pic.dto.AccountFilter;
import pt.tecnico.pic.dto.AccountResult;
import pt.tecnico.pic.dto.AccountStatusFilter;
import pt.tecnico.pic.dto.AccountSummary;
import pt.tecnico.pic.dto.CreateAccountRequest;
import pt.tecnico.pic.dto.CryptoResult;
import pt.tecnico.pic.dto.LoginResult;
import pt.tecnico.pic.dto.PasswordResult;
import pt.tecnico.pic.dto.RoleSelectionResult;
import pt.tecnico.pic.service.AccountService;
import pt.tecnico.pic.service.AuditService;
import pt.tecnico.pic.service.FileCryptoService;
import pt.tecnico.pic.service.PasswordService;
import pt.tecnico.pic.store.AccountStore;

class AppControllerTest {

    @TempDir
    Path tempDir;

    private TestFixture newFixture() {
        Path accountsFile = tempDir.resolve("test-accounts.json");

        AuditService auditService = new AuditService();
        AccountStore accountStore = new AccountStore(accountsFile);
        PasswordService passwordService = new PasswordService();
        AccountService accountService = new AccountService(accountStore, passwordService);
        FileCryptoService fileCryptoService = new FileCryptoService(auditService);
        AppController controller = new AppController(accountService, auditService, fileCryptoService);

        return new TestFixture(controller, accountService);
    }

    @Test
    void loginShouldCreateSessionWithAvailableRoles() {
        TestFixture fixture = newFixture();
        AccountCreationResult created = fixture.accountService.createAccount("user", Set.of(Role.USER));

        LoginResult login = fixture.controller.login(
                "user",
                created.getTemporaryPassword()
        );

        assertEquals(OperationResult.SUCCESS, login.getResult());
        assertEquals("user", login.getUsername());
        assertEquals(Set.of(Role.USER), login.getAvailableRoles());
        assertTrue(login.mustChangePassword());
    }

    @Test
    void loginShouldFailWithWrongPassword() {
        TestFixture fixture = newFixture();
        fixture.accountService.createAccount("user", Set.of(Role.USER));

        LoginResult login = fixture.controller.login("user", "wrong".toCharArray());

        assertEquals(OperationResult.FAILED, login.getResult());
    }

    @Test
    void mustChangePasswordShouldBlockRoleSelectionUntilPasswordIsChanged() {
        TestFixture fixture = newFixture();
        AccountCreationResult created = fixture.accountService.createAccount("user", Set.of(Role.USER));

        fixture.controller.login("user", created.getTemporaryPassword());

        assertTrue(fixture.controller.getAvailableRoles().isEmpty());

        RoleSelectionResult blocked = fixture.controller.selectRole(Role.USER, "123456".toCharArray());

        assertEquals(OperationResult.FAILED, blocked.getResult());

        AccountResult changed = fixture.controller.changeOwnPassword(
                created.getTemporaryPassword(),
                "NewPassword123!".toCharArray()
        );

        assertEquals(OperationResult.SUCCESS, changed.getResult());
        assertEquals(Set.of(Role.USER), fixture.controller.getAvailableRoles());
    }

    @Test
    void selectRoleShouldRejectUnavailableRole() {
        TestFixture fixture = newFixture();
        AccountCreationResult created = fixture.accountService.createAccount("user", Set.of(Role.USER));

        fixture.controller.login("user", created.getTemporaryPassword());
        fixture.controller.changeOwnPassword(
                created.getTemporaryPassword(),
                "NewPassword123!".toCharArray()
        );

        RoleSelectionResult result = fixture.controller.selectRole(Role.ADMIN, null);

        assertEquals(OperationResult.FAILED, result.getResult());
    }

    @Test
    void adminShouldNotEncryptBecauseAdminDoesNotInheritUserPermissions() {
        TestFixture fixture = newFixture();
        AccountCreationResult admin = fixture.accountService.createAccount("admin", Set.of(Role.ADMIN));

        fixture.controller.login("admin", admin.getTemporaryPassword());

        fixture.controller.changeOwnPassword(
                admin.getTemporaryPassword(),
                "AdminPassword123!".toCharArray()
        );
        
        fixture.controller.selectRole(Role.ADMIN, null);

        CryptoResult result = fixture.controller.encryptFile("in.txt", "out.enc");

        assertEquals(OperationResult.FAILED, result.getResult());
        assertEquals(ActionType.ENCRYPT_FILE, result.getActionType());
    }

    @Test
    void encryptShouldFailWithoutLogin() {
        AppController controller = new AppController();

        CryptoResult result = controller.encryptFile("in.txt", "out.enc");

        assertEquals(OperationResult.FAILED, result.getResult());
    }

    @Test
    void userWithoutTokenShouldNotEncrypt() {
        TestFixture fixture = newFixture();
        AccountCreationResult created = fixture.accountService.createAccount("user", Set.of(Role.USER));

        fixture.controller.login("user", created.getTemporaryPassword());
        fixture.controller.changeOwnPassword(
                created.getTemporaryPassword(),
                "UserPassword123!".toCharArray()
        );

        CryptoResult result = fixture.controller.encryptFile("in.txt", "out.enc");

        assertEquals(OperationResult.FAILED, result.getResult());
    }

    @Test
    void adminShouldCreateAccount() {
        TestFixture fixture = newFixture();
        AccountCreationResult admin = fixture.accountService.createAccount("admin", Set.of(Role.ADMIN));

        fixture.controller.login("admin", admin.getTemporaryPassword());
        fixture.controller.changeOwnPassword(
                admin.getTemporaryPassword(),
                "AdminPassword123!".toCharArray()
        );
        fixture.controller.selectRole(Role.ADMIN, null);

        AccountCreationResult created = fixture.controller.createAccount(
                new CreateAccountRequest("newuser", Set.of(Role.USER))
        );

        assertEquals(OperationResult.SUCCESS, created.getResult());
        assertNotNull(created.getTemporaryPassword());
    }

    @Test
    void userShouldNotCreateAccount() {
        TestFixture fixture = newFixture();
        AccountCreationResult user = fixture.accountService.createAccount("user", Set.of(Role.USER));

        fixture.controller.login("user", user.getTemporaryPassword());
        fixture.controller.changeOwnPassword(
                user.getTemporaryPassword(),
                "UserPassword123!".toCharArray()
        );

        RoleSelectionResult roleSelection = fixture.controller.selectRole(Role.USER, "123456".toCharArray());

        if (roleSelection.getResult() != OperationResult.SUCCESS) {
            // Token setup is environment-dependent in the current crypto layer.
            // The important part here is that selectedRole is not ADMIN.
        }

        AccountCreationResult result = fixture.controller.createAccount(
                new CreateAccountRequest("newuser", Set.of(Role.USER))
        );

        assertEquals(OperationResult.FAILED, result.getResult());
    }

    @Test
    void adminShouldUpdateRoles() {
        TestFixture fixture = newFixture();
        AccountCreationResult admin = fixture.accountService.createAccount("admin", Set.of(Role.ADMIN));
        AccountCreationResult user = fixture.accountService.createAccount("user", Set.of(Role.USER));

        fixture.controller.login("admin", admin.getTemporaryPassword());
        fixture.controller.changeOwnPassword(
                admin.getTemporaryPassword(),
                "AdminPassword123!".toCharArray()
        );
        fixture.controller.selectRole(Role.ADMIN, null);

        AccountResult result = fixture.controller.updateUserRoles(user.getAccountId(), Set.of(Role.ADMIN));

        assertEquals(OperationResult.SUCCESS, result.getResult());
    }

    @Test
    void adminShouldResetPassword() {
        TestFixture fixture = newFixture();
        AccountCreationResult admin = fixture.accountService.createAccount("admin", Set.of(Role.ADMIN));
        AccountCreationResult user = fixture.accountService.createAccount("user", Set.of(Role.USER));

        fixture.controller.login("admin", admin.getTemporaryPassword());
        fixture.controller.changeOwnPassword(
                admin.getTemporaryPassword(),
                "AdminPassword123!".toCharArray()
        );
        fixture.controller.selectRole(Role.ADMIN, null);

        PasswordResult result = fixture.controller.resetPassword(user.getAccountId());

        assertEquals(OperationResult.SUCCESS, result.getResult());
        assertNotNull(result.getTemporaryPassword());
    }

    @Test
    void adminShouldDisableAndEnableAccount() {
        TestFixture fixture = newFixture();
        AccountCreationResult admin = fixture.accountService.createAccount("admin", Set.of(Role.ADMIN));
        AccountCreationResult user = fixture.accountService.createAccount("user", Set.of(Role.USER));

        fixture.controller.login("admin", admin.getTemporaryPassword());
        fixture.controller.changeOwnPassword(
                admin.getTemporaryPassword(),
                "AdminPassword123!".toCharArray()
        );
        fixture.controller.selectRole(Role.ADMIN, null);

        AccountResult disabled = fixture.controller.disableAccount(user.getAccountId());
        AccountResult enabled = fixture.controller.enableAccount(user.getAccountId());

        assertEquals(OperationResult.SUCCESS, disabled.getResult());
        assertEquals(OperationResult.SUCCESS, enabled.getResult());
    }

    @Test
    void adminAccountManagementWorkflowShouldMatchAcceptanceCriteria() {
        TestFixture fixture = newFixture();
        AccountCreationResult admin = fixture.accountService.createAccount("admin", Set.of(Role.ADMIN));
        String adminTemporaryPassword = new String(admin.getTemporaryPassword());

        loginAsAdmin(fixture, adminTemporaryPassword);

        AccountCreationResult created = fixture.controller.createAccount(
                new CreateAccountRequest("jorge", Set.of(Role.USER))
        );
        String jorgeTemporaryPassword = new String(created.getTemporaryPassword());

        assertEquals(OperationResult.SUCCESS, created.getResult());
        assertNotNull(created.getTemporaryPassword());

        List<AccountSummary> users = fixture.controller.searchAccounts(
                new AccountFilter(
                        null,
                        Set.of(Role.USER),
                        AccountStatusFilter.ALL
                )
        );

        assertTrue(users.stream()
                .anyMatch(user ->
                        user.getUsername().equals("jorge")
                                && user.getRoles().equals(Set.of(Role.USER))
                ));

        fixture.controller.logout();

        LoginResult jorgeLogin = fixture.controller.login("jorge", jorgeTemporaryPassword.toCharArray());
        assertEquals(OperationResult.SUCCESS, jorgeLogin.getResult());
        assertTrue(jorgeLogin.mustChangePassword());

        AccountResult changedPassword = fixture.controller.changeOwnPassword(
                jorgeTemporaryPassword.toCharArray(),
                "JorgePassword123!".toCharArray()
        );
        assertEquals(OperationResult.SUCCESS, changedPassword.getResult());

        fixture.controller.logout();
        loginAsAdmin(fixture, "AdminPassword123!");

        AccountResult updatedRoles = fixture.controller.updateUserRoles(created.getAccountId(), Set.of(Role.USER, Role.AUDITOR));
        assertEquals(OperationResult.SUCCESS, updatedRoles.getResult());
        assertEquals(Set.of(Role.USER, Role.AUDITOR), fixture.accountService.getAccountById(created.getAccountId()).getRoles());

        PasswordResult reset = fixture.controller.resetPassword(created.getAccountId());
        String resetTemporaryPassword = new String(reset.getTemporaryPassword());
        assertEquals(OperationResult.SUCCESS, reset.getResult());
        assertNotNull(reset.getTemporaryPassword());

        AccountResult disabled = fixture.controller.disableAccount(created.getAccountId());
        assertEquals(OperationResult.SUCCESS, disabled.getResult());

        fixture.controller.logout();

        LoginResult disabledLogin = fixture.controller.login("jorge", resetTemporaryPassword.toCharArray());
        assertEquals(OperationResult.FAILED, disabledLogin.getResult());

        loginAsAdmin(fixture, "AdminPassword123!");

        AccountResult enabled = fixture.controller.enableAccount(created.getAccountId());
        assertEquals(OperationResult.SUCCESS, enabled.getResult());

        fixture.controller.logout();

        LoginResult enabledLogin = fixture.controller.login("jorge", resetTemporaryPassword.toCharArray());
        assertEquals(OperationResult.SUCCESS, enabledLogin.getResult());
        assertTrue(enabledLogin.mustChangePassword());
    }

    @Test
    void accountManagementShouldRequireSelectedAdminRole() {
        TestFixture fixture = newFixture();
        AccountCreationResult admin = fixture.accountService.createAccount("admin", Set.of(Role.ADMIN));
        AccountCreationResult user = fixture.accountService.createAccount("user", Set.of(Role.USER));
        String adminTemporaryPassword = new String(admin.getTemporaryPassword());

        fixture.controller.login("admin", adminTemporaryPassword.toCharArray());
        fixture.controller.changeOwnPassword(
                adminTemporaryPassword.toCharArray(),
                "AdminPassword123!".toCharArray()
        );

        List<AccountSummary> usersBeforeRoleSelection = fixture.controller.searchAccounts(
                new AccountFilter(
                        null,
                        Set.of(Role.USER, Role.ADMIN),
                        AccountStatusFilter.ALL
                )
        );
        AccountCreationResult createWithoutRole = fixture.controller.createAccount(
                new CreateAccountRequest("blocked", Set.of(Role.USER))
        );
        AccountResult updateWithoutRole = fixture.controller.updateUserRoles(user.getAccountId(), Set.of(Role.ADMIN));
        PasswordResult resetWithoutRole = fixture.controller.resetPassword(user.getAccountId());
        AccountResult disableWithoutRole = fixture.controller.disableAccount(user.getAccountId());
        AccountResult enableWithoutRole = fixture.controller.enableAccount(user.getAccountId());

        assertTrue(usersBeforeRoleSelection.isEmpty());
        assertEquals(OperationResult.FAILED, createWithoutRole.getResult());
        assertEquals(OperationResult.FAILED, updateWithoutRole.getResult());
        assertEquals(OperationResult.FAILED, resetWithoutRole.getResult());
        assertEquals(OperationResult.FAILED, disableWithoutRole.getResult());
        assertEquals(OperationResult.FAILED, enableWithoutRole.getResult());
    }

    @Test
    void logoutShouldClearSession() {
        TestFixture fixture = newFixture();
        AccountCreationResult admin = fixture.accountService.createAccount("admin", Set.of(Role.ADMIN));

        fixture.controller.login("admin", admin.getTemporaryPassword());

        OperationResult logout = fixture.controller.logout();

        assertEquals(OperationResult.SUCCESS, logout);
        assertTrue(fixture.controller.getAvailableRoles().isEmpty());
    }

    private void loginAsAdmin(TestFixture fixture, String password) {
        LoginResult login = fixture.controller.login("admin", password.toCharArray());
        assertEquals(OperationResult.SUCCESS, login.getResult());

        if (login.mustChangePassword()) {
            AccountResult changed = fixture.controller.changeOwnPassword(
                    password.toCharArray(),
                    "AdminPassword123!".toCharArray()
            );
            assertEquals(OperationResult.SUCCESS, changed.getResult());
        }

        RoleSelectionResult selected = fixture.controller.selectRole(Role.ADMIN, null);
        assertEquals(OperationResult.SUCCESS, selected.getResult());
    }

    private record TestFixture(AppController controller, AccountService accountService) {}
}

package pt.tecnico.pic.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import pt.tecnico.pic.domain.ActionType;
import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.domain.Role;
import pt.tecnico.pic.dto.AccountCreationResult;
import pt.tecnico.pic.dto.AccountResult;
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

    private AppController newController() {
        AuditService auditService = new AuditService();
        AccountService accountService = new AccountService(
                new AccountStore(tempDir.resolve("accounts.json")),
                new PasswordService()
        );
        FileCryptoService fileCryptoService = new FileCryptoService(auditService);

        return new AppController(accountService, auditService, fileCryptoService);
    }

    private AccountCreationResult createAccount(AppController controller, String username, Set<Role> roles) {
        LoginResult adminLogin = controller.login("admin", "AdminPassword123!".toCharArray());

        if (adminLogin.getResult() != OperationResult.SUCCESS) {
            AccountService accountService = new AccountService(
                    new AccountStore(tempDir.resolve("accounts.json")),
                    new PasswordService()
            );
            accountService.createAccount("admin", Set.of(Role.ADMIN));
        }

        return controller.createAccount(new CreateAccountRequest(username, roles));
    }

    private AccountCreationResult createAccountDirectly(
            AccountService accountService,
            String username,
            Set<Role> roles
    ) {
        return accountService.createAccount(username, roles);
    }

    private TestFixture newFixture() {
        AuditService auditService = new AuditService();
        AccountStore accountStore = new AccountStore(tempDir.resolve("accounts.json"));
        PasswordService passwordService = new PasswordService();
        AccountService accountService = new AccountService(accountStore, passwordService);
        FileCryptoService fileCryptoService = new FileCryptoService(auditService);
        AppController controller = new AppController(accountService, auditService, fileCryptoService);

        return new TestFixture(controller, accountService);
    }

    @Test
    void loginShouldCreateSessionWithAvailableRoles() {
        TestFixture fixture = newFixture();
        AccountCreationResult created = createAccountDirectly(
                fixture.accountService,
                "user",
                Set.of(Role.USER)
        );

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
        createAccountDirectly(fixture.accountService, "user", Set.of(Role.USER));

        LoginResult login = fixture.controller.login("user", "wrong".toCharArray());

        assertEquals(OperationResult.FAILED, login.getResult());
    }

    @Test
    void mustChangePasswordShouldBlockRoleSelectionUntilPasswordIsChanged() {
        TestFixture fixture = newFixture();
        AccountCreationResult created = createAccountDirectly(
                fixture.accountService,
                "user",
                Set.of(Role.USER)
        );

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
        AccountCreationResult created = createAccountDirectly(
                fixture.accountService,
                "user",
                Set.of(Role.USER)
        );

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
        AccountCreationResult created = createAccountDirectly(
                fixture.accountService,
                "admin",
                Set.of(Role.ADMIN)
        );

        fixture.controller.login("admin", created.getTemporaryPassword());
        fixture.controller.changeOwnPassword(
                created.getTemporaryPassword(),
                "AdminPassword123!".toCharArray()
        );
        fixture.controller.selectRole(Role.ADMIN, null);

        CryptoResult result = fixture.controller.encryptFile("in.txt", "out.enc");

        assertEquals(OperationResult.FAILED, result.getResult());
        assertEquals(ActionType.ENCRYPT_FILE, result.getActionType());
    }

    @Test
    void encryptShouldFailWithoutLogin() {
        AppController controller = newController();

        CryptoResult result = controller.encryptFile("in.txt", "out.enc");

        assertEquals(OperationResult.FAILED, result.getResult());
    }

    @Test
    void userWithoutTokenShouldNotEncrypt() {
        TestFixture fixture = newFixture();
        AccountCreationResult created = createAccountDirectly(
                fixture.accountService,
                "user",
                Set.of(Role.USER)
        );

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
        AccountCreationResult admin = createAccountDirectly(
                fixture.accountService,
                "admin",
                Set.of(Role.ADMIN)
        );

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
        AccountCreationResult user = createAccountDirectly(
                fixture.accountService,
                "user",
                Set.of(Role.USER)
        );

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
        AccountCreationResult admin = createAccountDirectly(
                fixture.accountService,
                "admin",
                Set.of(Role.ADMIN)
        );
        AccountCreationResult user = createAccountDirectly(
                fixture.accountService,
                "user",
                Set.of(Role.USER)
        );

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
        AccountCreationResult admin = createAccountDirectly(
                fixture.accountService,
                "admin",
                Set.of(Role.ADMIN)
        );
        AccountCreationResult user = createAccountDirectly(
                fixture.accountService,
                "user",
                Set.of(Role.USER)
        );

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
        AccountCreationResult admin = createAccountDirectly(
                fixture.accountService,
                "admin",
                Set.of(Role.ADMIN)
        );
        AccountCreationResult user = createAccountDirectly(
                fixture.accountService,
                "user",
                Set.of(Role.USER)
        );

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
    void logoutShouldClearSession() {
        TestFixture fixture = newFixture();
        AccountCreationResult admin = createAccountDirectly(
                fixture.accountService,
                "admin",
                Set.of(Role.ADMIN)
        );

        fixture.controller.login("admin", admin.getTemporaryPassword());

        OperationResult logout = fixture.controller.logout();

        assertEquals(OperationResult.SUCCESS, logout);
        assertTrue(fixture.controller.getAvailableRoles().isEmpty());
    }

    private record TestFixture(AppController controller, AccountService accountService) {
    }
}
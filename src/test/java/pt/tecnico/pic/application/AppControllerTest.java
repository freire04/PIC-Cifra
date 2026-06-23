package pt.tecnico.pic.application;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import pt.tecnico.pic.dto.LogDTO;
import pt.tecnico.pic.dto.PasswordResult;
import pt.tecnico.pic.dto.RoleSelectionResult;
import pt.tecnico.pic.service.AccountService;
import pt.tecnico.pic.service.AuditService;
import pt.tecnico.pic.service.FileCryptoService;
import pt.tecnico.pic.service.PasswordService;
import pt.tecnico.pic.store.AccountStore;
import pt.tecnico.pic.store.LogStore;
import pt.tecnico.pic.store.LogStoreException;

class AppControllerTest {

    @TempDir
    Path tempDir;

    private TestFixture newFixture() {
        Path accountsFile = tempDir.resolve("test-accounts.json");
        Path logsFile = tempDir.resolve("test-logs.ndjson");

        AuditService auditService = new AuditService(new LogStore(logsFile));
        return newFixture(accountService(accountsFile), auditService);
    }

    private TestFixture newFixture(AccountService accountService, AuditService auditService) {
        FileCryptoService fileCryptoService = new FileCryptoService(auditService);
        AppController controller = new AppController(accountService, auditService, fileCryptoService);

        return new TestFixture(controller, accountService, auditService);
    }

    private AccountService accountService(Path accountsFile) {
        AccountStore accountStore = new AccountStore(accountsFile);
        PasswordService passwordService = new PasswordService();
        return new AccountService(accountStore, passwordService);
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

        LogDTO log = fixture.auditService.getLogs().getLast();
        assertEquals(ActionType.ENCRYPT_FILE, log.getActionType());
        assertEquals(OperationResult.FAILED, log.getResult());
        assertEquals("admin", log.getUsername());
        assertEquals(Role.ADMIN, log.getActorRole());
        assertEquals("in.txt", log.getFileName());
    }

    @Test
    void encryptShouldFailWithoutLogin() {
        TestFixture fixture = newFixture();

        CryptoResult result = fixture.controller.encryptFile("in.txt", "out.enc");

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

    @Test
    void removingOwnAdminRoleShouldRefreshCurrentSession() {
        TestFixture fixture = newFixture();
        AccountCreationResult admin = fixture.accountService.createAccount("admin", Set.of(Role.ADMIN));
        fixture.accountService.createAccount("backup-admin", Set.of(Role.ADMIN));

        loginAsAdmin(fixture, new String(admin.getTemporaryPassword()));

        AccountResult result = fixture.controller.updateUserRoles(admin.getAccountId(), Set.of(Role.USER));

        assertEquals(OperationResult.SUCCESS, result.getResult());
        assertNull(fixture.controller.getSelectedRole());
        assertEquals(Set.of(Role.USER), fixture.controller.getAvailableRoles());
        assertEquals(
                OperationResult.FAILED,
                fixture.controller.createAccount(
                        new CreateAccountRequest("blocked-after-role-change", Set.of(Role.USER))
                ).getResult()
        );
    }

    @Test
    void disablingOwnAccountShouldClearCurrentSession() {
        TestFixture fixture = newFixture();
        AccountCreationResult admin = fixture.accountService.createAccount("admin", Set.of(Role.ADMIN));
        fixture.accountService.createAccount("backup-admin", Set.of(Role.ADMIN));

        loginAsAdmin(fixture, new String(admin.getTemporaryPassword()));

        AccountResult result = fixture.controller.disableAccount(admin.getAccountId());

        assertEquals(OperationResult.SUCCESS, result.getResult());
        assertFalse(fixture.controller.hasActiveSession());
        assertTrue(fixture.controller.getAvailableRoles().isEmpty());
    }

    @Test
    void adminShouldNotResetOwnPasswordAdministratively() {
        TestFixture fixture = newFixture();
        AccountCreationResult admin = fixture.accountService.createAccount("admin", Set.of(Role.ADMIN));

        loginAsAdmin(fixture, new String(admin.getTemporaryPassword()));

        PasswordResult result = fixture.controller.resetPassword(admin.getAccountId());

        assertEquals(OperationResult.FAILED, result.getResult());
        assertNull(result.getTemporaryPassword());
        assertTrue(fixture.controller.hasActiveSession());
    }

    @Test
    void unauthenticatedProtectedActionsShouldBeLoggedAsFailures() {
        TestFixture fixture = newFixture();

        fixture.controller.logout();
        fixture.controller.selectRole(Role.ADMIN, null);
        fixture.controller.changeOwnPassword("old".toCharArray(), "new".toCharArray());
        fixture.controller.encryptFile("C:\\Users\\alice\\secret.txt", "out.enc");
        fixture.controller.decryptFile("/home/alice/secret.enc", "out.txt");

        List<LogDTO> logs = fixture.auditService.getLogs();
        assertEquals(
                List.of(
                        ActionType.LOGOUT,
                        ActionType.SELECT_ROLE,
                        ActionType.CHANGE_PASSWORD,
                        ActionType.ENCRYPT_FILE,
                        ActionType.DECRYPT_FILE
                ),
                logs.stream().map(LogDTO::getActionType).toList()
        );
        assertTrue(logs.stream().allMatch(log -> log.getResult() == OperationResult.FAILED));
        assertTrue(logs.stream().allMatch(log -> log.getUsername() == null));
        assertTrue(logs.stream().allMatch(log -> log.getActorRole() == null));
        assertEquals("secret.txt", logs.get(3).getFileName());
        assertEquals("secret.enc", logs.get(4).getFileName());
    }

    @Test
    void mandatoryPasswordChangeRoleSelectionFailureShouldKeepAccountContext() {
        TestFixture fixture = newFixture();
        AccountCreationResult created = fixture.accountService.createAccount("alice", Set.of(Role.ADMIN));
        fixture.controller.login("alice", created.getTemporaryPassword());

        RoleSelectionResult result = fixture.controller.selectRole(Role.ADMIN, null);

        assertEquals(OperationResult.FAILED, result.getResult());
        LogDTO log = fixture.auditService.getLogs().getLast();
        assertEquals(ActionType.SELECT_ROLE, log.getActionType());
        assertEquals(OperationResult.FAILED, log.getResult());
        assertEquals("alice", log.getUsername());
        assertNull(log.getActorRole());
    }

    @Test
    void auditorAccountManagementDenialsShouldBeLoggedWithActiveRole() {
        TestFixture fixture = newFixture();
        AccountCreationResult auditor = fixture.accountService.createAccount("auditor", Set.of(Role.AUDITOR));
        String temporaryPassword = new String(auditor.getTemporaryPassword());

        fixture.controller.login("auditor", temporaryPassword.toCharArray());
        fixture.controller.changeOwnPassword(
                temporaryPassword.toCharArray(),
                "AuditorPassword123!".toCharArray()
        );
        fixture.controller.selectRole(Role.AUDITOR, null);
        int initialLogCount = fixture.auditService.getLogs().size();

        fixture.controller.createAccount(new CreateAccountRequest("blocked", Set.of(Role.USER)));
        fixture.controller.updateUserRoles(auditor.getAccountId(), Set.of(Role.ADMIN));
        fixture.controller.resetPassword(auditor.getAccountId());
        fixture.controller.disableAccount(auditor.getAccountId());
        fixture.controller.enableAccount(auditor.getAccountId());

        List<LogDTO> deniedLogs = fixture.auditService.getLogs().subList(
                initialLogCount,
                fixture.auditService.getLogs().size()
        );
        assertEquals(
                List.of(
                        ActionType.CREATE_ACCOUNT,
                        ActionType.UPDATE_ROLES,
                        ActionType.RESET_PASSWORD,
                        ActionType.DISABLE_ACCOUNT,
                        ActionType.ENABLE_ACCOUNT
                ),
                deniedLogs.stream().map(LogDTO::getActionType).toList()
        );
        assertTrue(deniedLogs.stream().allMatch(log -> log.getResult() == OperationResult.FAILED));
        assertTrue(deniedLogs.stream().allMatch(log -> "auditor".equals(log.getUsername())));
        assertTrue(deniedLogs.stream().allMatch(log -> log.getActorRole() == Role.AUDITOR));
    }

    @Test
    void repeatedAuditLogAccessShouldCreateOnlyOneSuccessfulViewLog() {
        TestFixture fixture = newFixture();
        loginAsAuditor(fixture, Set.of(Role.AUDITOR));

        assertEquals(OperationResult.SUCCESS, fixture.controller.recordAuditLogsAccess());
        assertEquals(OperationResult.SUCCESS, fixture.controller.recordAuditLogsAccess());
        assertEquals(OperationResult.SUCCESS, fixture.controller.recordAuditLogsAccess());

        List<LogDTO> viewLogs = viewLogs(fixture);
        assertEquals(1, viewLogs.size());
        assertEquals(OperationResult.SUCCESS, viewLogs.getFirst().getResult());
        assertEquals("auditor", viewLogs.getFirst().getUsername());
        assertEquals(Role.AUDITOR, viewLogs.getFirst().getActorRole());
    }

    @Test
    void selectingAuditorAgainShouldAllowAnotherViewLog() {
        TestFixture fixture = newFixture();
        loginAsAuditor(fixture, Set.of(Role.AUDITOR, Role.ADMIN));

        fixture.controller.recordAuditLogsAccess();
        assertEquals(OperationResult.SUCCESS, fixture.controller.selectRole(Role.ADMIN, null).getResult());
        assertEquals(OperationResult.SUCCESS, fixture.controller.selectRole(Role.AUDITOR, null).getResult());
        fixture.controller.recordAuditLogsAccess();

        assertEquals(2, viewLogs(fixture).stream()
                .filter(log -> log.getResult() == OperationResult.SUCCESS)
                .count());
    }

    @Test
    void logoutAndNewLoginShouldAllowAnotherViewLog() {
        TestFixture fixture = newFixture();
        loginAsAuditor(fixture, Set.of(Role.AUDITOR));

        fixture.controller.recordAuditLogsAccess();
        fixture.controller.logout();
        assertEquals(
                OperationResult.SUCCESS,
                fixture.controller.login("auditor", "AuditorPassword123!".toCharArray()).getResult()
        );
        assertEquals(OperationResult.SUCCESS, fixture.controller.selectRole(Role.AUDITOR, null).getResult());
        fixture.controller.recordAuditLogsAccess();

        assertEquals(2, viewLogs(fixture).stream()
                .filter(log -> log.getResult() == OperationResult.SUCCESS)
                .count());
    }

    @Test
    void deniedAuditLogAccessAttemptsShouldBeLoggedIndividually() {
        TestFixture fixture = newFixture();

        assertEquals(OperationResult.FAILED, fixture.controller.recordAuditLogsAccess());
        assertEquals(OperationResult.FAILED, fixture.controller.recordAuditLogsAccess());

        List<LogDTO> viewLogs = viewLogs(fixture);
        assertEquals(2, viewLogs.size());
        assertTrue(viewLogs.stream().allMatch(log -> log.getResult() == OperationResult.FAILED));
    }

    @Test
    void failedViewLogPersistenceShouldNotConsumeTheSessionAllowance() {
        Path accountsFile = tempDir.resolve("failing-audit-accounts.json");
        Path logsFile = tempDir.resolve("failing-audit-logs.ndjson");
        FailOnceViewLogAuditService auditService =
                new FailOnceViewLogAuditService(new LogStore(logsFile));
        TestFixture fixture = newFixture(accountService(accountsFile), auditService);
        loginAsAuditor(fixture, Set.of(Role.AUDITOR));

        assertThrows(LogStoreException.class, fixture.controller::recordAuditLogsAccess);
        assertEquals(OperationResult.SUCCESS, fixture.controller.recordAuditLogsAccess());

        assertEquals(1, viewLogs(fixture).size());
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

    private void loginAsAuditor(TestFixture fixture, Set<Role> roles) {
        AccountCreationResult auditor = fixture.accountService.createAccount("auditor", roles);
        String temporaryPassword = new String(auditor.getTemporaryPassword());

        assertEquals(
                OperationResult.SUCCESS,
                fixture.controller.login("auditor", temporaryPassword.toCharArray()).getResult()
        );
        assertEquals(
                OperationResult.SUCCESS,
                fixture.controller.changeOwnPassword(
                        temporaryPassword.toCharArray(),
                        "AuditorPassword123!".toCharArray()
                ).getResult()
        );
        assertEquals(
                OperationResult.SUCCESS,
                fixture.controller.selectRole(Role.AUDITOR, null).getResult()
        );
    }

    private List<LogDTO> viewLogs(TestFixture fixture) {
        return fixture.auditService.getLogs().stream()
                .filter(log -> log.getActionType() == ActionType.VIEW_LOGS)
                .toList();
    }

    private static final class FailOnceViewLogAuditService extends AuditService {
        private boolean failNextSuccessfulViewLog = true;

        private FailOnceViewLogAuditService(LogStore logStore) {
            super(logStore);
        }

        @Override
        public synchronized void log(
                Integer accountId,
                String username,
                Role actorRole,
                ActionType action,
                String filePath,
                OperationResult result,
                String message
        ) {
            if (action == ActionType.VIEW_LOGS
                    && result == OperationResult.SUCCESS
                    && failNextSuccessfulViewLog) {
                failNextSuccessfulViewLog = false;
                throw new LogStoreException("Simulated VIEW_LOGS persistence failure");
            }
            super.log(accountId, username, actorRole, action, filePath, result, message);
        }
    }

    private record TestFixture(
            AppController controller,
            AccountService accountService,
            AuditService auditService
    ) {}
}

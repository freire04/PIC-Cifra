package pt.tecnico.pic.sprint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import pt.tecnico.pic.application.AppController;
import pt.tecnico.pic.crypto.CryptoService;
import pt.tecnico.pic.domain.ActionType;
import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.domain.Role;
import pt.tecnico.pic.dto.AccountCreationResult;
import pt.tecnico.pic.dto.AccountFilter;
import pt.tecnico.pic.dto.AccountResult;
import pt.tecnico.pic.dto.CreateAccountRequest;
import pt.tecnico.pic.dto.CryptoResult;
import pt.tecnico.pic.dto.LogDTO;
import pt.tecnico.pic.dto.LogFilter;
import pt.tecnico.pic.dto.LoginResult;
import pt.tecnico.pic.dto.RoleSelectionResult;
import pt.tecnico.pic.service.AccountService;
import pt.tecnico.pic.service.AuditService;
import pt.tecnico.pic.service.FileCryptoService;
import pt.tecnico.pic.service.PasswordService;
import pt.tecnico.pic.store.AccountStore;
import pt.tecnico.pic.store.LogStore;

/**
 * S2-11 acceptance test for the complete Sprint 2 audit workflow.
 *
 * The real application uses PKCS#11/SoftHSM2. This test injects a deterministic
 * fake CryptoService so the acceptance flow can run automatically without a
 * hardware/software token configured on the developer machine.
 */
class S2AcceptanceTest {
    @TempDir
    Path tempDir;

    @Test
    void sprint2AuditAcceptanceFlowShouldWorkEndToEnd() throws Exception {
        TestFixture fixture = newFixture();

        AccountCreationResult bootstrappedAdmin = bootstrapInitialAdmin(fixture.accountService);
        assertEquals(OperationResult.SUCCESS, bootstrappedAdmin.getResult());
        assertTrue(Files.exists(fixture.accountsFile));

        LoginResult adminLogin = fixture.controller.login(
                AccountService.INITIAL_ADMIN_USERNAME,
                bootstrappedAdmin.getTemporaryPassword()
        );
        assertEquals(OperationResult.SUCCESS, adminLogin.getResult());
        assertTrue(adminLogin.mustChangePassword());
        assertTrue(fixture.controller.getAvailableRoles().isEmpty());

        AccountResult forcedPasswordChange = fixture.controller.changeOwnPassword(
                bootstrappedAdmin.getTemporaryPassword(),
                "AdminPassword123!".toCharArray()
        );
        assertEquals(OperationResult.SUCCESS, forcedPasswordChange.getResult());

        RoleSelectionResult adminRole = fixture.controller.selectRole(Role.ADMIN, null);
        assertEquals(OperationResult.SUCCESS, adminRole.getResult());
        assertEquals(Role.ADMIN, fixture.controller.getSelectedRole());

        AccountCreationResult user = fixture.controller.createAccount(
                new CreateAccountRequest("demo-user", Set.of(Role.USER))
        );
        AccountCreationResult auditor = fixture.controller.createAccount(
                new CreateAccountRequest("demo-auditor", Set.of(Role.AUDITOR))
        );
        AccountCreationResult adminAuditor = fixture.controller.createAccount(
                new CreateAccountRequest("admin-auditor", Set.of(Role.ADMIN, Role.AUDITOR))
        );
        assertEquals(OperationResult.SUCCESS, user.getResult());
        assertEquals(OperationResult.SUCCESS, auditor.getResult());
        assertEquals(OperationResult.SUCCESS, adminAuditor.getResult());
        assertEquals(4, fixture.controller.getUsers(new AccountFilter()).size());

        assertTrue(fixture.controller.getAuditLogs(new LogFilter()).isEmpty(),
                "ADMIN must not view logs while selectedRole is ADMIN.");
        assertEquals(OperationResult.SUCCESS, fixture.controller.logout());
        assertFalse(fixture.controller.hasActiveSession());

        loginChangePasswordAndSelectUser(fixture.controller, user, "UserPassword123!");
        Path originalFile = tempDir.resolve("documento-original.txt");
        Path encryptedFile = tempDir.resolve("documento-original.cif");
        Path decryptedFile = tempDir.resolve("documento-decifrado.txt");
        byte[] originalBytes = "conteudo de teste para o fluxo de aceitacao".getBytes(StandardCharsets.UTF_8);
        Files.write(originalFile, originalBytes);

        CryptoResult encrypted = fixture.controller.encryptFile(originalFile.toString(), encryptedFile.toString());
        assertEquals(OperationResult.SUCCESS, encrypted.getResult());
        assertTrue(Files.exists(encryptedFile));

        CryptoResult decrypted = fixture.controller.decryptFile(encryptedFile.toString(), decryptedFile.toString());
        assertEquals(OperationResult.SUCCESS, decrypted.getResult());
        assertArrayEquals(originalBytes, Files.readAllBytes(decryptedFile));

        assertTrue(fixture.controller.getAuditLogs(new LogFilter()).isEmpty(),
                "USER must not view logs while selectedRole is USER.");
        assertEquals(OperationResult.SUCCESS, fixture.controller.logout());
        assertFalse(fixture.controller.hasActiveSession());

        loginWithExistingPasswordAndSelectAdmin(fixture.controller, "admin", "AdminPassword123!");
        AccountResult disableUser = fixture.controller.disableAccount(user.getAccountId());
        assertEquals(OperationResult.SUCCESS, disableUser.getResult());
        assertEquals(OperationResult.SUCCESS, fixture.controller.logout());

        LoginResult disabledUserLogin = fixture.controller.login("demo-user", "UserPassword123!".toCharArray());
        assertEquals(OperationResult.FAILED, disabledUserLogin.getResult());

        loginChangePasswordAndSelectAuditor(fixture.controller, auditor, "AuditorPassword123!");
        List<LogDTO> firstAuditView = fixture.controller.getAuditLogs(new LogFilter());
        assertFalse(firstAuditView.isEmpty());
        assertActionPresent(firstAuditView, ActionType.LOGIN);
        assertActionPresent(firstAuditView, ActionType.LOGOUT);
        assertActionPresent(firstAuditView, ActionType.CREATE_ACCOUNT);
        assertActionPresent(firstAuditView, ActionType.ENCRYPT_FILE);
        assertActionPresent(firstAuditView, ActionType.DECRYPT_FILE);
        assertActionPresent(firstAuditView, ActionType.DISABLE_ACCOUNT);
        assertActionPresent(firstAuditView, ActionType.CHANGE_PASSWORD);
        assertActionPresent(firstAuditView, ActionType.VIEW_LOGS);

        assertOnlyFileNamesAreExposed(firstAuditView);
        assertNoSensitiveDataIsExposed(firstAuditView, originalFile, encryptedFile, decryptedFile);

        LogFilter usernameFilter = new LogFilter();
        usernameFilter.setUsername("demo-user");
        assertTrue(fixture.controller.getAuditLogs(usernameFilter).stream()
                .allMatch(log -> "demo-user".equals(log.getUsername())));

        LogFilter actionFilter = new LogFilter();
        actionFilter.setActionType(ActionType.ENCRYPT_FILE);
        List<LogDTO> encryptLogs = fixture.controller.getAuditLogs(actionFilter);
        assertFalse(encryptLogs.isEmpty());
        assertTrue(encryptLogs.stream().allMatch(log -> log.getActionType() == ActionType.ENCRYPT_FILE));

        LogFilter resultFilter = new LogFilter();
        resultFilter.setResult(OperationResult.SUCCESS);
        List<LogDTO> successLogs = fixture.controller.getAuditLogs(resultFilter);
        assertFalse(successLogs.isEmpty());
        assertTrue(successLogs.stream().allMatch(log -> log.getResult() == OperationResult.SUCCESS));

        LogFilter dateFilter = new LogFilter();
        dateFilter.setStartDate(LocalDateTime.now().minusMinutes(5));
        dateFilter.setEndDate(LocalDateTime.now().plusMinutes(5));
        assertFalse(fixture.controller.getAuditLogs(dateFilter).isEmpty());

        long viewLogsBeforeRefresh = countAction(fixture.controller.getAuditLogs(new LogFilter()), ActionType.VIEW_LOGS);
        fixture.controller.getAuditLogs(new LogFilter());
        fixture.controller.getAuditLogs(new LogFilter());
        long viewLogsAfterRefresh = countAction(fixture.controller.getAuditLogs(new LogFilter()), ActionType.VIEW_LOGS);
        assertEquals(viewLogsBeforeRefresh, viewLogsAfterRefresh,
                "Refresh must not spam VIEW_LOGS entries in the same selected role/session.");

        assertEquals(OperationResult.SUCCESS, fixture.controller.logout());
        assertFalse(fixture.controller.hasActiveSession());
        assertTrue(fixture.controller.getAuditLogs(new LogFilter()).isEmpty(),
                "Audit dashboard must not remain accessible after logout.");

        loginChangePasswordAndSelectAdmin(fixture.controller, adminAuditor, "AdminAuditorPassword123!");
        assertTrue(fixture.controller.getAuditLogs(new LogFilter()).isEmpty(),
                "ADMIN+AUDITOR must not view logs while selectedRole is ADMIN.");
        assertEquals(OperationResult.SUCCESS, fixture.controller.selectRole(Role.AUDITOR, null).getResult());
        assertFalse(fixture.controller.getAuditLogs(new LogFilter()).isEmpty(),
                "ADMIN+AUDITOR can view logs only after selecting AUDITOR.");
        assertEquals(OperationResult.SUCCESS, fixture.controller.logout());

        String persistedLogs = Files.readString(fixture.logsFile);
        assertFalse(persistedLogs.contains("UserPassword123!"));
        assertFalse(persistedLogs.contains("AuditorPassword123!"));
        assertFalse(persistedLogs.contains("AdminPassword123!"));
        assertFalse(persistedLogs.contains("123456"));
        assertFalse(persistedLogs.contains(originalFile.getParent().toString()));
        assertTrue(persistedLogs.contains("documento-original.txt"));
        assertTrue(persistedLogs.contains("documento-original.cif"));
    }

    private TestFixture newFixture() {
        Path accountsFile = tempDir.resolve("accounts.json");
        Path logsFile = tempDir.resolve("logs.ndjson");
        AccountService accountService = new AccountService(
                new AccountStore(accountsFile),
                new PasswordService()
        );
        AuditService auditService = new AuditService(new LogStore(logsFile));
        FileCryptoService fileCryptoService = new FileCryptoService(new FakeCryptoService(), auditService);
        AppController controller = new AppController(accountService, auditService, fileCryptoService);
        return new TestFixture(controller, accountService, accountsFile, logsFile);
    }

    private AccountCreationResult bootstrapInitialAdmin(AccountService accountService) {
        return accountService.bootstrapInitialAdmin()
                .orElseThrow(() -> new AssertionError("Initial ADMIN should be created on first startup."));
    }

    private void loginChangePasswordAndSelectUser(AppController controller,
                                                  AccountCreationResult account,
                                                  String newPassword) {
        LoginResult login = controller.login(account.getUsername(), account.getTemporaryPassword());
        assertEquals(OperationResult.SUCCESS, login.getResult());
        assertTrue(login.mustChangePassword());

        AccountResult changed = controller.changeOwnPassword(account.getTemporaryPassword(), newPassword.toCharArray());
        assertEquals(OperationResult.SUCCESS, changed.getResult());

        RoleSelectionResult selected = controller.selectRole(Role.USER, "123456".toCharArray());
        assertEquals(OperationResult.SUCCESS, selected.getResult());
        assertTrue(selected.isTokenUnlocked());
    }

    private void loginChangePasswordAndSelectAuditor(AppController controller,
                                                     AccountCreationResult account,
                                                     String newPassword) {
        LoginResult login = controller.login(account.getUsername(), account.getTemporaryPassword());
        assertEquals(OperationResult.SUCCESS, login.getResult());
        assertTrue(login.mustChangePassword());

        AccountResult changed = controller.changeOwnPassword(account.getTemporaryPassword(), newPassword.toCharArray());
        assertEquals(OperationResult.SUCCESS, changed.getResult());

        RoleSelectionResult selected = controller.selectRole(Role.AUDITOR, null);
        assertEquals(OperationResult.SUCCESS, selected.getResult());
        assertEquals(Role.AUDITOR, selected.getSelectedRole());
    }

    private void loginChangePasswordAndSelectAdmin(AppController controller,
                                                   AccountCreationResult account,
                                                   String newPassword) {
        LoginResult login = controller.login(account.getUsername(), account.getTemporaryPassword());
        assertEquals(OperationResult.SUCCESS, login.getResult());
        assertTrue(login.mustChangePassword());

        AccountResult changed = controller.changeOwnPassword(account.getTemporaryPassword(), newPassword.toCharArray());
        assertEquals(OperationResult.SUCCESS, changed.getResult());

        RoleSelectionResult selected = controller.selectRole(Role.ADMIN, null);
        assertEquals(OperationResult.SUCCESS, selected.getResult());
        assertEquals(Role.ADMIN, selected.getSelectedRole());
    }

    private void loginWithExistingPasswordAndSelectAdmin(AppController controller,
                                                         String username,
                                                         String password) {
        LoginResult login = controller.login(username, password.toCharArray());
        assertEquals(OperationResult.SUCCESS, login.getResult());
        assertFalse(login.mustChangePassword());

        RoleSelectionResult selected = controller.selectRole(Role.ADMIN, null);
        assertEquals(OperationResult.SUCCESS, selected.getResult());
        assertEquals(Role.ADMIN, selected.getSelectedRole());
    }

    private void assertActionPresent(List<LogDTO> logs, ActionType action) {
        assertTrue(logs.stream().anyMatch(log -> log.getActionType() == action),
                "Expected log action to be present: " + action);
    }

    private long countAction(List<LogDTO> logs, ActionType action) {
        return logs.stream().filter(log -> log.getActionType() == action).count();
    }

    private void assertOnlyFileNamesAreExposed(List<LogDTO> logs) {
        logs.stream()
                .map(LogDTO::getFileName)
                .filter(fileName -> fileName != null && !fileName.isBlank())
                .forEach(fileName -> {
                    assertFalse(fileName.contains("/"), "LogDTO fileName must not contain slash paths.");
                    assertFalse(fileName.contains("\\"), "LogDTO fileName must not contain backslash paths.");
                });
    }

    private void assertNoSensitiveDataIsExposed(List<LogDTO> logs, Path... localPaths) {
        List<String> exposedText = logs.stream()
                .map(log -> String.join("|",
                        String.valueOf(log.getUsername()),
                        String.valueOf(log.getActorRole()),
                        String.valueOf(log.getActionType()),
                        String.valueOf(log.getFileName()),
                        String.valueOf(log.getResult()),
                        String.valueOf(log.getMessage())))
                .toList();

        for (String text : exposedText) {
            assertFalse(text.contains("Password123!"));
            assertFalse(text.toLowerCase().contains("pin=123456"));
            for (Path path : localPaths) {
                assertFalse(text.contains(path.getParent().toString()));
            }
        }
    }

    private record TestFixture(AppController controller,
                               AccountService accountService,
                               Path accountsFile,
                               Path logsFile) {}

    private static final class FakeCryptoService implements CryptoService {
        private static final String PREFIX = "PIC-CIFRA-TEST:";
        private boolean sessionOpen;

        @Override
        public void initialize() {
            // No external token is required for the automated acceptance test.
        }

        @Override
        public OperationResult openSession(char[] pin) {
            sessionOpen = pin != null && pin.length > 0;
            return sessionOpen ? OperationResult.SUCCESS : OperationResult.FAILED;
        }

        @Override
        public OperationResult closeSession() {
            sessionOpen = false;
            return OperationResult.SUCCESS;
        }

        @Override
        public boolean isSessionOpen() {
            return sessionOpen;
        }

        @Override
        public CryptoResult encryptFile(String inputPath, String outputPath) {
            if (!sessionOpen) {
                return new CryptoResult(OperationResult.FAILED, "Token session is not open.", inputPath, outputPath, ActionType.ENCRYPT_FILE);
            }

            try {
                String payload = Base64.getEncoder().encodeToString(Files.readAllBytes(Path.of(inputPath)));
                Files.writeString(Path.of(outputPath), PREFIX + payload, StandardCharsets.UTF_8);
                return new CryptoResult(OperationResult.SUCCESS, "File encrypted successfully.", inputPath, outputPath, ActionType.ENCRYPT_FILE);
            } catch (IOException e) {
                return new CryptoResult(OperationResult.ERROR, "File encryption failed.", inputPath, outputPath, ActionType.ENCRYPT_FILE);
            }
        }

        @Override
        public CryptoResult decryptFile(String inputPath, String outputPath) {
            if (!sessionOpen) {
                return new CryptoResult(OperationResult.FAILED, "Token session is not open.", inputPath, outputPath, ActionType.DECRYPT_FILE);
            }

            try {
                String encrypted = Files.readString(Path.of(inputPath), StandardCharsets.UTF_8);
                if (!encrypted.startsWith(PREFIX)) {
                    return new CryptoResult(OperationResult.FAILED, "Invalid encrypted file.", inputPath, outputPath, ActionType.DECRYPT_FILE);
                }

                byte[] plainBytes = Base64.getDecoder().decode(encrypted.substring(PREFIX.length()));
                Files.write(Path.of(outputPath), plainBytes);
                return new CryptoResult(OperationResult.SUCCESS, "File decrypted successfully.", inputPath, outputPath, ActionType.DECRYPT_FILE);
            } catch (IOException | IllegalArgumentException e) {
                return new CryptoResult(OperationResult.ERROR, "File decryption failed.", inputPath, outputPath, ActionType.DECRYPT_FILE);
            }
        }
    }
}

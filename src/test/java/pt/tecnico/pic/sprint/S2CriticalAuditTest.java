package pt.tecnico.pic.sprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import pt.tecnico.pic.application.AppController;
import pt.tecnico.pic.domain.ActionType;
import pt.tecnico.pic.domain.Log;
import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.domain.Role;
import pt.tecnico.pic.domain.UserContext;
import pt.tecnico.pic.dto.AccountCreationResult;
import pt.tecnico.pic.dto.AccountFilter;
import pt.tecnico.pic.dto.AccountResult;
import pt.tecnico.pic.dto.CreateAccountRequest;
import pt.tecnico.pic.dto.CryptoResult;
import pt.tecnico.pic.dto.LogDTO;
import pt.tecnico.pic.dto.LogFilter;
import pt.tecnico.pic.service.AccountService;
import pt.tecnico.pic.service.AuditService;
import pt.tecnico.pic.service.FileCryptoService;
import pt.tecnico.pic.service.PasswordService;
import pt.tecnico.pic.store.AccountStore;
import pt.tecnico.pic.store.LogStore;

/**
 * S2-10 critical tests for persistent audit logs and AUDITOR permissions.
 */
class S2CriticalAuditTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void logStorePersistsTraceabilityFieldsAndSanitizesSensitiveData() throws Exception {
        Path logsPath = tempDir.resolve("logs.ndjson");
        LogStore logStore = new LogStore(logsPath);
        LocalDateTime timestamp = LocalDateTime.of(2026, 6, 21, 10, 30);

        logStore.save(new Log(
                1,
                42,
                timestamp,
                "alice",
                Role.AUDITOR,
                ActionType.VIEW_LOGS,
                "C:\\Users\\alice\\Documents\\audit-report.pdf",
                OperationResult.SUCCESS,
                "password=superSecret123 pin=123456 opened C:\\Users\\alice\\Documents\\audit-report.pdf"
                        + System.lineSeparator()
                        + "java.lang.IllegalStateException: internal stack trace"
        ));

        List<String> lines = Files.readAllLines(logsPath);
        assertEquals(1, lines.size(), "NDJSON must contain one JSON object per line");

        String persistedLine = lines.getFirst();
        JsonNode json = OBJECT_MAPPER.readTree(persistedLine);

        assertEquals(1, json.get("logId").asInt());
        assertEquals(42, json.get("accountId").asInt());
        assertEquals("alice", json.get("username").asText());
        assertEquals("AUDITOR", json.get("actorRole").asText());
        assertEquals("VIEW_LOGS", json.get("actionType").asText());
        assertEquals("audit-report.pdf", json.get("fileName").asText());
        assertEquals("SUCCESS", json.get("result").asText());

        assertFalse(persistedLine.contains("superSecret123"));
        assertFalse(persistedLine.contains("123456"));
        assertFalse(persistedLine.contains("C:\\Users\\alice"));
        assertFalse(persistedLine.contains("IllegalStateException"));

        Log loaded = logStore.findAll().getFirst();
        assertEquals(42, loaded.getAccountId());
        assertEquals(Role.AUDITOR, loaded.getActorRole());
        assertEquals("audit-report.pdf", loaded.getFileName());
    }

    @Test
    void logStoreFiltersByUsernameActionResultRoleFileNameAndDateRange() {
        Path logsPath = tempDir.resolve("filtered-logs.ndjson");
        LogStore logStore = new LogStore(logsPath);
        LocalDateTime base = LocalDateTime.of(2026, 6, 21, 12, 0);

        logStore.save(new Log(1, 10, base.minusHours(2), "alice", Role.USER,
                ActionType.ENCRYPT_FILE, "draft.txt", OperationResult.SUCCESS, "encrypted"));
        logStore.save(new Log(2, 11, base, "bob", Role.ADMIN,
                ActionType.RESET_PASSWORD, null, OperationResult.FAILED, "reset failed"));
        logStore.save(new Log(3, 12, base.plusHours(2), "alice", Role.AUDITOR,
                ActionType.VIEW_LOGS, "audit.ndjson", OperationResult.SUCCESS, "viewed logs"));

        LogFilter filter = new LogFilter();
        filter.setUsername("alice");
        filter.setActorRole(Role.AUDITOR);
        filter.setActionType(ActionType.VIEW_LOGS);
        filter.setResult(OperationResult.SUCCESS);
        filter.setFileName("/tmp/audit.ndjson");
        filter.setStartDate(base.minusMinutes(1));
        filter.setEndDate(base.plusHours(3));

        List<Log> logs = logStore.findByFilter(filter);

        assertEquals(1, logs.size());
        assertEquals(3, logs.getFirst().getLogId());
        assertEquals("alice", logs.getFirst().getUsername());
        assertEquals(Role.AUDITOR, logs.getFirst().getActorRole());
        assertEquals(ActionType.VIEW_LOGS, logs.getFirst().getAction());
        assertEquals("audit.ndjson", logs.getFirst().getFileName());
    }

    @Test
    void logDtoFromLogUsesStoredActorRoleAndDoesNotExposeInternalAccountId() throws Exception {
        Log domainLog = new Log(
                99,
                1234,
                LocalDateTime.of(2026, 6, 21, 12, 0),
                "auditor",
                Role.AUDITOR,
                ActionType.VIEW_LOGS,
                "/var/logs/audit.ndjson",
                OperationResult.SUCCESS,
                "ok"
        );

        LogDTO dto = LogDTO.fromLog(domainLog);

        assertEquals(99, dto.getLogId());
        assertEquals("auditor", dto.getUsername());
        assertEquals(Role.AUDITOR, dto.getActorRole());
        assertEquals(ActionType.VIEW_LOGS, dto.getActionType());
        assertEquals("audit.ndjson", dto.getFileName());
        assertThrows(NoSuchMethodException.class, () -> LogDTO.class.getMethod("getAccountId"));
    }

    @Test
    void auditServicePersistsLogsAndReturnsSafeDtosAfterRestart() {
        Path logsPath = tempDir.resolve("persistent-audit.ndjson");
        AuditService auditService = new AuditService(new LogStore(logsPath));

        auditService.log(
                7,
                "carla",
                Role.USER,
                ActionType.ENCRYPT_FILE,
                "/home/carla/private/document.txt",
                OperationResult.ERROR,
                "key=raw-secret failed at /home/carla/private/document.txt"
        );

        AuditService restartedAuditService = new AuditService(new LogStore(logsPath));
        LogFilter filter = new LogFilter();
        filter.setUsername("carla");
        filter.setActionType(ActionType.ENCRYPT_FILE);
        filter.setResult(OperationResult.ERROR);

        List<LogDTO> logs = restartedAuditService.getLogs(filter);

        assertEquals(1, logs.size());
        LogDTO dto = logs.getFirst();
        assertEquals("carla", dto.getUsername());
        assertEquals(Role.USER, dto.getActorRole());
        assertEquals(ActionType.ENCRYPT_FILE, dto.getActionType());
        assertEquals(OperationResult.ERROR, dto.getResult());
        assertEquals("document.txt", dto.getFileName());
        assertFalse(dto.getMessage().contains("raw-secret"));
        assertFalse(dto.getMessage().contains("/home/carla/private"));
    }

    @Test
    void appControllerAllowsAuditLogAccessOnlyWhenAuditorRoleIsSelected() {
        TestFixture userFixture = newFixture("user-permissions");
        loginAndSelectRole(userFixture, "normal-user", Set.of(Role.USER), Role.USER);
        assertTrue(userFixture.controller.getAuditLogs(new LogFilter()).isEmpty());

        TestFixture adminFixture = newFixture("admin-permissions");
        loginAndSelectRole(adminFixture, "admin-user", Set.of(Role.ADMIN), Role.ADMIN);
        assertTrue(adminFixture.controller.getAuditLogs(new LogFilter()).isEmpty());

        TestFixture auditorFixture = newFixture("auditor-permissions");
        loginAndSelectRole(auditorFixture, "auditor-user", Set.of(Role.AUDITOR), Role.AUDITOR);
        assertFalse(auditorFixture.controller.getAuditLogs(new LogFilter()).isEmpty());

        TestFixture multiRoleFixture = newFixture("multi-role-permissions");
        loginAndSelectRole(multiRoleFixture, "manager", Set.of(Role.ADMIN, Role.AUDITOR), Role.ADMIN);
        assertTrue(multiRoleFixture.controller.getAuditLogs(new LogFilter()).isEmpty());

        assertEquals(OperationResult.SUCCESS,
                multiRoleFixture.controller.selectRole(Role.AUDITOR, null).getResult());
        assertFalse(multiRoleFixture.controller.getAuditLogs(new LogFilter()).isEmpty());
    }

    @Test
    void auditorRoleCannotUseUserOrAdminOperations() {
        TestFixture fixture = newFixture("auditor-isolation");
        loginAndSelectRole(fixture, "auditor", Set.of(Role.AUDITOR), Role.AUDITOR);

        CryptoResult encryptResult = fixture.controller.encryptFile("plain.txt", "plain.cif");
        CryptoResult decryptResult = fixture.controller.decryptFile("plain.cif", "plain.txt");
        AccountCreationResult createResult = fixture.controller.createAccount(
                new CreateAccountRequest("created-by-auditor", Set.of(Role.USER))
        );
        AccountResult updateRolesResult = fixture.controller.updateUserRoles(1, Set.of(Role.ADMIN));
        AccountResult disableResult = fixture.controller.disableAccount(1);

        assertEquals(OperationResult.FAILED, encryptResult.getResult());
        assertEquals(OperationResult.FAILED, decryptResult.getResult());
        assertEquals(OperationResult.FAILED, createResult.getResult());
        assertEquals(OperationResult.FAILED, updateRolesResult.getResult());
        assertEquals(OperationResult.FAILED, disableResult.getResult());
        assertTrue(fixture.controller.getUsers(new AccountFilter()).isEmpty());
    }

    private TestFixture newFixture(String name) {
        AccountService accountService = new AccountService(
                new AccountStore(tempDir.resolve(name).resolve("accounts.json")),
                new PasswordService()
        );
        AuditService auditService = new AuditService(
                new LogStore(tempDir.resolve(name).resolve("logs.ndjson"))
        );
        FileCryptoService fileCryptoService = new TokenStubFileCryptoService(auditService);
        AppController controller = new AppController(accountService, auditService, fileCryptoService);

        return new TestFixture(controller, accountService);
    }

    private void loginAndSelectRole(TestFixture fixture, String username, Set<Role> roles, Role roleToSelect) {
        AccountCreationResult created = fixture.accountService.createAccount(username, roles);
        assertEquals(OperationResult.SUCCESS, created.getResult());
        assertNotNull(created.getTemporaryPassword());

        assertEquals(OperationResult.SUCCESS,
                fixture.controller.login(username, created.getTemporaryPassword()).getResult());

        char[] permanentPassword = (username + "Password123!").toCharArray();
        assertEquals(OperationResult.SUCCESS,
                fixture.controller.changeOwnPassword(created.getTemporaryPassword(), permanentPassword).getResult());

        char[] pin = roleToSelect == Role.USER ? "123456".toCharArray() : null;
        assertEquals(OperationResult.SUCCESS,
                fixture.controller.selectRole(roleToSelect, pin).getResult());

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

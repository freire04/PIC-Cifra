package pt.tecnico.pic.store;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import pt.tecnico.pic.domain.ActionType;
import pt.tecnico.pic.domain.Log;
import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.domain.Role;
import pt.tecnico.pic.dto.LogFilter;

class LogStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void saveCreatesLogsFileAndFindsLog() {
        Path logsPath = tempDir.resolve("logs.ndjson");
        LogStore store = new LogStore(logsPath);

        Log log = new Log(
                1,
                10,
                LocalDateTime.of(2026, 6, 21, 12, 0),
                "afonso",
                Role.USER,
                ActionType.LOGIN,
                "document.txt",
                OperationResult.SUCCESS,
                "User login successful"
        );

        store.save(log);

        assertTrue(Files.exists(logsPath));
        assertTrue(store.logsFileExists());
        assertEquals(1, store.findAll().size());
        assertEquals("afonso", store.findAll().get(0).getUsername());
        assertEquals(ActionType.LOGIN, store.findAll().get(0).getAction());
        assertEquals(OperationResult.SUCCESS, store.findAll().get(0).getResult());
    }

    @Test
    void findByFilterWithNullReturnsAllLogs() {
        Path logsPath = tempDir.resolve("logs.ndjson");
        LogStore store = new LogStore(logsPath);

        store.save(new Log(1, 10, LocalDateTime.now(), "user1", Role.USER, ActionType.LOGIN, "f1.txt", OperationResult.SUCCESS, "m1"));
        store.save(new Log(2, 11, LocalDateTime.now(), "user2", Role.ADMIN, ActionType.VIEW_LOGS, "f2.txt", OperationResult.FAILED, "m2"));

        List<Log> filtered = store.findByFilter(null);
        assertEquals(2, filtered.size());
    }

    @Test
    void findByFilterReturnsOnlyMatchingLogs() {
        Path logsPath = tempDir.resolve("logs.ndjson");
        LogStore store = new LogStore(logsPath);

        LocalDateTime now = LocalDateTime.of(2026, 6, 21, 12, 0);

        store.save(new Log(1, 10, now, "afonso", Role.USER, ActionType.ENCRYPT_FILE, "f1.txt", OperationResult.SUCCESS, "msg"));
        store.save(new Log(2, 11, now.plusHours(1), "bob", Role.ADMIN, ActionType.DISABLE_ACCOUNT, "f2.txt", OperationResult.FAILED, "msg"));
        store.save(new Log(3, 12, now.plusHours(2), "charlie", Role.ADMIN, ActionType.DECRYPT_FILE, "f3.txt", OperationResult.ERROR, "fatal error"));

        // Testar filtro por username
        LogFilter filterUser = new LogFilter();
        filterUser.setUsername("afonso");
        List<Log> resUser = store.findByFilter(filterUser);
        assertEquals(1, resUser.size());
        assertEquals("afonso", resUser.get(0).getUsername());

        // Testar filtro por ActionType (DISABLE_ACCOUNT) e Result (FAILED)
        LogFilter filterFailed = new LogFilter();
        filterFailed.setActionType(ActionType.DISABLE_ACCOUNT);
        filterFailed.setResult(OperationResult.FAILED);
        List<Log> resFailed = store.findByFilter(filterFailed);
        assertEquals(1, resFailed.size());
        assertEquals("bob", resFailed.get(0).getUsername());

        // Testar filtro por Result (ERROR)
        LogFilter filterError = new LogFilter();
        filterError.setResult(OperationResult.ERROR);
        List<Log> resError = store.findByFilter(filterError);
        assertEquals(1, resError.size());
        assertEquals("charlie", resError.get(0).getUsername());

        LogFilter filterRole = new LogFilter();
        filterRole.setActorRole(Role.ADMIN);
        List<Log> resRole = store.findByFilter(filterRole);
        assertEquals(2, resRole.size());
        assertTrue(resRole.stream().allMatch(log -> log.getActorRole() == Role.ADMIN));
    }

    @Test
    void findByFilterFiltersCorrectlyByDates() {
        Path logsPath = tempDir.resolve("logs.ndjson");
        LogStore store = new LogStore(logsPath);

        LocalDateTime baseTime = LocalDateTime.of(2026, 6, 21, 12, 0);

        store.save(new Log(1, 10, baseTime.minusDays(2), "user", Role.USER, ActionType.TOKEN_LOCK, "f.txt", OperationResult.SUCCESS, "m"));
        store.save(new Log(2, 10, baseTime, "user", Role.USER, ActionType.TOKEN_UNLOCK, "f.txt", OperationResult.SUCCESS, "m"));
        store.save(new Log(3, 10, baseTime.plusDays(2), "user", Role.USER, ActionType.TOKEN_LOCK, "f.txt", OperationResult.SUCCESS, "m"));

        LogFilter filter = new LogFilter();
        filter.setStartDate(baseTime.minusDays(1));
        filter.setEndDate(baseTime.plusDays(1));

        List<Log> result = store.findByFilter(filter);
        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getLogId());
        assertEquals(ActionType.TOKEN_UNLOCK, result.get(0).getAction());
    }

    @Test
    void queryMethodsReturnMutableListsConsistently() {
        Path logsPath = tempDir.resolve("logs.ndjson");
        LogStore store = new LogStore(logsPath);

        store.save(new Log(1, 10, LocalDateTime.now(), "user", Role.USER, ActionType.LOGOUT, "f.txt", OperationResult.SUCCESS, "m"));

        assertDoesNotThrow(() -> store.findAll().add(
                new Log(2, 11, LocalDateTime.now(), "other", Role.USER, ActionType.LOGOUT, "f.txt", OperationResult.SUCCESS, "m")
        ));

        assertDoesNotThrow(() -> store.findByFilter(new LogFilter()).add(
                new Log(2, 11, LocalDateTime.now(), "other", Role.USER, ActionType.LOGOUT, "f.txt", OperationResult.SUCCESS, "m")
        ));
    }

    @Test
    void missingFileReturnsEmptyResults() {
        Path logsPath = tempDir.resolve("logs.ndjson");
        LogStore store = new LogStore(logsPath);

        assertFalse(store.logsFileExists());
        assertTrue(store.findAll().isEmpty());
        assertTrue(store.findByFilter(new LogFilter()).isEmpty());
    }

    @Test
    void logsAreLoadedAfterCreatingNewStoreInstanceAndIdCounterIsRestored() {
        Path logsPath = tempDir.resolve("logs.ndjson");
        LogStore firstStore = new LogStore(logsPath);

        firstStore.save(new Log(1, 10, LocalDateTime.now(), "afonso", Role.USER, ActionType.CREATE_ACCOUNT, "f.txt", OperationResult.SUCCESS, "m"));
        firstStore.save(new Log(5, 10, LocalDateTime.now(), "bob", Role.ADMIN, ActionType.UPDATE_ROLES, "f.txt", OperationResult.ERROR, "m"));

        // Criar nova instância para simular reboot - deve ler o max ID (5) e preparar o próximo (6)
        LogStore secondStore = new LogStore(logsPath);

        assertEquals(6, secondStore.nextLogId());
        assertEquals(2, secondStore.findAll().size());
    }

    @Test
    void nextLogIdReturnsOneWhenThereAreNoLogs() {
        Path logsPath = tempDir.resolve("logs.ndjson");
        LogStore store = new LogStore(logsPath);

        assertEquals(1, store.nextLogId());
    }

    @Test
    void nextLogIdIncrementsInMemoryWithoutReadingFileEverytime() {
        Path logsPath = tempDir.resolve("logs.ndjson");
        LogStore store = new LogStore(logsPath);

        assertEquals(1, store.nextLogId());
        assertEquals(2, store.nextLogId());
        assertEquals(3, store.nextLogId());
    }

    @Test
    void corruptedJsonLineShouldThrowLogStoreException() throws Exception {
        Path logsPath = tempDir.resolve("logs.ndjson");
        Files.writeString(logsPath, "{ invalid ndjson line\n");

        assertThrows(LogStoreException.class, () -> new LogStore(logsPath));
    }

    @Test
    void saveCreatesMissingParentDirectories() {
        Path logsPath = tempDir
                .resolve("audit")
                .resolve("deep")
                .resolve("logs.ndjson");

        LogStore store = new LogStore(logsPath);

        store.save(new Log(1, 10, LocalDateTime.now(), "afonso", Role.USER, ActionType.CHANGE_PASSWORD, "f.txt", OperationResult.SUCCESS, "m"));

        assertTrue(Files.exists(logsPath));
        assertTrue(Files.exists(logsPath.getParent()));
    }

    @Test
    void saveNullLogThrowsException() {
        Path logsPath = tempDir.resolve("logs.ndjson");
        LogStore store = new LogStore(logsPath);

        assertThrows(
                NullPointerException.class,
                () -> store.save(null)
        );
    }

    @Test
    void jsonContainsSanitizedMessageAndFileName() throws Exception {
        Path logsPath = tempDir.resolve("logs.ndjson");
        LogStore store = new LogStore(logsPath);

        Log dangerousLog = new Log(
                1,
                10,
                LocalDateTime.now(),
                "afonso",
                Role.USER,
                ActionType.RESET_PASSWORD,
                "/var/tmp/unsafe_file.txt",
                OperationResult.FAILED,
                "Tentativa com senha:minhasenha123 no caminho /etc/passwd"
        );

        store.save(dangerousLog);

        String ndjsonContent = Files.readString(logsPath);

        // Deve conter os tokens sanitizados
        assertTrue(ndjsonContent.contains("unsafe_file.txt"));
        assertFalse(ndjsonContent.contains("/var/tmp/"));

        assertTrue(ndjsonContent.contains("senha=[REDACTED]"));
        assertFalse(ndjsonContent.contains("minhasenha123"));
    }

    @Test
    void saveAppendsOneValidJsonObjectPerLine() throws Exception {
        Path logsPath = tempDir.resolve("logs.ndjson");
        LogStore store = new LogStore(logsPath);
        ObjectMapper objectMapper = new ObjectMapper();
        LocalDateTime timestamp = LocalDateTime.of(2026, 6, 22, 10, 30);

        store.save(new Log(1, 10, timestamp, "alice", null, ActionType.LOGIN,
                null, OperationResult.SUCCESS, "ok"));
        store.save(new Log(2, 10, timestamp.plusMinutes(1), "alice", Role.USER,
                ActionType.ENCRYPT_FILE, "report.pdf", OperationResult.SUCCESS, "encrypted"));
        store.save(new Log(3, null, timestamp.plusMinutes(2), null, null, ActionType.LOGIN,
                null, OperationResult.FAILED, "invalid credentials"));

        List<String> lines = Files.readAllLines(logsPath);
        assertEquals(3, lines.size());

        for (String line : lines) {
            JsonNode json = objectMapper.readTree(line);
            assertTrue(json.isObject());
            assertTrue(json.has("logId"));
            assertTrue(json.has("accountId"));
            assertTrue(json.has("timestamp"));
            assertTrue(json.has("actorRole"));
            assertTrue(json.has("actionType"));
            assertTrue(json.has("fileName"));
            assertTrue(json.has("result"));
            assertTrue(json.has("message"));
        }
    }

    @Test
    void duplicateIdsAreRejectedAcrossStoreInstances() {
        Path logsPath = tempDir.resolve("logs.ndjson");
        LogStore firstStore = new LogStore(logsPath);
        LogStore secondStore = new LogStore(logsPath);
        LocalDateTime timestamp = LocalDateTime.now();

        firstStore.save(new Log(1, 10, timestamp, "alice", Role.USER, ActionType.LOGIN,
                null, OperationResult.SUCCESS, "ok"));

        assertThrows(LogStoreException.class, () -> secondStore.save(
                new Log(1, 11, timestamp, "bob", Role.ADMIN, ActionType.LOGIN,
                        null, OperationResult.SUCCESS, "ok")
        ));
        assertEquals(1, firstStore.findAll().size());
    }

    @Test
    void differentInstancesShareTheSameIdSequence() {
        Path logsPath = tempDir.resolve("logs.ndjson");
        LogStore firstStore = new LogStore(logsPath);
        LogStore secondStore = new LogStore(logsPath);

        assertEquals(1, firstStore.nextLogId());
        assertEquals(2, secondStore.nextLogId());
        assertEquals(3, firstStore.nextLogId());
    }

    @Test
    void concurrentInstancesGenerateUniqueIds() throws Exception {
        Path logsPath = tempDir.resolve("logs.ndjson");
        LogStore firstStore = new LogStore(logsPath);
        LogStore secondStore = new LogStore(logsPath);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        Set<Integer> generatedIds = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < 200; i++) {
            LogStore selectedStore = i % 2 == 0 ? firstStore : secondStore;
            executor.submit(() -> generatedIds.add(selectedStore.nextLogId()));
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(200, generatedIds.size());
        assertEquals(1, generatedIds.stream().mapToInt(Integer::intValue).min().orElseThrow());
        assertEquals(200, generatedIds.stream().mapToInt(Integer::intValue).max().orElseThrow());
    }

    @Test
    void savingExplicitIdAdvancesNextIdCounter() {
        Path logsPath = tempDir.resolve("logs.ndjson");
        LogStore store = new LogStore(logsPath);

        store.save(new Log(20, 10, LocalDateTime.now(), "alice", Role.USER, ActionType.LOGIN,
                null, OperationResult.SUCCESS, "ok"));

        assertEquals(21, store.nextLogId());
    }

    @Test
    void saveRejectsInvalidRequiredFieldsBeforeWriting() {
        Path logsPath = tempDir.resolve("logs.ndjson");
        LogStore store = new LogStore(logsPath);
        LocalDateTime timestamp = LocalDateTime.now();

        assertThrows(IllegalArgumentException.class, () -> store.save(
                new Log(0, 10, timestamp, "alice", Role.USER, ActionType.LOGIN,
                        null, OperationResult.SUCCESS, "ok")
        ));
        assertThrows(NullPointerException.class, () -> store.save(
                new Log(1, 10, null, "alice", Role.USER, ActionType.LOGIN,
                        null, OperationResult.SUCCESS, "ok")
        ));
        assertThrows(NullPointerException.class, () -> store.save(
                new Log(1, 10, timestamp, "alice", Role.USER, null,
                        null, OperationResult.SUCCESS, "ok")
        ));
        assertThrows(NullPointerException.class, () -> store.save(
                new Log(1, 10, timestamp, "alice", Role.USER, ActionType.LOGIN,
                        null, null, "ok")
        ));

        assertFalse(Files.exists(logsPath));
    }

    @Test
    void constructorRejectsDuplicateIdsAlreadyPresentOnDisk() throws Exception {
        Path logsPath = tempDir.resolve("logs.ndjson");
        String timestamp = LocalDateTime.of(2026, 6, 22, 12, 0).toString();
        String firstLine = """
                {"logId":1,"accountId":10,"timestamp":"%s","username":"alice","actorRole":"USER","actionType":"LOGIN","fileName":null,"result":"SUCCESS","message":"ok"}
                """.formatted(timestamp).trim();
        String secondLine = firstLine.replace("\"alice\"", "\"bob\"");
        Files.writeString(logsPath, firstLine + System.lineSeparator() + secondLine);

        assertThrows(LogStoreException.class, () -> new LogStore(logsPath));
    }

    @Test
    void jsonRoundTripPreservesUnicodeAndEscapedCharacters() {
        Path logsPath = tempDir.resolve("logs.ndjson");
        LogStore store = new LogStore(logsPath);
        String username = "utilizador-\u00e7";
        String message = "Ficheiro \"relat\u00f3rio\" processado com tab\t.";

        store.save(new Log(1, null, LocalDateTime.now(), username, null, ActionType.LOGIN,
                null, OperationResult.SUCCESS, message));

        Log loaded = store.findAll().getFirst();
        assertEquals(username, loaded.getUsername());
        assertEquals(message, loaded.getMessage());
        assertNull(loaded.getAccountId());
        assertNull(loaded.getActorRole());
    }

    @Test
    void sanitizationRedactsQuotedSecretsAndRemovesStackTraceLines() throws Exception {
        Path logsPath = tempDir.resolve("logs.ndjson");
        LogStore store = new LogStore(logsPath);

        store.save(new Log(
                1,
                10,
                LocalDateTime.now(),
                "alice",
                Role.USER,
                ActionType.ENCRYPT_FILE,
                "C:\\Users\\alice\\report.pdf",
                OperationResult.ERROR,
                "password=\"very secret\" failed at C:\\Users\\alice\\report.pdf"
                        + System.lineSeparator()
                        + "java.lang.IllegalStateException: internal detail"
        ));

        String persisted = Files.readString(logsPath);
        assertFalse(persisted.contains("very secret"));
        assertFalse(persisted.contains("C:\\Users\\alice"));
        assertFalse(persisted.contains("IllegalStateException"));
        assertTrue(persisted.contains("password=[REDACTED]"));
        assertTrue(persisted.contains("report.pdf"));
    }
}

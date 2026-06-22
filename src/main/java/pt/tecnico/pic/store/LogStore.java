package pt.tecnico.pic.store;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import pt.tecnico.pic.domain.ActionType;
import pt.tecnico.pic.domain.Log;
import pt.tecnico.pic.domain.OperationResult;
import pt.tecnico.pic.domain.Role;
import pt.tecnico.pic.dto.LogFilter;
import pt.tecnico.pic.util.PathSanitizer;

/**
 * Append-only NDJSON store for audit logs.
 *
 * Each persisted line is one JSON object and contains only safe audit fields.
 * Full file paths, passwords, PINs, keys, file contents and stack traces are not
 * persisted by this class.
 */
public class LogStore {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private static final Pattern SENSITIVE_VALUE_PATTERN = Pattern.compile(
            "(?i)\\b(password|pin|senha|key|chave|secret|token)\\b\\s*[:=]\\s*"
                    + "(?:\"(?:\\\\.|[^\"])*\"|'(?:\\\\.|[^'])*'|\\S+)"
    );

    private static final Pattern PATH_TOKEN_PATTERN =
            Pattern.compile("\\S*[\\\\/]\\S+");

    private static final ConcurrentMap<Path, StoreState> STORE_STATES = new ConcurrentHashMap<>();

    private final Path logsFilePath;
    private final StoreState state;

    public LogStore() {
        this("data/logs.ndjson");
    }

    public LogStore(String logsFilePath) {
        this(Path.of(logsFilePath));
    }

    public LogStore(Path logsFilePath) {
        this.logsFilePath = Objects.requireNonNull(logsFilePath, "logsFilePath must not be null");
        Path stateKey = logsFilePath.toAbsolutePath().normalize();
        this.state = STORE_STATES.computeIfAbsent(stateKey, ignored -> new StoreState());

        synchronized (state.lock) {
            initializeState();
        }
    }

    public void save(Log log) {
        Objects.requireNonNull(log, "log must not be null");
        validateLog(log);

        Log safeLog = sanitizeLog(log);
        String line = toJson(safeLog) + System.lineSeparator();

        synchronized (state.lock) {
            if (state.persistedLogIds.contains(safeLog.getLogId())) {
                throw new LogStoreException("Audit log ID already exists: " + safeLog.getLogId());
            }

            try {
                Path parent = logsFilePath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }

                Files.writeString(
                        logsFilePath,
                        line,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.APPEND
                );
                state.persistedLogIds.add(safeLog.getLogId());
                state.lastIssuedLogId = Math.max(state.lastIssuedLogId, safeLog.getLogId());
            } catch (IOException e) {
                throw new LogStoreException("Failed to write audit log", e);
            }
        }
    }

    public List<Log> findAll() {
        synchronized (state.lock) {
            if (!Files.exists(logsFilePath)) {
                return new ArrayList<>();
            }

            try {
                List<Log> logs = new ArrayList<>();
                for (String line : Files.readAllLines(logsFilePath, StandardCharsets.UTF_8)) {
                    if (!line.isBlank()) {
                        logs.add(fromJson(line));
                    }
                }
                return logs;
            } catch (IOException e) {
                throw new LogStoreException("Failed to read audit logs", e);
            }
        }
    }

    public List<Log> findByFilter(LogFilter filter) {
        if (filter == null) {
            return findAll();
        }

        synchronized (state.lock) {
            if (!Files.exists(logsFilePath)) {
                return new ArrayList<>();
            }

            try (var lines = Files.lines(logsFilePath, StandardCharsets.UTF_8)) {
                return lines
                        .filter(line -> !line.isBlank())
                        .map(LogStore::fromJson)
                        .filter(log -> matchesFilter(log, filter))
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            } catch (IOException e) {
                throw new LogStoreException("Failed to read audit logs", e);
            }
        }
    }

    public int nextLogId() {
        synchronized (state.lock) {
            return ++state.lastIssuedLogId;
        }
    }

    public boolean logsFileExists() {
        return Files.exists(logsFilePath);
    }

    public Path getLogsFilePath() {
        return logsFilePath;
    }

    private void initializeState() {
        if (!Files.exists(logsFilePath)) {
            return;
        }

        Set<Integer> persistedIds = new HashSet<>();
        int maxId = 0;

        try {
            for (String line : Files.readAllLines(logsFilePath, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }

                Log log = fromJson(line);
                if (!persistedIds.add(log.getLogId())) {
                    throw new LogStoreException("Duplicate audit log ID: " + log.getLogId());
                }
                maxId = Math.max(maxId, log.getLogId());
            }
        } catch (IOException e) {
            throw new LogStoreException("Failed to initialize log ID counter", e);
        } catch (LogStoreException e) {
            throw new LogStoreException("Failed to initialize log ID counter", e);
        }

        state.persistedLogIds.clear();
        state.persistedLogIds.addAll(persistedIds);
        state.lastIssuedLogId = Math.max(state.lastIssuedLogId, maxId);
    }

    private static void validateLog(Log log) {
        if (log.getLogId() <= 0) {
            throw new IllegalArgumentException("logId must be greater than zero");
        }
        Objects.requireNonNull(log.getTimestamp(), "timestamp must not be null");
        Objects.requireNonNull(log.getAction(), "actionType must not be null");
        Objects.requireNonNull(log.getResult(), "result must not be null");
    }

    private static boolean matchesFilter(Log log, LogFilter filter) {
        if (filter.getUsername() != null && !filter.getUsername().isBlank()
                && !filter.getUsername().equals(log.getUsername())) {
            return false;
        }

        if (filter.getActorRole() != null && filter.getActorRole() != log.getActorRole()) {
            return false;
        }

        if (filter.getActionType() != null && filter.getActionType() != log.getAction()) {
            return false;
        }

        if (filter.getResult() != null && filter.getResult() != log.getResult()) {
            return false;
        }

        String filterFileName = PathSanitizer.toFileName(filter.getFileName());
        if (filterFileName != null && !filterFileName.isBlank()
                && !filterFileName.equals(log.getFileName())) {
            return false;
        }

        if (filter.getStartDate() != null && log.getTimestamp().isBefore(filter.getStartDate())) {
            return false;
        }

        return filter.getEndDate() == null || !log.getTimestamp().isAfter(filter.getEndDate());
    }

    private static Log sanitizeLog(Log log) {
        return new Log(
                log.getLogId(),
                log.getAccountId(),
                log.getTimestamp(),
                log.getUsername(),
                log.getActorRole(),
                log.getAction(),
                PathSanitizer.toFileName(log.getFileName()),
                log.getResult(),
                sanitizeMessage(log.getMessage())
        );
    }

    private static String sanitizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return message;
        }

        String firstLine = message.lines().findFirst().orElse("");
        String withoutSensitiveValues = SENSITIVE_VALUE_PATTERN
                .matcher(firstLine)
                .replaceAll("$1=[REDACTED]");

        Matcher matcher = PATH_TOKEN_PATTERN.matcher(withoutSensitiveValues);
        StringBuffer safeMessage = new StringBuffer();

        while (matcher.find()) {
            String replacement = PathSanitizer.toFileName(matcher.group());
            matcher.appendReplacement(
                    safeMessage,
                    Matcher.quoteReplacement(replacement == null ? "" : replacement)
            );
        }

        matcher.appendTail(safeMessage);
        return safeMessage.toString();
    }

    private static String toJson(Log log) {
        ObjectNode json = OBJECT_MAPPER.createObjectNode();
        json.put("logId", log.getLogId());
        putNullableInteger(json, "accountId", log.getAccountId());
        json.put("timestamp", log.getTimestamp().toString());
        putNullableText(json, "username", log.getUsername());
        putNullableText(json, "actorRole", log.getActorRole() == null ? null : log.getActorRole().name());
        json.put("actionType", log.getAction().name());
        putNullableText(json, "fileName", log.getFileName());
        json.put("result", log.getResult().name());
        putNullableText(json, "message", log.getMessage());

        try {
            return OBJECT_MAPPER.writeValueAsString(json);
        } catch (IOException e) {
            throw new LogStoreException("Failed to serialize audit log", e);
        }
    }

    private static Log fromJson(String line) {
        try {
            JsonNode json = OBJECT_MAPPER.readTree(line);
            if (json == null || !json.isObject()) {
                throw new IllegalArgumentException("Audit log line must be a JSON object");
            }

            int logId = requiredPositiveInt(json, "logId");
            LocalDateTime timestamp = LocalDateTime.parse(requiredText(json, "timestamp"));
            ActionType action = parseEnum(ActionType.class, requiredText(json, "actionType"));
            OperationResult result = parseEnum(OperationResult.class, requiredText(json, "result"));

            return new Log(
                    logId,
                    nullableInteger(json, "accountId"),
                    timestamp,
                    nullableText(json, "username"),
                    parseNullableEnum(Role.class, nullableText(json, "actorRole")),
                    action,
                    nullableText(json, "fileName"),
                    result,
                    nullableText(json, "message")
            );
        } catch (IOException | DateTimeParseException | IllegalArgumentException e) {
            throw new LogStoreException("Failed to parse audit log line", e);
        }
    }

    private static int requiredPositiveInt(JsonNode json, String fieldName) {
        JsonNode value = json.get(fieldName);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() <= 0) {
            throw new IllegalArgumentException("Invalid required audit log field: " + fieldName);
        }
        return value.intValue();
    }

    private static Integer nullableInteger(JsonNode json, String fieldName) {
        JsonNode value = json.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException("Invalid audit log field: " + fieldName);
        }
        return value.intValue();
    }

    private static String requiredText(JsonNode json, String fieldName) {
        String value = nullableText(json, fieldName);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required audit log field: " + fieldName);
        }
        return value;
    }

    private static String nullableText(JsonNode json, String fieldName) {
        JsonNode value = json.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new IllegalArgumentException("Invalid audit log field: " + fieldName);
        }
        return value.textValue();
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        return Enum.valueOf(type, value);
    }

    private static <E extends Enum<E>> E parseNullableEnum(Class<E> type, String value) {
        return value == null ? null : parseEnum(type, value);
    }

    private static void putNullableInteger(ObjectNode json, String fieldName, Integer value) {
        if (value == null) {
            json.putNull(fieldName);
        } else {
            json.put(fieldName, value);
        }
    }

    private static void putNullableText(ObjectNode json, String fieldName, String value) {
        if (value == null) {
            json.putNull(fieldName);
        } else {
            json.put(fieldName, value);
        }
    }

    private static final class StoreState {
        private final Object lock = new Object();
        private final Set<Integer> persistedLogIds = new HashSet<>();
        private int lastIssuedLogId;
    }
}

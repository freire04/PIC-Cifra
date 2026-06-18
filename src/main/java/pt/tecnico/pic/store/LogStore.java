package pt.tecnico.pic.store;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final Pattern SENSITIVE_VALUE_PATTERN =
            Pattern.compile("(?i)\\b(password|pin|senha|key|chave)\\b\\s*[:=]\\s*\\S+");

    private static final Pattern PATH_TOKEN_PATTERN =
            Pattern.compile("\\S*[\\\\/]\\S+");

    private final Path logsFilePath;

    public LogStore() {
        this("data/logs.ndjson");
    }

    public LogStore(String logsFilePath) {
        this(Path.of(logsFilePath));
    }

    public LogStore(Path logsFilePath) {
        this.logsFilePath = Objects.requireNonNull(logsFilePath, "logsFilePath must not be null");
    }

    public synchronized void save(Log log) {
        Objects.requireNonNull(log, "log must not be null");

        Log safeLog = sanitizeLog(log);
        String line = toJson(safeLog) + System.lineSeparator();

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
        } catch (IOException e) {
            throw new LogStoreException("Failed to write audit log", e);
        }
    }

    public synchronized List<Log> findAll() {
        if (!Files.exists(logsFilePath)) {
            return new ArrayList<>();
        }

        try {
            List<Log> logs = new ArrayList<>();
            for (String line : Files.readAllLines(logsFilePath, StandardCharsets.UTF_8)) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                logs.add(fromJson(line));
            }
            return logs;
        } catch (IOException e) {
            throw new LogStoreException("Failed to read audit logs", e);
        }
    }

    public synchronized List<Log> findByFilter(LogFilter filter) {
        if (filter == null) {
            return findAll();
        }

        return findAll().stream()
                .filter(log -> matchesFilter(log, filter))
                .toList();
    }

    public synchronized int nextLogId() {
        return findAll().stream()
                .mapToInt(Log::getLogId)
                .max()
                .orElse(0) + 1;
    }

    public boolean logsFileExists() {
        return Files.exists(logsFilePath);
    }

    public Path getLogsFilePath() {
        return logsFilePath;
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

        String withoutSensitiveValues = SENSITIVE_VALUE_PATTERN
                .matcher(message)
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
        StringBuilder json = new StringBuilder();
        json.append('{');
        appendNumber(json, "logId", log.getLogId());
        appendNullableNumber(json, "accountId", log.getAccountId());
        appendString(json, "timestamp", log.getTimestamp() == null ? null : log.getTimestamp().toString());
        appendString(json, "username", log.getUsername());
        appendString(json, "actorRole", log.getActorRole() == null ? null : log.getActorRole().name());
        appendString(json, "actionType", log.getAction() == null ? null : log.getAction().name());
        appendString(json, "fileName", log.getFileName());
        appendString(json, "result", log.getResult() == null ? null : log.getResult().name());
        appendString(json, "message", log.getMessage());
        json.append('}');
        return json.toString();
    }

    private static Log fromJson(String line) {
        try {
            Map<String, String> fields = parseJsonObject(line);
            return new Log(
                    Integer.parseInt(required(fields, "logId")),
                    parseInteger(fields.get("accountId")),
                    LocalDateTime.parse(required(fields, "timestamp")),
                    fields.get("username"),
                    parseEnum(Role.class, fields.get("actorRole")),
                    parseEnum(ActionType.class, required(fields, "actionType")),
                    fields.get("fileName"),
                    parseEnum(OperationResult.class, required(fields, "result")),
                    fields.get("message")
            );
        } catch (RuntimeException e) {
            throw new LogStoreException("Failed to parse audit log line", e);
        }
    }

    private static String required(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required audit log field: " + key);
        }
        return value;
    }

    private static Integer parseInteger(String value) {
        return value == null ? null : Integer.valueOf(value);
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }

    private static void appendNumber(StringBuilder json, String key, int value) {
        appendCommaIfNeeded(json);
        json.append(quote(key)).append(':').append(value);
    }

    private static void appendNullableNumber(StringBuilder json, String key, Integer value) {
        appendCommaIfNeeded(json);
        json.append(quote(key)).append(':');
        if (value == null) {
            json.append("null");
        } else {
            json.append(value);
        }
    }

    private static void appendString(StringBuilder json, String key, String value) {
        appendCommaIfNeeded(json);
        json.append(quote(key)).append(':');
        if (value == null) {
            json.append("null");
        } else {
            json.append(quote(value));
        }
    }

    private static void appendCommaIfNeeded(StringBuilder json) {
        if (json.length() > 1 && json.charAt(json.length() - 1) != '{') {
            json.append(',');
        }
    }

    private static String quote(String value) {
        return '"' + escapeJson(value) + '"';
    }

    private static String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private static Map<String, String> parseJsonObject(String json) {
        String trimmed = json.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw new IllegalArgumentException("Invalid JSON object");
        }

        Map<String, String> fields = new LinkedHashMap<>();
        int index = 1;
        int end = trimmed.length() - 1;

        while (index < end) {
            index = skipWhitespace(trimmed, index);
            if (index < end && trimmed.charAt(index) == ',') {
                index++;
                index = skipWhitespace(trimmed, index);
            }
            if (index >= end) {
                break;
            }

            ParsedString key = parseJsonString(trimmed, index);
            index = skipWhitespace(trimmed, key.nextIndex());
            if (index >= end || trimmed.charAt(index) != ':') {
                throw new IllegalArgumentException("Missing ':' after JSON key");
            }
            index++;
            index = skipWhitespace(trimmed, index);

            String value;
            if (trimmed.charAt(index) == '"') {
                ParsedString parsedValue = parseJsonString(trimmed, index);
                value = parsedValue.value();
                index = parsedValue.nextIndex();
            } else {
                int valueStart = index;
                while (index < end && trimmed.charAt(index) != ',') {
                    index++;
                }
                String rawValue = trimmed.substring(valueStart, index).trim();
                value = "null".equals(rawValue) ? null : rawValue;
            }

            fields.put(key.value(), value);
        }

        return fields;
    }

    private static ParsedString parseJsonString(String json, int startIndex) {
        if (json.charAt(startIndex) != '"') {
            throw new IllegalArgumentException("Expected JSON string");
        }

        StringBuilder value = new StringBuilder();
        int index = startIndex + 1;
        while (index < json.length()) {
            char c = json.charAt(index++);
            if (c == '"') {
                return new ParsedString(value.toString(), index);
            }

            if (c != '\\') {
                value.append(c);
                continue;
            }

            if (index >= json.length()) {
                throw new IllegalArgumentException("Invalid JSON escape");
            }

            char escaped = json.charAt(index++);
            switch (escaped) {
                case '"' -> value.append('"');
                case '\\' -> value.append('\\');
                case '/' -> value.append('/');
                case 'b' -> value.append('\b');
                case 'f' -> value.append('\f');
                case 'n' -> value.append('\n');
                case 'r' -> value.append('\r');
                case 't' -> value.append('\t');
                case 'u' -> {
                    if (index + 4 > json.length()) {
                        throw new IllegalArgumentException("Invalid unicode escape");
                    }
                    String hex = json.substring(index, index + 4);
                    value.append((char) Integer.parseInt(hex, 16));
                    index += 4;
                }
                default -> throw new IllegalArgumentException("Unsupported JSON escape: " + escaped);
            }
        }

        throw new IllegalArgumentException("Unterminated JSON string");
    }

    private static int skipWhitespace(String value, int index) {
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        return index;
    }

    private record ParsedString(String value, int nextIndex) {}
}
